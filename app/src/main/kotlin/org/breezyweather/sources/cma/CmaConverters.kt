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

import breezyweather.domain.weather.reference.AlertSeverity
import breezyweather.domain.weather.reference.WeatherCode
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * CMA weather phenomenon codes (WEP) mapped to Breezy Weather codes.
 * Same code space as the "img" field of the gridded forecast endpoint:
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

/**
 * Drops CMA missing-data sentinels (the website treats values >= 9999 as missing)
 * and any other magnitude outside the given physical bounds
 */
internal fun Double?.cmaSanitized(
    min: Double,
    max: Double,
): Double? = this?.takeIf { it.isFinite() && it in min..max }

/**
 * True when a textual field holds a missing-data sentinel instead of content,
 * e.g. the weather text "9999" returned outside the validity period
 */
internal fun String?.cmaMissingValue(): Boolean {
    val value = this?.trim()?.toDoubleOrNull() ?: return false
    return value >= 9998.0 || value <= -998.0
}

/**
 * Builds the province-level administrative code from a county-level code,
 * e.g. "450405" (长洲区) -> "450000" (广西壮族自治区).
 * Returns null for malformed codes.
 */
internal fun getCmaProvinceCode(areaCode: String?): String? {
    if (areaCode == null || areaCode.length != 6 || areaCode.any { !it.isDigit() }) return null
    return areaCode.substring(0, 2) + "0000"
}

/**
 * Level name of a CMA alert severity, matching the naming used by the official
 * website, e.g. "Yellow" -> "黄色". "White" (e.g. typhoon white alerts issued by
 * some provincial offices) is handled defensively as the lowest level.
 */
internal fun getCmaAlertLevelName(severity: String?): String? =
    when (severity?.lowercase(Locale.ENGLISH)) {
        "red" -> "红色"
        "orange" -> "橙色"
        "yellow" -> "黄色"
        "blue" -> "蓝色"
        "white" -> "白色"
        else -> null
    }

/**
 * Maps a CMA alert severity to a Breezy Weather severity.
 * Returns null for unknown severities: the official website drops such alerts,
 * so we do the same to stay consistent.
 */
internal fun getCmaAlertSeverity(severity: String?): AlertSeverity? =
    when (severity?.lowercase(Locale.ENGLISH)) {
        "red" -> AlertSeverity.EXTREME
        "orange" -> AlertSeverity.SEVERE
        "yellow" -> AlertSeverity.MODERATE
        "blue", "white" -> AlertSeverity.MINOR
        else -> null
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
