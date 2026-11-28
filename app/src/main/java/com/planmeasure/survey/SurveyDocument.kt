package com.planmeasure.survey

import com.planmeasure.measurements.MeasurementSource
import com.planmeasure.units.LengthUnit
import kotlinx.serialization.Serializable

/** Current version of the on-disk survey document. Bump when the shape changes. */
const val SURVEY_SCHEMA_VERSION = 1

/** Where the photo of a survey came from; informational only. */
@Serializable
enum class SurveyPhotoSource {
    CAMERA,
    GALLERY,
}

/**
 * The photo a survey is drawn on.
 *
 * Only the stored pixel size is kept: the photo file itself has a name derived from the document id
 * (`<id>.jpg`) and lives next to the document, so the file cannot be moved to an absolute path that
 * breaks on another device. The photo is stored normalized: EXIF rotation is applied on import and
 * the long side is reduced, see `SurveyPhotoStore` and ADR 0022.
 *
 * [importedAt] is when this image was added. Two replacements can have identical dimensions and
 * source, so the screen needs a value that tells them apart to reload the file.
 */
@Serializable
data class SurveyPhoto(
    val width: Int,
    val height: Int,
    val source: SurveyPhotoSource = SurveyPhotoSource.GALLERY,
    val importedAt: Long = 0L,
)

/** A point of the sketch in the photo sheet, see `SurveyGeometry`. */
@Serializable
data class SurveyPoint(val x: Double, val y: Double)

/** Which end of a line an edit gesture grabbed. */
enum class SurveyEndpoint { START, END }

/**
 * A distance the user attached to a sketched line, with an optional note.
 *
 * The value is canonical millimeters (see `com.planmeasure.units.Length`); the unit of the survey
 * only decides how it is shown. Unlike a plan dimension it never reshapes the sketch and never
 * produces an area: a photo carries perspective distortion, so it cannot give a real scale
 * (ADR 0021).
 */
@Serializable
data class SurveyDimension(
    val valueMm: String,
    val source: MeasurementSource,
    val updatedAt: Long,
    val comment: String = "",
)

/**
 * One straight line drawn over the photo.
 *
 * A line is a separate entity with its own id because a dimension label is attached to it; the
 * coordinates alone cannot identify the line the user tapped. The endpoints may be snapped to the
 * endpoints of neighbouring lines so that a chain drawn in several drags stays connected, but they
 * are never snapped to a grid.
 */
@Serializable
data class SurveySegment(
    val id: String,
    val start: SurveyPoint,
    val end: SurveyPoint,
    val dimension: SurveyDimension? = null,
)

/**
 * One named photo sketch, stored as `surveys/<id>.json` with its photo in `surveys/photos/`.
 *
 * A survey is an annotated photo, not a scaled plan: it has no grid, no scale and no areas, and its
 * dimensions are labels that follow the drawing instead of reshaping it (ADR 0021). One survey has
 * exactly one photo; several photos of a room are several surveys in one project folder.
 */
@Serializable
data class SurveyDocument(
    val schemaVersion: Int = SURVEY_SCHEMA_VERSION,
    val id: String,
    val name: String,
    /** Unit of every dimension of this survey; the sketch itself stays in photo sheet units. */
    val unit: LengthUnit = LengthUnit.METERS,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val photo: SurveyPhoto? = null,
    val segments: List<SurveySegment> = emptyList(),
) {
    val hasPhoto: Boolean
        get() = photo != null

    val hasSketch: Boolean
        get() = segments.isNotEmpty()

    val segmentCount: Int
        get() = segments.size
}
