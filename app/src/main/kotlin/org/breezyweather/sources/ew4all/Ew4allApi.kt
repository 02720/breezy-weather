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

package org.breezyweather.sources.ew4all

import io.reactivex.rxjava3.core.Observable
import okhttp3.RequestBody
import org.breezyweather.sources.ew4all.json.Ew4allPointResult
import org.breezyweather.sources.ew4all.json.Ew4allTimeListResult
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * EW4ALL (Cloud-based Early Warning Supporting System) API
 *
 * Reverse-engineered from http://ew4all.wmc-bj.net/EW4ALL/predictions
 * Last checked: 2026-10-07
 *
 * The server is only reachable over cleartext HTTP, which is allowed for
 * this host in the network security configuration.
 */
interface Ew4allApi {

    /**
     * Returns the model runs available for a given model and element,
     * as UTC times, e.g. "20261007000000" for 2026-10-07 00:00 UTC.
     */
    @GET("EW4ALL/api/modelTimeList")
    fun getModelTimeList(
        @Query("data_type") dataType: String,
        @Query("element") element: String,
    ): Observable<Ew4allTimeListResult>

    /**
     * Returns the forecast series of one element at one point, for the model
     * run given as `dataTime` ("YYYYMMDDHH", UTC). Valid times are returned
     * as UTC "yyyy-MM-dd HH:mm:ss" strings.
     */
    @POST("EW4ALL/api/raster/findByPoint")
    fun findByPoint(
        @Body body: RequestBody,
    ): Observable<Ew4allPointResult>
}
