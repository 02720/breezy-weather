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

package org.breezyweather.sources.qweather.json

import kotlinx.serialization.Serializable

/**
 * Response of `GET /weather/v1/hourly/{lat}/{lon}`.
 */
@Serializable
data class QWeatherHourlyResult(
    val hours: List<QWeatherHourly>? = null,
)

@Serializable
data class QWeatherHourly(
    val forecastTime: String? = null,
    val condition: QWeatherCondition? = null,
    val temperature: QWeatherValueUnit? = null,
    val feelsLike: QWeatherValueUnit? = null,
    val humidity: Double? = null,
    val wind: QWeatherWind? = null,
    val windGust: QWeatherValueUnit? = null,
    val precipitation: QWeatherPrecipitation? = null,
    val pressure: QWeatherValueUnit? = null,
    val visibility: QWeatherValueUnit? = null,
    val dewPoint: QWeatherValueUnit? = null,
    val cloudCover: Double? = null,
    val uvIndex: Double? = null,
)
