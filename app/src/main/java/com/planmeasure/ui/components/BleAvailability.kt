package com.planmeasure.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.planmeasure.R
import com.planmeasure.ble.BlePermissions
import com.planmeasure.ble.BleUtils

private class BlePermissionState(
    val granted: Boolean,
    val request: () -> Unit,
)

/**
 * Renders [content] only when BLE can be used: permissions are granted and Bluetooth is on.
 * Otherwise shows an explanatory card with a single action.
 */
@Composable
fun BleAvailabilityGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val permissions = rememberBlePermissions()
    var bluetoothEnabled by remember { mutableStateOf(BleUtils.isBluetoothEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                bluetoothEnabled = BleUtils.isBluetoothEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        !permissions.granted -> InfoCard(
            title = stringResource(R.string.permissions_title),
            text = stringResource(R.string.permissions_text),
            actionLabel = stringResource(R.string.permissions_action),
            onAction = permissions.request,
        )

        !bluetoothEnabled -> InfoCard(
            title = stringResource(R.string.bluetooth_off_title),
            text = stringResource(R.string.bluetooth_off_text),
            actionLabel = stringResource(R.string.bluetooth_off_action),
            onAction = { openBluetoothSettings(context) },
        )

        else -> content()
    }
}

@Composable
private fun rememberBlePermissions(): BlePermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(BlePermissions.hasAll(context)) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        granted = BlePermissions.hasAll(context)
    }
    val request: () -> Unit = remember(launcher) {
        { launcher.launch(BlePermissions.required()) }
    }
    return BlePermissionState(granted, request)
}

private fun openBluetoothSettings(context: Context) {
    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Some OEM ROMs do not expose this screen.
    }
}
