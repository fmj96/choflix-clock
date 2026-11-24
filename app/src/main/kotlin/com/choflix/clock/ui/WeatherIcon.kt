package com.choflix.clock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.choflix.clock.data.Weather

private val Sun = Color(0xFFFFC83D)
private val Moon = Color(0xFF3D8BFF)
private val Drop = Color(0xFF5AB0FF)

private enum class Sky { Clear, Partly, Cloudy, Fog, Rain, Snow, Thunder }

/**
 * 사진처럼 흰 테두리 구름 + 색 있는 해/달. 큰 그림 하나에 작은 덧그림 하나.
 *
 * 날씨 코드만 믿지 않는다: open-meteo 의 current 는 모델 예보를 보간한 값이라 갑자기 오는 비를
 * 놓칠 때가 있다. 같이 오는 강수량·구름양으로 앞뒤를 맞춘다.
 */
@Composable
fun WeatherIcon(w: Weather, modifier: Modifier, line: Color, night: Boolean) {
    val sky = when {
        w.precipitation > 0.05 && w.code in 0..3 -> if (w.currentC <= 0.5) Sky.Snow else Sky.Rain
        w.code in 0..2 && w.cloudPercent >= 80 -> Sky.Cloudy
        w.code == 0 -> Sky.Clear
        w.code in 1..2 -> Sky.Partly
        w.code == 3 -> Sky.Cloudy
        w.code in 45..48 -> Sky.Fog
        w.code in 51..67 || w.code in 80..82 -> Sky.Rain
        w.code in 71..77 || w.code in 85..86 -> Sky.Snow
        w.code in 95..99 -> Sky.Thunder
        else -> Sky.Cloudy
    }
    // 새벽 엠버 모드에서는 색을 빼고 한 가지 톤으로.
    val sunMoon: Pair<ImageVector, Color> =
        if (w.isDay) Icons.Filled.WbSunny to (if (night) line else Sun)
        else Icons.Filled.NightsStay to (if (night) line else Moon)
    val drop = if (night) line else Drop

    Box(modifier) {
        when (sky) {
            Sky.Clear -> Icon(sunMoon.first, null, tint = sunMoon.second, modifier = Modifier.fillMaxSize())
            Sky.Thunder -> Icon(Icons.Filled.Thunderstorm, null, tint = line, modifier = Modifier.fillMaxSize())
            Sky.Fog -> Icon(Icons.Outlined.Dehaze, null, tint = line, modifier = Modifier.fillMaxSize())
            else -> {
                // 구름은 왼쪽 아래, 덧그림은 오른쪽 위(해·달) 또는 아래(비·눈).
                if (sky == Sky.Partly) {
                    Icon(
                        sunMoon.first, null, tint = sunMoon.second,
                        modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.5f).fillMaxHeight(0.5f),
                    )
                }
                Icon(
                    Icons.Outlined.Cloud, null, tint = line,
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.9f).fillMaxHeight(0.9f),
                )
                if (sky == Sky.Rain || sky == Sky.Snow) {
                    Icon(
                        if (sky == Sky.Rain) Icons.Filled.WaterDrop else Icons.Filled.AcUnit, null, tint = drop,
                        modifier = Modifier.align(Alignment.BottomEnd).fillMaxWidth(0.35f).fillMaxHeight(0.35f),
                    )
                }
            }
        }
    }
}
