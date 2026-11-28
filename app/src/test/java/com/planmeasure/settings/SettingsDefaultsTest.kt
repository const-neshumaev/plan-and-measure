package com.planmeasure.settings

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDefaultsTest {

    @Test
    fun englishResolvesToEnglishLocale() {
        assertEquals("en", AppLanguage.English.resolveLocale().language)
    }

    @Test
    fun russianResolvesToRussianLocale() {
        assertEquals("ru", AppLanguage.Russian.resolveLocale().language)
    }

    @Test
    fun supportedSystemLanguageIsAdopted() {
        assertEquals(AppLanguage.Russian, languageForSystemTag("ru"))
        assertEquals(AppLanguage.English, languageForSystemTag("en"))
    }

    @Test
    fun unsupportedSystemLanguageFallsBackToEnglish() {
        assertEquals(AppLanguage.English, languageForSystemTag("de"))
        assertEquals(AppLanguage.English, languageForSystemTag("uk"))
    }

    @Test
    fun themeFollowsSystemDarkModeOnFirstLaunch() {
        assertEquals(ThemeMode.Dark, themeModeForNightMode(isNightMode = true))
        assertEquals(ThemeMode.Light, themeModeForNightMode(isNightMode = false))
    }

    @Test
    fun localeUsesLanguageOnly() {
        val locale = AppLanguage.Russian.resolveLocale()

        assertEquals(Locale("ru", "RU").language, locale.language)
    }

    @Test
    fun projectTreeLayoutStartsWithNestedCards() {
        assertEquals(ProjectTreeStyle.Nested, AppSettings().projectTreeStyle)
    }

    @Test
    fun projectTreeImagesStartSmall() {
        assertEquals(ProjectTreePreview.Small, AppSettings().projectTreePreview)
    }

    @Test
    fun hintsAreShownByDefault() {
        assertTrue(AppSettings().showHints)
    }

    @Test
    fun magnifierIsShownByDefault() {
        assertTrue(AppSettings().showMagnifier)
    }
}
