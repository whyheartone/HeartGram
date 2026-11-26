package com.exteragram.messenger.utils.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ExteraHttpClient {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
