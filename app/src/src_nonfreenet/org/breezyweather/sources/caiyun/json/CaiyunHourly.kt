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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.breezyweather.common.serializer.DateSerializer
import java.util.Date

@Serializable
data class CaiyunHourly(
    val status: String?,
    val description: String?,
    val precipitation: List<CaiyunHourlyPrecipitation>?,
    val temperature: List<CaiyunHourlyValue>?,
    val apparent_temperature: List<CaiyunHourlyValue>?,
    val wind: List<CaiyunHourlyWind>?,
    val humidity: List<CaiyunHourlyValue>?,
    val cloudrate: List<CaiyunHourlyValue>?,
    val skycon: List<CaiyunHourlySkycon>?,
    val pressure: List<CaiyunHourlyValue>?,
    val visibility: List<CaiyunHourlyValue>?,
    val dswrf: List<CaiyunHourlyValue>?,
    val air_quality: CaiyunHourlyAirQuality?,
)

@Serializable
data class CaiyunHourlyValue(
    @Serializable(DateSerializer::class) @SerialName("datetime") val date: Date?,
    val value: Double?,
)

@Serializable
data class CaiyunHourlyPrecipitation(
    @Serializable(DateSerializer::class) @SerialName("datetime") val date: Date?,
    val value: Double?,
    /**
     * Precipitation probability, 0-100
     */
    val probability: Double?,
)

@Serializable
data class CaiyunHourlySkycon(
    @Serializable(DateSerializer::class) @SerialName("datetime") val date: Date?,
    val value: String?,
)

/**
 * Wind is documented as a flat structure ({datetime, speed, direction}) since
 * v2.6, but older versions used a nested value object. Both are supported for
 * robustness.
 */
@Serializable
data class CaiyunHourlyWind(
    @Serializable(DateSerializer::class) @SerialName("datetime") val date: Date?,
    val speed: Double?,
    val direction: Double?,
    val value: CaiyunWind?,
)

@Serializable
data class CaiyunHourlyAirQuality(
    val aqi: List<CaiyunHourlyAqi>?,
    val pm25: List<CaiyunHourlyValue>?,
)

@Serializable
data class CaiyunHourlyAqi(
    @Serializable(DateSerializer::class) @SerialName("datetime") val date: Date?,
    val value: CaiyunAqiValue?,
)
