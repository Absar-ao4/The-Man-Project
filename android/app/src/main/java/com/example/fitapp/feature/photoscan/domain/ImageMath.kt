package com.example.fitapp.feature.photoscan.domain

/** Pure arithmetic for shrinking and turning a camera photo, kept apart from Bitmap so it can be unit tested. */
object ImageMath {
    const val MAX_EDGE = 1024
    const val JPEG_QUALITY = 80

    /** How a photo must be turned to look upright: rotate clockwise by [degrees], then mirror left to right if [mirror]. */
    data class ExifTransform(val degrees: Int, val mirror: Boolean) {
        val isIdentity: Boolean get() = degrees == 0 && !mirror
    }

    /** Maps the EXIF orientation tag (1 to 8) to the turn that undoes it. Anything unknown means "leave as is". */
    fun exifTransform(orientation: Int): ExifTransform =
        when (orientation) {
            2 -> ExifTransform(0, true)
            3 -> ExifTransform(180, false)
            4 -> ExifTransform(180, true)
            5 -> ExifTransform(90, true)
            6 -> ExifTransform(90, false)
            7 -> ExifTransform(270, true)
            8 -> ExifTransform(270, false)
            else -> ExifTransform(0, false)
        }

    /** True when the turn swaps width and height. */
    fun swapsAxes(transform: ExifTransform): Boolean = transform.degrees == 90 || transform.degrees == 270

    /** The size with the longest edge at most [maxEdge], keeping the aspect ratio. Never enlarges. */
    fun targetSize(
        width: Int,
        height: Int,
        maxEdge: Int = MAX_EDGE,
    ): Pair<Int, Int> {
        require(width > 0 && height > 0) { "size must be positive" }
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return width to height
        val scale = maxEdge.toDouble() / longest
        return Math.max(1, Math.round(width * scale).toInt()) to Math.max(1, Math.round(height * scale).toInt())
    }

    /**
     * The power of two to hand to the decoder so it reads a smaller bitmap without dropping below the target:
     * the decoded image stays at least [maxEdge] on its longest side, then is scaled exactly afterwards.
     */
    fun sampleSize(
        width: Int,
        height: Int,
        maxEdge: Int = MAX_EDGE,
    ): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxEdge) {
            longest /= 2
            sample *= 2
        }
        return sample
    }
}
