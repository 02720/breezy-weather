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

package org.breezyweather.sources.cma.json

import kotlinx.serialization.Serializable

/**
 * Response of rest/gowfs/day: gridded 7-day day/night forecast for a point.
 * All numeric fields are strings and use "9999" as missing-data sentinel.
 */
@Serializable
data class CmaGridForecastResult(
    val detail: List<CmaGridForecastDay>? = null,
)

@Serializable
data class CmaGridForecastDay(
    val date: String? = null,
    val day: CmaGridForecastHalf? = null,
    val night: CmaGridForecastHalf? = null,
)

@Serializable
data class CmaGridForecastHalf(
    val weather: CmaGridForecastWeather? = null,
    val wind: CmaGridForecastWind? = null,
)

@Serializable
data class CmaGridForecastWeather(
    /** Weather text, e.g. "小雨" (or "9999" when missing) */
    val info: String? = null,
    /** WEP weather phenomenon code, e.g. "7" */
    val img: String? = null,
    /** Temperature in °C */
    val temperature: String? = null,
)

@Serializable
data class CmaGridForecastWind(
    /** Wind direction in degrees clockwise from north */
    val direct: String? = null,
    /** Wind speed in m/s */
    val power: String? = null,
)
