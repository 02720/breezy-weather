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

package org.breezyweather.sources.geovis.json

import kotlinx.serialization.Serializable

/**
 * Response of `GET cn/realtime/aqi/stations`.
 *
 * [GeovisAqi] aggregates the air quality of the monitoring stations around the requested point;
 * [GeovisAqiDetails] are the individual stations. Per-station pollutant values are assumed
 * (like every other field of this product) to be in µg/m³.
 */
@Serializable
data class GeovisAqiResult(
    val status: Int? = null,
    val result: GeovisAqi? = null,
)

@Serializable
data class GeovisAqi(
    val aqi: Double? = null,
    val co: Double? = null,
    val o3: Double? = null,
    val no2: Double? = null,
    val so2: Double? = null,
    val pm10: Double? = null,
    val pm25: Double? = null,
    val aqi_level: String? = null,
    val pollutant: String? = null,
    val details: List<GeovisAqiDetails>? = null,
)

@Serializable
data class GeovisAqiDetails(
    val staCode: String? = null,
    val staName: String? = null,
    val lonlat: List<Double>? = null,
    val aqi: Double? = null,
    val co: Double? = null,
    val o3: Double? = null,
    val no2: Double? = null,
    val so2: Double? = null,
    val pm10: Double? = null,
    val pm25: Double? = null,
    val aqi_level: String? = null,
    val pollutant: String? = null,
)
