package com.coze.kotlin_example

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.coze.openapi.client.audio.rooms.CreateRoomReq
import com.coze.openapi.client.audio.rooms.CreateRoomResp
import com.coze.openapi.client.chat.model.ChatEventType
import com.coze.openapi.client.connversations.message.model.Message
import com.coze.openapi.service.service.CozeAPI
import com.coze.kotlin_example.config.Config
import com.coze.kotlin_example.manager.CozeAPIManager
import com.coze.kotlin_example.utils.ToastUtil
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.ss.bytertc.engine.*
import com.ss.bytertc.engine.data.RemoteStreamKey
import com.ss.bytertc.engine.data.StreamIndex
import com.ss.bytertc.engine.handler.IRTCRoomEventHandler
import com.ss.bytertc.engine.handler.IRTCVideoEventHandler
import com.ss.bytertc.engine.type.*
import java.nio.ByteBuffer

class KotlinActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "KotlinActivity"
        private val mapper = ObjectMapper()
    }

    private lateinit var btnConnect: Button
    private lateinit var btnVideo: Button
    private lateinit var btnAudio: Button
    private lateinit var btnInterrupt: Button
    private lateinit var localViewContainer: FrameLayout
    private lateinit var roomIdInput: EditText
    private lateinit var messageTextView: TextView

    private var rtcVideo: RTCVideo? = null
    private var rtcRoom: RTCRoom? = null
    private var roomInfo: CreateRoomResp? = null
    private lateinit var cozeCli: CozeAPI

    private var isVideoEnabled = false
    private var isAudioEnabled = true
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        requestPermission()
        try {
            // 确保之前的实例被清理
            RTCVideo.destroyRTCVideo()

            Config.init(this)
            setContentView(R.layout.activity_main)
            title = "Coze Android RTC Demo"

            // 检查配置
            checkNotNull(Config.getInstance()) { "配置初始化失败" }

            // 初始化API客户端
            cozeCli = CozeAPIManager.getInstance().getCozeAPI();

            // 初始化UI
            initUI()

            // 检查权限
            checkAndRequestPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败", e)
            ToastUtil.showAlert(this, "初始化失败: ${e.message}")
            finish()
        }
    }

    private fun initUI() {
        localViewContainer = findViewById(R.id.local_view_container)

        btnConnect = findViewById<Button>(R.id.btn_connect).apply {
            setBackgroundColor(Color.GRAY)
        }
        btnVideo = findViewById<Button>(R.id.btn_video).apply {
            setBackgroundColor(Color.GRAY)
        }
        btnAudio = findViewById<Button>(R.id.btn_audio).apply {
            setBackgroundColor(Color.GRAY)
        }
        btnInterrupt = findViewById<Button>(R.id.btn_interrupt).apply {
            setBackgroundColor(Color.GRAY)
        }
        roomIdInput = findViewById(R.id.room_id_input)
        messageTextView = findViewById(R.id.message_text_view)

        connect()
        initVideoControl()
        initAudioControl()
        setBtnInterrupt()
    }

    private fun connect() {
        btnConnect.setOnClickListener {
            if (!isConnected) {
                doConnect()
            } else {
                disconnect()
            }
            isConnected = !isConnected
            btnConnect.apply {
                isEnabled = true
                text = if (isConnected) "断开连接" else "连接"
                setBackgroundColor(if (isConnected) Color.RED else Color.GRAY)
            }
        }
    }

    private fun disconnect() {
        rtcRoom?.apply {
            leaveRoom()
            destroy()
        }
        rtcVideo?.apply {
            stopVoice()
            stopVideo()
            RTCVideo.destroyRTCVideo()
            rtcVideo = null
        }
        ToastUtil.showAlert(this, "断开连接成功")
    }

    private fun doConnect() {
        // 禁用按钮防止重复点击
        btnConnect.apply {
            isEnabled = false
            text = "连接中"
            setBackgroundColor(Color.CYAN)
            setTextColor(Color.WHITE)
        }

        // 异步执行网络请求
        Thread {
            try {
                // 第一步，在coze创建房间
                val req = CreateRoomReq.builder()
                    .botID(Config.getInstance().botID)
                    .voiceID(Config.getInstance().voiceID)
                    .build()
                
                val roomInfoTemp = cozeCli.audio().rooms().create(req)

                // 在主线程中执行UI相关操作
                runOnUiThread {
                    try {
                        roomInfo = roomInfoTemp
                        // 检查权限
                        if (!checkAndRequestPermissions()) {
                            return@runOnUiThread  // 等待权限申请结果
                        }

                        // 第二步，创建引擎，并开启音视频采集
                        createRTCEngine()
                        startVoice()

                        // 设置本地预览窗口
                        val localTextureView = TextureView(this@KotlinActivity)
                        localViewContainer.removeAllViews()
                        localViewContainer.addView(localTextureView)

                        VideoCanvas().apply {
                            renderView = localTextureView
                            renderMode = VideoCanvas.RENDER_MODE_HIDDEN
                            // 设置本地视频渲染视图
                            rtcVideo?.setLocalVideoCanvas(StreamIndex.STREAM_INDEX_MAIN, this)
                        }

                        // 第三步，创建RTC房间
                        rtcRoom = rtcVideo?.createRTCRoom(roomInfo?.roomID)?.apply {
                            setRTCRoomEventHandler(rtcRoomEventHandler)
                            // 用户信息
                            val userInfo = UserInfo(roomInfo?.uid, "")
                            // 设置房间配置
                            val roomConfig = RTCRoomConfig(
                                ChannelProfile.CHANNEL_PROFILE_CHAT_ROOM,
                                true, true, true
                            )

                            // 第四步，加入房间
                            joinRoom(roomInfo?.token, userInfo, roomConfig)
                        }

                        roomIdInput.setText(roomInfo?.roomID)
                        ToastUtil.showShortToast(this@KotlinActivity, "连接成功")
                    } catch (e: Exception) {
                        ToastUtil.showAlert(this@KotlinActivity, "连接失败: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                // 在主线程处理错误
                runOnUiThread {
                    btnConnect.isEnabled = true
                    ToastUtil.showAlert(this@KotlinActivity, "连接失败: ${e.message}")
                }
            }
        }.start()
    }

    private fun setRemoteRenderView(uid: String) {
        val remoteTextureView = TextureView(this)
        VideoCanvas().apply {
            renderView = remoteTextureView
            renderMode = VideoCanvas.RENDER_MODE_HIDDEN

            val remoteStreamKey = RemoteStreamKey(roomInfo?.roomID, uid, StreamIndex.STREAM_INDEX_MAIN)
            // 设置远端视频渲染视图
            rtcVideo?.setRemoteVideoCanvas(remoteStreamKey, this)
        }
    }

    private fun removeRemoteView(uid: String) {
        val remoteStreamKey = RemoteStreamKey(roomInfo?.roomID, uid, StreamIndex.STREAM_INDEX_MAIN)
        rtcVideo?.setRemoteVideoCanvas(remoteStreamKey, null)
    }

    private val rtcRoomEventHandler = object : IRTCRoomEventHandler() {
        override fun onRoomStateChanged(roomId: String, uid: String, state: Int, extraInfo: String) {
            super.onRoomStateChanged(roomId, uid, state, extraInfo)
            Log.w(TAG, "roomId:$roomId, uid:$uid, state:$state, extraInfo:$extraInfo")
        }

        override fun onUserPublishStream(uid: String, type: MediaStreamType) {
            super.onUserPublishStream(uid, type)
            runOnUiThread {
                // 设置远端视频渲染视图
                setRemoteRenderView(uid)
            }
        }

        override fun onUserUnpublishStream(uid: String, type: MediaStreamType, reason: StreamRemoveReason) {
            super.onUserUnpublishStream(uid, type, reason)
            runOnUiThread {
                // 解除远端视频渲染视图绑定
                removeRemoteView(uid)
            }
        }

        override fun onLeaveRoom(stats: RTCRoomStats) {
            super.onLeaveRoom(stats)
            ToastUtil.showLongToast(this@KotlinActivity, "onLeaveRoom, stats:${stats}")
        }

        override fun onTokenWillExpire() {
            super.onTokenWillExpire()
            ToastUtil.showAlert(this@KotlinActivity, "Token Will Expire")
        }

        override fun onRoomMessageReceived(uid: String, message: String) {
            Log.w(TAG, "收到消息：$message")
        }

        override fun onRoomBinaryMessageReceived(uid: String, message: ByteBuffer) {
            Log.w(TAG, "收到消息：$message")
        }

        override fun onUserMessageReceived(uid: String, message: String) {
            try {
                val messageMap = mapper.readValue<Map<String, Any>>(
                    message,
                    object : TypeReference<Map<String, Any>>() {}
                )
                Log.d(TAG, "接收到原始消息: $messageMap")

                val jsonMap = messageMap.mapValues { (_, value) ->
                    when (value) {
                        is String -> value
                        else -> try {
                            mapper.writeValueAsString(value)
                        } catch (e: Exception) {
                            Log.e(TAG, "序列化value失败: ${e.message}")
                            null
                        }
                    }
                }

                when (jsonMap["event_type"]) {
                    ChatEventType.CONVERSATION_MESSAGE_DELTA.value -> {
                        val msg = mapper.readValue(
                            jsonMap["data"] as String,
                            Message::class.java
                        )
                        updateMessage(msg.content)
                    }
                    ChatEventType.CONVERSATION_MESSAGE_COMPLETED.value -> {
                        updateMessage("\n")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析消息失败", e)
            }
        }

        override fun onUserBinaryMessageReceived(uid: String, message: ByteBuffer) {
            Log.w(TAG, "收到消息：$message")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateMessage(content: String) {
        runOnUiThread {
            messageTextView?.let { textView ->
                // 追加新消息，并加上换行
                val currentText = textView.text.toString()
                textView.text = currentText + content

                // 自动滚动到底部
                val scrollAmount = textView.layout.getLineTop(textView.lineCount) - textView.height
                if (scrollAmount > 0) {
                    textView.scrollTo(0, scrollAmount)
                }
            }
        }
    }

    private fun initVideoControl() {
        btnVideo.setOnClickListener {
            if (rtcVideo != null) {
                if (isVideoEnabled) {
                    stopVideo()
                } else {
                    startVideo()
                }
            } else {
                ToastUtil.showAlert(this, "请先连接")
            }
        }
    }

    private fun stopVideo() {
        rtcVideo?.stopVideoCapture()
        btnVideo.text = "打开视频"
        isVideoEnabled = !isVideoEnabled
    }

    private fun startVideo() {
        rtcVideo?.startVideoCapture()
        btnVideo.text = "关闭视频"
        isVideoEnabled = !isVideoEnabled
    }

    private fun startVoice() {
        rtcVideo?.startAudioCapture()
        btnAudio.text = "静音"
        isAudioEnabled = !isAudioEnabled
    }

    private fun stopVoice() {
        rtcVideo?.stopAudioCapture()
        btnAudio.text = "打开声音"
        isAudioEnabled = !isAudioEnabled
    }

    private fun initAudioControl() {
        btnAudio.setOnClickListener {
            if (rtcVideo != null) {
                if (isAudioEnabled) {
                    stopVoice()
                } else {
                    startVoice()
                }
            } else {
                ToastUtil.showAlert(this, "请先连接")
            }
        }
    }

    private fun setBtnInterrupt() {
        btnInterrupt.setOnClickListener {
            if (rtcRoom == null) {
                ToastUtil.showAlert(this, "请先连接")
                return@setOnClickListener
            }
            try {
                val data = mapOf(
                    "id" to "event_1",
                    "event_type" to "conversation.chat.cancel",
                    "data" to "{}"
                )
                rtcRoom?.sendUserMessage(
                    roomInfo?.uid,
                    mapper.writeValueAsString(data),
                    MessageConfig.RELIABLE_ORDERED
                )
                ToastUtil.showShortToast(this, "打断成功")
            } catch (e: Exception) {
                ToastUtil.showShortToast(this, "打断失败")
            }
        }
    }

    override fun onDestroy() {
        try {
            rtcVideo?.apply {
                stopVoice()
                stopVideo()
                RTCVideo.destroyRTCVideo()
                rtcVideo = null
            }
            rtcRoom?.apply {
                leaveRoom()
                destroy()
                rtcRoom = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理资源失败", e)
        } finally {
            super.onDestroy()
        }
    }

    override fun onPause() {
        super.onPause()
        rtcVideo?.apply {
            try {
                stopVoice()
                stopVideo()
            } catch (e: Exception) {
                Log.e(TAG, "暂停采集失败", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rtcVideo?.apply {
            try {
                startVoice()
                startVideo()
            } catch (e: Exception) {
                Log.e(TAG, "恢复采集失败", e)
            }
        }
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )

        val permissionsToRequest = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        return if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                100
            )
            false  // 返回false表示有权限需要申请
        } else {
            true  // 返回true表示所有权限都已获取
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                ToastUtil.showAlert(this, "缺少必要权限，无法继续")
            }
        }
    }

    private fun createRTCEngine() {
        if (isFinishing || isDestroyed) {
            return
        }

        try {
            // 确保旧的实例被正确释放
            rtcVideo?.apply {
                try {
                    stopVideo()
                    stopVoice()
                    RTCVideo.destroyRTCVideo()
                    rtcVideo = null
                } catch (e: Exception) {
                    Log.e(TAG, "销毁旧实例失败", e)
                }
            }

            // 检查参数
            checkNotNull(roomInfo?.appID) { "AppID无效" }

            // 创建引擎前的延迟
            Thread.sleep(100)  // 给系统一些时间来处理之前的资源释放

            // 创建引擎
            rtcVideo = RTCVideo.createRTCVideo(
                applicationContext,  // 使用ApplicationContext而不是Activity
                roomInfo?.appID,
                object : IRTCVideoEventHandler() {
                    override fun onWarning(warn: Int) {
                        Log.w(TAG, "RTCVideo warning: $warn")
                    }

                    override fun onError(err: Int) {
                        Log.e(TAG, "RTCVideo error: $err")
                    }
                },
                null,
                null
            )

            checkNotNull(rtcVideo) { "创建RTC引擎失败" }

            ToastUtil.showShortToast(this, "创建引擎成功")
        } catch (e: Exception) {
            Log.e(TAG, "创建引擎失败", e)
            ToastUtil.showAlert(this, "创建引擎失败: ${e.message}")
            rtcVideo = null
        }
    }

    fun requestPermission() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET
        )

        val needPermission = permissions.any { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (needPermission) {
            requestPermissions(permissions, 22)
        }
    }
} 