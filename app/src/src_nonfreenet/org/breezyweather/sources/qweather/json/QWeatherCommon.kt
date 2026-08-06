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
 * A QWeather weather phenomenon: `condition` object with a localized `text` and a stable `code`.
 */
@Serializable
data class QWeatherCondition(
    val text: String? = null,
    val code: String? = null,
)

/**
 * A measured value with its unit string, e.g. `{ "value": 29.94, "unit": "°C" }`.
 * The unit is kept around so that air quality pollutants can be converted correctly
 * (QWeather may return µg/m³, mg/m³, ppb or ppm depending on the pollutant and endpoint).
 */
@Serializable
data class QWeatherValueUnit(
    val value: Double? = null,
    val unit: String? = null,
)

@Serializable
data class QWeatherWindDirection(
    val degree: Double? = null,
    val compass: String? = null,
)

@Serializable
data class QWeatherWindSpeed(
    val value: Double? = null,
    val unit: String? = null,
)

@Serializable
data class QWeatherWind(
    val direction: QWeatherWindDirection? = null,
    val speed: QWeatherWindSpeed? = null,
    val scale: Double? = null,
)

/**
 * Precipitation data. `amount` is the accumulated precipitation over the period, `intensity`
 * is the instantaneous rate (mm/h, only in current/hourly), `probability` is a [0, 1] ratio
 * (only in daily daytime/nighttime and hourly), and `type` describes the precipitation kind.
 */
@Serializable
data class QWeatherPrecipitation(
    val amount: QWeatherValueUnit? = null,
    val intensity: QWeatherValueUnit? = null,
    val type: String? = null,
    val probability: Double? = null,
)
