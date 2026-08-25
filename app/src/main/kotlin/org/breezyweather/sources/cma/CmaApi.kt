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
import org.breezyweather.sources.cma.json.CmaGridLiveResult
import org.breezyweather.sources.cma.json.CmaNearStationResult
import org.breezyweather.sources.cma.json.CmaStationLatestResult
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CMA (data.cma.cn) API
 *
 * Reverse-engineered from https://data.cma.cn/dataGis/static/gridgis/#/pcindex
 * Last checked: 2026-08-25
 */
interface CmaApi {

    /**
     * Nearest national station for given coordinates.
     * Note: the longitude parameter is intentionally spelled "lag" by the server.
     */
    @GET("dataGis/api/station/getNearStation")
    fun getNearStation(
        @Query("lag") longitude: Double,
        @Query("lat") latitude: Double,
        @Query("dist") dist: Int,
        @Query("location") location: Int = 1,
        @Query("stationName") stationName: String = "none",
        @Query("apiTp") apiTp: Int = 1,
    ): Observable<CmaNearStationResult>

    /**
     * Latest observation and 7-day day/night forecast for a station
     */
    @GET("app/Rest/liveDataService/station/{stationId}/latest")
    fun getStationLatest(
        @Path("stationId") stationId: String,
        @Query("datetime") datetime: String,
    ): Observable<CmaStationLatestResult>

    /**
     * Gridded live data for a point, used as a fallback when no station is found
     */
    @GET("dataGis/multiSource/getAPILiveDataInfo")
    fun getGridLiveData(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
    ): Observable<CmaGridLiveResult>

    /**
     * All currently effective alerts nationwide
     */
    @GET("dataGis/api/internetWarn/getEffectiveAlert")
    fun getEffectiveAlerts(
        @Query("areaCode") areaCode: Long = 100000,
        @Query("eventCode") eventCode: Long = 10000,
        @Query("isAreaRecursion") isAreaRecursion: Int = 1,
        @Query("severity") severity: String = "all",
    ): Observable<CmaAlertResult>
}
