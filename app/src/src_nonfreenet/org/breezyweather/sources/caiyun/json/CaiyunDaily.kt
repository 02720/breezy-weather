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
data class CaiyunDaily(
    val status: String?,
    val description: String?,
    val skycon: List<CaiyunDailySkycon>?,
    /**
     * Daytime (08:00-20:00) weather conditions
     */
    val skycon_08h_20h: List<CaiyunDailySkycon>?,
    /**
     * Nighttime (20:00-08:00) weather conditions
     */
    val skycon_20h_32h: List<CaiyunDailySkycon>?,
    val temperature: List<CaiyunDailyTemperature>?,
    val temperature_08h_20h: List<CaiyunDailyTemperature>?,
    val temperature_20h_32h: List<CaiyunDailyTemperature>?,
    val precipitation: List<CaiyunDailyPrecipitation>?,
    val precipitation_08h_20h: List<CaiyunDailyPrecipitation>?,
    val precipitation_20h_32h: List<CaiyunDailyPrecipitation>?,
    val wind: List<CaiyunDailyWind>?,
    val wind_08h_20h: List<CaiyunDailyWind>?,
    val wind_20h_32h: List<CaiyunDailyWind>?,
    val humidity: List<CaiyunDailyValue>?,
    val cloudrate: List<CaiyunDailyValue>?,
    val pressure: List<CaiyunDailyValue>?,
    val visibility: List<CaiyunDailyValue>?,
    val dswrf: List<CaiyunDailyValue>?,
    val air_quality: CaiyunDailyAirQuality?,
    val life_index: CaiyunDailyLifeIndex?,
    val astro: List<CaiyunDailyAstro>?,
)

@Serializable
data class CaiyunDailySkycon(
    @Serializable(DateSerializer::class) @SerialName("date") val date: Date?,
    val value: String?,
)

@Serializable
data class CaiyunDailyTemperature(
    @Serializable(DateSerializer::class) @SerialName("date") val date: Date?,
    val max: Double?,
    val min: Double?,
    val avg: Double?,
)

@Serializable
data class CaiyunDailyPrecipitation(
    @Serializable(DateSerializer::class) @SerialName("date") val date: Date?,
    val max: Double?,
    val min: Double?,
    val avg: Double?,
    /**
     * Precipitation probability, 0-100
     */
    val probability: Double?,
)

/**
 * Wind is documented as a flat structure ({date, max, avg, min}) since v2.6,
 * but older versions used a nested value object. Both are supported for
 * robustness.
 */
@Serializable
data class CaiyunDailyWind(
    @Serializable(DateSerializer::class) @SerialName("date") val date: Date?,
    val max: CaiyunDailyWindValue?,
    val avg: CaiyunDailyWindValue?,
    val min: CaiyunDailyWindValue?,
    val value: CaiyunDailyWindNested?,
)

@Serializable
data class CaiyunDailyWindValue(
    val speed: Double?,
    val direction: Double?,
)

@Serializable
data class CaiyunDailyWindNested(
    val max: CaiyunDailyWindValue?,
    val avg: CaiyunDailyWindValue?,
    val min: CaiyunDailyWindValue?,
)

@Serializable
data class CaiyunDailyValue(
    @Serializable(DateSerializer::class) @SerialName("date") val date: Date?,
    val max: Double?,
    val min: Double?,
    val avg: Double?,
)

@Serializable
data class CaiyunDailyAirQuality(
    val aqi: List<CaiyunDailyAqi>?,
    val pm25: List<CaiyunDailyValue>?,
)

@Serializable
data class CaiyunDailyAqi(
    @Serializable(DateSerializer::class) @SerialName("date") val date: Date?,
    val max: CaiyunAqiValue?,
    val min: CaiyunAqiValue?,
    val avg: CaiyunAqiValue?,
)

@Serializable
data class CaiyunDailyLifeIndex(
    val ultraviolet: List<CaiyunDailyLifeIndexValue>?,
    val carWashing: List<CaiyunDailyLifeIndexValue>?,
    val dressing: List<CaiyunDailyLifeIndexValue>?,
    val comfort: List<CaiyunDailyLifeIndexValue>?,
    val coldRisk: List<CaiyunDailyLifeIndexValue>?,
)

@Serializable
data class CaiyunDailyLifeIndexValue(
    @Serializable(DateSerializer::class) @SerialName("date") val date: Date?,
    val index: String?,
    val desc: String?,
)

@Serializable
data class CaiyunDailyAstro(
    @Serializable(DateSerializer::class) @SerialName("date") val date: Date?,
    val sunrise: CaiyunSunTime?,
    val sunset: CaiyunSunTime?,
)

@Serializable
data class CaiyunSunTime(
    val time: String?,
)
