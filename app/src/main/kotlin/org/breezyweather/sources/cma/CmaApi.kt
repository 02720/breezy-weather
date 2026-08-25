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

package org.breezyweather.sources.cma

import io.reactivex.rxjava3.core.Observable
import org.breezyweather.sources.cma.json.CmaAlertResult
import org.breezyweather.sources.cma.json.CmaGridForecastResult
import org.breezyweather.sources.cma.json.CmaGridLiveResult
import org.breezyweather.sources.cma.json.CmaRegeoResult
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * CMA (data.cma.cn) API
 *
 * Reverse-engineered from https://data.cma.cn/dataGis/static/gridgis/#/pcindex,
 * mirroring the endpoints its detail panel calls for a given location.
 * Last checked: 2026-08-25
 */
interface CmaApi {

    /**
     * Gridded live analysis for a point: the same data the website shows in its
     * live condition panel (temperature, wind, humidity, cloud cover, visibility...)
     */
    @GET("dataGis/multiSource/getAPILiveDataInfo")
    fun getGridLiveData(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
    ): Observable<CmaGridLiveResult>

    /**
     * Gridded day/night forecast (GOWFS) for a point: what the website shows
     * in its 7-day panel
     */
    @GET("rest/gowfs/day")
    fun getGridForecast(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
    ): Observable<CmaGridForecastResult>

    /**
     * Reverse geocoding proxy (AMap) used to resolve coordinates to the
     * administrative division code required for area-based alert queries.
     * The location must be formatted as "longitude,latitude".
     */
    @GET("dataGis/api/gdmap/regeo")
    fun getRegeo(
        @Query("location") location: String,
        @Query("extensions") extensions: String = "all",
        @Query("radius") radius: Int = 1000,
    ): Observable<CmaRegeoResult>

    /**
     * Currently effective alerts for the given administrative area code(s).
     * Pass a comma-separated "province,county" pair (e.g. "450000,450405") to
     * mirror the website, or the nationwide code "100000" for everything.
     */
    @GET("dataGis/api/internetWarn/getEffectiveAlert")
    fun getEffectiveAlerts(
        @Query("areaCode") areaCode: String,
        @Query("eventCode") eventCode: Long = 10000,
        @Query("isAreaRecursion") isAreaRecursion: Int = 1,
        @Query("severity") severity: String = "all",
    ): Observable<CmaAlertResult>
}
