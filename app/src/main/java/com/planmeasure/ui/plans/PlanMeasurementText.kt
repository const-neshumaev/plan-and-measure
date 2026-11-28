package com.planmeasure.ui.plans

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.planmeasure.ui.components.areaLabelRes
import com.planmeasure.ui.components.labelRes
import com.planmeasure.units.Area
import com.planmeasure.units.Length
import com.planmeasure.units.LengthUnit
import java.math.BigDecimal

/** Prefix of a value that disagrees with the entered dimensions; the same symbol as on the canvas. */
internal const val MISMATCH_MARK = "≈ "

/** Length in the plan's unit, formatted the same way as dimension labels. */
@Composable
internal fun planLengthText(millimeters: Double, unit: LengthUnit): String {
    if (!millimeters.isFinite()) return ""
    val value = Length.format(Length.canonical(BigDecimal.valueOf(millimeters)), unit)
    return "$value ${stringResource(unit.labelRes())}"
}

/** Area in the square of the plan's unit. */
@Composable
internal fun planAreaText(squareMillimeters: Double, unit: LengthUnit): String {
    val value = Area.format(squareMillimeters, unit)
    return if (value.isEmpty()) "" else "$value ${stringResource(unit.areaLabelRes())}"
}
