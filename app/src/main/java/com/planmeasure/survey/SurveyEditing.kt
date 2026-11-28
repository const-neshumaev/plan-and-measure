package com.planmeasure.survey

/**
 * Pure changes of a survey document.
 *
 * The drawing gesture and the input card call these functions; persistence and id generation stay
 * in the ViewModel. Lines never reshape each other and never change the sheet: the photo is the
 * reference, so there is no solver here (ADR 0021).
 */
object SurveyEditing {

    /**
     * Attaches [photo] to the document.
     *
     * Replacing a photo under an existing sketch would silently move every line off the detail it
     * was drawn on, so it is refused. The editor offers a replacement only while the sketch is
     * empty.
     */
    fun setPhoto(document: SurveyDocument, photo: SurveyPhoto?): SurveyDocument {
        if (photo == null) {
            if (document.photo == null) return document
            return document.copy(photo = null)
        }
        if (document.photo == photo) return document
        if (document.hasSketch) return document
        return document.copy(photo = photo)
    }

    /**
     * Adds one line with endpoints snapped to neighbouring endpoints and clamped to the sheet.
     *
     * Returns the same document when there is no photo or the drag was too short to be a wall.
     */
    fun addSegment(
        document: SurveyDocument,
        from: SurveyPoint,
        to: SurveyPoint,
        snapRadius: Double,
        id: String,
    ): SurveyDocument {
        if (!document.hasPhoto) return document
        val sheet = SurveyGeometry.sheetFor(document.photo)
        val start = SurveyGeometry.clamp(
            SurveyGeometry.snapPoint(document.segments, from, snapRadius),
            sheet,
        )
        val end = SurveyGeometry.clamp(
            SurveyGeometry.snapPoint(document.segments, to, snapRadius),
            sheet,
        )
        if (!SurveyGeometry.canAddSegment(start, end)) return document
        return document.copy(
            segments = document.segments + SurveySegment(id = id, start = start, end = end),
        )
    }

    /**
     * Moves one end of a line to [to], snapping it to a free endpoint and clamping it to the sheet.
     *
     * Endpoints that share the same coordinates are one corner of the sketch, so they move together:
     * dragging a corner of a chain does not tear the contour apart. The endpoints being moved are
     * excluded from snapping, otherwise the corner would stick to its old position.
     */
    fun moveEndpoint(
        document: SurveyDocument,
        segmentId: String,
        endpoint: SurveyEndpoint,
        to: SurveyPoint,
        snapRadius: Double,
    ): SurveyDocument {
        val segment = document.segments.firstOrNull { it.id == segmentId } ?: return document
        val from = if (endpoint == SurveyEndpoint.START) segment.start else segment.end
        val sheet = SurveyGeometry.sheetFor(document.photo)
        val candidates = document.segments.flatMap { candidate ->
            buildList {
                if (candidate.start != from) add(candidate.start)
                if (candidate.end != from) add(candidate.end)
            }
        }
        val snapped = SurveyGeometry.nearestPoint(candidates, to, snapRadius) ?: to
        val target = SurveyGeometry.clamp(snapped, sheet)
        return withEndpointsMoved(document, from, target)
    }

    /**
     * Slides a whole line by [delta], keeping its direction and length.
     *
     * Shared corners move with it and the lines attached to them stretch, so the sketch stays
     * connected. The delta is limited so the line itself remains on the photo sheet.
     */
    fun moveSegment(
        document: SurveyDocument,
        segmentId: String,
        delta: SurveyPoint,
    ): SurveyDocument {
        val segment = document.segments.firstOrNull { it.id == segmentId } ?: return document
        if (delta.x == 0.0 && delta.y == 0.0) return document
        val sheet = SurveyGeometry.sheetFor(document.photo)
        val minX = minOf(segment.start.x, segment.end.x)
        val maxX = maxOf(segment.start.x, segment.end.x)
        val minY = minOf(segment.start.y, segment.end.y)
        val maxY = maxOf(segment.start.y, segment.end.y)
        val dx = delta.x.coerceIn(-minX, sheet.width - maxX)
        val dy = delta.y.coerceIn(-minY, sheet.height - maxY)
        if (dx == 0.0 && dy == 0.0) return document

        var result = withEndpointsMoved(
            document = document,
            from = segment.start,
            to = SurveyPoint(segment.start.x + dx, segment.start.y + dy),
        )
        result = withEndpointsMoved(
            document = result,
            from = segment.end,
            to = SurveyPoint(segment.end.x + dx, segment.end.y + dy),
        )
        return result
    }

    fun removeSegment(document: SurveyDocument, segmentId: String): SurveyDocument {
        if (document.segments.none { it.id == segmentId }) return document
        return document.copy(segments = document.segments.filterNot { it.id == segmentId })
    }

    fun clearSketch(document: SurveyDocument): SurveyDocument {
        if (!document.hasSketch) return document
        return document.copy(segments = emptyList())
    }

    /** Writes [dimension] on [segmentId], or removes it when `null`. */
    fun setDimension(
        document: SurveyDocument,
        segmentId: String,
        dimension: SurveyDimension?,
    ): SurveyDocument {
        if (document.segments.none { it.id == segmentId }) return document
        var changed = false
        val segments = document.segments.map { segment ->
            if (segment.id != segmentId || segment.dimension == dimension) {
                segment
            } else {
                changed = true
                segment.copy(dimension = dimension)
            }
        }
        return if (changed) document.copy(segments = segments) else document
    }

    fun removeDimension(document: SurveyDocument, segmentId: String): SurveyDocument {
        val segment = document.segments.firstOrNull { it.id == segmentId } ?: return document
        if (segment.dimension == null) return document
        return setDimension(document, segmentId, null)
    }

    /** Moves every endpoint at [from] to [to]; coincident endpoints are one corner. */
    private fun withEndpointsMoved(
        document: SurveyDocument,
        from: SurveyPoint,
        to: SurveyPoint,
    ): SurveyDocument {
        if (from == to) return document
        var changed = false
        val segments = document.segments.map { segment ->
            val start = if (segment.start == from) to else segment.start
            val end = if (segment.end == from) to else segment.end
            if (start == segment.start && end == segment.end) {
                segment
            } else {
                changed = true
                segment.copy(start = start, end = end)
            }
        }
        return if (changed) document.copy(segments = segments) else document
    }
}
