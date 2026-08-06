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

package org.breezyweather.sources.qweather

import breezyweather.domain.weather.reference.AlertSeverity
import breezyweather.domain.weather.reference.WeatherCode

/**
 * Maps a QWeather `condition.code` (a stable string such as "100" or "305") to the
 * project's [WeatherCode]. Codes are sourced from
 * https://dev.qweather.com/docs/resource/weather-conditions/ and may be extended by
 * QWeather over time, so unknown codes fall back to `null` rather than throwing.
 *
 * Note: QWeather does not emit a dedicated "wind" condition code, so [WeatherCode.WIND]
 * is never produced here; wind is conveyed through the wind/gust fields and alerts instead.
 */
fun getWeatherCode(conditionCode: String?): WeatherCode? {
    if (conditionCode.isNullOrEmpty()) return null
    return when (conditionCode) {
        // Clear / mostly clear
        "100", "900", "901" -> WeatherCode.CLEAR
        // Partly cloudy
        "101", "102", "103" -> WeatherCode.PARTLY_CLOUDY
        // Overcast
        "104" -> WeatherCode.CLOUDY
        // Rain (showers, drizzle, storms without hail)
        "300", "301", "305", "306", "307", "308", "309",
        "310", "311", "312", "313", "314", "315", "316", "317", "318",
        "350", "399" -> WeatherCode.RAIN
        // Thunderstorms
        "302", "303" -> WeatherCode.THUNDERSTORM
        // Hail (thunderstorm with hail)
        "304" -> WeatherCode.HAIL
        // Snow
        "400", "401", "402", "403", "407", "408", "409", "410", "499" -> WeatherCode.SNOW
        // Sleet / rain with snow
        "404", "405", "406" -> WeatherCode.SLEET
        // Fog
        "500", "501", "509", "510", "514", "515" -> WeatherCode.FOG
        // Haze, dust and sand
        "502", "503", "504", "507", "508", "511", "512", "513" -> WeatherCode.HAZE
        // 999 = unknown
        else -> null
    }
}

/**
 * Maps a QWeather alert `severity` string to the project's [AlertSeverity].
 * Values are documented at https://dev.qweather.com/docs/resource/warning-info/#severity.
 */
fun getAlertSeverity(severity: String?): AlertSeverity {
    if (severity.isNullOrEmpty()) return AlertSeverity.UNKNOWN
    return when (severity.lowercase()) {
        "extreme" -> AlertSeverity.EXTREME
        "severe" -> AlertSeverity.SEVERE
        "moderate" -> AlertSeverity.MODERATE
        "minor" -> AlertSeverity.MINOR
        else -> AlertSeverity.UNKNOWN
    }
}
