package com.luyuan.silkzoom

import kotlin.math.*

/** Local pyramidal optical flow with independent points and forward/backward validation. */
internal class CompositionTracker(
    private val width: Int, private val height: Int, initial: IntArray,
    subjectX: Float = 0.5f, subjectY: Float = 0.5f,
    private val wholeFrame: Boolean = false,
    private val featureRadiusFraction: Float = .12f,
) {
    data class Match(val x: Float, val y: Float)
    private data class Point(val x: Float, val y: Float)
    private data class Level(val width: Int, val height: Int, val pixels: FloatArray) {
        fun sample(x: Float, y: Float): Float {
            val ix = floor(x).toInt().coerceIn(0, width - 2)
            val iy = floor(y).toInt().coerceIn(0, height - 2)
            val fx = (x - ix).coerceIn(0f, 1f)
            val fy = (y - iy).coerceIn(0f, 1f)
            val top = pixels[iy * width + ix] * (1 - fx) + pixels[iy * width + ix + 1] * fx
            val bottom = pixels[(iy + 1) * width + ix] * (1 - fx) + pixels[(iy + 1) * width + ix + 1] * fx
            return top * (1 - fy) + bottom * fy
        }
        fun inside(p: Point, margin: Int = 5) = p.x >= margin && p.y >= margin && p.x < width - margin - 1 && p.y < height - margin - 1
    }
    private var mappingA = 1f; private var mappingB = 0f
    private var mappingX = 0f; private var mappingY = 0f
    fun mapOriginalPoint(x: Float, y: Float): Pair<Float, Float> =
        ((mappingA * x * width - mappingB * y * height + mappingX) / width) to
            ((mappingB * x * width + mappingA * y * height + mappingY) / height)
    private var previous = pyramid(initial)
    private val reference = previous[0]
    private val referenceCenter = Point(subjectX * width, subjectY * height)
    private var center = referenceCenter
    private var points = corners(previous[0], center)
    private var failures = 0
    private var frames = 0

    fun locate(frame: IntArray, cropScale: Float = 1.12f): Match? {
        if (frame.size != width * height || points.size < 4 && failures == 0) return null
        val current = pyramid(frame)
        val tracked = points.mapNotNull { p ->
            val q = flow(previous, current, p) ?: return@mapNotNull null
            val back = flow(current, previous, q) ?: return@mapNotNull null
            if (distance(p, back) > 1.6f || photometricError(previous[0], current[0], p, q) > 32f) null else p to q
        }
        if (tracked.size >= 4) {
            val dx = median(tracked.map { it.second.x - it.first.x })
            val dy = median(tracked.map { it.second.y - it.first.y })
            val inliers = tracked.filter { hypot(it.second.x - it.first.x - dx, it.second.y - it.first.y - dy) < 4f }
            if (inliers.size >= 4 && inliers.size >= tracked.size / 2) {
                val px = inliers.map { it.first.x }.average().toFloat()
                val py = inliers.map { it.first.y }.average().toFloat()
                val qx = inliers.map { it.second.x }.average().toFloat()
                val qy = inliers.map { it.second.y }.average().toFloat()
                var denominator = 0f; var a = 0f; var b = 0f
                for ((p, q) in inliers) {
                    val x = p.x - px; val y = p.y - py
                    denominator += x * x + y * y
                    a += x * (q.x - qx) + y * (q.y - qy)
                    b += x * (q.y - qy) - y * (q.x - qx)
                }
                if (denominator > 1f) {
                    a /= denominator; b /= denominator
                    val scale = hypot(a, b)
                    val next = Point(qx + a * (center.x - px) - b * (center.y - py), qy + b * (center.x - px) + a * (center.y - py))
                    if (scale in 0.8f..1.3f && current[0].inside(next) && distance(center, next) < max(width, height) * 0.3f) {
                        val tx = qx - a * px + b * py
                        val ty = qy - b * px - a * py
                        val nextA = a * mappingA - b * mappingB
                        val nextB = b * mappingA + a * mappingB
                        val nextX = a * mappingX - b * mappingY + tx
                        val nextY = b * mappingX + a * mappingY + ty
                        mappingA = nextA; mappingB = nextB; mappingX = nextX; mappingY = nextY
                        center = next
                        previous = current
                        points = inliers.map { it.second }
                        failures = 0
                        frames++
                        if (points.size < 10 || frames % 20 == 0) {
                            val additions = corners(current[0], center)
                            points = (points + additions.filter { candidate -> points.none { distance(it, candidate) < 4f } }).take(28)
                        }
                        return Match(center.x / width, center.y / height)
                    }
                }
            }
        }
        failures++
        // Reference-only recovery never learns an occluder or hunts across the whole frame.
        if (!wholeFrame && failures <= 4) recover(current[0], cropScale)?.let { recovered ->
            center = recovered
            previous = current
            points = corners(current[0], center)
            if (points.size >= 4) { failures = 0; return Match(center.x / width, center.y / height) }
        }
        return null
    }

    private fun pyramid(frame: IntArray): List<Level> {
        val levels = mutableListOf(Level(width, height, FloatArray(frame.size) { i ->
            val c = frame[i]
            (((c shr 16) and 255) * 77 + ((c shr 8) and 255) * 150 + (c and 255) * 29) / 256f
        }))
        repeat(2) {
            val source = levels.last()
            val w = source.width / 2; val h = source.height / 2
            levels += Level(w, h, FloatArray(w * h) { i ->
                val offset = (i / w * 2) * source.width + i % w * 2
                (source.pixels[offset] + source.pixels[offset + 1] + source.pixels[offset + source.width] + source.pixels[offset + source.width + 1]) / 4f
            })
        }
        return levels
    }

    private fun corners(image: Level, around: Point): List<Point> {
        val radius = max(12, (width * featureRadiusFraction).roundToInt())
        val candidates = mutableListOf<Pair<Float, Point>>()
        val left = if (wholeFrame) 6 else max(6, around.x.toInt() - radius)
        val top = if (wholeFrame) 6 else max(6, around.y.toInt() - radius)
        val right = if (wholeFrame) width - 7 else min(width - 7, around.x.toInt() + radius)
        val bottom = if (wholeFrame) height - 7 else min(height - 7, around.y.toInt() + radius)
        val step = if (wholeFrame) 4 else 2
        for (y in top..bottom step step) {
            for (x in left..right step step) {
                var xx = 0f; var xy = 0f; var yy = 0f
                for (dy in -2..2) for (dx in -2..2) {
                    val index = (y + dy) * width + x + dx
                    val gx = (image.pixels[index + 1] - image.pixels[index - 1]) * 0.5f
                    val gy = (image.pixels[index + width] - image.pixels[index - width]) * 0.5f
                    xx += gx * gx; xy += gx * gy; yy += gy * gy
                }
                val strength = (xx + yy - sqrt((xx - yy) * (xx - yy) + 4 * xy * xy)) * 0.5f
                if (strength > 100f) candidates += strength to Point(x.toFloat(), y.toFloat())
            }
        }
        val result = mutableListOf<Point>()
        for ((_, point) in candidates.sortedByDescending { it.first }) {
            if (result.none { distance(it, point) < 5f }) result += point
            if (result.size == 28) break
        }
        return result
    }

    private fun flow(from: List<Level>, to: List<Level>, point: Point): Point? {
        var displacement = Point(0f, 0f)
        for (level in 2 downTo 0) {
            val divisor = (1 shl level).toFloat()
            val p = Point(point.x / divisor, point.y / divisor)
            val source = from[level]; val destination = to[level]
            if (!source.inside(p)) continue
            if (level < 2) displacement = Point(displacement.x * 2, displacement.y * 2)
            val values = FloatArray(49); val gx = FloatArray(49); val gy = FloatArray(49)
            var xx = 0f; var xy = 0f; var yy = 0f
            for (i in 0 until 49) {
                val x = p.x + i % 7 - 3; val y = p.y + i / 7 - 3
                values[i] = source.sample(x, y)
                gx[i] = (source.sample(x + 1, y) - source.sample(x - 1, y)) * 0.5f
                gy[i] = (source.sample(x, y + 1) - source.sample(x, y - 1)) * 0.5f
                xx += gx[i] * gx[i]; xy += gx[i] * gy[i]; yy += gy[i] * gy[i]
            }
            val determinant = xx * yy - xy * xy
            if (determinant < 100f) continue
            val mean = values.average().toFloat()
            for (iteration in 0 until 12) {
                val q = Point(p.x + displacement.x, p.y + displacement.y)
                if (!destination.inside(q)) return null
                var currentMean = 0f
                for (i in 0 until 49) currentMean += destination.sample(q.x + i % 7 - 3, q.y + i / 7 - 3)
                currentMean /= 49f
                var ex = 0f; var ey = 0f
                for (i in 0 until 49) {
                    val error = destination.sample(q.x + i % 7 - 3, q.y + i / 7 - 3) - currentMean - (values[i] - mean)
                    ex += gx[i] * error; ey += gy[i] * error
                }
                val dx = ((xy * ey - yy * ex) / determinant).coerceIn(-2f, 2f)
                val dy = ((xy * ex - xx * ey) / determinant).coerceIn(-2f, 2f)
                displacement = Point(displacement.x + dx, displacement.y + dy)
                if (dx * dx + dy * dy < 0.001f) break
            }
        }
        return Point(point.x + displacement.x, point.y + displacement.y).takeIf { to[0].inside(it) }
    }

    private fun photometricError(a: Level, b: Level, p: Point, q: Point): Float {
        var offset = 0f
        for (y in -3..3) for (x in -3..3) offset += b.sample(q.x + x, q.y + y) - a.sample(p.x + x, p.y + y)
        offset /= 49f
        var error = 0f
        for (y in -3..3) for (x in -3..3) error += abs(b.sample(q.x + x, q.y + y) - a.sample(p.x + x, p.y + y) - offset)
        return error / 49f
    }

    private fun recover(image: Level, cropScale: Float): Point? {
        val offsets = (-8..8 step 2).flatMap { y -> (-8..8 step 2).map { x -> x to y } }
        val template = FloatArray(offsets.size) { i -> reference.sample(referenceCenter.x + offsets[i].first, referenceCenter.y + offsets[i].second) }
        val mean = template.average().toFloat()
        val variance = template.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat()
        if (variance < template.size * 25) return null
        var best = -1f; var runnerUp = -1f; var found = center
        val candidates = mutableListOf<Pair<Float, Point>>()
        val range = max(24, (width * 0.35f).toInt())
        for (y in ((max(10, center.y.toInt() - range) + 1) / 2 * 2)..min(height - 11, center.y.toInt() + range) step 2) {
            for (x in ((max(10, center.x.toInt() - range) + 1) / 2 * 2)..min(width - 11, center.x.toInt() + range) step 2) {
                var score = -1f
                for (scale in floatArrayOf(1f, cropScale.coerceIn(1f, 8f))) {
                    var sum = 0f; var squared = 0f; var dot = 0f
                    for (i in offsets.indices) {
                        val value = image.sample(x + offsets[i].first * scale, y + offsets[i].second * scale)
                        sum += value; squared += value * value; dot += (template[i] - mean) * value
                    }
                    val norm = squared - sum * sum / offsets.size
                    if (norm > 1f) score = max(score, dot / sqrt(variance * norm))
                }
                val point = Point(x.toFloat(), y.toFloat())
                candidates += score to point
                if (score > best) { best = score; found = point }
            }
        }
        for ((score, point) in candidates) if (distance(point, found) > 12f) runnerUp = max(runnerUp, score)
        return found.takeIf { best >= 0.8f && best - runnerUp >= 0.12f }
    }

    private fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
    private fun median(values: List<Float>): Float = values.sorted()[values.size / 2]
}
