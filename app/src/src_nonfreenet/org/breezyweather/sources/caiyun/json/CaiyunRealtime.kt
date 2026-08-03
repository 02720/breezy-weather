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

@Serializable
data class CaiyunRealtime(
    val status: String?,
    val temperature: Double?,
    val humidity: Double?,
    val cloudrate: Double?,
    val skycon: String?,
    val visibility: Double?,
    val dswrf: Double?,
    val pressure: Double?,
    val apparent_temperature: Double?,
    val gust: Double?,
    val dewpoint: Double?,
    val wind: CaiyunWind?,
    val precipitation: CaiyunRealtimePrecipitation?,
    val air_quality: CaiyunRealtimeAirQuality?,
    val life_index: CaiyunLifeIndex?,
)

@Serializable
data class CaiyunWind(
    val speed: Double?,
    val direction: Double?,
)

@Serializable
data class CaiyunRealtimePrecipitation(
    val status: String?,
    val local: CaiyunPrecipitationArea?,
    val nearest: CaiyunPrecipitationArea?,
)

@Serializable
data class CaiyunPrecipitationArea(
    val status: String?,
    val datasource: String?,
    val intensity: Double?,
    val distance: Double?,
)

@Serializable
data class CaiyunRealtimeAirQuality(
    val pm25: Double?,
    val pm10: Double?,
    val o3: Double?,
    val so2: Double?,
    val no2: Double?,
    val co: Double?,
    val aqi: CaiyunAqiValue?,
    val description: CaiyunAqiDescription?,
)

@Serializable
data class CaiyunAqiValue(
    val chn: Double?,
    val usa: Double?,
)

@Serializable
data class CaiyunAqiDescription(
    val chn: String?,
    val usa: String?,
)

@Serializable
data class CaiyunLifeIndex(
    val ultraviolet: CaiyunLifeIndexValue?,
    val comfort: CaiyunLifeIndexValue?,
)

@Serializable
data class CaiyunLifeIndexValue(
    val index: String?,
    val desc: String?,
)
