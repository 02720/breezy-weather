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
 * TJWeather forecast availability
 * Endpoint: /meteorological/main/factor/forecast-available
 *
 * Returns one entry per available (production, mode, factorCode, region) combination,
 * with the latest base time and the daily forecast slots for that factor.
 */
@Serializable
data class TJWeatherAvailabilityResult(
    val code: Int? = null,
    val message: String? = null,
    val data: List<TJWeatherAvailability>? = null,
)

@Serializable
data class TJWeatherAvailability(
    val mode: String? = null,
    val production: String? = null,
    @SerialName("factorCode") val factorCode: String? = null,
    val region: String? = null,
    @SerialName("baseTimeString") val baseTimeString: String? = null,
    @SerialName("baseTime") val baseTime: String? = null,
    val forecast: List<TJWeatherAvailabilityForecast>? = null,
)

@Serializable
data class TJWeatherAvailabilityForecast(
    val id: Long? = null,
    @SerialName("forecastTimeString") val forecastTimeString: String? = null,
    @SerialName("forecastTime") val forecastTime: String? = null,
    val umin: List<Double>? = null,
    val umax: List<Double>? = null,
    val vmin: List<Double>? = null,
    val vmax: List<Double>? = null,
)
