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

package org.breezyweather.sources.geovis

import breezyweather.domain.weather.reference.AlertSeverity
import breezyweather.domain.weather.reference.WeatherCode

/**
 * Maps a Geovis weather phenomenon code (`wp_code`) to the project's [WeatherCode].
 *
 * The code table comes from the official 天气现象编码表V1.0:
 * https://datacloud.geovisearth.com/support/meteorological/summary. Codes are transmitted with a
 * leading zero by the Chinese products ("01", "04") and without one by the global products ("1",
 * "4"); this is handled by parsing the code numerically. While the standard only defines 00-99
 * and 301/302, the mapping falls back to `null` (rather than throwing) in case new codes appear.
 */
fun getGeovisWeatherCode(weatherCode: String?): WeatherCode? {
    val code = weatherCode?.toIntOrNull() ?: return null
    return when (code) {
        // 00 晴
        0 -> WeatherCode.CLEAR

        // 01 多云
        1 -> WeatherCode.PARTLY_CLOUDY

        // 02 阴
        2 -> WeatherCode.CLOUDY

        // 03 阵雨, 07-12 rain intensities, 21-25 rain combinations, 301 雨
        3, 7, 8, 9, 10, 11, 12, 21, 22, 23, 24, 25, 301 -> WeatherCode.RAIN

        // 04 雷阵雨
        4 -> WeatherCode.THUNDERSTORM

        // 05 雷阵雨伴有冰雹
        5 -> WeatherCode.HAIL

        // 06 雨夹雪, 19 冻雨 (freezing rain; the closest available code)
        6, 19 -> WeatherCode.SLEET

        // 13-17 snow in all intensities, 26-28 snow combinations, 302 雪
        13, 14, 15, 16, 17, 26, 27, 28, 302 -> WeatherCode.SNOW

        // 18 雾, 32 浓雾, 49 强浓雾, 57 大雾, 58 特强浓雾
        18, 32, 49, 57, 58 -> WeatherCode.FOG

        // 20 沙尘暴, 29 浮尘, 30 扬沙, 31 强沙尘暴, 53-56 霾
        20, 29, 30, 31, 53, 54, 55, 56 -> WeatherCode.HAZE

        // 99 无 (no phenomenon)
        else -> null
    }
}

/**
 * Maps a Geovis alert `levelCode` (one of "Blue", "Yellow", "Orange" or "Red", as used by the
 * China Meteorological Administration) to the project's [AlertSeverity].
 */
fun getGeovisAlertSeverity(levelCode: String?): AlertSeverity {
    return when (levelCode?.lowercase()) {
        "blue" -> AlertSeverity.MINOR
        "yellow" -> AlertSeverity.MODERATE
        "orange" -> AlertSeverity.SEVERE
        "red" -> AlertSeverity.EXTREME
        else -> AlertSeverity.UNKNOWN
    }
}
