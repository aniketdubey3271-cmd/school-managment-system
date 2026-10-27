package com.sms.mobile

import android.app.Application
import com.sms.mobile.data.ApiClient
import com.sms.mobile.data.TokenStore

class SmsApplication : Application() {

    lateinit var client: ApiClient
        private set

    override fun onCreate() {
        super.onCreate()
        client = ApiClient(TokenStore(this))
    }
}
