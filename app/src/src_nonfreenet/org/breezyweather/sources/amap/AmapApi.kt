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

package org.breezyweather.sources.amap

import io.reactivex.rxjava3.core.Observable
import org.breezyweather.sources.amap.json.AmapPlaceTextResult
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Amap (高德地图) Web service API
 *
 * https://lbs.amap.com/api/webservice/guide/api/search
 * Last checked: 2026-10-11
 */
interface AmapApi {

    /**
     * Keyword search for POI, without city restriction (nationwide search)
     */
    @GET("v3/place/text")
    fun searchPlaces(
        @Query("keywords") keywords: String,
        @Query("offset") offset: Int,
        @Query("page") page: Int,
        @Query("extensions") extensions: String,
        @Query("key") key: String,
    ): Observable<AmapPlaceTextResult>
}
