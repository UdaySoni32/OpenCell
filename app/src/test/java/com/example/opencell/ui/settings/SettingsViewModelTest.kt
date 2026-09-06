package com.example.opencell.ui.settings

import com.example.opencell.data.local.preferences.SettingsDataStore
import com.example.opencell.data.local.preferences.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class FakeSettingsDataStore : SettingsDataStore {
    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    private val _dynamic = MutableStateFlow(true)

    override val themeMode: Flow<ThemeMode> = _theme
    override val dynamicColorEnabled: Flow<Boolean> = _dynamic

    override suspend fun setThemeMode(mode: ThemeMode) { _theme.value = mode }
    override suspend fun setDynamicColorEnabled(enabled: Boolean) { _dynamic.value = enabled }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dataStore: FakeSettingsDataStore
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dataStore = FakeSettingsDataStore()
        viewModel = SettingsViewModel(dataStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setThemeMode_updatesPreference() = runTest(testDispatcher) {
        viewModel.setThemeMode(ThemeMode.DARK)
        testScheduler.advanceUntilIdle()

        assertEquals(ThemeMode.DARK, dataStore.themeMode.first())
    }

    @Test
    fun setDynamicColor_updatesPreference() = runTest(testDispatcher) {
        viewModel.setDynamicColorEnabled(false)
        testScheduler.advanceUntilIdle()

        assertFalse(dataStore.dynamicColorEnabled.first())
    }
}
