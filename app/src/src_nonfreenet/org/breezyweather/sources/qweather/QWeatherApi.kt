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

package org.breezyweather.sources.qweather

import io.reactivex.rxjava3.core.Observable
import org.breezyweather.sources.qweather.json.QWeatherAirCurrentResult
import org.breezyweather.sources.qweather.json.QWeatherAirHourlyResult
import org.breezyweather.sources.qweather.json.QWeatherAlertResult
import org.breezyweather.sources.qweather.json.QWeatherCurrent
import org.breezyweather.sources.qweather.json.QWeatherDailyResult
import org.breezyweather.sources.qweather.json.QWeatherHourlyResult
import org.breezyweather.sources.qweather.json.QWeatherMinutelyResult
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * QWeather API. Uses the modern v1 endpoints (lat/lon path parameters) for weather, alerts and
 * air quality, and the legacy v7 endpoint for minutely precipitation (China only).
 *
 * Authentication is done with an API Key passed via the `X-QW-Api-Key` header, which is supported
 * by every endpoint used here. The base URL is the developer's personal API Host.
 */
interface QWeatherApi {

    @GET("weather/v1/current/{lat}/{lon}")
    fun getCurrent(
        @Header("X-QW-Api-Key") apiKey: String,
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Query("lang") lang: String,
    ): Observable<QWeatherCurrent>

    @GET("weather/v1/daily/{lat}/{lon}")
    fun getDaily(
        @Header("X-QW-Api-Key") apiKey: String,
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Query("days") days: Int,
        @Query("lang") lang: String,
    ): Observable<QWeatherDailyResult>

    @GET("weather/v1/hourly/{lat}/{lon}")
    fun getHourly(
        @Header("X-QW-Api-Key") apiKey: String,
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Query("hours") hours: Int,
        @Query("lang") lang: String,
    ): Observable<QWeatherHourlyResult>

    @GET("v7/minutely/5m")
    fun getMinutely(
        @Header("X-QW-Api-Key") apiKey: String,
        @Query("location") location: String,
        @Query("lang") lang: String,
    ): Observable<QWeatherMinutelyResult>

    @GET("weatheralert/v1/current/{lat}/{lon}")
    fun getAlert(
        @Header("X-QW-Api-Key") apiKey: String,
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Query("lang") lang: String,
    ): Observable<QWeatherAlertResult>

    @GET("airquality/v1/current/{lat}/{lon}")
    fun getAirCurrent(
        @Header("X-QW-Api-Key") apiKey: String,
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Query("lang") lang: String,
    ): Observable<QWeatherAirCurrentResult>

    @GET("airquality/v1/hourly/{lat}/{lon}")
    fun getAirHourly(
        @Header("X-QW-Api-Key") apiKey: String,
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Query("lang") lang: String,
    ): Observable<QWeatherAirHourlyResult>
}
