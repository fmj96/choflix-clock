package com.choflix.clock.ui

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.min

/**
 * 숫자를 칸에 맞춰 그린다.
 *
 *  - stretch = true  : 가로세로 비율을 무시하고 칸을 꽉 채운다(시계, 바깥 기온 — 사용자 요청).
 *  - stretch = false : 비율을 지키며 칸 안에 들어가는 가장 큰 크기.
 *
 * 높이는 그 글자열이 아니라 숫자 '0~9' 의 실제 높이로 잰다 — 빼기(-)처럼 낮은 글자나 글꼴의 위아래
 * 여백 때문에 칸을 넘치던 문제(시안 v15)가 없도록. 폭은 글자 진행 폭(고정폭 숫자)이라 "11" 과
 * "10" 이 같은 폭을 차지한다. suffix(°C, %)는 숫자 높이의 [suffixScale] 배로 오른쪽 위에 붙인다.
 *
 * reference 를 주면 크기를 그 글자열(예: 바깥 기온 "-20")에 맞춰 고정한다 — 글자 수가 달라져도
 * 숫자 크기가 그대로다. 빼기(-)는 [minusScale] 배로 작게 그려 폭을 아낀다.
 */
@Composable
fun FitText(
    text: String,
    font: DigitFont,
    weight: Int,
    color: Color,
    modifier: Modifier,
    stretch: Boolean = false,
    align: Alignment.Horizontal = Alignment.CenterHorizontally,
    suffix: String = "",
    suffixScale: Float = 0.36f,
    reference: String? = null,
    minusScale: Float = 1f,
) {
    val resolver = LocalFontFamilyResolver.current
    val w = font.nearest(weight)
    val typeface = remember(font, w) {
        (runCatching { resolver.resolve(font.family, FontWeight(w)).value }.getOrNull() as? Typeface)
            ?: Typeface.create(Typeface.DEFAULT, if (w >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
    val paint = remember(typeface) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 100f
            fontFeatureSettings = "tnum"
        }
    }
    // 숫자 높이(잉크) — 0~9 를 모두 덮는 상자.
    val ref = remember(paint) { Rect().also { paint.getTextBounds("0123456789", 0, 10, it) } }
    val minus = remember(paint) { Rect().also { paint.getTextBounds("-", 0, 1, it) } }
    // 빼기를 작게 그린 만큼 줄인 글자열 폭.
    fun widthOf(s: String): Float =
        if (s.startsWith("-") && minusScale != 1f) paint.measureText("-") * minusScale + paint.measureText(s, 1, s.length)
        else paint.measureText(s)

    Canvas(modifier) {
        if (text.isEmpty() || ref.height() <= 0) return@Canvas
        paint.color = color.toArgb()
        val textW = widthOf(text)
        val gap = if (suffix.isEmpty()) 0f else 4f
        val sufW = if (suffix.isEmpty()) 0f else paint.measureText(suffix) * suffixScale
        val totalW = maxOf(textW, reference?.let(::widthOf) ?: 0f) + gap + sufW
        val inkH = ref.height().toFloat()
        var sx = size.width / totalW
        var sy = size.height / inkH
        if (!stretch) { val s = min(sx, sy); sx = s; sy = s }
        val drawnW = (textW + gap + sufW) * sx
        val drawnH = inkH * sy
        val x0 = when (align) {
            Alignment.Start -> 0f
            Alignment.End -> size.width - drawnW
            else -> (size.width - drawnW) / 2f
        }
        val y0 = (size.height - drawnH) / 2f
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.translate(x0, y0)
            nc.scale(sx, sy)
            if (text.startsWith("-") && minusScale != 1f) {
                // 빼기는 제 높이 가운데를 축으로 줄인다.
                val cy = -ref.top.toFloat() + minus.exactCenterY()
                nc.save()
                nc.translate(0f, cy)
                nc.scale(minusScale, minusScale)
                nc.drawText("-", 0f, -cy, paint)
                nc.restore()
                nc.drawText(text, 1, text.length, paint.measureText("-") * minusScale, -ref.top.toFloat(), paint)
            } else {
                nc.drawText(text, 0f, -ref.top.toFloat(), paint)
            }
            if (suffix.isNotEmpty()) {
                // 단위는 숫자 윗선에 맞춰 작게.
                nc.save()
                nc.translate(textW + gap, 0f)
                nc.scale(suffixScale, suffixScale)
                nc.drawText(suffix, 0f, -ref.top.toFloat(), paint)
                nc.restore()
            }
            nc.restore()
        }
    }
}
