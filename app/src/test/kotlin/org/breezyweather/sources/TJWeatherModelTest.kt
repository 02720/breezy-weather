package org.breezyweather.sources

import io.kotest.matchers.shouldBe
import org.breezyweather.sources.tjweather.TJWeatherModel
import org.junit.jupiter.api.Test

class TJWeatherModelTest {

    @Test
    fun getInstanceTest() {
        // Values are stored in the location settings ("weatherModels" parameter)
        TJWeatherModel.getInstance("nextgen") shouldBe TJWeatherModel.FUSION
        TJWeatherModel.getInstance("tj2_early") shouldBe TJWeatherModel.T2_EARLY
        TJWeatherModel.getInstance("tj2_late") shouldBe TJWeatherModel.T2
        TJWeatherModel.getInstance("t1_ai") shouldBe TJWeatherModel.T1_AI
        TJWeatherModel.getInstance("T1H-AI") shouldBe TJWeatherModel.T1H_AI
        TJWeatherModel.getInstance("IFS") shouldBe TJWeatherModel.IFS
        TJWeatherModel.getInstance("GFS") shouldBe TJWeatherModel.GFS
        TJWeatherModel.getInstance("GSM") shouldBe TJWeatherModel.GSM
        TJWeatherModel.getInstance("ICON") shouldBe TJWeatherModel.ICON
        TJWeatherModel.getInstance("GEM") shouldBe TJWeatherModel.GEM
        TJWeatherModel.getInstance("unknown_model") shouldBe null
    }

    @Test
    fun idTest() {
        // Stored parameter values are the official productModeCode values, and
        // must not change, or saved preferences will break
        TJWeatherModel.FUSION.id shouldBe "nextgen"
        TJWeatherModel.T2_EARLY.id shouldBe "tj2_early"
        TJWeatherModel.T2.id shouldBe "tj2_late"
        TJWeatherModel.T1_AI.id shouldBe "t1_ai"
        TJWeatherModel.T1H_AI.id shouldBe "T1H-AI"
        TJWeatherModel.IFS.id shouldBe "IFS"
        TJWeatherModel.GFS.id shouldBe "GFS"
        TJWeatherModel.GSM.id shouldBe "GSM"
        TJWeatherModel.ICON.id shouldBe "ICON"
        TJWeatherModel.GEM.id shouldBe "GEM"
    }
}
