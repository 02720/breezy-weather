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

package org.breezyweather.sources.caiyun.json

import kotlinx.serialization.Serializable

/**
 * Root object of the Caiyun v2.6 weather API response.
 * https://docs.caiyunapp.com/weather-api/v2/v2.6/6-weather.html
 */
@Serializable
data class CaiyunWeatherResult(
    val status: String?,
    val api_version: String?,
    val api_status: String?,
    val lang: String?,
    val unit: String?,
    val tzshift: Int?,
    val timezone: String?,
    val server_time: Long?,
    val location: List<Double>?,
    val result: CaiyunResult?,
)
