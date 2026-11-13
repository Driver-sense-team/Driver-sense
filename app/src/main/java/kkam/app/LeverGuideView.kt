package kkam.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * "어떻게 켜는지" 그림. 운전대와 양쪽 레버를 그리고, 움직여야 할 레버만 진하게 칠해 그 방향으로 반복해서 움직인다.
 * 그림 파일 없이 Canvas로 그린다. 색은 카드 글자색(ink)을 받아 쓴다.
 */
class LeverGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var motion: LeverMotion? = null
    private var ink = 0xFF0B0E13.toInt()
    private var progress = 0f
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arrow = Path()
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = MOVE_MS
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    /** motion이 null이면 숨긴다. */
    fun show(motion: LeverMotion?, inkColor: Int) {
        val changed = motion != this.motion || inkColor != ink
        this.motion = motion
        ink = inkColor
        visibility = if (motion == null) GONE else VISIBLE
        if (motion == null) animator.cancel() else if (!animator.isStarted && isAttachedToWindow) animator.start()
        if (changed) invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (motion != null) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val motion = motion ?: return
        val cx = width / 2f
        val cy = height * 0.55f
        val radius = min(width * 0.26f, height * 0.40f)
        val thick = radius * 0.17f
        val muted = (ink and 0x00FFFFFF) or (MUTED_ALPHA shl 24)

        // 운전대: 바퀴 테와 가운데.
        stroke.color = muted
        stroke.strokeWidth = thick
        canvas.drawCircle(cx, cy, radius, stroke)
        fill.color = muted
        canvas.drawCircle(cx, cy, radius * 0.28f, fill)

        // 레버 두 개. 움직일 레버만 진하게, 움직임을 반영한다.
        val leverLength = radius * 1.35f
        val angle = when (motion) {
            LeverMotion.LEFT_TAP_DOWN, LeverMotion.LEFT_TAP_UP -> TAP_DEGREES
            LeverMotion.LEFT_TWIST -> 0f
            else -> FULL_DEGREES
        } * progress
        val down = motion == LeverMotion.LEFT_DOWN || motion == LeverMotion.LEFT_TAP_DOWN || motion == LeverMotion.RIGHT_DOWN
        val leftPivotX = cx - radius * 0.32f
        val rightPivotX = cx + radius * 0.32f

        val leftAngle = if (motion.rightLever) 0f else angle
        val rightAngle = if (motion.rightLever) angle else 0f
        val leftTip = drawLever(canvas, leftPivotX, cy, -1f, leftAngle, down, leverLength, thick, if (motion.rightLever) muted else ink)
        val rightTip = drawLever(canvas, rightPivotX, cy, 1f, rightAngle, down, leverLength, thick, if (motion.rightLever) ink else muted)

        val tip = if (motion.rightLever) rightTip else leftTip
        when (motion) {
            LeverMotion.LEFT_TWIST -> drawTwist(canvas, tip.first, tip.second, thick)
            else -> drawDirection(canvas, tip.first, tip.second + (if (down) 1 else -1) * thick * 2.2f, down, thick)
        }
    }

    /** 레버 하나를 그리고 끝 좌표를 돌려준다. side: 왼쪽 -1, 오른쪽 1. */
    private fun drawLever(
        canvas: Canvas, pivotX: Float, pivotY: Float, side: Float, degrees: Float, down: Boolean,
        length: Float, thick: Float, color: Int,
    ): Pair<Float, Float> {
        val radians = Math.toRadians(degrees.toDouble())
        val tipX = pivotX + side * length * cos(radians).toFloat()
        val tipY = pivotY + (if (down) 1 else -1) * length * sin(radians).toFloat()
        stroke.color = color
        stroke.strokeWidth = thick * 1.15f
        canvas.drawLine(pivotX, pivotY, tipX, tipY, stroke)
        return tipX to tipY
    }

    /** 레버 끝 바깥에 움직일 방향 화살표. */
    private fun drawDirection(canvas: Canvas, x: Float, y: Float, down: Boolean, thick: Float) {
        val size = thick * 1.3f
        val dir = if (down) 1 else -1
        arrow.reset()
        arrow.moveTo(x - size, y - dir * size * 0.6f)
        arrow.lineTo(x, y + dir * size * 0.6f)
        arrow.lineTo(x + size, y - dir * size * 0.6f)
        stroke.color = ink
        stroke.strokeWidth = thick * 0.6f
        canvas.drawPath(arrow, stroke)
    }

    /** 레버 끝 손잡이를 돌리는 모양: 링과 돌아가는 표시. */
    private fun drawTwist(canvas: Canvas, x: Float, y: Float, thick: Float) {
        val ring = thick * 1.6f
        stroke.color = ink
        stroke.strokeWidth = thick * 0.5f
        canvas.drawCircle(x, y, ring, stroke)
        val markAngle = Math.toRadians((-90 + 300 * progress).toDouble())
        fill.color = ink
        canvas.drawCircle(x + ring * cos(markAngle).toFloat(), y + ring * sin(markAngle).toFloat(), thick * 0.55f, fill)
        // 돌리는 방향 (앞으로) 표시: 링 바깥 호.
        val outer = ring * 1.7f
        stroke.strokeWidth = thick * 0.45f
        canvas.drawArc(RectF(x - outer, y - outer, x + outer, y + outer), -150f, 120f, false, stroke)
    }

    private companion object {
        const val MOVE_MS = 700L
        const val FULL_DEGREES = 24f
        const val TAP_DEGREES = 10f
        const val MUTED_ALPHA = 0x55
    }
}
