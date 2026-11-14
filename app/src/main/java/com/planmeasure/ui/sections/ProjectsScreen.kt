package com.planmeasure.ui.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.planmeasure.R
import com.planmeasure.ui.components.DemoBanner
import com.planmeasure.ui.components.SectionScaffold

@Composable
fun ProjectsScreen(onBack: () -> Unit) {
    SectionScaffold(titleRes = R.string.section_projects, onBack = onBack) {
        DemoBanner()
        ProjectCard(
            titleRes = R.string.projects_demo_apartment,
            metaRes = R.string.projects_demo_apartment_meta,
        )
        ProjectCard(
            titleRes = R.string.projects_demo_office,
            metaRes = R.string.projects_demo_office_meta,
        )
        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.projects_new))
        }
    }
}

@Composable
private fun ProjectCard(@StringRes titleRes: Int, @StringRes metaRes: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_section_projects),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(metaRes),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
