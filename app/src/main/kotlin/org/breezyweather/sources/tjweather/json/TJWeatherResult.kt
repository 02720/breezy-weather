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

package org.breezyweather.sources.tjweather.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * TJWeather single-point forecast query
 * Endpoint: /meteorological/spas/single-point/query
 */
@Serializable
data class TJWeatherResult(
    val code: Int? = null,
    val message: String? = null,
    val data: TJWeatherData? = null,
)

@Serializable
data class TJWeatherData(
    val lon: Double? = null,
    val lat: Double? = null,
    @SerialName("baseTimeString") val baseTimeString: String? = null,
    @SerialName("baseTime") val baseTime: String? = null,
    val mode: String? = null,
    val forecast: List<TJWeatherFactor>? = null,
)

@Serializable
data class TJWeatherFactor(
    @SerialName("factorCode") val factorCode: String? = null,
    @SerialName("forecastDetails") val forecastDetails: List<TJWeatherForecastDetail>? = null,
)

@Serializable
data class TJWeatherForecastDetail(
    @SerialName("forecastTimeString") val forecastTimeString: String? = null,
    @SerialName("forecastTime") val forecastTime: String? = null,
    val value: List<Double>? = null,
    val icon: String? = null,
)
