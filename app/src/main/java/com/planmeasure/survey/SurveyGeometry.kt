package com.planmeasure.survey

import kotlin.math.hypot

/**
 * Size of the photo sheet in abstract units.
 *
 * Coordinates of a sketch are stored in this space, not in pixels, so the sketch survives a change
 * of screen size or orientation. The sheet always has the photo's aspect ratio: the photo is drawn
 * to fill it, so a point of the sketch stays on the same detail of the photo (ADR 0021).
 */
data class SurveySheet(val width: Double, val height: Double)

/** Endpoint of [segmentId] under the finger, used by the Edit tool. */
data class SurveyEndpointHit(
    val segmentId: String,
    val endpoint: SurveyEndpoint,
    val point: SurveyPoint,
)

/**
 * Pure geometry of a photo sketch.
 *
 * There is deliberately no grid snapping and no angle magnet here: the photo is the reference, and
 * a line must follow the finger even when perspective has bent the wall. The only snapping is to
 * the endpoints of neighbouring lines, so a chain drawn in several drags stays connected.
 */
object SurveyGeometry {

    /** Width of the sheet in abstract units; the height follows the photo aspect. */
    const val SHEET_WIDTH = 1000.0

    /** Shortest line that can be added, in sheet units: a tap is not a wall. */
    const val MIN_SEGMENT_LENGTH = 3.0

    private const val FALLBACK_WIDTH = 4.0
    private const val FALLBACK_HEIGHT = 3.0

    /** Sheet for [photo]; without a photo a neutral 4:3 sheet is used for the empty editor. */
    fun sheetFor(photo: SurveyPhoto?): SurveySheet {
        val photoWidth = photo?.width?.takeIf { it > 0 }?.toDouble() ?: FALLBACK_WIDTH
        val photoHeight = photo?.height?.takeIf { it > 0 }?.toDouble() ?: FALLBACK_HEIGHT
        return SurveySheet(width = SHEET_WIDTH, height = SHEET_WIDTH * photoHeight / photoWidth)
    }

    fun distance(a: SurveyPoint, b: SurveyPoint): Double = hypot(b.x - a.x, b.y - a.y)

    fun midpoint(segment: SurveySegment): SurveyPoint = SurveyPoint(
        x = (segment.start.x + segment.end.x) / 2.0,
        y = (segment.start.y + segment.end.y) / 2.0,
    )

    /** Distance from [point] to the nearest point of [segment]. */
    fun distanceToSegment(segment: SurveySegment, point: SurveyPoint): Double {
        val dx = segment.end.x - segment.start.x
        val dy = segment.end.y - segment.start.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0.0) return distance(segment.start, point)
        val projection = ((point.x - segment.start.x) * dx + (point.y - segment.start.y) * dy) /
            lengthSquared
        val t = projection.coerceIn(0.0, 1.0)
        return distance(
            SurveyPoint(x = segment.start.x + t * dx, y = segment.start.y + t * dy),
            point,
        )
    }

    /** Line under [point], or `null` when nothing is closer than [tolerance]. */
    fun nearestSegment(
        segments: List<SurveySegment>,
        point: SurveyPoint,
        tolerance: Double,
    ): SurveySegment? = segments
        .asSequence()
        .map { it to distanceToSegment(it, point) }
        .filter { (_, distance) -> distance <= tolerance }
        .minByOrNull { (_, distance) -> distance }
        ?.first

    /** Nearest endpoint of any line within [radius]; the Edit tool grabs that end. */
    fun nearestSegmentEndpoint(
        segments: List<SurveySegment>,
        point: SurveyPoint,
        radius: Double,
    ): SurveyEndpointHit? {
        var best: SurveyEndpointHit? = null
        var bestDistance = radius
        segments.forEach { segment ->
            listOf(
                SurveyEndpoint.START to segment.start,
                SurveyEndpoint.END to segment.end,
            ).forEach { (endpoint, candidate) ->
                val distance = distance(candidate, point)
                if (distance < bestDistance) {
                    best = SurveyEndpointHit(segment.id, endpoint, candidate)
                    bestDistance = distance
                }
            }
        }
        return best
    }

    /** Nearest of [points] within [radius], or `null`. */
    fun nearestPoint(
        points: List<SurveyPoint>,
        point: SurveyPoint,
        radius: Double,
    ): SurveyPoint? {
        var best: SurveyPoint? = null
        var bestDistance = radius
        points.forEach { candidate ->
            val distance = distance(candidate, point)
            if (distance < bestDistance) {
                best = candidate
                bestDistance = distance
            }
        }
        return best
    }

    /**
     * Endpoint of an existing line close enough to grab, or `null`.
     *
     * [excludeSegmentId] lets a dragged preview ignore the line it belongs to when one is added
     * later; the drawing gesture adds a whole new line, so it normally does not need it.
     */
    fun nearestEndpoint(
        segments: List<SurveySegment>,
        point: SurveyPoint,
        radius: Double,
        excludeSegmentId: String? = null,
    ): SurveyPoint? {
        var best: SurveyPoint? = null
        var bestDistance = radius
        segments.forEach { segment ->
            if (segment.id == excludeSegmentId) return@forEach
            listOf(segment.start, segment.end).forEach { endpoint ->
                val candidate = distance(endpoint, point)
                if (candidate <= bestDistance) {
                    best = endpoint
                    bestDistance = candidate
                }
            }
        }
        return best
    }

    /** [point] snapped to a neighbouring endpoint, otherwise unchanged. */
    fun snapPoint(
        segments: List<SurveySegment>,
        point: SurveyPoint,
        radius: Double,
        excludeSegmentId: String? = null,
    ): SurveyPoint = nearestEndpoint(segments, point, radius, excludeSegmentId) ?: point

    /** Keeps a point on the photo sheet; a sketch outside the photo has no reference. */
    fun clamp(point: SurveyPoint, sheet: SurveySheet): SurveyPoint = SurveyPoint(
        x = point.x.coerceIn(0.0, sheet.width),
        y = point.y.coerceIn(0.0, sheet.height),
    )

    /** A line shorter than [MIN_SEGMENT_LENGTH] is treated as an accidental tap. */
    fun canAddSegment(start: SurveyPoint, end: SurveyPoint): Boolean =
        distance(start, end) >= MIN_SEGMENT_LENGTH
}
