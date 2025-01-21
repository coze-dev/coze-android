package com.coze.java_example.config;

import android.content.Context;


import com.coze.java_example.R;

import lombok.Getter;

@Getter
public class Config {
    private String cozeAccessToken;
    private String baseURL;
    private String botID;
    private String voiceID;

    private static Config Instance;
    private static Context context;

    public static void init(Context appContext) {
        context = appContext.getApplicationContext();
        getInstance();
    }

    private static Config initInstance() {
        if (Instance == null) {
            synchronized (Config.class) {
                if (Instance == null) {
                    Instance = new Config();
                }
                // 从 strings.xml 或其他资源文件中读取配置
                Instance.cozeAccessToken = context.getString(R.string.coze_access_token);
                Instance.baseURL = context.getString(R.string.base_url);
                Instance.botID = context.getString(R.string.bot_id);
                Instance.voiceID = context.getString(R.string.voice_id);
            }
        }
        return Instance;
    }

    private Config() {
    }

    public static Config getInstance() {
        if (context == null) {
            throw new IllegalStateException("Config must be initialized with context first");
        }
        return initInstance();
    }
}
