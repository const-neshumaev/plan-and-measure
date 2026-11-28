package com.planmeasure.ui.survey

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.planmeasure.R
import com.planmeasure.survey.SurveyPhoto
import com.planmeasure.survey.SurveyViewModel

/** State of the photo of a survey on screen. */
sealed interface SurveyImageState {
    data object Loading : SurveyImageState

    data object Missing : SurveyImageState

    data class Ready(val image: ImageBitmap) : SurveyImageState
}

/**
 * Decodes the photo of [surveyId] off the UI thread through the ViewModel.
 *
 * The photo file is the source of truth and may be missing (the file was deleted outside the app,
 * or a previous import failed); the returned state tells the caller whether to show the photo or
 * the empty state. [photo] is a key so importing a new photo reloads the image.
 */
@Composable
fun rememberSurveyImage(
    surveyId: String,
    photo: SurveyPhoto?,
    viewModel: SurveyViewModel,
    maxSidePx: Int = 2048,
): SurveyImageState {
    val state by produceState<SurveyImageState>(
        initialValue = SurveyImageState.Loading,
        surveyId,
        photo,
        maxSidePx,
        viewModel,
    ) {
        value = if (photo == null) {
            SurveyImageState.Missing
        } else {
            val image = viewModel.loadPhoto(surveyId, maxSidePx)?.asImageBitmap()
            if (image == null) SurveyImageState.Missing else SurveyImageState.Ready(image)
        }
    }
    return state
}

/** Small photo preview of a survey in the list. */
@Composable
fun SurveyThumbnail(
    surveyId: String,
    photo: SurveyPhoto?,
    viewModel: SurveyViewModel,
    modifier: Modifier = Modifier,
    maxSidePx: Int = 192,
) {
    val state = rememberSurveyImage(surveyId, photo, viewModel, maxSidePx = maxSidePx)
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is SurveyImageState.Ready -> Image(
                bitmap = state.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            else -> Icon(
                painter = painterResource(R.drawable.ic_section_capture),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
