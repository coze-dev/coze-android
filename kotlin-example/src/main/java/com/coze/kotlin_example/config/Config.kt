package com.coze.kotlin_example.config

import android.content.Context
import com.coze.kotlin_example.R

class Config private constructor() {
    var cozeAccessToken: String = ""
        private set
    var baseURL: String = ""
        private set
    var botID: String = ""
        private set
    var voiceID: String = ""
        private set

    companion object {
        private var instance: Config? = null
        private var context: Context? = null

        fun init(appContext: Context) {
            context = appContext.applicationContext
            getInstance()
        }

        @Synchronized
        private fun initInstance(): Config {
            if (instance == null) {
                instance = Config().apply {
                    context?.let { ctx ->
                        cozeAccessToken = ctx.getString(R.string.coze_access_token)
                        baseURL = ctx.getString(R.string.base_url)
                        botID = ctx.getString(R.string.bot_id)
                        voiceID = ctx.getString(R.string.voice_id)
                    }
                }
            }
            return instance!!
        }

        fun getInstance(): Config {
            checkNotNull(context) { "Config must be initialized with context first" }
            return initInstance()
        }
    }
} 