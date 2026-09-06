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

package org.breezyweather.sources.msn

import breezyweather.domain.weather.model.Minutely
import org.breezyweather.sources.msn.json.MsnNowcasting
import org.breezyweather.unit.precipitation.Precipitation.Companion.millimeters
import java.util.Date

/**
 * MSN Weather nowcasting mapping.
 *
 * The app expects 5-minute intervals, whereas the API usually provides
 * 4-minute ones (a length that can’t be split into 5-minute slices), so
 * rates are resampled onto a 5-minute grid starting at the nowcast timestamp.
 */
internal fun getMinutelyForecast(
    nowcasting: MsnNowcasting?,
): List<Minutely>? {
    val rates = nowcasting?.precipitationRate ?: return null
    val startTime = nowcasting.timestamp?.time ?: return null
    val stepMinutes = nowcasting.minutesBetweenHorrizons ?: return null
    if (stepMinutes <= 0.0 || rates.isEmpty()) return null

    // A rate is an average over its interval, so anchor it to the middle of
    // the interval, then interpolate linearly between anchors
    val rateValues = rates.map { (it ?: 0.0).coerceAtLeast(0.0) }
    val lastAnchorMinutes = (rateValues.size - 0.5) * stepMinutes

    fun rateAt(timeMinutes: Double): Double {
        if (timeMinutes <= 0.5 * stepMinutes) return rateValues.first()
        if (timeMinutes >= lastAnchorMinutes) return rateValues.last()
        val position = timeMinutes / stepMinutes - 0.5
        // coerceIn protects against floating-point rounding pushing the index
        // out of bounds when a midpoint lands next to the last anchor
        val index = position.toInt().coerceIn(0, rateValues.size - 2)
        val weight = position - index
        return rateValues[index] * (1.0 - weight) + rateValues[index + 1] * weight
    }

    val minutelyList = mutableListOf<Minutely>()
    val horizonMinutes = rateValues.size * stepMinutes
    var i = 0
    while ((i + 1) * MINUTELY_INTERVAL_MINUTES <= horizonMinutes) {
        val startMinutes = i * MINUTELY_INTERVAL_MINUTES.toDouble()
        minutelyList.add(
            Minutely(
                date = Date(startTime + (startMinutes * 60_000.0).toLong()),
                minuteInterval = MINUTELY_INTERVAL_MINUTES,
                precipitationIntensity = rateAt(
                    startMinutes + MINUTELY_INTERVAL_MINUTES / 2.0
                ).millimeters
            )
        )
        i++
    }
    return minutelyList.ifEmpty { null }
}

private const val MINUTELY_INTERVAL_MINUTES = 5
