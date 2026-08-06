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
 * Response of `GET /v7/minutely/5m?location=lon,lat` (legacy WebAPI, China only).
 *
 * This endpoint uses the legacy flat-string response shape: a `code` of `"200"` means success,
 * while other codes (e.g. `"204"` no data) mean there is no minutely forecast for the location.
 */
@Serializable
data class QWeatherMinutelyResult(
    val code: String? = null,
    val summary: String? = null,
    val minutely: List<QWeatherMinutelyItem>? = null,
)

@Serializable
data class QWeatherMinutelyItem(
    val fxTime: String? = null,
    /** 5-minute accumulated precipitation in mm, returned as a string by the API. */
    val precip: String? = null,
    val type: String? = null,
)
