package com.coze.java_example;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.coze.openapi.client.audio.rooms.CreateRoomReq;
import com.coze.openapi.client.audio.rooms.CreateRoomResp;
import com.coze.openapi.client.chat.model.ChatEventType;
import com.coze.openapi.client.connversations.message.model.Message;
import com.coze.openapi.service.auth.TokenAuth;
import com.coze.openapi.service.service.CozeAPI;
import com.coze.java_example.config.Config;
import com.coze.java_example.utils.ToastUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ss.bytertc.engine.RTCRoom;
import com.ss.bytertc.engine.RTCRoomConfig;
import com.ss.bytertc.engine.RTCVideo;
import com.ss.bytertc.engine.UserInfo;
import com.ss.bytertc.engine.VideoCanvas;
import com.ss.bytertc.engine.data.RemoteStreamKey;
import com.ss.bytertc.engine.data.StreamIndex;
import com.ss.bytertc.engine.handler.IRTCRoomEventHandler;
import com.ss.bytertc.engine.handler.IRTCVideoEventHandler;
import com.ss.bytertc.engine.type.ChannelProfile;
import com.ss.bytertc.engine.type.MediaStreamType;
import com.ss.bytertc.engine.type.MessageConfig;
import com.ss.bytertc.engine.type.RTCRoomStats;
import com.ss.bytertc.engine.type.StreamRemoveReason;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class JavaActivity extends AppCompatActivity {
    private static final String TAG = "JavaActivity";
    private static final ObjectMapper mapper = new ObjectMapper();

    private Button btnConnect;
    private Button btnVideo;
    private Button btnAudio;
    private Button btnInterrupt;

    private FrameLayout localViewContainer;
    private EditText roomIdInput;

    private RTCVideo rtcVideo;
    private RTCRoom rtcRoom;
    private CreateRoomResp roomInfo;

    private CozeAPI cozeCli;

    private TextView messageTextView;

    private boolean isVideoEnabled = false;
    private boolean isAudioEnabled = true;

    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        requestPermission();
        try {
            // 确保之前的实例被清理
            RTCVideo.destroyRTCVideo();

            Config.init(this);
            setContentView(R.layout.activity_main);
            setTitle("Coze Android RTC Demo");

            // 检查配置
            if (Config.getInstance() == null) {
                throw new RuntimeException("配置初始化失败");
            }

            // 初始化API客户端
            cozeCli = new CozeAPI.Builder()
                    .baseURL(Config.getInstance().getBaseURL())
                    .auth(new TokenAuth(Config.getInstance().getCozeAccessToken()))
                    .build();

            // 初始化UI
            initUI();

            // 检查权限
            checkAndRequestPermissions();
        } catch (Exception e) {
            Log.e(TAG, "初始化失败", e);
            Toast.makeText(this, "初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initUI() {
        localViewContainer = findViewById(R.id.local_view_container);

        btnConnect = findViewById(R.id.btn_connect);
        btnConnect.setBackgroundColor(Color.GRAY);
        btnVideo = findViewById(R.id.btn_video);
        btnVideo.setBackgroundColor(Color.GRAY);
        btnAudio = findViewById(R.id.btn_audio);
        btnAudio.setBackgroundColor(Color.GRAY);
        btnInterrupt = findViewById(R.id.btn_interrupt);
        btnInterrupt.setBackgroundColor(Color.GRAY);
        roomIdInput = findViewById(R.id.room_id_input);
        messageTextView = findViewById(R.id.message_text_view);

        connect();
        initVideoControl();
        initAudioControl();
        setBtnInterrupt();
    }

    private void connect(){
        btnConnect.setOnClickListener(v -> {
            if (!isConnected){
                doConnect();
            }else {
                disconnect();
            }
            isConnected = !isConnected;
            if (isConnected) {
                btnConnect.setEnabled(true);
                btnConnect.setText("断开连接");
                btnConnect.setBackgroundColor(Color.RED);
            } else {
                btnConnect.setEnabled(true);
                btnConnect.setText("连接");
                btnConnect.setBackgroundColor(Color.GRAY);
            }
        });
    }

    private void disconnect(){
        if (rtcRoom != null) {
            rtcRoom.leaveRoom();
            rtcRoom.destroy();
        }
        if (rtcVideo != null){
            stopVoice();
            stopVideo();
            RTCVideo.destroyRTCVideo();
            rtcVideo = null;
        }
        ToastUtil.showAlert(this, "断开连接成功");
    }

    private void doConnect() {
        // 禁用按钮防止重复点击
        btnConnect.setEnabled(false);
        btnConnect.setText("连接中");
        btnConnect.setBackgroundColor(Color.CYAN);
        btnConnect.setTextColor(Color.WHITE);
        // 异步执行网络请求
        new Thread(() -> {
            CreateRoomResp roomInfoTemp = null;
            try {
                // 第一步，在coze创建房间
                CreateRoomReq req = CreateRoomReq.builder()
                        .botID(Config.getInstance().getBotID())
                        .voiceID(Config.getInstance().getVoiceID())
                        .build();
                roomInfoTemp = cozeCli.audio().rooms().create(req);
                final CreateRoomResp finalRoomInfo = roomInfoTemp;

                // 在主线程中执行UI相关操作
                runOnUiThread(() -> {
                    try {
                        roomInfo = finalRoomInfo;
                        // 检查权限
                        if (!checkAndRequestPermissions()) {
                            return;  // 等待权限申请结果
                        }

                        // 第二步，创建引擎，并开启音视频采集
                        createRTCEngine();
                        startVoice();

                        // 设置本地预览窗口
                        TextureView localTextureView = new TextureView(JavaActivity.this);
                        localViewContainer.removeAllViews();
                        localViewContainer.addView(localTextureView);

                        VideoCanvas videoCanvas = new VideoCanvas();
                        videoCanvas.renderView = localTextureView;
                        videoCanvas.renderMode = VideoCanvas.RENDER_MODE_HIDDEN;
                        // 设置本地视频渲染视图
                        rtcVideo.setLocalVideoCanvas(StreamIndex.STREAM_INDEX_MAIN, videoCanvas);

                        // 第三步，创建RTC房间
                        rtcRoom = rtcVideo.createRTCRoom(roomInfo.getRoomID());
                        rtcRoom.setRTCRoomEventHandler(rtcRoomEventHandler);
                        // 用户信息
                        UserInfo userInfo = new UserInfo(roomInfo.getUid(), "");
                        // 设置房间配置
                        RTCRoomConfig roomConfig = new RTCRoomConfig(
                                ChannelProfile.CHANNEL_PROFILE_CHAT_ROOM,
                                true, true, true);

                        // 第四步，加入房间
                        rtcRoom.joinRoom(roomInfo.getToken(), userInfo, roomConfig);

                        roomIdInput.setText(roomInfo.getRoomID());
                        ToastUtil.showShortToast(JavaActivity.this, "连接成功");
                    } catch (Exception e) {
                        ToastUtil.showAlert(JavaActivity.this, "连接失败: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                // 在主线程处理错误
                runOnUiThread(() -> {
                    btnConnect.setEnabled(true);
                    ToastUtil.showAlert(JavaActivity.this, "连接失败: " + e.getMessage());
                });
            }
        }).start();
    }

    private void setRemoteRenderView(String uid) {
        TextureView remoteTextureView = new TextureView(this);
        VideoCanvas videoCanvas = new VideoCanvas();
        videoCanvas.renderView = remoteTextureView;
        videoCanvas.renderMode = VideoCanvas.RENDER_MODE_HIDDEN;

        RemoteStreamKey remoteStreamKey = new RemoteStreamKey(roomInfo.getRoomID(), uid, StreamIndex.STREAM_INDEX_MAIN);
        // 设置远端视频渲染视图
        rtcVideo.setRemoteVideoCanvas(remoteStreamKey, videoCanvas);
    }

    private void removeRemoteView(String uid) {
        RemoteStreamKey remoteStreamKey = new RemoteStreamKey(roomInfo.getRoomID(), uid, StreamIndex.STREAM_INDEX_MAIN);
        rtcVideo.setRemoteVideoCanvas(remoteStreamKey, null);
    }

    IRTCRoomEventHandler rtcRoomEventHandler = new IRTCRoomEventHandler() {
        @Override
        public void onRoomStateChanged(String roomId, String uid, int state, String extraInfo) {
            super.onRoomStateChanged(roomId, uid, state, extraInfo);
            Log.w(TAG, String.format("roomId:%s, uid:%s, state:%d, extraInfo:%s", roomId, uid, state, extraInfo));
        }

        @Override
        public void onUserPublishStream(String uid, MediaStreamType type) {
            super.onUserPublishStream(uid, type);
            runOnUiThread(() -> {
                // 设置远端视频渲染视图
                setRemoteRenderView(uid);
            });
        }

        @Override
        public void onUserUnpublishStream(String uid, MediaStreamType type, StreamRemoveReason reason) {
            super.onUserUnpublishStream(uid, type, reason);
            runOnUiThread(() -> {
                // 解除远端视频渲染视图绑定
                removeRemoteView(uid);
            });
        }

        @Override
        public void onLeaveRoom(RTCRoomStats stats) {
            super.onLeaveRoom(stats);
            ToastUtil.showLongToast(JavaActivity.this, "onLeaveRoom, stats:" + stats.toString());
        }

        @Override
        public void onTokenWillExpire() {
            super.onTokenWillExpire();
            ToastUtil.showAlert(JavaActivity.this, "Token Will Expire");
        }
        @Override
        public void onRoomMessageReceived(String uid, String message) {
            Log.w(TAG, "收到消息：" + message);
        }
        @Override

        public void onRoomBinaryMessageReceived(String uid, ByteBuffer message) {
            Log.w(TAG, "收到消息：" + message);

        }
        @Override
        public void onUserMessageReceived(String uid, String message) {
            try {
                Map<String, Object> messageMap = mapper.readValue(message, new TypeReference<Map<String, Object>>() {});
                Log.d(TAG, "接收到原始消息: " + messageMap);

                Map<String, String> jsonMap = new HashMap<>();
                for (Map.Entry<String, Object> entry : messageMap.entrySet()) {
                    try {
                        if (entry.getValue() instanceof String) {
                            jsonMap.put(entry.getKey(), (String) entry.getValue());
                            continue;
                        }
                        String jsonValue = mapper.writeValueAsString(entry.getValue());
                        jsonMap.put(entry.getKey(), jsonValue);
                    } catch (JsonProcessingException e) {
                        Log.e(TAG, "序列化value失败: " + entry.getKey(), e);
                    }
                }
                if (ChatEventType.CONVERSATION_MESSAGE_DELTA.getValue().equals(jsonMap.get("event_type"))){
                    Message msg = mapper.readValue(jsonMap.get("data"), Message.class);
                    updateMessage(msg.getContent());
                } else if (ChatEventType.CONVERSATION_MESSAGE_COMPLETED.getValue().equals(jsonMap.get("event_type"))) {
                    updateMessage("\n");
                }
            } catch (JsonProcessingException e) {
                Log.e(TAG, "解析消息失败", e);
            }
        }
        @Override
        public void onUserBinaryMessageReceived(String uid, ByteBuffer message) {
            Log.w(TAG, "收到消息：" + message);

        }

    };

    @Override
    protected void onDestroy() {
        try {
            if (rtcVideo != null) {
                stopVoice();
                stopVideo();
                RTCVideo.destroyRTCVideo();
                rtcVideo = null;
            }
            if (rtcRoom != null) {
                rtcRoom.leaveRoom();
                rtcRoom.destroy();
                rtcRoom = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "清理资源失败", e);
        } finally {
            super.onDestroy();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (rtcVideo != null) {
            try {
                stopVoice();
                stopVideo();
            } catch (Exception e) {
                Log.e(TAG, "暂停采集失败", e);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rtcVideo != null) {
            try {
                startVoice();
                startVideo();
            } catch (Exception e) {
                Log.e(TAG, "恢复采集失败", e);
            }
        }
    }

    private boolean checkAndRequestPermissions() {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.INTERNET,
                Manifest.permission.MODIFY_AUDIO_SETTINGS
        };

        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    100);
            return false;  // 返回false表示有权限需要申请
        }
        return true;  // 返回true表示所有权限都已获取
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                ToastUtil.showAlert(this, "缺少必要权限，无法继续");
            }
        }
    }

    private void createRTCEngine() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        try {
            // 确保旧的实例被正确释放
            if (rtcVideo != null) {
                try {
                    stopVideo();
                    stopVoice();
                    RTCVideo.destroyRTCVideo();
                    rtcVideo = null;
                } catch (Exception e) {
                    Log.e(TAG, "销毁旧实例失败", e);
                }
            }

            // 检查参数
            if (roomInfo == null || roomInfo.getAppID() == null
                    || roomInfo.getAppID().isEmpty()) {
                ToastUtil.showAlert(this, "AppID无效");
                return;
            }

            // 创建引擎前的延迟
            Thread.sleep(100);  // 给系统一些时间来处理之前的资源释放

            // 创建引擎
            rtcVideo = RTCVideo.createRTCVideo(
                    getApplicationContext(),  // 使用ApplicationContext而不是Activity
                    roomInfo.getAppID(),
                    new IRTCVideoEventHandler() {
                        @Override
                        public void onWarning(int warn) {
                            Log.w(TAG, "RTCVideo warning: " + warn);
                        }

                        @Override
                        public void onError(int err) {
                            Log.e(TAG, "RTCVideo error: " + err);
                        }
                    },
                    null,
                    null
            );

            if (rtcVideo == null) {
                throw new RuntimeException("创建RTC引擎失败");
            }


            ToastUtil.showShortToast(this, "创建引擎成功");
        } catch (Exception e) {
            Log.e(TAG, "创建引擎失败", e);
            ToastUtil.showAlert(this, "创建引擎失败: " + e.getMessage());
            rtcVideo = null;
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateMessage(String content) {
        runOnUiThread(() -> {
            if (messageTextView != null) {
                // 追加新消息，并加上换行
                String currentText = messageTextView.getText().toString();
                messageTextView.setText(currentText + content);

                // 自动滚动到底部
                final int scrollAmount = messageTextView.getLayout().getLineTop(messageTextView.getLineCount()) - messageTextView.getHeight();
                if (scrollAmount > 0) {
                    messageTextView.scrollTo(0, scrollAmount);
                }
            }
        });
    }

    private void initVideoControl() {
        btnVideo.setOnClickListener(v -> {
            if (rtcVideo != null) {
                if (isVideoEnabled) {
                    stopVideo();
                } else {
                    startVideo();
                }
            }else{
                ToastUtil.showAlert(this, "请先连接");
            }
        });
    }

    private void stopVideo(){
        rtcVideo.stopVideoCapture();
        btnVideo.setText("打开视频");
        isVideoEnabled = !isVideoEnabled;
    }

    private void startVideo(){
        rtcVideo.startVideoCapture();
        btnVideo.setText("关闭视频");
        isVideoEnabled = !isVideoEnabled;
    }

    private void startVoice(){
        rtcVideo.startAudioCapture();
        btnAudio.setText("静音");
        isAudioEnabled = !isAudioEnabled;
    }

    private void stopVoice(){
        rtcVideo.stopAudioCapture();
        btnAudio.setText("打开声音");
        isAudioEnabled = !isAudioEnabled;
    }

    private void initAudioControl() {
        btnAudio.setOnClickListener(v -> {
            if (rtcVideo != null) {
                if (isAudioEnabled) {
                    stopVoice();
                } else {
                    startVoice();
                }
            }else {
                ToastUtil.showAlert(this, "请先连接");
            }
        });
    }

    private void setBtnInterrupt(){
        btnInterrupt.setOnClickListener(v -> {
            if(rtcRoom == null){
                ToastUtil.showAlert(this,"请先连接");
                return;
            }
            try {
                Map<String, String> data = new HashMap<>();
                data.put("id", "event_1");
                data.put("event_type", "conversation.chat.cancel");
                data.put("data", "{}");
                rtcRoom.sendUserMessage(roomInfo.getUid(), mapper.writeValueAsString(data), MessageConfig.RELIABLE_ORDERED);
                ToastUtil.showShortToast(this, "打断成功");
            }catch (Exception e){
                ToastUtil.showShortToast(this, "打断失败");
            }

        });
    }

    public void requestPermission() {
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.INTERNET

        };
        boolean needPermission = false;

        for (String permission : PERMISSIONS_STORAGE) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needPermission = true;
                break;
            }
        }
        if(needPermission){
            requestPermissions(PERMISSIONS_STORAGE, 22);
        }

    }
}