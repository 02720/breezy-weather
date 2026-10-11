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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response of rest/gowfs/hour: gridded hourly forecast for a point, from the
 * same GOWFS run as rest/gowfs/day. The series is hourly for the first 24 hours
 * then switches to 3-hourly, up to 10 days; the gaps are filled by linear
 * interpolation by the source.
 *
 * Numeric fields use "9999" as missing-data sentinel (air_pressure is always
 * "9999" on the gridded interface, so it is not modeled).
 */
@Serializable
data class CmaGridHourlyResult(
    val detail: List<CmaGridHourly>? = null,
)

@Serializable
data class CmaGridHourly(
    /** Forecast time in "Asia/Shanghai", formatted as "yyyy-MM-dd HH:mm" */
    @SerialName("time") val time: String? = null,
    /** Temperature in °C */
    @SerialName("temperature") val temperature: String? = null,
    /** Relative humidity in % */
    @SerialName("humidity") val humidity: Double? = null,
    /** Visibility in km */
    @SerialName("visibility") val visibility: Double? = null,
    /** Total cloud cover in % */
    @SerialName("total_cloud") val totalCloud: Double? = null,
    /** Precipitation accumulation in mm over the interval since the previous reported time */
    @SerialName("total_rain") val totalRain: String? = null,
    /** WEP weather phenomenon code, e.g. "0" (see getCmaWeatherCode) */
    @SerialName("weather_icon") val weatherIcon: String? = null,
    /** Weather text, e.g. "小雨" */
    @SerialName("weather_info") val weatherInfo: String? = null,
    /** Wind direction in degrees clockwise from north */
    @SerialName("wind_direction") val windDirection: Double? = null,
    /** Wind speed in m/s */
    @SerialName("wind_speed") val windSpeed: Double? = null,
)
