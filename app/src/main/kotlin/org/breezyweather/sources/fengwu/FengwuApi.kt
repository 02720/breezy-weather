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

package org.breezyweather.sources.fengwu

import io.reactivex.rxjava3.core.Observable
import org.breezyweather.sources.fengwu.json.FengwuAvailabilityResult
import org.breezyweather.sources.fengwu.json.FengwuResult
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Fengwu (相风科技) open API
 *
 * Reverse-engineered from https://fengwuai.com/simple-query
 * Last checked: 2026-09-30
 */
interface FengwuApi {

    /**
     * Returns the time range of the model runs available for querying.
     * `api_end_time` is the latest run and is used as `forecast_time` below.
     */
    @GET("api/v1/weather/availability")
    fun getAvailability(
        @Query("model_type") modelType: String,
        @Query("region") region: String,
    ): Observable<FengwuAvailabilityResult>

    /**
     * Point forecast without authentication. Returns a limited horizon (about 7 days).
     */
    @GET("api/open/v1/weather/visual/query")
    fun getVisualQuery(
        @Query("longitude") longitude: Double,
        @Query("latitude") latitude: Double,
        @Query("variables") variables: String,
        @Query("model_type") modelType: String,
        @Query("region") region: String,
        @Query("forecast_time") forecastTime: String,
    ): Observable<FengwuResult>

    /**
     * Point forecast with a personal API key, extending the horizon up to the full
     * length of the model run.
     */
    @GET("api/open/v1/weather/query")
    fun getQuery(
        @Header("Authorization") authorization: String,
        @Query("longitude") longitude: Double,
        @Query("latitude") latitude: Double,
        @Query("variables") variables: String,
        @Query("model_type") modelType: String,
        @Query("region") region: String,
        @Query("forecast_time") forecastTime: String,
    ): Observable<FengwuResult>
}