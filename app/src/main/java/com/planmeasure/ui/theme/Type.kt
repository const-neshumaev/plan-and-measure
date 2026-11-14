package com.planmeasure.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

private val Default = Typography()

/** Material defaults with a heavier display hierarchy: titles read as headings, not body text. */
val PlanMeasureTypography = Typography(
    headlineMedium = Default.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Default.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Default.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)
