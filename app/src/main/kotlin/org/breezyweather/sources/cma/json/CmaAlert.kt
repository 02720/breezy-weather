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
data class CmaAlertResult(
    val code: String? = null,
    val message: String? = null,
    val data: List<CmaAlert>? = null,
)

@Serializable
data class CmaAlert(
    val identifier: String? = null,
    val severity: String? = null,
    val effective: String? = null,
    val expires: String? = null,
    val headline: String? = null,
    val description: String? = null,
    val senderName: String? = null,
    val areaName: String? = null,
    val lon: Double? = null,
    val lat: Double? = null,
    val status: String? = null,
)
