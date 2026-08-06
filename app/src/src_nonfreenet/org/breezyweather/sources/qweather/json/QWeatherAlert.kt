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

package org.breezyweather.sources.qweather.json

import kotlinx.serialization.Serializable

/**
 * Response of `GET /weatheralert/v1/current/{lat}/{lon}`.
 */
@Serializable
data class QWeatherAlertResult(
    val alerts: List<QWeatherAlert>? = null,
)

@Serializable
data class QWeatherAlert(
    val id: String? = null,
    val senderName: String? = null,
    val issuedTime: String? = null,
    val severity: String? = null,
    val color: QWeatherAlertColor? = null,
    val effectiveTime: String? = null,
    val onsetTime: String? = null,
    val expireTime: String? = null,
    val headline: String? = null,
    val description: String? = null,
    val instruction: String? = null,
)

@Serializable
data class QWeatherAlertColor(
    val code: String? = null,
    // Declared as Double to accept both integer ("30") and fractional ("30.5") JSON numbers.
    val red: Double? = null,
    val green: Double? = null,
    val blue: Double? = null,
    /** Alpha in the [0, 1] range. */
    val alpha: Double? = null,
)
