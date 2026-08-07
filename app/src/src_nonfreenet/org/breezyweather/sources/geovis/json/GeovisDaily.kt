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
 * Response of `GET {cn|global}/forecast/day/area[/professional]`.
 */
@Serializable
data class GeovisDailyResult(
    val status: Int? = null,
    val result: GeovisDailyData? = null,
)

@Serializable
data class GeovisDailyData(
    val start: String? = null,
    val end: String? = null,
    val size: Int? = null,
    val datas: List<GeovisDaily>? = null,
)

/**
 * A single daily forecast item.
 *
 * The Chinese and global products expose different field names for the same concepts:
 * - China: `wp_day_code`/`wp_day` and `wp_night_code`/`wp_night`, `pre_pro_day`/`pre_day` and
 *   `pre_pro_night`/`pre_night`, `rh_max`/`rh_min`.
 * - Global: `wp_code`/`wp`, `pre_pro`/`pre`, `rh`, plus `cloud_cover` and moon data.
 *
 * Wind fields (`ws_day`/`wd_day`/`ws_night`/`wd_night` in China, `ws_desc`/`wd_desc` globally)
 * are human-readable descriptions such as "2级" or "东北风", so they cannot be used to build a
 * numeric [Wind]. Numeric fields are declared as Double to accept both integer ("34") and
 * decimal ("34.0") JSON numbers.
 */
@Serializable
data class GeovisDaily(
    val fc_time: String? = null,
    val week: String? = null,
    // China product
    val wp_day_code: String? = null,
    val wp_day: String? = null,
    val wp_night_code: String? = null,
    val wp_night: String? = null,
    val ws_day: String? = null,
    val wd_day: String? = null,
    val ws_night: String? = null,
    val wd_night: String? = null,
    val pre_pro_day: Double? = null,
    val pre_day: Double? = null,
    val pre_pro_night: Double? = null,
    val pre_night: Double? = null,
    val rh_max: Double? = null,
    val rh_min: Double? = null,
    val vis: Double? = null,
    // Global product
    val wp_code: String? = null,
    val wp: String? = null,
    val ws_desc: String? = null,
    val wd_desc: String? = null,
    val pre: Double? = null,
    val pre_pro: Double? = null,
    val rh: Double? = null,
    val cloud_cover: Double? = null,
    val moonrise: String? = null,
    val moonset: String? = null,
    val moonphase: String? = null,
    // Common
    val tem_max: Double? = null,
    val tem_min: Double? = null,
    val real_tem_max: Double? = null,
    val real_tem_min: Double? = null,
    val prs: Double? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val uv_level: Double? = null,
    val uv_desc: String? = null,
)
