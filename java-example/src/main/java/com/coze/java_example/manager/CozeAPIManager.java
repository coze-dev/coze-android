package com.coze.java_example.manager;

import com.coze.openapi.service.auth.TokenAuth;
import com.coze.openapi.service.service.CozeAPI;
import com.coze.java_example.config.Config;

public class CozeAPIManager {
    private static volatile CozeAPIManager instance;
    private CozeAPI cozeAPI;

    private CozeAPIManager() {
        // 初始化 CozeAPI
        initCozeAPI();
    }

    public static CozeAPIManager getInstance() {
        if (instance == null) {
            synchronized (CozeAPIManager.class) {
                if (instance == null) {
                    instance = new CozeAPIManager();
                }
            }
        }
        return instance;
    }

    private void initCozeAPI() {
        cozeAPI = new CozeAPI.Builder()
                .auth(new TokenAuth(Config.getInstance().getCozeAccessToken()))
                .baseURL(Config.getInstance().getBaseURL())
                .build();
    }

    public CozeAPI getCozeAPI() {
        return cozeAPI;
    }
} 