package kkam.app

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.util.Locale

/** Pleos TTS로 안내 문장을 읽는다. 앱 등록이 필요 없는 온디바이스 모드를 쓴다. */
class Speaker(context: Context) {
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private var ready = false
    private var pending: String? = null

    private val listener = object : EventListener {
        override fun onStart() = Log.i(TAG, "speech start").let {}
        override fun onReady() = Unit
        override fun onDone() = Log.i(TAG, "speech done").let {}
        override fun onStop() = Unit
        override fun onError(errorType: ErrorType) = Log.w(TAG, "speech error $errorType").let {}
        override fun onUpdatedRms(rms: Double) = Unit
    }

    fun start() {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.addEventListener(listener)
                tts.setLocale(Locale.KOREA)
                ready = true
                pending?.let(::speak)
                pending = null
            }

            override fun onFailure(errorType: ErrorType) {
                Log.w(TAG, "init failed $errorType")
            }
        })
    }

    fun speak(text: String) {
        if (ready) tts.speak(text, true) else pending = text
    }

    fun stop() {
        if (ready) tts.stop()
    }

    fun release() {
        if (ready) tts.removeEventListener(listener)
        tts.release()
    }

    private companion object {
        const val TAG = "KkamSpeaker"
    }
}
