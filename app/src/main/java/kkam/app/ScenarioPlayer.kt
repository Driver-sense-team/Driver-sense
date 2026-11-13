package kkam.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import kkam.engine.model.Scenario
import kkam.engine.model.SignalFrame
import kotlinx.serialization.json.Json

/** assets의 시나리오 파일을 읽고 녹화된 시각에 맞춰 프레임을 내보낸다. 모두 메인 스레드에서 돈다. */
class ScenarioPlayer(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val token = Any()
    private val json = Json { ignoreUnknownKeys = true }

    /** S1, S2, ..., S10 순서의 파일 이름. */
    fun fileNames(): List<String> = context.assets.list("").orEmpty()
        .filter { it.matches(Regex("""S\d+_.*\.json""")) }
        .sortedBy { it.drop(1).substringBefore('_').toInt() }

    /** assets 파일 글자. 경로 데모 파일도 이것으로 읽는다. */
    fun readAsset(fileName: String): String = context.assets.open(fileName).bufferedReader().use { it.readText() }

    /** 시나리오 파일의 프레임. */
    fun load(fileName: String): List<SignalFrame> =
        json.decodeFromString(Scenario.serializer(), readAsset(fileName)).frames

    fun play(fileName: String, onFrame: (SignalFrame) -> Unit, onEnd: () -> Unit) =
        playFrames(load(fileName), onFrame, onEnd)

    /**
     * 프레임을 t에 맞춰 내보낸다. 첫 프레임은 바로, 나머지는 첫 프레임과의 시각 차이만큼 뒤에.
     * 끝나고 [END_HOLD_MS] 뒤에 [onEnd].
     */
    fun playFrames(frames: List<SignalFrame>, onFrame: (SignalFrame) -> Unit, onEnd: () -> Unit) {
        stop()
        val first = frames.first().t
        for (frame in frames) {
            handler.postDelayed({ onFrame(frame) }, token, frame.t - first)
        }
        handler.postDelayed(onEnd, token, frames.last().t - first + END_HOLD_MS)
    }

    fun stop() = handler.removeCallbacksAndMessages(token)

    private companion object {
        const val END_HOLD_MS = 1000L
    }
}
