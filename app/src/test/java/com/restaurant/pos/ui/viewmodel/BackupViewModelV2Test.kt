package com.restaurant.pos.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupViewModelV2Test {

    private lateinit var app: Application
    private lateinit var viewModel: BackupViewModelV2

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = BackupViewModelV2(app)
    }

    @Test
    fun testInitialUiStateIsIdle() {
        assertEquals(BackupUiStateV2.Idle, viewModel.uiState.value)
    }

    @Test
    fun testResetStateResetsToIdle() {
        viewModel.resetState()
        assertEquals(BackupUiStateV2.Idle, viewModel.uiState.value)
    }
}
