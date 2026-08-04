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

import android.content.Context
import org.breezyweather.R
import org.breezyweather.common.options.BaseEnum
import org.breezyweather.common.utils.UnitUtils

/**
 * Query parameters (API factor code and production) for one factor of a model
 */
data class TJWeatherFactorQuery(
    val factorCode: String,
    val production: String,
)

/**
 * TJWeather forecast models, as listed by the model selector of the official
 * website https://www.tjweather.com/vis/ (checked on 2026-08-04)
 *
 * A model is a (production, mode) combination of the TJWeather API. The API
 * factor code and production can vary per factor: the kilometer-level fusion
 * model serves every factor from a different product (c1km, c2_5km, c10km),
 * while the other models use a single product with their own factor codes.
 * Humidity is only available for the models that list it on the official site.
 */
enum class TJWeatherModel(
    override val id: String,
    val mode: String,
    val temperature: TJWeatherFactorQuery,
    val wind: TJWeatherFactorQuery,
    val precipitation: TJWeatherFactorQuery,
    val humidity: TJWeatherFactorQuery? = null,
) : BaseEnum {
    FUSION(
        "nextgen", "nextgen",
        temperature = factorQuery("tmp2m", "c1km"),
        wind = factorQuery("wgrd10m", "c10km"),
        precipitation = factorQuery("pratesfc", "c2_5km"),
        humidity = factorQuery("rh2m", "c10km")
    ),
    T2_EARLY(
        "tj2_early", "early",
        temperature = factorQuery("t2mz", "t2"),
        wind = factorQuery("wgrd10m", "t2"),
        precipitation = factorQuery("pratesfc", "t2"),
        humidity = factorQuery("rh2m", "t2")
    ),
    T2(
        "tj2_late", "late",
        temperature = factorQuery("t2mz", "t2"),
        wind = factorQuery("wgrd10m", "t2"),
        precipitation = factorQuery("pratesfc", "t2"),
        humidity = factorQuery("rh2m", "t2")
    ),
    T1_AI(
        "t1_ai", "t1_ai",
        temperature = factorQuery("t2mz", "t1"),
        wind = factorQuery("wgrd10m", "t1"),
        precipitation = factorQuery("pratesfc", "t1")
    ),
    T1H_AI(
        "T1H-AI", "early",
        temperature = factorQuery("t2mz", "t1h"),
        wind = factorQuery("wgrd10m", "t1h"),
        precipitation = factorQuery("pratesfc", "t1h")
    ),
    IFS(
        "IFS", "ifs",
        temperature = factorQuery("t2m", "i10km"),
        wind = factorQuery("w10m", "i10km"),
        precipitation = factorQuery("tp", "i10km")
    ),
    GFS(
        "GFS", "gfs",
        temperature = factorQuery("t2m", "g28km"),
        wind = factorQuery("w10m", "g28km"),
        precipitation = factorQuery("tp", "g28km")
    ),
    GSM(
        "GSM", "gsm",
        temperature = factorQuery("t2m", "g56km"),
        wind = factorQuery("w10m", "g56km"),
        precipitation = factorQuery("tp", "g56km"),
        humidity = factorQuery("rh", "g56km")
    ),
    ICON(
        "ICON", "icon",
        temperature = factorQuery("t2m", "g28km"),
        wind = factorQuery("w10m", "g28km"),
        precipitation = factorQuery("tp", "g28km"),
        humidity = factorQuery("rh", "g28km")
    ),
    GEM(
        "GEM", "gem",
        temperature = factorQuery("t2m", "g17km"),
        wind = factorQuery("w10m", "g17km"),
        precipitation = factorQuery("tp", "g17km"),
        humidity = factorQuery("rh", "g17km")
    ),
    ;

    companion object {

        fun getInstance(
            value: String,
        ) = TJWeatherModel.entries.firstOrNull {
            it.id == value
        }

        private fun factorQuery(
            factorCode: String,
            production: String,
        ) = TJWeatherFactorQuery(factorCode, production)
    }

    override val valueArrayId = R.array.tjweather_weather_models_values
    override val nameArrayId = R.array.tjweather_weather_models

    override fun getName(context: Context) =
        UnitUtils.getName(context, this)
            .replace(
                "KM-Fusion",
                context.getString(R.string.settings_weather_source_tjweather_weather_models_fusion)
            )
            .replace(
                "T2-Early",
                context.getString(R.string.settings_weather_source_tjweather_weather_models_t2_early)
            )
            .replace(
                "T2",
                context.getString(R.string.settings_weather_source_tjweather_weather_models_t2)
            )
            .replace(
                "T1-AI",
                context.getString(R.string.settings_weather_source_tjweather_weather_models_t1_ai)
            )
        // T1H-AI, IFS, GFS, GSM, ICON and GEM are the same in every language

    fun getDescription(context: Context): String? = when (this) {
        FUSION -> context.getString(R.string.settings_weather_source_tjweather_weather_models_fusion_description)
        T2_EARLY -> context.getString(R.string.settings_weather_source_tjweather_weather_models_t2_early_description)
        T2 -> context.getString(R.string.settings_weather_source_tjweather_weather_models_t2_description)
        T1_AI -> context.getString(R.string.settings_weather_source_tjweather_weather_models_t1_ai_description)
        T1H_AI -> context.getString(R.string.settings_weather_source_tjweather_weather_models_t1h_ai_description)
        IFS -> context.getString(R.string.settings_weather_source_tjweather_weather_models_ifs_description)
        GFS -> context.getString(R.string.settings_weather_source_tjweather_weather_models_gfs_description)
        GSM -> context.getString(R.string.settings_weather_source_tjweather_weather_models_gsm_description)
        ICON -> context.getString(R.string.settings_weather_source_tjweather_weather_models_icon_description)
        GEM -> context.getString(R.string.settings_weather_source_tjweather_weather_models_gem_description)
    }
}
