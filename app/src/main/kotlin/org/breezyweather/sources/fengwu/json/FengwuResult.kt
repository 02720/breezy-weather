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

package org.breezyweather.sources.fengwu.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response of /api/v1/weather/availability
 * Times are UTC, second-precision ISO 8601 strings, e.g. "2026-09-30T06:00:00Z"
 */
@Serializable
data class FengwuAvailabilityResult(
    @SerialName("api_start_time") val apiStartTime: String? = null,
    /** Latest model run published for querying, used as the `forecast_time` of a data request */
    @SerialName("api_end_time") val apiEndTime: String? = null,
    @SerialName("visual_start_time") val visualStartTime: String? = null,
    @SerialName("visual_end_time") val visualEndTime: String? = null,
)

/**
 * Response of /api/open/v1/weather/query (with a personal API key) and
 * /api/open/v1/weather/visual/query (anonymous). Both share the same schema.
 */
@Serializable
data class FengwuResult(
    val longitude: Double? = null,
    val latitude: Double? = null,
    /** Model run time the series is based on, in UTC */
    @SerialName("forecast_time") val forecastTime: String? = null,
    /** Length of the returned forecast, in hours */
    @SerialName("forecast_hours") val forecastHours: Int? = null,
    val data: List<FengwuTimeData>? = null,
)

/**
 * One forecast time step. The model outputs every 3 hours, so consecutive
 * entries are 3 hours apart.
 */
@Serializable
data class FengwuTimeData(
    /** Valid time, in UTC, e.g. "2026-09-30T07:00:00Z" */
    val time: String? = null,
    /** Variable short names (e.g. "t2m", "tp6h", "u10", "v10") mapped to their value */
    val values: Map<String, Double?>? = null,
)