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

package org.breezyweather.sources.fuxi.json

import kotlinx.serialization.Serializable

/**
 * Response of /gw/weather/api/v1/weather/queryWeatherTile
 * A successful response carries one entry per published model run, e.g.:
 * `"forecastType": "1"` for the medium-range model (伏羲中期/FuXi-C88) and
 * `"forecastType": "2"` for the subseasonal model (伏羲次季节/FuXi-S2S)
 */
@Serializable
data class FuxiTilesResult(
    val msgCode: String? = null,
    val msg: String? = null,
    val success: Boolean? = null,
    val data: List<FuxiTileInfo>? = null,
)

@Serializable
data class FuxiTileInfo(
    val forecastType: String? = null,
    /** Base time of the model run, in UTC, e.g. "2026092412" for 2026-09-24 12:00 UTC */
    val startTime: String? = null,
)

/**
 * Response of /gw/weather/api/v1/weather/queryWeatherInfo
 * `data` is null when the location has no forecast
 */
@Serializable
data class FuxiWeatherInfoResult(
    val msgCode: String? = null,
    val msg: String? = null,
    val success: Boolean? = null,
    val data: FuxiWeatherData? = null,
)

@Serializable
data class FuxiWeatherData(
    val stepRange: Int? = null,
    val weatherInfoList: List<FuxiStep>? = null,
)

/**
 * One hourly step of the model output. All values are written as strings.
 * The API also returns q850 (850 hPa specific humidity) and ssrd (surface
 * solar radiation downwards, W/m²) which have no hourly equivalent in
 * Breezy Weather, so they are intentionally not mapped
 */
@Serializable
data class FuxiStep(
    /** Hours after [FuxiTileInfo.startTime] */
    val step: Int? = null,
    /** 2 m temperature, in °C */
    val t2m: String? = null,
    /** Total precipitation, in mm/h */
    val tp: String? = null,
    /** 10 m eastward wind component, in m/s */
    val u10m: String? = null,
    /** 10 m northward wind component, in m/s */
    val v10m: String? = null,
)
