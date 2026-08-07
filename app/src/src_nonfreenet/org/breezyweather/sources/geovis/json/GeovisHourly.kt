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
 * Response of `GET {cn|global}/forecast/hour/area[/professional]`.
 */
@Serializable
data class GeovisHourlyResult(
    val status: Int? = null,
    val result: GeovisHourlyData? = null,
)

@Serializable
data class GeovisHourlyData(
    val start: String? = null,
    val end: String? = null,
    val size: Int? = null,
    val datas: List<GeovisHourly>? = null,
)

/**
 * A single hourly forecast item (`fc_time` is a local time string "yyyyMMddHH").
 * The global product additionally returns `pre_pro` and `uv_level`.
 */
@Serializable
data class GeovisHourly(
    val fc_time: String? = null,
    val wp_code: String? = null,
    val wp: String? = null,
    val tem: Double? = null,
    val real_tem: Double? = null,
    val dp_tem: Double? = null,
    val ws: Double? = null,
    val ws_desc: String? = null,
    val wd: Double? = null,
    val wd_desc: String? = null,
    val rh: Double? = null,
    val prs: Double? = null,
    val pre: Double? = null,
    val pre_pro: Double? = null,
    val cloud_cover: Double? = null,
    val vis: Double? = null,
    val uv_level: Double? = null,
    val uv_desc: String? = null,
)
