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
 * Response of `GET nowcast/point/desc` (Rain-Pulse minute-level precipitation nowcast,
 * China only; product range 73–135.5°E, 12.15–54.2°N).
 */
@Serializable
data class GeovisMinutelyResult(
    val status: Int? = null,
    val result: GeovisMinutelyData? = null,
)

@Serializable
data class GeovisMinutelyData(
    /** Start of the forecast window, a local time string "yyyyMMddHHmm"; empty when no rain. */
    val start: String? = null,
    /** End of the forecast window, a local time string "yyyyMMddHHmm". */
    val end: String? = null,
    /**
     * The precipitation intensity in mm/h for every minute from [start] to [end] (121 values
     * over the 2-hour window). Empty when no rain is forecast at the point.
     */
    val series: List<Double>? = null,
    /** Natural-language precipitation description, e.g. "雨一直下，2小时内不会停". */
    val tips: String? = null,
)