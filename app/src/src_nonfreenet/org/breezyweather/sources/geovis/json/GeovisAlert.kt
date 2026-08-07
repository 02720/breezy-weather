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
 * Response of `GET alert/now/data`.
 */
@Serializable
data class GeovisAlertResult(
    val status: Int? = null,
    val result: GeovisAlertData? = null,
)

@Serializable
data class GeovisAlertData(
    val dataCode: String? = null,
    val dataSize: Int? = null,
    val alerts: List<GeovisAlert>? = null,
)

/**
 * A single China Meteorological Administration alert. `effective` and `expires` are local
 * "yyyy-MM-dd HH:mm:ss" strings; `levelCode` is one of "Blue"/"Yellow"/"Orange"/"Red".
 */
@Serializable
data class GeovisAlert(
    val geoCode: String? = null,
    val sender: String? = null,
    val typeCode: String? = null,
    val type: String? = null,
    val levelCode: String? = null,
    val level: String? = null,
    val effective: String? = null,
    val expires: String? = null,
    val title: String? = null,
    val detail: String? = null,
)
