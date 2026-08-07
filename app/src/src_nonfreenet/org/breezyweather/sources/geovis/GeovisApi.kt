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

package org.breezyweather.sources.geovis

import io.reactivex.rxjava3.core.Observable
import org.breezyweather.sources.geovis.json.GeovisAlertResult
import org.breezyweather.sources.geovis.json.GeovisAqiResult
import org.breezyweather.sources.geovis.json.GeovisDailyResult
import org.breezyweather.sources.geovis.json.GeovisHourlyResult
import org.breezyweather.sources.geovis.json.GeovisRealtimeResult
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 中科星图 (Geovis Earth Data Cloud) meteorological API.
 *
 * Reverse-engineered from https://datacloud.geovisearth.com/support/meteorological/summary
 * Last checked: 2026-08-07
 *
 * All endpoints answer with HTTP 200 and signal failures through the body-level `status` field.
 * The `location` parameter is "lon,lat" in GCJ-02 (Mars) coordinates, with the longitude first
 * — coordinates must be converted from the WGS-84 that the app stores. The `cn/` products cover
 * mainland China while the `global/` products cover the rest of the world.
 */
interface GeovisApi {

    @GET("{region}/realtime/area")
    fun getRealtime(
        @Path("region") region: String,
        @Query("location") location: String,
        @Query("token") token: String,
    ): Observable<GeovisRealtimeResult>

    /**
     * Basic daily forecast (15 days in China, 7 days elsewhere).
     */
    @GET("{region}/forecast/day/area")
    fun getDaily(
        @Path("region") region: String,
        @Query("location") location: String,
        @Query("token") token: String,
    ): Observable<GeovisDailyResult>

    /**
     * Extended daily forecast (90 days in China, 15 days elsewhere).
     */
    @GET("{region}/forecast/day/area/professional")
    fun getDailyProfessional(
        @Path("region") region: String,
        @Query("location") location: String,
        @Query("token") token: String,
    ): Observable<GeovisDailyResult>

    /**
     * Basic hourly forecast (48 h everywhere).
     */
    @GET("{region}/forecast/hour/area")
    fun getHourly(
        @Path("region") region: String,
        @Query("location") location: String,
        @Query("token") token: String,
    ): Observable<GeovisHourlyResult>

    /**
     * Extended hourly forecast (120 h in China, 82 h elsewhere).
     */
    @GET("{region}/forecast/hour/area/professional")
    fun getHourlyProfessional(
        @Path("region") region: String,
        @Query("location") location: String,
        @Query("token") token: String,
    ): Observable<GeovisHourlyResult>

    /**
     * Air quality of the monitoring stations around the requested point (China only).
     */
    @GET("cn/realtime/aqi/stations")
    fun getAirQuality(
        @Query("location") location: String,
        @Query("token") token: String,
    ): Observable<GeovisAqiResult>

    /**
     * The alerts currently in effect for the requested point (China only; other regions return
     * a non-zero status or an empty list).
     */
    @GET("alert/now/data")
    fun getAlert(
        @Query("location") location: String,
        @Query("token") token: String,
    ): Observable<GeovisAlertResult>
}
