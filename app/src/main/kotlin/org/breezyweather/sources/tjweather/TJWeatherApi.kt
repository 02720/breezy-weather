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

package org.breezyweather.sources.tjweather

import io.reactivex.rxjava3.core.Observable
import org.breezyweather.sources.tjweather.json.TJWeatherAvailabilityResult
import org.breezyweather.sources.tjweather.json.TJWeatherResult
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * TJWeather API
 *
 * Reverse-engineered from https://www.tjweather.com/vis/
 * Last checked: 2026-08-04
 */
interface TJWeatherApi {

    /**
     * Hourly forecast for a single point, for a single factor of a single model
     * Base time must be the latest available one, fetched from [getAvailableForecasts]
     */
    @GET("spas/single-point/query")
    fun getForecast(
        @Query("lon") longitude: Double,
        @Query("lat") latitude: Double,
        @Query("mode") mode: String,
        @Query("baseTime") baseTime: String,
        @Query("production") production: String,
        @Query("region") region: String = "global",
        @Query("factorCode") factorCode: String,
    ): Observable<TJWeatherResult>

    /**
     * Returns the available models (mode/production/region) and their latest base time
     * for a given factor and production
     */
    @GET("main/factor/forecast-available")
    fun getAvailableForecasts(
        @Query("factorCode") factorCode: String,
        @Query("production") production: String,
    ): Observable<TJWeatherAvailabilityResult>
}
