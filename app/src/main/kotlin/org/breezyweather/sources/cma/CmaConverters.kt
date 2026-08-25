/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.sources.cma

import breezyweather.domain.weather.reference.WeatherCode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * CMA weather phenomenon codes (WEP) mapped to Breezy Weather codes:
 * 0 晴 1 多云 2 阴 3 阵雨 4 雷阵雨 5 雷阵雨伴冰雹 6 雨夹雪 7 小雨 8 中雨 9 大雨
 * 10 暴雨 11 大暴雨 12 特大暴雨 13 阵雪 14 小雪 15 中雪 16 大雪 17 暴雪 18 雾
 * 19 冻雨 20 沙尘暴 21-25 各级降雨过渡 26-28 各级降雪过渡 29 浮尘 30 扬沙
 * 31 强沙尘暴 32/53-56 霾
 */
internal fun getCmaWeatherCode(wepCode: Int?): WeatherCode? {
    return when (wepCode) {
        0 -> WeatherCode.CLEAR
        1 -> WeatherCode.PARTLY_CLOUDY
        2 -> WeatherCode.CLOUDY
        3, 7, 8, 9, 10, 11, 12, 21, 22, 23, 24, 25 -> WeatherCode.RAIN
        4 -> WeatherCode.THUNDERSTORM
        5 -> WeatherCode.HAIL
        6, 19 -> WeatherCode.SLEET
        13, 14, 15, 16, 17, 26, 27, 28 -> WeatherCode.SNOW
        18 -> WeatherCode.FOG
        20, 29, 30, 31, 32, 53, 54, 55, 56 -> WeatherCode.HAZE
        else -> null
    }
}

internal fun getCmaWeatherCodeFromText(text: String?): WeatherCode? {
    if (text.isNullOrBlank()) return null
    return when {
        text.contains("雷") -> WeatherCode.THUNDERSTORM
        text.contains("冰雹") -> WeatherCode.HAIL
        text.contains("雨夹雪") || text.contains("冻雨") -> WeatherCode.SLEET
        text.contains("雪") -> WeatherCode.SNOW
        text.contains("雨") -> WeatherCode.RAIN
        text.contains("雾") -> WeatherCode.FOG
        text.contains("霾") || text.contains("沙尘") || text.contains("浮尘") -> WeatherCode.HAZE
        text.contains("阴") -> WeatherCode.CLOUDY
        text.contains("多云") -> WeatherCode.PARTLY_CLOUDY
        text.contains("晴") -> WeatherCode.CLEAR
        else -> null
    }
}

private fun directionDegrees(direction: Char): Double? = when (direction) {
    '东' -> 90.0
    '南' -> 180.0
    '西' -> 270.0
    '北' -> 0.0
    else -> null
}

private fun shortestDelta(
    from: Double,
    to: Double,
): Double {
    var delta = (to - from) % 360.0
    if (delta > 180.0) delta -= 360.0
    if (delta < -180.0) delta += 360.0
    return delta
}

/**
 * Parses wind direction texts such as "北风", "东北风", "北偏东" (optionally followed by
 * a Beaufort scale like "北偏东1级"). Returns degrees clockwise from north,
 * or null when unparsable ("无持续风向", "旋转不定", etc.)
 */
internal fun getCmaWindDirectionDegree(text: String?): Double? {
    if (text.isNullOrBlank()) return null
    val directionPart = text.takeWhile { !it.isDigit() }.removeSuffix("风")
    val directions = directionPart.filter { it in "东南西北" }.mapNotNull(::directionDegrees)
    if (directions.isEmpty()) return null
    val base = directions.first()
    val modifier = directions.getOrNull(1)
    return when {
        modifier == null -> base
        directionPart.contains('偏') ->
            (base + shortestDelta(base, modifier) / 4.0 + 360.0) % 360.0
        else -> (base + shortestDelta(base, modifier) / 2.0 + 360.0) % 360.0
    }
}

/**
 * Beaufort scale midpoints in m/s for levels 0..12
 */
private val BEAUFORT_MIDPOINTS = doubleArrayOf(
    0.0, 0.9, 2.45, 4.45, 6.7, 9.35, 12.3, 15.5, 18.95, 22.6, 26.45, 30.55, 34.0
)

/**
 * Extracts the trailing Beaufort level from texts like "北偏东1级" and converts it
 * to a speed in m/s
 */
internal fun getCmaWindSpeed(text: String?): Double? {
    val level = text?.takeLastWhile { it.isDigit() }?.takeIf { it.isNotEmpty() }
        ?.toIntOrNull() ?: return null
    return BEAUFORT_MIDPOINTS[level.coerceIn(0, BEAUFORT_MIDPOINTS.lastIndex)]
}

internal fun getCmaDistanceKm(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    return earthRadiusKm * 2 * atan2(sqrt(h), sqrt(1.0 - h))
}
