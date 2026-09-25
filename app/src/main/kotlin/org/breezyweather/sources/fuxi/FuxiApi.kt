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

package org.breezyweather.sources.fuxi

import io.reactivex.rxjava3.core.Observable
import okhttp3.RequestBody
import org.breezyweather.sources.fuxi.json.FuxiTilesResult
import org.breezyweather.sources.fuxi.json.FuxiWeatherInfoResult
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * FuXi weather API
 *
 * Reverse-engineered from https://fuxi-ai.cn/visual/weather
 * Last checked: 2026-09-25
 */
interface FuxiApi {

    /**
     * Returns the latest published model run of each model
     * forecastType "1" is the medium-range model 伏羲中期 (FuXi-C88)
     */
    @GET("gw/weather/api/v1/weather/queryWeatherTile")
    fun getWeatherTiles(): Observable<FuxiTilesResult>

    /**
     * Hourly point forecast of the model run given in the request body
     */
    @POST("gw/weather/api/v1/weather/queryWeatherInfo")
    fun getWeatherInfo(
        @Body body: RequestBody,
    ): Observable<FuxiWeatherInfoResult>
}
