package com.navibrowser.ui.readaloud

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * 朗读模式管理器，封装 Android TTS。
 * 使用方：在 BrowserActivity 中持有单例，生命周期跟随 Activity。
 */
class ReadAloudManager(private val context: Context) {

    enum class State { IDLE, LOADING, PLAYING, PAUSED, ERROR }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingText: String? = null
    private var pendingSpeed: Float = 1.0f
    private var pendingPitch: Float = 1.0f

    var state: State = State.IDLE
        private set(v) { field = v; onStateChanged?.invoke(v) }

    var onStateChanged: ((State) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun init() {
        if (tts != null) return
        state = State.LOADING
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ttsReady = true
                state = State.IDLE
                pendingText?.let { speak(it, pendingSpeed, pendingPitch) }
                pendingText = null
            } else {
                state = State.ERROR
                onError?.invoke("TTS 初始化失败，请检查系统是否安装语音引擎")
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { state = State.PLAYING }
            override fun onDone(utteranceId: String?) { state = State.IDLE }
            @Deprecated("Deprecated in API 21")
            override fun onError(utteranceId: String?) { state = State.ERROR }
        })
    }

    /** 朗读指定文本；若 TTS 未就绪则缓存，初始化后自动播放 */
    fun speak(text: String, speed: Float = 1.0f, pitch: Float = 1.0f) {
        if (!ttsReady) {
            pendingText = text; pendingSpeed = speed; pendingPitch = pitch
            init(); return
        }
        tts?.setSpeechRate(speed)
        tts?.setPitch(pitch)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        state = State.PLAYING
    }

    fun pause() {
        tts?.stop()
        state = State.PAUSED
    }

    fun resume(text: String, speed: Float = 1.0f, pitch: Float = 1.0f) = speak(text, speed, pitch)

    fun stop() {
        tts?.stop()
        state = State.IDLE
    }

    fun isPlaying() = state == State.PLAYING

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        state = State.IDLE
    }
}
