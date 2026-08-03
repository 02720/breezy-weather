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

package org.breezyweather.sources.caiyun

import io.reactivex.rxjava3.core.Observable
import org.breezyweather.sources.caiyun.json.CaiyunWeatherResult
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CaiyunApi {

    /**
     * https://docs.caiyunapp.com/weather-api/v2/v2.6/6-weather.html
     * Longitude comes first in the URL path.
     */
    @GET("v2.6/{token}/{lng},{lat}/weather")
    fun getWeather(
        @Path("token") token: String,
        @Path("lng") lng: Double,
        @Path("lat") lat: Double,
        @Query("lang") lang: String,
        @Query("unit") unit: String,
        @Query("dailysteps") dailysteps: Int,
        @Query("dailystart") dailystart: Int,
        @Query("hourlysteps") hourlysteps: Int,
        @Query("alert") alert: Boolean,
    ): Observable<CaiyunWeatherResult>
}
