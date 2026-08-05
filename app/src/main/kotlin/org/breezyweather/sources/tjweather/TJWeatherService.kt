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

/**
 * The four factor results of a single base run, grouped together so that they can
 * be sent through Observable.zip() (which cannot emit a null value)
 */
private data class ModelForecastData(
    val temperature: TJWeatherResult,
    val wind: TJWeatherResult,
    val precipitation: TJWeatherResult,
    val humidity: TJWeatherResult,
)

class TJWeatherService @Inject constructor(
    @ApplicationContext context: Context,
    @Named("JsonClient") val client: Retrofit.Builder,
) : HttpSource(), WeatherSource, PreferencesParametersSource {

    override val id = "tjweather"
    override val name = "中科天机"
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
     * 1. Get the available base times of the model from the availability endpoint,
     *    newest first
     * 2. Query each factor for the newest base time, and merge them by forecast time.
     *
     * The factors of a base run are not necessarily all published at the same moment
     * (e.g. the precipitation product can be a few minutes late compared to the
     * temperature product): querying a factor at a base time it does not cover yet
     * returns an empty list, which would silently drop the whole factor. When that
     * happens we fall back to the previous base run (see getModelForecastAtBaseTime).
     */
    private fun getModelForecast(
        location: Location,
        model: TJWeatherModel,
    ): Observable<List<HourlyWrapper>> {
        return mApi.getAvailableForecasts(
            model.temperature.factorCode,
            model.temperature.production
        ).flatMap { availability ->
            val baseTimes = availability.data
                ?.filter {
                    it.mode == model.mode &&
                        it.production == model.temperature.production &&
                        it.factorCode == model.temperature.factorCode
                }
                ?.mapNotNull { it.baseTimeString?.takeIf { baseTime -> baseTime.isNotBlank() } }
                ?.distinct()
                ?.sortedDescending()
                // Only the newest base runs are relevant: a factor publication lag
                // is a matter of minutes, and this bounds the fallback requests
                ?.take(MAX_BASE_TIME_ATTEMPTS)
            if (baseTimes.isNullOrEmpty()) {
                // Model has no forecast available right now
                Observable.just(emptyList())
            } else {
                getModelForecastAtBaseTime(location, model, baseTimes, 0)
            }
        }
    }

    /**
     * Fetch the forecast of the model at the given base time, and retry with the
     * previous base time if a required factor is missing for this base run
     */
    private fun getModelForecastAtBaseTime(
        location: Location,
        model: TJWeatherModel,
        baseTimes: List<String>,
        baseTimeIndex: Int,
    ): Observable<List<HourlyWrapper>> {
        val baseTime = baseTimes[baseTimeIndex]
        // Humidity is not available for every model, and is optional:
        // a failure on it must not prevent the rest of the forecast
        val humidityQuery = model.humidity?.let {
            mApi.getForecast(
                location.longitude, location.latitude,
                model.mode, baseTime, it.production, factorCode = it.factorCode
            ).onErrorResumeNext { Observable.just(TJWeatherResult()) }
        } ?: Observable.just(TJWeatherResult())
        // zip() cannot emit a null, so the results are first grouped into a holder,
        // and the base-time fallback decision is taken inside flatMap
        return Observable.zip(
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
            ModelForecastData(temperature, wind, precipitation, humidity)
        }.flatMap { forecastData ->
            // On the last available base time, incomplete factors are tolerated
            // (graceful degradation) instead of failing the whole forecast
            val hourlyForecast = convertToHourlyForecast(
                forecastData.temperature,
                forecastData.wind,
                forecastData.precipitation,
                forecastData.humidity,
                model,
                allowIncompleteFactors = baseTimeIndex + 1 >= baseTimes.size
            )
            if (hourlyForecast == null) {
                // A required factor (e.g. precipitation) is not published yet for
                // this base run: retry with the previous base run
                getModelForecastAtBaseTime(location, model, baseTimes, baseTimeIndex + 1)
            } else {
                Observable.just(hourlyForecast)
            }
        }
    }

    /**
     * @return the merged hourly forecast; an empty list if the model has no
     * forecast for this location at this base time; null if a required factor
     * (wind or precipitation) is missing for this base run and
     * [allowIncompleteFactors] is false (the caller can then fall back to a
     * previous base run)
     */
    private fun convertToHourlyForecast(
        temperature: TJWeatherResult,
        wind: TJWeatherResult,
        precipitation: TJWeatherResult,
        humidity: TJWeatherResult,
        model: TJWeatherModel,
        allowIncompleteFactors: Boolean,
    ): List<HourlyWrapper>? {
        val temperatureDetails = getFactorDetails(temperature, model.temperature.factorCode)
        if (temperatureDetails.isEmpty()) return emptyList()

        // Factor codes are model-specific (e.g. "w10m" for the NWP models)
        val windDetails = getFactorDetails(wind, model.wind.factorCode)
        val precipitationDetails = getFactorDetails(precipitation, model.precipitation.factorCode)
        if (!allowIncompleteFactors && (windDetails.isEmpty() || precipitationDetails.isEmpty())) {
            // The wind or precipitation product is not published yet for this base run
            return null
        }
        val windDetailsByTime = windDetails.associateBy { it.forecastTimeString }
        val precipitationDetailsByTime = precipitationDetails.associateBy { it.forecastTimeString }
        val humidityDetailsByTime = model.humidity
            ?.let { getFactorDetails(humidity, it.factorCode).associateBy { it.forecastTimeString } }
            ?: emptyMap()

        val hourlyForecast = mutableListOf<HourlyWrapper>()
        // "forecastTimeString" is written in Beijing time (UTC+8), while
        // "forecastTime" is ISO-8601 with an explicit UTC offset (see convertToDate)
        val dateFormatter = SimpleDateFormat(FORECAST_TIME_FORMAT, Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
        val dateFormatterIso = SimpleDateFormat(ISO_FORECAST_TIME_FORMAT, Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        for (detail in temperatureDetails) {
            val date = convertToDate(dateFormatter, dateFormatterIso, detail) ?: continue
            val windDetail = windDetailsByTime[detail.forecastTimeString]
            hourlyForecast.add(
                HourlyWrapper(
                    date = date,
                    temperature = TemperatureWrapper(
                        temperature = detail.value?.getOrNull(0)?.celsius
                    ),
                    precipitation = Precipitation(
                        // Precipitation rate in mm/h, equivalent to the hourly accumulation
                        total = precipitationDetailsByTime[detail.forecastTimeString]
                            ?.value?.getOrNull(0)?.millimeters
                    ),
                    wind = Wind(
                        degree = windDetail?.value?.getOrNull(3),
                        speed = windDetail?.value?.getOrNull(2)?.metersPerSecond
                    ),
                    relativeHumidity = humidityDetailsByTime[detail.forecastTimeString]
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

    /**
     * Returns the date of a forecast detail. The ISO-8601 "forecastTime" carries an
     * explicit UTC offset and is preferred; the "forecastTimeString" (Beijing time,
     * UTC+8) is only used as a fallback.
     */
    private fun convertToDate(
        dateFormatter: SimpleDateFormat,
        dateFormatterIso: SimpleDateFormat,
        detail: TJWeatherForecastDetail,
    ): Date? {
        convertToDateFromIsoString(dateFormatterIso, detail.forecastTime)?.let { return it }
        val forecastTimeString = detail.forecastTimeString
            ?: return null
        return try {
            dateFormatter.parse(forecastTimeString)
        } catch (e: ParseException) {
            null
        }
    }

    /**
     * Parse the ISO-8601 forecast time (e.g. "2026-08-05T01:00:00.000+00:00").
     * The TJWeather API always writes a "+00:00" offset, so the milliseconds and
     * offset can safely be truncated and the time parsed as UTC.
     */
    private fun convertToDateFromIsoString(
        dateFormatterIso: SimpleDateFormat,
        forecastTime: String?,
    ): Date? {
        if (forecastTime == null) return null
        return try {
            dateFormatterIso.parse(forecastTime.substringBefore('.'))
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
            // Day boundaries are at local midnight
            val dayDate = hourly.date
                .toCalendarWithTimeZone(location.timeZone)
                .apply {
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

        // How many of the newest base runs are tried before giving up on a
        // missing factor (see getModelForecastAtBaseTime)
        private const val MAX_BASE_TIME_ATTEMPTS = 3

        // "forecastTimeString" (e.g. "2026080509") is written in Beijing time (UTC+8),
        // while "forecastTime" is ISO-8601 with an explicit UTC offset
        // (e.g. "2026-08-05T01:00:00.000+00:00")
        // The ISO-8601 format must not use the "X" pattern (not supported on all API levels)
        private const val FORECAST_TIME_FORMAT = "yyyyMMddHH"
        private const val ISO_FORECAST_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
    }
}
