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

/**
 * Response of dataGis/api/gdmap/regeo: reverse geocoding proxy used by the
 * website to resolve coordinates to an administrative area code.
 * Note: unresolvable points return the sentinel codes "100000" (nationwide)
 * or "900000" (outside populated areas) instead of a county code.
 */
@Serializable
data class CmaRegeoResult(
    val infocode: String? = null,
    val regeocode: CmaRegeocode? = null,
)

@Serializable
data class CmaRegeocode(
    val addressComponent: CmaRegeoAddressComponent? = null,
)

@Serializable
data class CmaRegeoAddressComponent(
    /** 6-digit administrative division code, e.g. "450405" */
    val adcode: String? = null,
)
