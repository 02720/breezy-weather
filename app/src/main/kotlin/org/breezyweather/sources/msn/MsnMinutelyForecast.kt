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
import kotlin.math.min

/**
 * MSN Weather nowcasting mapping.
 *
 * Source rates are average intensities over consecutive, equally sized
 * intervals. They are averaged onto complete 5-minute intervals by the amount
 * of time overlapping each source interval.
 */
internal fun getMinutelyForecast(
    nowcasting: MsnNowcasting?,
): List<Minutely>? {
    val rates = nowcasting?.precipitationRate ?: return null
    val startTime = nowcasting.timestamp?.time ?: return null
    val sourceIntervalMinutes = nowcasting.minutesBetweenHorrizons ?: return null
    if (!sourceIntervalMinutes.isFinite() || sourceIntervalMinutes <= 0.0 || rates.isEmpty()) {
        return null
    }

    val rateValues = rates.map {
        val rate = it ?: 0.0
        if (!rate.isFinite()) return null
        rate.coerceAtLeast(0.0)
    }

    val horizonMinutes = rateValues.size * sourceIntervalMinutes
    val intervalCount = (horizonMinutes / MINUTELY_INTERVAL_MINUTES).toInt()
    if (intervalCount <= 0) return null

    return List(intervalCount) { intervalIndex ->
        val intervalStart = intervalIndex * MINUTELY_INTERVAL_MINUTES.toDouble()
        val intervalEnd = intervalStart + MINUTELY_INTERVAL_MINUTES
        var weightedRate = 0.0
        var sourceTime = intervalStart

        while (sourceTime < intervalEnd) {
            val sourceIndex = (sourceTime / sourceIntervalMinutes).toInt()
            val sourceEnd = (sourceIndex + 1) * sourceIntervalMinutes
            val overlapEnd = min(intervalEnd, sourceEnd)
            weightedRate += rateValues[sourceIndex] * (overlapEnd - sourceTime)
            sourceTime = overlapEnd
        }

        Minutely(
            date = Date(startTime + (intervalStart * 60_000.0).toLong()),
            minuteInterval = MINUTELY_INTERVAL_MINUTES,
            precipitationIntensity = (weightedRate / MINUTELY_INTERVAL_MINUTES).millimeters
        )
    }
}

private const val MINUTELY_INTERVAL_MINUTES = 5
