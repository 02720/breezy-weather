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
 * Response of `GET {cn|global}/realtime/area`.
 */
@Serializable
data class GeovisRealtimeResult(
    /**
     * Body-level status code: 0 success, non-zero error. Geovis answers with HTTP 200 even for
     * errors, which are only detectable through this field.
     */
    val status: Int? = null,
    val result: GeovisRealtime? = null,
)

@Serializable
data class GeovisRealtime(
    val wp_code: String? = null,
    val wp: String? = null,
    val tem: Double? = null,
    val real_tem: Double? = null,
    val rh: Double? = null,
    val dp_tem: Double? = null,
    val wd: Double? = null,
    val wd_desc: String? = null,
    val ws: Double? = null,
    val ws_desc: String? = null,
    val gust_speed: Double? = null,
    val gust_desc: String? = null,
    val vis: Double? = null,
    val prs: Double? = null,
    val pre_1h: Double? = null,
)
