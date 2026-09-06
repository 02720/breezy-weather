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

package org.breezyweather.sources.msn

import breezyweather.domain.weather.reference.AlertSeverity
import breezyweather.domain.weather.reference.WeatherCode

/**
 * MSN Weather conditions mapping.
 *
 * Sky codes are prefixed with "d" (daytime) or "n" (night), e.g. "d2000".
 * The website truncates the 4th digit, except for its "1" value combined
 * with a sky code starting with "0" to "4" and a "0" second digit, which
 * is a windy variant of the condition (e.g. "d00001").
 */
internal fun getWeatherCode(
    symbol: String?,
): WeatherCode? {
    if (symbol.isNullOrEmpty() || symbol.length < 4) return null
    if (symbol.length >= 5 &&
        symbol[1] in '0'..'4' &&
        symbol[2] == '0' &&
        symbol[4] == '1'
    ) {
        return WeatherCode.WIND
    }
    return when (symbol.substring(1, 4)) {
        "000", "100" -> WeatherCode.CLEAR // Sunny, mostly sunny
        "200" -> WeatherCode.PARTLY_CLOUDY
        "300", "400", "500" -> WeatherCode.CLOUDY // Mostly cloudy, cloudy
        "210", "220", "310", "320", "410", "420", "430" -> WeatherCode.RAIN
        "211", "221", "311", "321", "411", "421", "431", "603" -> WeatherCode.SLEET
        "212", "222", "312", "322", "412", "422", "432" -> WeatherCode.SNOW
        "240", "340", "440" -> WeatherCode.THUNDERSTORM
        "600" -> WeatherCode.FOG
        "605", "705", "905" -> WeatherCode.HAIL
        "900", "907" -> WeatherCode.HAZE
        else -> null
    }
}

/**
 * "severity" may be localized depending on the alert provider (e.g. Japanese
 * "注意報"), so the caller should fall back to "level", which stays English.
 */
internal fun getAlertSeverity(
    severity: String?,
): AlertSeverity {
    return when (severity?.lowercase()) {
        "extreme" -> AlertSeverity.EXTREME
        "severe" -> AlertSeverity.SEVERE
        "warning", "moderate" -> AlertSeverity.MODERATE
        "advisory", "watch", "minor" -> AlertSeverity.MINOR
        else -> AlertSeverity.UNKNOWN
    }
}
