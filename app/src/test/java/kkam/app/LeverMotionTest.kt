package kkam.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeverMotionTest {
    @Test
    fun `회전 깜빡이는 왼쪽이면 왼쪽 레버 아래, 오른쪽이면 위`() {
        assertEquals(LeverMotion.LEFT_DOWN, leverMotion(GuideKind.TURN_SIGNAL, right = false))
        assertEquals(LeverMotion.LEFT_UP, leverMotion(GuideKind.TURN_SIGNAL, right = true))
    }

    @Test
    fun `차선 변경은 방향에 따라 살짝 눌렀다 놓는다`() {
        assertEquals(LeverMotion.LEFT_TAP_UP, leverMotion(GuideKind.LANE_CHANGE, right = true))
        assertEquals(LeverMotion.LEFT_TAP_DOWN, leverMotion(GuideKind.LANE_CHANGE, right = false))
    }

    @Test
    fun `전조등은 왼쪽 레버 끝을 돌리고 와이퍼는 오른쪽 레버를 내린다`() {
        assertEquals(LeverMotion.LEFT_TWIST, leverMotion(GuideKind.HEADLIGHT, right = false))
        assertEquals(LeverMotion.RIGHT_DOWN, leverMotion(GuideKind.WIPER, right = false))
        assertTrue(LeverMotion.RIGHT_DOWN.rightLever)
    }

    @Test
    fun `레버 그림은 레버를 쓰는 안내를 켜야 할 때(주황)만 보여준다`() {
        assertEquals(LeverMotion.RIGHT_DOWN, leverFor(GuideKind.WIPER, right = false, warn = true))
        assertNull(leverFor(GuideKind.WIPER, right = false, warn = false))
        assertEquals(LeverMotion.LEFT_DOWN, leverFor(GuideKind.TURN_SIGNAL, right = false, warn = true))
        assertEquals(LeverMotion.LEFT_UP, leverFor(GuideKind.TURN_SIGNAL, right = true, warn = true))
        assertNull(leverFor(GuideKind.RED_RIGHT_TURN, right = true, warn = true))
        assertNull(leverFor(GuideKind.DEFOG, right = false, warn = true))
    }

    @Test
    fun `레버를 쓰지 않는 안내는 그림이 없고 해야 할 일을 말한다`() {
        assertNull(leverMotion(GuideKind.RED_RIGHT_TURN, right = true))
        assertEquals("정지선에서 완전히 멈췄다가 우회전하세요", previewHowTo(GuideKind.RED_RIGHT_TURN, right = true))
        assertEquals(LeverMotion.LEFT_TWIST.howTo, previewHowTo(GuideKind.HEADLIGHT, right = false))
    }

    @Test
    fun `김서림은 레버가 아니라 공조 버튼이라 그림 대신 할 일을 말한다`() {
        assertNull(leverMotion(GuideKind.DEFOG, right = false))
        assertEquals("앞유리 성에 제거 버튼을 누르고, 공조를 외기 순환으로 바꾸세요", previewHowTo(GuideKind.DEFOG, right = false))
    }
}
