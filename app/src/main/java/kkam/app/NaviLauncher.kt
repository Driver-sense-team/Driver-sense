package kkam.app

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/** 차량 기본 내비를 앞으로 띄운다. 출발할 때([CoachService])와 데모 "재생하면 내비 띄우기"에 쓴다. */
object NaviLauncher {
    /** Pleos 에뮬레이터의 기본 내비. 다른 차량에서는 이름이 다를 수 있다. */
    private val NAVI = ComponentName("ai.umos.maps.android.navigation.app", "ai.umos.maps.android.navigation.app.MainActivity")

    fun open(context: Context) {
        try {
            context.startActivity(Intent().setComponent(NAVI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w("KkamCoach", "navi not found", e)
        }
    }
}
