package com.example.fitapp.feature.photoscan

import com.example.fitapp.fakes.scanItem
import com.example.fitapp.feature.photoscan.domain.ImageMath
import com.example.fitapp.feature.photoscan.domain.ReviewItem
import com.example.fitapp.feature.photoscan.domain.ReviewMath
import com.example.fitapp.feature.photoscan.domain.ScanItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewMathTest {
    @Test
    fun `macros scale from per 100 grams to the confirmed portion`() {
        val item = scanItem(kcal = 130.0, protein = 2.7, carbs = 28.0, fat = 0.3)
        val macros = item.macrosFor(250.0)
        assertEquals(325.0, macros.calories, 0.0001)
        assertEquals(6.75, macros.proteinG, 0.0001)
        assertEquals(70.0, macros.carbsG, 0.0001)
        assertEquals(0.75, macros.fatG, 0.0001)
    }

    @Test
    fun `confidence below half is flagged and exactly half is not`() {
        assertTrue(scanItem(confidence = 0.49).lowConfidence)
        assertFalse(scanItem(confidence = ScanItem.LOW_CONFIDENCE).lowConfidence)
    }

    @Test
    fun `a review row starts at the estimated portion without a pointless decimal`() {
        assertEquals("200", ReviewItem(scanItem(grams = 200.0)).gramsText)
        assertEquals("150.5", ReviewItem(scanItem(grams = 150.5)).gramsText)
    }

    @Test
    fun `a row is loggable only when ticked and the portion is a valid number`() {
        val row = ReviewItem(scanItem())
        assertTrue(row.loggable)
        assertFalse(row.copy(included = false).loggable)
        assertFalse(row.copy(gramsText = "").loggable)
        assertFalse(row.copy(gramsText = "0").loggable)
        assertFalse(row.copy(gramsText = "10001").loggable)
        assertTrue(row.copy(gramsText = "12,5").loggable)
        assertNull(row.copy(gramsText = "abc").macros)
    }

    @Test
    fun `the total only counts rows that will be logged`() {
        val rows =
            listOf(
                ReviewItem(scanItem(grams = 100.0, kcal = 200.0)),
                ReviewItem(scanItem(grams = 100.0, kcal = 300.0), included = false),
                ReviewItem(scanItem(grams = 50.0, kcal = 100.0), gramsText = "abc"),
                ReviewItem(scanItem(grams = 200.0, kcal = 50.0)),
            )
        assertEquals(300.0, ReviewMath.total(rows).calories, 0.0001)
        assertEquals(2, ReviewMath.loggable(rows).size)
    }

    @Test
    fun `stepping moves by the delta, starts from the estimate when the text is unreadable and stays in range`() {
        assertEquals("210", ReviewMath.stepGrams("200", 10, 999.0))
        assertEquals("190", ReviewMath.stepGrams("200", -10, 999.0))
        assertEquals("1", ReviewMath.stepGrams("5", -10, 999.0))
        assertEquals("10000", ReviewMath.stepGrams("9995", 10, 999.0))
        assertEquals("160", ReviewMath.stepGrams("", -10, 170.0))
        assertEquals("160.5", ReviewMath.stepGrams("150,5", 10, 170.0))
    }
}

class ImageMathTest {
    @Test
    fun `a big photo shrinks so the longest edge is 1024 and keeps its shape`() {
        assertEquals(1024 to 768, ImageMath.targetSize(4000, 3000))
        assertEquals(768 to 1024, ImageMath.targetSize(3000, 4000))
        assertEquals(1024 to 1024, ImageMath.targetSize(2000, 2000))
    }

    @Test
    fun `a small photo is never enlarged`() {
        assertEquals(640 to 480, ImageMath.targetSize(640, 480))
        assertEquals(1024 to 500, ImageMath.targetSize(1024, 500))
    }

    @Test
    fun `an extreme panorama never collapses to zero`() {
        val (w, h) = ImageMath.targetSize(20000, 10)
        assertEquals(1024, w)
        assertTrue(h >= 1)
    }

    @Test
    fun `the decoder sample size is a power of two that keeps at least the target size`() {
        assertEquals(1, ImageMath.sampleSize(1000, 800))
        assertEquals(1, ImageMath.sampleSize(2000, 1500))
        assertEquals(2, ImageMath.sampleSize(4032, 3024))
        assertEquals(4, ImageMath.sampleSize(8000, 6000))
        assertTrue(8000 / ImageMath.sampleSize(8000, 6000) >= ImageMath.MAX_EDGE)
    }

    @Test
    fun `every EXIF orientation maps to the right turn`() {
        assertTrue(ImageMath.exifTransform(1).isIdentity)
        assertTrue(ImageMath.exifTransform(0).isIdentity)
        assertTrue(ImageMath.exifTransform(99).isIdentity)
        assertEquals(ImageMath.ExifTransform(0, true), ImageMath.exifTransform(2))
        assertEquals(ImageMath.ExifTransform(180, false), ImageMath.exifTransform(3))
        assertEquals(ImageMath.ExifTransform(180, true), ImageMath.exifTransform(4))
        assertEquals(ImageMath.ExifTransform(90, true), ImageMath.exifTransform(5))
        assertEquals(ImageMath.ExifTransform(90, false), ImageMath.exifTransform(6))
        assertEquals(ImageMath.ExifTransform(270, true), ImageMath.exifTransform(7))
        assertEquals(ImageMath.ExifTransform(270, false), ImageMath.exifTransform(8))
    }

    @Test
    fun `only quarter turns swap width and height`() {
        assertTrue(ImageMath.swapsAxes(ImageMath.exifTransform(6)))
        assertTrue(ImageMath.swapsAxes(ImageMath.exifTransform(8)))
        assertFalse(ImageMath.swapsAxes(ImageMath.exifTransform(3)))
        assertFalse(ImageMath.swapsAxes(ImageMath.exifTransform(1)))
    }
}
