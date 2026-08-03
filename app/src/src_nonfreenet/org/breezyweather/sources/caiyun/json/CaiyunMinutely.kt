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
data class CaiyunMinutely(
    val status: String?,
    val datasource: String?,
    /**
     * Future 1 hour precipitation intensity, minute by minute
     */
    val precipitation: List<Double>?,
    /**
     * Future 2 hours precipitation intensity, minute by minute
     */
    val precipitation_2h: List<Double>?,
    /**
     * Precipitation probability for [0-30, 30-60, 60-90, 90-120] minutes (0-100)
     */
    val probability: List<Double>?,
    val description: String?,
)
