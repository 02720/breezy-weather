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
 * Minute-level precipitation nowcasting, based on radar data.
 *
 * "precipitationRate" (in mm/h) holds one value per time interval: the i-th
 * value covers the minutes going from timestamp + i * minutesBetweenHorrizons
 * to timestamp + (i + 1) * minutesBetweenHorrizons. "minutesBetweenHorrizons"
 * is usually 4 (sic, the typo comes from the API), and the horizon length is
 * not fixed (commonly 45 to 60 values).
 */
@Serializable
data class MsnNowcasting(
    @Serializable(DateSerializer::class) val timestamp: Date?,
    val minutesBetweenHorrizons: Double?,
    val precipitationRate: List<Double?>?,
)
