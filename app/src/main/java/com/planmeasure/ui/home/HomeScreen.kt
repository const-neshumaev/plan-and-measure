package com.planmeasure.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.planmeasure.BuildConfig
import com.planmeasure.LabViewModel
import com.planmeasure.R
import com.planmeasure.ble.GattState
import com.planmeasure.ui.HomeSection

@Composable
fun HomeScreen(
    viewModel: LabViewModel,
    onOpenSection: (HomeSection) -> Unit,
    onOpenConnect: () -> Unit,
    onOpenLab: () -> Unit,
) {
    val gattState by viewModel.gattState.collectAsState()
    val reconnecting by viewModel.reconnecting.collectAsState()
    val lastDevice by viewModel.lastDevice.collectAsState()

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HomeHeader() }
            item {
                ConnectionCard(
                    state = gattState,
                    reconnecting = reconnecting,
                    lastDeviceName = lastDevice?.name,
                    onClick = onOpenConnect,
                )
            }
            item { SectionGrid(onOpenSection) }
            item { LabRow(onOpenLab) }
            item {
                Text(
                    text = stringResource(R.string.home_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_section_measurements),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConnectionCard(
    state: GattState,
    reconnecting: Boolean,
    lastDeviceName: String?,
    onClick: () -> Unit,
) {
    val connected = state.connected
    val container = when {
        connected -> MaterialTheme.colorScheme.primaryContainer
        reconnecting -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        connected -> MaterialTheme.colorScheme.onPrimaryContainer
        reconnecting -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val title = stringResource(
        when {
            connected -> R.string.home_status_connected
            reconnecting -> R.string.home_status_reconnecting
            else -> R.string.home_status_disconnected
        },
    )
    val details = when {
        connected -> {
            val servicesText = pluralStringResource(
                R.plurals.services_count,
                state.services.size,
                state.services.size,
            )
            stringResource(
                R.string.home_status_connected_details,
                state.deviceName ?: stringResource(R.string.device_unknown),
                state.mtu,
                servicesText,
            )
        }

        reconnecting -> stringResource(
            R.string.home_status_reconnecting_details,
            lastDeviceName ?: stringResource(R.string.device_unknown),
        )

        else -> stringResource(R.string.home_status_disconnected_details)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(content.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bluetooth),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = details, style = MaterialTheme.typography.bodySmall)
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = content.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private data class SectionTileData(
    val section: HomeSection,
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val container: Color,
    val content: Color,
)

@Composable
private fun SectionGrid(onOpenSection: (HomeSection) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tiles = listOf(
        SectionTileData(
            HomeSection.Measurements,
            R.drawable.ic_section_measurements,
            R.string.section_measurements,
            R.string.section_measurements_details,
            colors.primaryContainer,
            colors.onPrimaryContainer,
        ),
        SectionTileData(
            HomeSection.Projects,
            R.drawable.ic_section_projects,
            R.string.section_projects,
            R.string.section_projects_details,
            colors.secondaryContainer,
            colors.onSecondaryContainer,
        ),
        SectionTileData(
            HomeSection.Capture,
            R.drawable.ic_section_capture,
            R.string.section_capture,
            R.string.section_capture_details,
            colors.tertiaryContainer,
            colors.onTertiaryContainer,
        ),
        SectionTileData(
            HomeSection.Settings,
            R.drawable.ic_section_settings,
            R.string.section_settings,
            R.string.section_settings_details,
            colors.surfaceVariant,
            colors.onSurfaceVariant,
        ),
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowTiles.forEach { tile ->
                    SectionTile(
                        tile = tile,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenSection(tile.section) },
                    )
                }
                if (rowTiles.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SectionTile(
    tile: SectionTileData,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 132.dp),
        shape = MaterialTheme.shapes.large,
        color = tile.container,
        contentColor = tile.content,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(tile.icon),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(tile.title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(tile.subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = tile.content.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun LabRow(onClick: () -> Unit) {
    val content = MaterialTheme.colorScheme.onTertiaryContainer
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lab_flask),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_open_lab),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.home_open_lab_details),
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f),
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = content.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
