package com.planmeasure.ui.lab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.planmeasure.LabScreen
import com.planmeasure.LabViewModel
import com.planmeasure.R

/**
 * The BLE lab as a separate top-level flow. Scan/device navigation stays in [LabViewModel];
 * this composable only adds the app bar, snackbar host and the exit path back to the home menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabFlow(viewModel: LabViewModel, onExit: () -> Unit) {
    val screen by viewModel.screen.collectAsState()
    val gattState by viewModel.gattState.collectAsState()
    val lastDevice by viewModel.lastDevice.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbar.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    fun goBack() {
        when (screen) {
            LabScreen.Scan -> {
                viewModel.stopScan()
                onExit()
            }

            LabScreen.Device -> viewModel.disconnect()
        }
    }

    BackHandler { goBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    when (screen) {
                        LabScreen.Scan -> Column {
                            Text(
                                text = stringResource(R.string.lab_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stringResource(R.string.lab_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        LabScreen.Device -> Column {
                            Text(
                                text = gattState.deviceName
                                    ?: lastDevice?.name
                                    ?: stringResource(R.string.device_unknown),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = gattState.deviceAddress
                                    ?: lastDevice?.address.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { goBack() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (screen) {
                LabScreen.Scan -> ScanScreen(viewModel)
                LabScreen.Device -> DeviceScreen(viewModel)
            }
        }
    }
}
