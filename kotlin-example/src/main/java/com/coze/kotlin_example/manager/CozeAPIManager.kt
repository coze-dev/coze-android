package com.coze.kotlin_example.manager

import com.coze.openapi.service.auth.TokenAuth
import com.coze.openapi.service.service.CozeAPI
import com.coze.kotlin_example.config.Config

class CozeAPIManager private constructor() {
    private var cozeAPI: CozeAPI = initCozeAPI()

    private fun initCozeAPI(): CozeAPI {
        return CozeAPI.Builder()
            .auth(TokenAuth(Config.getInstance().cozeAccessToken))
            .baseURL(Config.getInstance().baseURL)
            .build()
    }

    fun getCozeAPI(): CozeAPI = cozeAPI

    companion object {
        @Volatile
        private var instance: CozeAPIManager? = null

        fun getInstance(): CozeAPIManager =
            instance ?: synchronized(this) {
                instance ?: CozeAPIManager().also { instance = it }
            }
    }
} 