package com.sms.mobile.data

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.sms.mobile.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the backend. Every request carries the access token; when the server answers 401
 * the refresh token is swapped for a new pair and the request is retried once. If the refresh
 * token is also rejected, the session is cleared and the app returns to the login screen.
 */
class ApiClient(val store: TokenStore) {

    val baseUrl: String = BuildConfig.API_BASE_URL

    private val gson = Gson()
    private val refreshLock = Any()

    private val baseHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** No token handling; used only for the refresh call itself. */
    private val refreshApi: SmsApi = retrofit(baseHttp)

    val api: SmsApi = retrofit(
        baseHttp.newBuilder()
            .addInterceptor { chain -> addToken(chain) }
            .authenticator { _, response -> refreshAndRetry(response) }
            .build()
    )

    private fun retrofit(client: OkHttpClient): SmsApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(SmsApi::class.java)

    private fun addToken(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = store.current?.accessToken
        if (token == null || isAuthCall(request) || request.header(AUTHORIZATION) != null) {
            return chain.proceed(request)
        }
        return chain.proceed(request.withToken(token))
    }

    private fun refreshAndRetry(response: Response): Request? {
        val request = response.request
        if (isAuthCall(request) || response.priorResponse != null) return null
        val sentToken = request.header(AUTHORIZATION)?.removePrefix("Bearer ")
        synchronized(refreshLock) {
            val session = store.current ?: return null
            if (session.accessToken != sentToken) {
                // Another request refreshed while this one was waiting.
                return request.withToken(session.accessToken)
            }
            val result = try {
                refreshApi.refreshCall(RefreshRequest(session.refreshToken)).execute()
            } catch (e: IOException) {
                return null
            }
            val tokens = result.body()
            if (!result.isSuccessful || tokens == null) {
                if (result.code() == 401) store.clear()
                return null
            }
            store.save(tokens)
            return request.withToken(tokens.accessToken)
        }
    }

    private fun Request.withToken(token: String): Request =
        newBuilder().header(AUTHORIZATION, "Bearer $token").build()

    private fun isAuthCall(request: Request) = request.url.encodedPath.contains("/api/v1/auth/")

    private companion object {
        const val AUTHORIZATION = "Authorization"
    }
}

/** A failed call, with the server's message and any per-field errors for forms. */
class ApiException(
    message: String,
    val status: Int = 0,
    val fieldErrors: Map<String, String> = emptyMap(),
) : Exception(message)

private val errorGson = Gson()

fun Throwable.toApiException(): ApiException = when (this) {
    is ApiException -> this
    is HttpException -> {
        val body = try {
            response()?.errorBody()?.string()
        } catch (e: IOException) {
            null
        }
        val parsed = body?.let { runCatching { errorGson.fromJson(it, ApiError::class.java) }.getOrNull() }
        val message = parsed?.message?.takeIf { it.isNotBlank() } ?: defaultMessage(code())
        ApiException(message, code(), parsed?.fieldErrors.orEmpty())
    }
    is JsonParseException -> ApiException("The server sent an unexpected reply. Check the server address.")
    is IOException -> ApiException("Can't reach the server. Check your internet connection.")
    else -> ApiException(message ?: "Something went wrong.")
}

private fun defaultMessage(status: Int): String = when (status) {
    401 -> "Your session has expired. Sign in again."
    403 -> "You don't have access to this."
    404 -> "Not found."
    409 -> "This is still in use, so it can't be deleted."
    413 -> "File is too large (max 10 MB)."
    in 500..599 -> "The server had a problem. Try again shortly."
    else -> "Request failed ($status)."
}
