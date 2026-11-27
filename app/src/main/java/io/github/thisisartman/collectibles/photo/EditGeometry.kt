package io.github.thisisartman.collectibles.photo

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** An axis-aligned rectangle in picture pixels (or, for [PhotoEdit.crop], in shares of the turned picture, 0..1). */
data class EditRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val cx get() = (left + right) / 2
    val cy get() = (top + bottom) / 2

    fun scaled(sx: Float, sy: Float) = EditRect(left * sx, top * sy, right * sx, bottom * sy)

    companion object {
        fun of(w: Float, h: Float) = EditRect(0f, 0f, w, h)
    }
}

/**
 * What the photo edit screen (#208) asks for: [quarterTurns] clockwise turns, then straightened by [degrees]
 * (plus is clockwise), then cut to [crop] (shares of the turned picture), as a circle if [circle]. [fromOriginal]:
 * cut from the kept original, else from the photo as it is now.
 */
data class PhotoEdit(val quarterTurns: Int, val degrees: Float, val crop: EditRect, val circle: Boolean, val fromOriginal: Boolean)

/**
 * Which picture an edit starts from, and when the edit screen says the full photo is gone (#208; owner's device
 * test, 2026-11-28: a photo never cut had no original, so its first crop was for good). Pure, for tests.
 */
object EditOriginal {
    /** Schema v7, which keeps the original of every photo the app cuts, came in on this day (2026-11-28, JST). */
    const val ORIGINALS_SINCE = 1_795_791_600_000L

    /** The kinds the app may have cut on the way in before v7: a card or stamp, a cover's circle, an Ekitag. */
    private val CUT_KINDS = setOf(
        io.github.thisisartman.collectibles.data.PhotoKind.STAMP,
        io.github.thisisartman.collectibles.data.PhotoKind.MANHOLE,
        io.github.thisisartman.collectibles.data.PhotoKind.EKITAG,
    )

    /** What an edit cuts from ([source]), and the original to store before the edit ([storeOriginal]; null: none). */
    data class Plan(val source: String, val storeOriginal: String?)

    /**
     * A kept original that still exists is the source. Without one, the photo as it is now is stored as its
     * original first, so a later Reset or a wider crop always has it; [inApp] says the photo is the app's own copy
     * (a picture still only in the gallery is not taken as an original).
     */
    fun plan(contentUri: String, originalUri: String?, originalExists: Boolean, inApp: Boolean): Plan = when {
        originalUri != null && originalExists -> Plan(originalUri, null)
        inApp -> Plan(contentUri, contentUri)
        else -> Plan(contentUri, null)
    }

    /**
     * Whether to say the full photo isn't kept: only for a photo with no original, of a kind the app cut, added
     * before originals were kept. Any other photo is itself the full photo, or has its original.
     */
    fun fullPhotoLost(hasOriginal: Boolean, role: String, createdAt: Long): Boolean =
        !hasOriginal && role in CUT_KINDS && createdAt < ORIGINALS_SINCE
}

/** Which part of the crop box a finger holds. */
enum class Handle { TOP_LEFT, TOP, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT, LEFT, MOVE }

/**
 * The sums behind the photo edit screen (#208), pure for tests. The picture is turned by quarter turns into a
 * frame of [turnedSize], then straightened about its centre inside that frame; the crop box lives in the frame and
 * is kept inside the straightened picture, so a saved photo never has empty corners.
 */
object EditGeometry {
    const val MAX_DEGREES = 15f

    /**
     * [lo]..[hi] like coerceIn, but a range a hair the wrong way round (float rounding, a box already at its
     * smallest) gives [lo] instead of throwing: a drag must never crash the screen.
     */
    private fun Float.within(lo: Float, hi: Float): Float = if (hi < lo) lo else coerceIn(lo, hi)

    /** The frame's size after [quarterTurns] clockwise turns of a [w] by [h] picture. */
    fun turnedSize(w: Float, h: Float, quarterTurns: Int): Pair<Float, Float> = if (Math.floorMod(quarterTurns, 2) == 1) h to w else w to h

    /**
     * Where the picture's pixel lands in the frame: turned by [quarterTurns] and [degrees] about the picture's centre,
     * which lands on the frame's centre. Six values a, b, c, d, e, f: x' = a x + b y + c, y' = d x + e y + f.
     */
    fun affine(w: Float, h: Float, quarterTurns: Int, degrees: Float): FloatArray {
        val (fw, fh) = turnedSize(w, h, quarterTurns)
        val t = Math.toRadians(Math.floorMod(quarterTurns, 4) * 90.0 + degrees)
        val c = cos(t).toFloat(); val s = sin(t).toFloat()
        // Exact zeros at quarter turns, so whole pixels stay whole
        val cc = if (abs(c) < 1e-6f) 0f else c; val ss = if (abs(s) < 1e-6f) 0f else s
        return floatArrayOf(cc, -ss, fw / 2 - cc * w / 2 + ss * h / 2, ss, cc, fh / 2 - ss * w / 2 - cc * h / 2)
    }

    fun map(m: FloatArray, x: Float, y: Float): Pair<Float, Float> = (m[0] * x + m[1] * y + m[2]) to (m[3] * x + m[4] * y + m[5])

    /**
     * The biggest box, centred and of the frame's shape, that sits wholly inside a [w] by [h] frame's picture
     * straightened by [degrees]: the most a straightened photo can keep.
     */
    fun allowed(w: Float, h: Float, degrees: Float): EditRect {
        val a = Math.toRadians(abs(degrees.toDouble()))
        val c = cos(a).toFloat(); val s = sin(a).toFloat()
        val k = min(1f, min(w / (w * c + h * s), h / (w * s + h * c)))
        val bw = w * k; val bh = h * k
        return EditRect((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2)
    }

    /** The crop box after a clockwise quarter turn of a [w] by [h] frame (the new frame is [h] by [w]). */
    fun turnRect(r: EditRect, w: Float, h: Float): EditRect = EditRect(h - r.bottom, r.left, h - r.top, r.right)

    /** [r] moved, then cut down where needed, to lie inside [bounds], and at least [minSide] each way. */
    fun clamp(r: EditRect, bounds: EditRect, minSide: Float): EditRect {
        val m = min(minSide, min(bounds.width, bounds.height))
        val w = r.width.within(m, bounds.width)
        val h = r.height.within(m, bounds.height)
        val l = r.left.within(bounds.left, bounds.right - w)
        val t = r.top.within(bounds.top, bounds.bottom - h)
        return EditRect(l, t, l + w, t + h)
    }

    /** The biggest square centred in [r], for a circle. */
    fun square(r: EditRect): EditRect {
        val side = min(r.width, r.height)
        return EditRect(r.cx - side / 2, r.cy - side / 2, r.cx + side / 2, r.cy + side / 2)
    }

    /**
     * The crop box after a finger on [handle] moved by [dx], [dy] (frame pixels): corners and edges move their
     * sides, the inside moves the whole box. Stays inside [bounds] and at least [minSide]; a [square] box stays square.
     */
    fun drag(r: EditRect, handle: Handle, dx: Float, dy: Float, bounds: EditRect, minSide: Float, square: Boolean = false): EditRect {
        if (handle == Handle.MOVE) {
            val l = (r.left + dx).within(bounds.left, bounds.right - r.width)
            val t = (r.top + dy).within(bounds.top, bounds.bottom - r.height)
            return EditRect(l, t, l + r.width, t + r.height)
        }
        val m = min(minSide, min(bounds.width, bounds.height))
        val left = handle == Handle.TOP_LEFT || handle == Handle.LEFT || handle == Handle.BOTTOM_LEFT
        val right = handle == Handle.TOP_RIGHT || handle == Handle.RIGHT || handle == Handle.BOTTOM_RIGHT
        val top = handle == Handle.TOP_LEFT || handle == Handle.TOP || handle == Handle.TOP_RIGHT
        val bottom = handle == Handle.BOTTOM_LEFT || handle == Handle.BOTTOM || handle == Handle.BOTTOM_RIGHT
        if (square) {
            // One size for both sides, from the larger pull; the opposite corner (or edge's middle) stays put
            val grow = when {
                (left || right) && (top || bottom) -> {
                    val gx = if (left) -dx else dx; val gy = if (top) -dy else dy
                    if (abs(gx) > abs(gy)) gx else gy
                }
                left -> -dx
                right -> dx
                top -> -dy
                else -> dy
            }
            val ax = when { left -> r.right; right -> r.left; else -> r.cx }
            val ay = when { top -> r.bottom; bottom -> r.top; else -> r.cy }
            // The most room there is from the anchor towards the pull
            fun room(anchor: Float, lo: Float, hi: Float, toLow: Boolean?, centred: Boolean) = when {
                centred -> 2 * min(anchor - lo, hi - anchor)
                toLow == true -> anchor - lo
                else -> hi - anchor
            }
            val maxW = room(ax, bounds.left, bounds.right, left, !left && !right)
            val maxH = room(ay, bounds.top, bounds.bottom, top, !top && !bottom)
            val side = (r.width + grow).within(m, max(m, min(maxW, maxH)))
            val l = when { left -> ax - side; right -> ax; else -> ax - side / 2 }
            val t = when { top -> ay - side; bottom -> ay; else -> ay - side / 2 }
            return EditRect(l, t, l + side, t + side)
        }
        var l = r.left; var t = r.top; var rr = r.right; var b = r.bottom
        if (left) l = (l + dx).within(bounds.left, rr - m)
        if (right) rr = (rr + dx).within(l + m, bounds.right)
        if (top) t = (t + dy).within(bounds.top, b - m)
        if (bottom) b = (b + dy).within(t + m, bounds.bottom)
        return EditRect(l, t, rr, b)
    }

    /** The part of [r] a touch at [x], [y] holds, with [slop] of reach around corners and edges; null outside. */
    fun handleAt(r: EditRect, x: Float, y: Float, slop: Float): Handle? {
        val nearL = abs(x - r.left) <= slop; val nearR = abs(x - r.right) <= slop
        val nearT = abs(y - r.top) <= slop; val nearB = abs(y - r.bottom) <= slop
        val inX = x >= r.left - slop && x <= r.right + slop
        val inY = y >= r.top - slop && y <= r.bottom + slop
        if (!inX || !inY) return null
        return when {
            nearT && nearL -> Handle.TOP_LEFT
            nearT && nearR -> Handle.TOP_RIGHT
            nearB && nearL -> Handle.BOTTOM_LEFT
            nearB && nearR -> Handle.BOTTOM_RIGHT
            nearT -> Handle.TOP
            nearB -> Handle.BOTTOM
            nearL -> Handle.LEFT
            nearR -> Handle.RIGHT
            else -> Handle.MOVE
        }
    }

    /**
     * How a [w] by [h] frame sits in a [boxW] by [boxH] view, shown whole and centred: the scale and the offset of
     * its top left. A frame point (x, y) is at (x * scale + ox, y * scale + oy) on screen.
     */
    fun fit(w: Float, h: Float, boxW: Float, boxH: Float): Triple<Float, Float, Float> {
        val s = min(boxW / w, boxH / h)
        return Triple(s, (boxW - w * s) / 2, (boxH - h * s) / 2)
    }

    /** A box in the frame as shares of it (0..1), for [PhotoEdit.crop], and back. */
    fun toShares(r: EditRect, w: Float, h: Float) = r.scaled(1 / w, 1 / h)
    fun fromShares(r: EditRect, w: Float, h: Float) = r.scaled(w, h)

    /**
     * A box cut from a picture straightened by [degrees] keeps inside it: [r] clamped to [allowed]. Used after a
     * straighten so the box shrinks only as much as needed.
     */
    fun fitAfterStraighten(r: EditRect, w: Float, h: Float, degrees: Float, minSide: Float): EditRect {
        val a = allowed(w, h, degrees)
        // Cut to the allowed box, keeping the part of [r] inside it
        val l = max(r.left, a.left); val t = max(r.top, a.top)
        val rr = min(r.right, a.right); val b = min(r.bottom, a.bottom)
        return clamp(if (rr > l && b > t) EditRect(l, t, rr, b) else a, a, minSide)
    }
}
