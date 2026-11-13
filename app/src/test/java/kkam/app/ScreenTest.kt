package kkam.app

import kkam.engine.model.Gear
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenTest {
    @Test
    fun `기어 P가 아니면 어떤 화면이든 주행 화면으로 돌아간다`() {
        Screen.entries.forEach { assertEquals(Screen.DRIVE, nextScreen(it, Gear.D)) }
    }

    @Test
    fun `기어 P에서 진단을 보고 있으면 리포트로 덮지 않는다`() {
        assertEquals(Screen.DIAGNOSE, nextScreen(Screen.DIAGNOSE, Gear.P))
        assertEquals(Screen.REPORT, nextScreen(Screen.DRIVE, Gear.P))
    }

    @Test
    fun `기어 P에서 교정 진행, Q&A, 모드 화면도 리포트로 덮지 않는다`() {
        listOf(Screen.PROGRESS, Screen.QNA, Screen.MODE, Screen.REPORT).forEach {
            assertEquals(it, nextScreen(it, Gear.P))
        }
    }

    @Test
    fun `주행 화면 말고는 모두 기어 P 전용이다`() {
        Screen.entries.filter { it != Screen.DRIVE }.forEach {
            assertEquals(Screen.DRIVE, openScreen(it, Gear.D))
            assertEquals(it, openScreen(it, Gear.P))
        }
    }

    @Test
    fun `진단은 기어 P에서만 열린다`() {
        assertEquals(Screen.DIAGNOSE, openScreen(Screen.DIAGNOSE, Gear.P))
        assertEquals(Screen.DRIVE, openScreen(Screen.DIAGNOSE, Gear.D))
        assertEquals(Screen.DRIVE, openScreen(Screen.DIAGNOSE, null)) // 아직 기어를 모름
        assertEquals(Screen.DRIVE, openScreen(Screen.DRIVE, Gear.D))
    }
}
