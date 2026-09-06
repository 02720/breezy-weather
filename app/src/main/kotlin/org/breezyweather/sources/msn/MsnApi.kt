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

package org.breezyweather.sources.msn

import io.reactivex.rxjava3.core.Observable
import org.breezyweather.sources.msn.json.MsnWeatherResult
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * MSN Weather (https://www.msn.cn/zh-cn/weather)
 * Single overview endpoint returning current conditions, daily and hourly
 * forecasts, minute-level precipitation nowcasting, and weather alerts.
 */
interface MsnApi {
    @GET("service/weather/overview")
    fun getOverview(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("locale") locale: String,
        @Query("units") units: String,
        @Query("days") days: Int,
        @Query("cuthour") cutHour: Boolean,
        @Query("wrapodata") wrapOData: Boolean,
        @Query("apikey") apikey: String,
        @Query("appId") appId: String,
        @Query("ocid") ocid: String,
    ): Observable<MsnWeatherResult>
}
