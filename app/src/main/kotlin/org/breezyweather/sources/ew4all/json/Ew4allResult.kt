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

package org.breezyweather.sources.ew4all.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response of /EW4ALL/api/modelTimeList
 */
@Serializable
data class Ew4allTimeListResult(
    val code: Int? = null,
    /** One entry per published model run, in UTC, e.g. "20261007000000" */
    val data: List<Ew4allModelTime>? = null,
)

@Serializable
data class Ew4allModelTime(
    /** Model run time, in UTC, e.g. "20261007000000" for 2026-10-07 00:00 UTC */
    @SerialName("data_time") val dataTime: String? = null,
)

/**
 * Response of /EW4ALL/api/raster/findByPoint for a single model.
 * When several models are requested (mode joined with "|"), "data" is an
 * object instead, which this source never triggers.
 */
@Serializable
data class Ew4allPointResult(
    val code: Int? = null,
    val data: List<Ew4allPointStep>? = null,
)

/**
 * One forecast step. Only the field matching the requested element carries a
 * value, plus the valid time; missing values use the -9999 sentinel.
 */
@Serializable
data class Ew4allPointStep(
    /** Valid time, in UTC, e.g. "2026-10-07 06:00:00" */
    @SerialName("Datetime") val datetime: String? = null,

    /** 2 m temperature, in °C */
    @SerialName("TEM") val temperature: Double? = null,

    /** 2 m relative humidity, in % (the website labels it "g/kg" but the values are percentages) */
    @SerialName("RHU") val relativeHumidity: Double? = null,

    /** 10 m wind direction, in degrees following the meteorological convention (direction the wind blows from) */
    @SerialName("WIN_D") val windDirection: Double? = null,

    /** 10 m wind speed, in m/s */
    @SerialName("WIN_S") val windSpeed: Double? = null,

    /** Precipitation accumulated over the 6 hours ending at the valid time, in mm */
    @SerialName("SIXTPE") val precipitation6Hours: Double? = null,
)
