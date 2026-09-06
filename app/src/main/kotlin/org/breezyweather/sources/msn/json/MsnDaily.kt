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

package org.breezyweather.sources.msn.json

import kotlinx.serialization.Serializable
import org.breezyweather.common.serializer.DateSerializer
import java.util.Date

/**
 * Daily summary of a forecast day. "valid" is the day at 00:00 in the
 * timezone of the requested location.
 */
@Serializable
data class MsnDaily(
    @Serializable(DateSerializer::class) val valid: Date?,
    val day: MsnHalfDay?,
    val night: MsnHalfDay?,
    val tempHi: Double?,
    val tempLo: Double?,
    val feelsHi: Double?,
    val feelsLo: Double?,
    val precip: Double?,
    val uv: Double?,
    val rh: Double?,
    val rhHi: Double?,
    val rhLo: Double?,
    val dewPt: Double?,
    val baro: Double?,
    val vis: Double?,
)
