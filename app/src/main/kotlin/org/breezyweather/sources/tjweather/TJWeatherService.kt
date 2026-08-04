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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import breezyweather.domain.location.model.Location
import breezyweather.domain.source.SourceContinent
import breezyweather.domain.source.SourceFeature
import breezyweather.domain.weather.model.Precipitation
import breezyweather.domain.weather.model.Wind
import breezyweather.domain.weather.wrappers.DailyWrapper
import breezyweather.domain.weather.wrappers.HourlyWrapper
import breezyweather.domain.weather.wrappers.TemperatureWrapper
import breezyweather.domain.weather.wrappers.WeatherWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import kotlinx.collections.immutable.ImmutableList
import org.breezyweather.R
import org.breezyweather.common.exceptions.InvalidLocationException
import org.breezyweather.common.extensions.toCalendarWithTimeZone
import org.breezyweather.common.source.HttpSource
import org.breezyweather.common.source.PreferencesParametersSource
import org.breezyweather.common.source.WeatherSource
import org.breezyweather.sources.tjweather.json.TJWeatherForecastDetail
import org.breezyweather.sources.tjweather.json.TJWeatherResult
import org.breezyweather.ui.common.composables.AlertDialogNoPadding
import org.breezyweather.ui.settings.preference.composables.PreferenceView
import org.breezyweather.ui.settings.preference.composables.SwitchPreferenceView
import org.breezyweather.unit.precipitation.Precipitation.Companion.millimeters
import org.breezyweather.unit.ratio.Ratio.Companion.percent
import org.breezyweather.unit.speed.Speed.Companion.metersPerSecond
import org.breezyweather.unit.temperature.Temperature.Companion.celsius
import retrofit2.Retrofit
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Named

class TJWeatherService @Inject constructor(
    @ApplicationContext context: Context,
    @Named("JsonClient") val client: Retrofit.Builder,
) : HttpSource(), WeatherSource, PreferencesParametersSource {

    override val id = "tjweather"
    override val name = "Tianji Weather"
    override val continent = SourceContinent.WORLDWIDE
    override val privacyPolicyUrl = "https://www.tjweather.com/agreementProfile"

    private val mApi by lazy {
        client
            .baseUrl(BASE_URL)
            .build()
            .create(TJWeatherApi::class.java)
    }

    override val supportedFeatures = mapOf(
        SourceFeature.FORECAST to name
    )
    override val attributionLinks = mapOf(
        name to "https://www.tjweather.com/"
    )

    // Locations to test in the debug version
    override val testingLocations: List<Location> = listOf(
        Location(
            city = "Beijing",
            latitude = 39.9042,
            longitude = 116.4074,
            timeZone = TimeZone.getTimeZone("Asia/Shanghai"),
            country = "China",
            countryCode = "CN",
            forecastSource = id
        ),
        Location(
            city = "Paris",
            latitude = 48.8566,
            longitude = 2.3522,
            timeZone = TimeZone.getTimeZone("Europe/Paris"),
            country = "France",
            countryCode = "FR",
            forecastSource = id
        )
    )

    override fun requestWeather(
        context: Context,
        location: Location,
        requestedFeatures: List<SourceFeature>,
    ): Observable<WeatherWrapper> {
        val failedFeatures = mutableMapOf<SourceFeature, Throwable>()
        if (SourceFeature.FORECAST !in requestedFeatures) {
            return Observable.just(WeatherWrapper(failedFeatures = failedFeatures))
        }
        return getModelForecast(location, getWeatherModel(location))
            .map { hourlyForecast ->
                if (hourlyForecast.isEmpty()) {
                    // The selected model has no forecast for this location
                    failedFeatures[SourceFeature.FORECAST] = InvalidLocationException()
                    WeatherWrapper(
                        dailyForecast = null,
                        hourlyForecast = null,
                        failedFeatures = failedFeatures
                    )
                } else {
                    WeatherWrapper(
                        dailyForecast = getDailyList(hourlyForecast, location),
                        hourlyForecast = hourlyForecast,
                        failedFeatures = failedFeatures
                    )
                }
            }
            .onErrorResumeNext { e ->
                failedFeatures[SourceFeature.FORECAST] = e
                Observable.just(
                    WeatherWrapper(
                        dailyForecast = null,
                        hourlyForecast = null,
                        failedFeatures = failedFeatures
                    )
                )
            }
    }

    /**
     * Fetch the forecast of a single model:
     * 1. Get the latest base time of the model from the availability endpoint
     * 2. Query each factor for that base time, and merge them by forecast time
     */
    private fun getModelForecast(
        location: Location,
        model: TJWeatherModel,
    ): Observable<List<HourlyWrapper>> {
        return mApi.getAvailableForecasts(
            model.temperature.factorCode,
            model.temperature.production
        ).flatMap { availability ->
            // Take the latest base time of the model, whatever the region
            val baseTime = availability.data
                ?.filter {
                    it.mode == model.mode && it.factorCode == model.temperature.factorCode
                }
                ?.maxByOrNull { it.baseTimeString ?: "" }
                ?.baseTimeString
            if (baseTime.isNullOrEmpty()) {
                // Model has no forecast available right now
                Observable.just(emptyList())
            } else {
                // Humidity is not available for every model, and is optional:
                // a failure on it must not prevent the rest of the forecast
                val humidityQuery = model.humidity?.let {
                    mApi.getForecast(
                        location.longitude, location.latitude,
                        model.mode, baseTime, it.production, factorCode = it.factorCode
                    ).onErrorResumeNext { Observable.just(TJWeatherResult()) }
                } ?: Observable.just(TJWeatherResult())
                Observable.zip(
                    mApi.getForecast(
                        location.longitude, location.latitude,
                        model.mode, baseTime, model.temperature.production, factorCode = model.temperature.factorCode
                    ),
                    mApi.getForecast(
                        location.longitude, location.latitude,
                        model.mode, baseTime, model.wind.production, factorCode = model.wind.factorCode
                    ),
                    mApi.getForecast(
                        location.longitude, location.latitude,
                        model.mode, baseTime, model.precipitation.production, factorCode = model.precipitation.factorCode
                    ),
                    humidityQuery
                ) { temperature, wind, precipitation, humidity ->
                    convertToHourlyForecast(temperature, wind, precipitation, humidity, model)
                }
            }
        }
    }

    private fun convertToHourlyForecast(
        temperature: TJWeatherResult,
        wind: TJWeatherResult,
        precipitation: TJWeatherResult,
        humidity: TJWeatherResult,
        model: TJWeatherModel,
    ): List<HourlyWrapper> {
        val temperatureDetails = getFactorDetails(temperature, model.temperature.factorCode)
        if (temperatureDetails.isEmpty()) return emptyList()

        // Factor codes are model-specific (e.g. "w10m" for the NWP models)
        val windDetails = getFactorDetails(wind, model.wind.factorCode)
            .associateBy { it.forecastTimeString }
        val precipitationDetails = getFactorDetails(precipitation, model.precipitation.factorCode)
            .associateBy { it.forecastTimeString }
        val humidityDetails = model.humidity
            ?.let { getFactorDetails(humidity, it.factorCode).associateBy { it.forecastTimeString } }
            ?: emptyMap()

        val hourlyForecast = mutableListOf<HourlyWrapper>()
        val dateFormatter = SimpleDateFormat(FORECAST_TIME_FORMAT, Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        for (detail in temperatureDetails) {
            val date = convertToDate(dateFormatter, detail) ?: continue
            val windDetail = windDetails[detail.forecastTimeString]
            hourlyForecast.add(
                HourlyWrapper(
                    date = date,
                    temperature = TemperatureWrapper(
                        temperature = detail.value?.getOrNull(0)?.celsius
                    ),
                    precipitation = Precipitation(
                        // Precipitation rate in mm/h, equivalent to the hourly accumulation
                        total = precipitationDetails[detail.forecastTimeString]
                            ?.value?.getOrNull(0)?.millimeters
                    ),
                    wind = Wind(
                        degree = windDetail?.value?.getOrNull(3),
                        speed = windDetail?.value?.getOrNull(2)?.metersPerSecond
                    ),
                    relativeHumidity = humidityDetails[detail.forecastTimeString]
                        ?.value?.getOrNull(0)?.percent
                )
            )
        }
        return hourlyForecast
    }

    private fun getFactorDetails(
        result: TJWeatherResult,
        factorCode: String,
    ): List<TJWeatherForecastDetail> {
        return result.data?.forecast
            ?.firstOrNull { it.factorCode == factorCode }
            ?.forecastDetails
            ?: emptyList()
    }

    private fun convertToDate(
        dateFormatter: SimpleDateFormat,
        detail: TJWeatherForecastDetail,
    ): Date? {
        val forecastTimeString = detail.forecastTimeString
            ?: return convertToDateFromIsoString(detail.forecastTime)
        return try {
            dateFormatter.parse(forecastTimeString)
        } catch (e: ParseException) {
            // Fallback on the ISO-8601 forecast time if the string format changes
            convertToDateFromIsoString(detail.forecastTime)
        }
    }

    /**
     * Parse the ISO-8601 forecast time, always in UTC (e.g. "2026-08-04T01:00:00.000+00:00")
     */
    private fun convertToDateFromIsoString(
        forecastTime: String?,
    ): Date? {
        if (forecastTime == null) return null
        return try {
            SimpleDateFormat(ISO_FORECAST_TIME_FORMAT, Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(forecastTime.substringBefore('.'))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns one DailyWrapper per local day covered by the hourly forecast, with only the date set.
     * The app computes the rest of the daily data from the hourly forecast.
     */
    private fun getDailyList(
        hourlyForecast: List<HourlyWrapper>,
        location: Location,
    ): List<DailyWrapper> {
        val dailyList = mutableListOf<DailyWrapper>()
        var lastDayDate: Date? = null
        for (hourly in hourlyForecast) {
            val dayDate = hourly.date
                .toCalendarWithTimeZone(location.timeZone)
                .apply {
                    add(Calendar.HOUR_OF_DAY, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
            if (lastDayDate == null || dayDate.time != lastDayDate.time) {
                dailyList.add(DailyWrapper(date = dayDate))
                lastDayDate = dayDate
            }
        }
        return dailyList
    }

    // Per-location preferences
    override fun hasPreferencesScreen(
        location: Location,
        features: List<SourceFeature>,
    ): Boolean {
        return SourceFeature.FORECAST in features
    }

    private fun getWeatherModel(
        location: Location,
    ): TJWeatherModel {
        return location.parameters
            .getOrElse(id) { null }?.getOrElse("weatherModels") { null }
            ?.let { TJWeatherModel.getInstance(it) }
            ?: TJWeatherModel.FUSION
    }

    data class WeatherModelStatus(
        val model: TJWeatherModel,
        val enabled: Boolean,
    )

    @Composable
    override fun PerLocationPreferences(
        context: Context,
        location: Location,
        features: ImmutableList<SourceFeature>,
        onSave: (Map<String, String>) -> Unit,
    ) {
        val dialogModelsOpenState = remember { mutableStateOf(false) }
        val changedWeatherModelsState = remember { mutableStateOf(false) }
        val weatherModels = remember {
            mutableStateListOf<WeatherModelStatus>().apply {
                val cv = getWeatherModel(location)
                addAll(
                    TJWeatherModel.entries.map {
                        WeatherModelStatus(
                            model = it,
                            enabled = it == cv
                        )
                    }
                )
            }
        }

        PreferenceView(
            title = stringResource(R.string.settings_weather_source_tjweather_weather_models),
            summary = weatherModels
                .firstOrNull { it.enabled }
                ?.model
                ?.getName(context)
                ?: TJWeatherModel.FUSION.getName(context),
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        ) {
            dialogModelsOpenState.value = true
        }

        if (dialogModelsOpenState.value) {
            AlertDialogNoPadding(
                title = {
                    Text(
                        text = stringResource(R.string.settings_weather_source_tjweather_weather_models),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        items(
                            weatherModels,
                            { key ->
                                // Doesn’t update otherwise
                                key.hashCode()
                            }
                        ) { model ->
                            SwitchPreferenceView(
                                title = model.model.getName(context),
                                summary = { context, _ -> model.model.getDescription(context) },
                                checked = model.enabled,
                                card = false,
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                )
                            ) { checked ->
                                if (checked) {
                                    // Exclusive selection: only one model can be enabled
                                    weatherModels.forEachIndexed { index, modelStatus ->
                                        weatherModels[index] = modelStatus.copy(
                                            enabled = modelStatus.model == model.model
                                        )
                                    }
                                } else {
                                    weatherModels.indexOfFirst { it.model == model.model }.let {
                                        if (it != -1) {
                                            weatherModels[it] = weatherModels[it].copy(enabled = false)
                                        }
                                    }
                                }
                                changedWeatherModelsState.value = true
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (changedWeatherModelsState.value) {
                                onSave(
                                    mapOf(
                                        "weatherModels" to (
                                            weatherModels.firstOrNull { it.enabled }?.model
                                                ?: TJWeatherModel.FUSION
                                            ).id
                                    )
                                )
                            }
                            dialogModelsOpenState.value = false
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.action_confirm),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            val cv = getWeatherModel(location)
                            weatherModels.forEachIndexed { key, value ->
                                weatherModels[key] = value.copy(
                                    enabled = value.model == cv
                                )
                            }
                            dialogModelsOpenState.value = false
                        }
                    ) {
                        Text(
                            text = stringResource(android.R.string.cancel),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                },
                onDismissRequest = {
                    val cv = getWeatherModel(location)
                    weatherModels.forEachIndexed { key, value ->
                        weatherModels[key] = value.copy(
                            enabled = value.model == cv
                        )
                    }
                    dialogModelsOpenState.value = false
                }
            )
        }
    }

    companion object {
        private const val BASE_URL = "https://www.tjweather.com/meteorological/"

        // Forecast times are UTC: e.g. "2026080409" or "2026-08-04T01:00:00.000+00:00"
        // The ISO-8601 format must not use the "X" pattern (not supported on all API levels)
        private const val FORECAST_TIME_FORMAT = "yyyyMMddHH"
        private const val ISO_FORECAST_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
    }
}
