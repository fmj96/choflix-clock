package com.choflix.clock.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import com.choflix.clock.R

/**
 * 숫자 글꼴(설정에서 고른다). 글자 폭(em)은 글꼴 파일에서 잰 값 — 칸 폭에 맞춰 크기를 정할 때 쓴다.
 * 고정폭 숫자(tnum)를 켜서 분이 바뀌어도 자리가 흔들리지 않게 한다.
 */
enum class DigitFont(
    val label: String,
    val family: FontFamily,
    val digit: Float, val dot: Float, val minus: Float, val colon: Float,
    /** 숫자 글자의 실제 높이(em). 세로로 늘릴 배율을 정할 때 쓴다. */
    val digitHeight: Float = 0.70f,
    /** 큰 시각용 아주 굵은 판(Black)과 그 숫자·콜론 폭. 없으면 보통 판을 그대로 쓴다. */
    val heavyFamily: FontFamily? = null,
    val heavyDigit: Float = digit, val heavyColon: Float = colon,
) {
    // 폭이 좁아 같은 화면 폭에 가장 크게 들어간다.
    Condensed(
        "Barlow Condensed (기본)", FontFamily(Font(R.font.barlow_condensed_semibold)), 0.475f, 0.211f, 0.325f, 0.252f,
        heavyFamily = FontFamily(Font(R.font.barlow_condensed_black)), heavyDigit = 0.54f, heavyColon = 0.318f,
    ),
    Barlow(
        "Barlow", FontFamily(Font(R.font.barlow_semibold)), 0.538f, 0.273f, 0.404f, 0.355f,
        heavyFamily = FontFamily(Font(R.font.barlow_black)), heavyDigit = 0.556f, heavyColon = 0.381f,
    ),
    // 전자시계(7세그먼트). 점은 앞 숫자에 겹쳐 그려져 폭이 없다.
    Segment("전자시계 (DSEG7)", FontFamily(Font(R.font.dseg7_modern_bold)), 0.816f, 0f, 0.816f, 0.2f, 0.95f),
    // 폰 기본 글꼴(Roboto·MiSans 등) — 기기마다 달라 넉넉히 잡는다.
    System(
        "폰 기본 글꼴", FontFamily.Default, 0.58f, 0.27f, 0.36f, 0.3f, 0.72f,
        heavyFamily = FontFamily.Default, heavyDigit = 0.62f, heavyColon = 0.34f,
    );

    /** 이 글꼴로 쓴 글자열의 폭(em). 4% 여유. */
    fun em(s: String): Float = 1.04f * s.sumOf {
        when (it) { '.' -> dot; '-' -> minus; ':' -> colon; else -> digit }.toDouble()
    }.toFloat()

    /** 줄 높이를 1em 으로 줄여 위아래 빈 여백 없이 칸을 채운다. */
    fun style(size: TextUnit, color: Color) = TextStyle(
        fontFamily = family, fontSize = size, color = color, fontFeatureSettings = "tnum",
        fontWeight = if (this == System) FontWeight.Medium else null,
        lineHeight = size,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )

    /** 큰 시각("10:30")의 폭(em) — 아주 굵게면 굵은 판 기준. */
    fun timeEm(heavy: Boolean): Float =
        if (heavy && heavyFamily != null) 1.04f * (4 * heavyDigit + heavyColon) else em("88:88")

    /** 큰 시각 글자 모양. 아주 굵게면 Black 판(폰 기본 글꼴은 가장 굵은 굵기). */
    fun timeStyle(size: TextUnit, color: Color, heavy: Boolean): TextStyle {
        if (!heavy || heavyFamily == null) return style(size, color)
        return style(size, color).copy(
            fontFamily = heavyFamily,
            fontWeight = if (this == System) FontWeight.Black else null,
        )
    }

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
