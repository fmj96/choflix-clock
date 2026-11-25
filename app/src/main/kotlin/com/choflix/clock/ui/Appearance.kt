package com.choflix.clock.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.choflix.clock.R

/** 굵기 단계(설정의 굵기 고르기). */
val WeightNames: Map<Int, String> = mapOf(
    300 to "가늘게", 400 to "보통", 500 to "중간", 600 to "약간 굵게",
    700 to "굵게", 800 to "더 굵게", 900 to "아주 굵게",
)

private fun statics(vararg fonts: Pair<Int, Int>) =
    FontFamily(fonts.map { (res, w) -> Font(res, FontWeight(w)) })

/** 가변 글꼴 한 파일에서 굵기마다 한 벌씩 꺼낸다(안드로이드 8.0+). */
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, range: IntRange) = FontFamily(
    (100..900 step 100).filter { it in range }.map { w ->
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

/**
 * 숫자 글꼴(설정에서 고른다). 숫자는 칸에 맞춰 늘리거나 줄여 그리므로([FitText]) 글자 폭을
 * 따로 잴 필요가 없다. weights 는 이 글꼴에 실제로 있는 굵기 — 설정 화면이 이것만 보여 준다.
 */
enum class DigitFont(val label: String, val family: FontFamily, val weights: List<Int>) {
    Condensed(
        "Barlow Condensed (기본)",
        statics(
            R.font.barlowc_light to 300, R.font.barlowc_regular to 400, R.font.barlowc_medium to 500,
            R.font.barlowc_semibold to 600, R.font.barlowc_bold to 700, R.font.barlowc_extrabold to 800,
            R.font.barlowc_black to 900,
        ),
        listOf(300, 400, 500, 600, 700, 800, 900),
    ),
    Barlow(
        "Barlow",
        statics(R.font.barlow_regular to 400, R.font.barlow_semibold to 600, R.font.barlow_bold to 700, R.font.barlow_black to 900),
        listOf(400, 600, 700, 900),
    ),
    Oswald("Oswald", variable(R.font.oswald_var, 200..700), listOf(300, 400, 500, 600, 700)),
    RobotoCondensed("Roboto Condensed", variable(R.font.robotocondensed_var, 100..900), listOf(300, 400, 500, 600, 700, 800, 900)),
    Teko("Teko", variable(R.font.teko_var, 300..700), listOf(300, 400, 500, 600, 700)),
    Bebas("Bebas Neue", statics(R.font.bebasneue_regular to 400), listOf(400)),
    Anton("Anton", statics(R.font.anton_regular to 400), listOf(400)),
    // 전자시계(7세그먼트).
    Segment(
        "전자시계 (DSEG7)",
        statics(R.font.dseg7_light to 300, R.font.dseg7_regular to 400, R.font.dseg7_bold to 700),
        listOf(300, 400, 700),
    ),
    System("폰 기본 글꼴", FontFamily.Default, listOf(300, 400, 500, 600, 700, 800, 900));

    /** 고른 굵기를 이 글꼴에 있는 가장 가까운 굵기로. */
    fun nearest(weight: Int): Int = weights.minByOrNull { kotlin.math.abs(it - weight) } ?: 400

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: Condensed
    }
}

/** 글자·아이콘 색(설정에서 고른다). 기본은 흰색. */
val ColorPresets: List<Pair<String, Long>> = listOf(
    "흰색" to 0xFFFFFFFF,
    "따뜻한 흰색" to 0xFFFFEBC8,
    "앰버" to 0xFFFFB054,
    "빨강" to 0xFFFF5A5A,
    "초록" to 0xFF5BE37D,
    "하늘" to 0xFF6AD0FF,
    "파랑" to 0xFF4D8DFF,
    "보라" to 0xFFB98CFF,
)

/** 야간 자동 밝기 구간의 엠버 톤. */
val NightAmber = Color(0xFFD98A3C)

/** 경고(와이파이 끊김, 플러그 응답 없음, 새 버전 점)는 색 설정과 상관없이 빨강. */
val WarnRed = Color(0xFFFF5A5A)
