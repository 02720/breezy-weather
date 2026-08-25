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

package org.breezyweather.sources.cma.json

import kotlinx.serialization.Serializable

@Serializable
data class CmaNearStationResult(
    val code: String? = null,
    val message: String? = null,
    val data: CmaNearStationData? = null,
)

@Serializable
data class CmaNearStationData(
    val returnCode: Int? = null,
    val errorMsg: String? = null,
    val dataMethod: String? = null,
    val DS: CmaNearStation? = null,
)

@Serializable
data class CmaNearStation(
    val stationId: String? = null,
    val stationName: String? = null,
    val province: String? = null,
    val city: String? = null,
    val district: String? = null,
    val areacode: String? = null,
)
