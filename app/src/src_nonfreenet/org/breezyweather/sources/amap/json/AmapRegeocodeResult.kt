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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.breezyweather.sources.amap.serializers.AmapStringSerializer

/**
 * Response of the Amap reverse geocoding endpoint (v3/geocode/regeo).
 *
 * https://lbs.amap.com/api/webservice/guide/api/georegeo
 */
@Serializable
data class AmapRegeocodeResult(
    /** "1" if the request succeeded, "0" otherwise */
    val status: String? = null,
    val info: String? = null,
    val infocode: String? = null,
    val regeocode: AmapRegeocode? = null,
)

@Serializable
data class AmapRegeocode(
    @SerialName("addressComponent")
    val addressComponent: AmapAddressComponent? = null,
    /**
     * POIs near the requested coordinates, ordered by distance.
     * Only returned when requesting with extensions=all
     */
    val pois: List<AmapPoi>? = null,
)

@Serializable
data class AmapAddressComponent(
    /** Country name, e.g. "中国" */
    @Serializable(with = AmapStringSerializer::class)
    val country: String? = null,
    /** Province / municipality name, e.g. "北京市" or "河北省" */
    @Serializable(with = AmapStringSerializer::class)
    val province: String? = null,
    /** City name (an empty array for direct-controlled municipalities) */
    @Serializable(with = AmapStringSerializer::class)
    val city: String? = null,
    /** District or county name, e.g. "海淀区" */
    @Serializable(with = AmapStringSerializer::class)
    val district: String? = null,
    /** Administrative division code, e.g. "110108" */
    @Serializable(with = AmapStringSerializer::class)
    val adcode: String? = null,
    /** Township / subdistrict / street name, e.g. "中关村街道" */
    @Serializable(with = AmapStringSerializer::class)
    val township: String? = null,
)
