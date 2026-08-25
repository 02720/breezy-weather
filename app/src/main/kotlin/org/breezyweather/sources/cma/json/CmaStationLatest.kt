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

package org.breezyweather.sources.cma.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CmaStationLatestResult(
    val status: Int? = null,
    val code: Int? = null,
    val message: String? = null,
    val content: CmaStationContent? = null,
)

@Serializable
data class CmaStationContent(
    @SerialName("D_datetime") val datetime: String? = null,
    @SerialName("V12001") val temperature: Double? = null,
    @SerialName("V13003") val humidity: Double? = null,
    @SerialName("V10004") val pressure: Double? = null,
    @SerialName("V11292T") val windDirectionText: String? = null,
    @SerialName("V11293") val windSpeed: Double? = null,
    @SerialName("V20003T") val weatherText: String? = null,
    @SerialName("V13019") val precipitation: Double? = null,
    @SerialName("V20001") val visibility: Double? = null,
    @SerialName("foreCast") val forecast: CmaStationForecast? = null,
)

@Serializable
data class CmaStationForecast(
    @SerialName("PRE_24h") val pre24h: String? = null,
    @SerialName("foreList") val foreList: List<CmaForecastItem>? = null,
)

@Serializable
data class CmaForecastItem(
    @SerialName("DAN") val period: String? = null,
    @SerialName("dataShow") val date: String? = null,
    @SerialName("WEP_Past_12h") val weatherCode: Int? = null,
    @SerialName("Wth") val weatherText: String? = null,
    @SerialName("win") val wind: String? = null,
    @SerialName("tem") val temperature: String? = null,
)
