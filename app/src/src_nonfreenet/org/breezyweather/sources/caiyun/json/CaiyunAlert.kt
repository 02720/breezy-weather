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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.breezyweather.common.serializer.DateSerializer
import java.util.Date

/**
 * Alerts are only returned if the token has alert permission.
 * https://docs.caiyunapp.com/weather-api/v2/v2.6/5-alert.html
 */
@Serializable
data class CaiyunAlert(
    val status: String?,
    val content: List<CaiyunAlertContent>?,
)

@Serializable
data class CaiyunAlertContent(
    val adcode: String?,
    val alertId: String?,
    val city: String?,
    val code: String?,
    val country: String?,
    val description: String?,
    val detailUrl: String?,
    @Serializable(DateSerializer::class) val effective: Date?,
    @Serializable(DateSerializer::class) val expires: Date?,
    val headline: String?,
    val latitude: Double?,
    val longitude: Double?,
    val location: String?,
    val province: String?,
    val pubtimestamp: Long?,
    val regionId: String?,
    val regionName: String?,
    val sender: String?,
    val severity: String?,
    val source: String?,
    val status: String?,
    val subtype: CaiyunAlertSubtype?,
    val title: String?,
    val type: String?,
    val typeName: String?,
    @SerialName("tzshift") val timeZoneShift: Int?,
)

@Serializable
data class CaiyunAlertSubtype(
    val color: String?,
    val name: String?,
)
