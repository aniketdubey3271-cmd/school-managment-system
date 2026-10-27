package com.sms.mobile.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The signed-in session, kept in the app's private storage so users stay signed in between
 * launches. A null session means signed out; the UI switches to the login screen.
 */
class TokenStore(context: Context) {

    data class Session(val accessToken: String, val refreshToken: String, val username: String, val role: String)

    private val prefs = context.getSharedPreferences("sms_session", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val session: StateFlow<Session?> = state.asStateFlow()

    val current: Session?
        get() = state.value

    @Synchronized
    fun save(tokens: TokenResponse) {
        val session = Session(tokens.accessToken, tokens.refreshToken, tokens.user.username, tokens.user.role)
        prefs.edit()
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_ROLE, session.role)
            .apply()
        state.value = session
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
        state.value = null
    }

    private fun read(): Session? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val role = prefs.getString(KEY_ROLE, null) ?: return null
        return Session(access, refresh, username, role)
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_USERNAME = "username"
        const val KEY_ROLE = "role"
    }
}
