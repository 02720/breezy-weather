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

package org.breezyweather.sources.amap.json

import kotlinx.serialization.Serializable
import org.breezyweather.sources.amap.serializers.AmapStringSerializer

@Serializable
data class AmapPlaceTextResult(
    /** "1" if the request succeeded, "0" otherwise */
    val status: String? = null,
    val info: String? = null,
    val infocode: String? = null,
    val pois: List<AmapPoi>? = null,
)

@Serializable
data class AmapPoi(
    /** Unique POI identifier */
    @Serializable(with = AmapStringSerializer::class)
    val id: String? = null,
    /**
     * Longitude and latitude, comma-separated, e.g. "116.310905,39.992806"
     * Only returned when requesting with extensions=all
     */
    @Serializable(with = AmapStringSerializer::class)
    val location: String? = null,
    /** Province name, e.g. "北京市" */
    @Serializable(with = AmapStringSerializer::class)
    val pname: String? = null,
    /** Province code, e.g. "110000" */
    @Serializable(with = AmapStringSerializer::class)
    val pcode: String? = null,
    /** City name, e.g. "北京市" */
    @Serializable(with = AmapStringSerializer::class)
    val cityname: String? = null,
    /** District or county name, e.g. "海淀区" */
    @Serializable(with = AmapStringSerializer::class)
    val adname: String? = null,
)
