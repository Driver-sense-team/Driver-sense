package kkam.app

import android.content.Context

/**
 * 운전 코치 켬/끔. 사용자 요청으로 기획서 4.3의 4개 모드(진단·교정·연수·조용)를 이 두 값으로 줄였다.
 * - 켬: 목적지까지 가는 동안 음성 안내를 하고 판정을 기록한다. 도착하면 경로 리포트를 보여준다.
 * - 끔: 음성·카드·팝업 안내만 끈다. 판정은 계속 기록한다(진단→교정→졸업과 리포트가 끊기지 않게).
 */
enum class DriveMode {
    ON,
    OFF,
    ;

    companion object {
        /** 처음 켰을 때. 앱을 깔고 바로 코치를 받게 켬으로 둔다. */
        val DEFAULT = ON

        /**
         * 저장된 이름을 값으로. 모르는 값이면 [DEFAULT].
         * 예전 4개 모드 값은 "주행 중 음성 안내를 했는가"로 옮긴다:
         * 교정·연수(안내함) → 켬, 진단·조용(안내 안 함) → 끔.
         */
        fun fromName(name: String?): DriveMode = when (name) {
            ON.name, "CORRECT", "TRAINING" -> ON
            OFF.name, "DIAGNOSE", "QUIET" -> OFF
            else -> DEFAULT
        }
    }
}

/** 주행 중에 음성 안내를 하는가. */
fun guidesOn(mode: DriveMode): Boolean = mode == DriveMode.ON

/**
 * 코치 켬/끔을 SharedPreferences에 저장한다. 앱을 다시 켜도 남는다.
 * 세션은 프레임마다 [isCoachOn]을 읽으므로 주행 중에 바꿔도 바로 반영된다.
 * TODO(엔진 모드 입력, spec v0.3 · AI 담당 P6): 엔진이 모드를 받게 되면 켬/끔을 어떻게 넘길지 맞춘다.
 */
class ModeStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): DriveMode = DriveMode.fromName(prefs.getString(KEY_MODE, null))

    fun save(mode: DriveMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    /** 코치가 켜져 있는가. 경로 코치 등 다른 곳에서는 이 함수만 쓰면 된다. */
    fun isCoachOn(): Boolean = load() == DriveMode.ON

    private companion object {
        const val PREFS = "kkam_mode"
        const val KEY_MODE = "mode"
    }
}
