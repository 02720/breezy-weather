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
 * Response of `GET /weather/v1/daily/{lat}/{lon}`.
 */
@Serializable
data class QWeatherDailyResult(
    /**
     * Body-level status code: "200" success, "204" no data. QWeather answers with HTTP 200
     * even for errors (401/402/403/429/5xx), which are only detectable through this field.
     */
    val code: String? = null,
    val days: List<QWeatherDaily>? = null,
)

@Serializable
data class QWeatherDaily(
    val forecastStartTime: String? = null,
    val forecastEndTime: String? = null,
    val astro: QWeatherDailyAstro? = null,
    val temperatureMax: QWeatherValueUnit? = null,
    val temperatureMin: QWeatherValueUnit? = null,
    val temperatureAvg: QWeatherValueUnit? = null,
    val uvIndexMax: Double? = null,
    val daytime: QWeatherDailyHalfDay? = null,
    val nighttime: QWeatherDailyHalfDay? = null,
)

@Serializable
data class QWeatherDailyAstro(
    val sunrise: String? = null,
    val sunset: String? = null,
    val moonPhase: String? = null,
)

@Serializable
data class QWeatherDailyHalfDay(
    val forecastStartTime: String? = null,
    val forecastEndTime: String? = null,
    val condition: QWeatherCondition? = null,
    val temperatureMax: QWeatherValueUnit? = null,
    val temperatureMin: QWeatherValueUnit? = null,
    val humidity: Double? = null,
    val wind: QWeatherWind? = null,
    val windGustMax: QWeatherValueUnit? = null,
    val precipitation: QWeatherPrecipitation? = null,
    val cloudCover: Double? = null,
)
