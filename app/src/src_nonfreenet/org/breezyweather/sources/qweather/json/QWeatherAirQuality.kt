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
 * Response of `GET /airquality/v1/current/{lat}/{lon}`.
 *
 * Only `pollutants` (with their concentration and unit) is consumed: the project's AirQuality
 * model stores raw pollutant concentrations, not a calculated AQI (every country uses its own
 * AQI standard, so a source-provided index would be misleading).
 */
@Serializable
data class QWeatherAirCurrentResult(
    val pollutants: List<QWeatherPollutant>? = null,
)

/**
 * Response of `GET /airquality/v1/hourly/{lat}/{lon}`.
 */
@Serializable
data class QWeatherAirHourlyResult(
    val hours: List<QWeatherAirHour>? = null,
)

@Serializable
data class QWeatherAirHour(
    val forecastTime: String? = null,
    val pollutants: List<QWeatherPollutant>? = null,
)

@Serializable
data class QWeatherPollutant(
    val code: String? = null,
    val concentration: QWeatherValueUnit? = null,
)
