package com.example.fitapp.feature.photoscan

import com.example.fitapp.core.network.PhotoScanApi
import com.example.fitapp.core.network.dto.PhotoScanRequestDto
import com.example.fitapp.core.network.dto.PhotoScanResponseDto
import com.example.fitapp.core.network.dto.ScanItemDto
import com.example.fitapp.fakes.FakeTokenStore
import com.example.fitapp.feature.photoscan.data.PhotoScanRepositoryImpl
import com.example.fitapp.feature.photoscan.domain.ScanError
import com.example.fitapp.feature.photoscan.domain.ScanImage
import com.example.fitapp.feature.photoscan.domain.ScanOutcome
import com.example.fitapp.feature.photoscan.domain.ScanSource
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64

private class ScriptedScanApi : PhotoScanApi {
    var error: Exception? = null
    var response =
        PhotoScanResponseDto(
            isFood = true,
            items =
                listOf(
                    ScanItemDto("white rice", 200.0, 0.9, 130.0, 2.7, 28.0, 0.3, "USDA_MATCH", "Rice, white, cooked", "168878", "USDA:168878"),
                    ScanItemDto("home curry", 150.0, 0.4, 150.0, 5.0, 8.0, 10.0, "AI_ESTIMATE"),
                ),
            notes = "Portions are rough",
            scansLeftToday = 2,
            plan = "FREE",
        )
    var sent: PhotoScanRequestDto? = null

    override suspend fun scan(request: PhotoScanRequestDto): PhotoScanResponseDto {
        sent = request
        error?.let { throw it }
        return response
    }
}

private fun httpError(
    code: Int,
    body: String = "{}",
) = HttpException(retrofit2.Response.error<Any>(code, body.toResponseBody(null)))

class PhotoScanRepositoryTest {
    private val api = ScriptedScanApi()
    private val tokens = FakeTokenStore(loggedIn = true)
    private val repo = PhotoScanRepositoryImpl(api, tokens)
    private val image = ScanImage(byteArrayOf(1, 2, 3, 4, 5))

    private fun failure(outcome: ScanOutcome) = outcome as ScanOutcome.Failure

    @Test
    fun `the photo goes up as base64 with its type and a trimmed hint`() =
        runTest {
            repo.scan(image, "  dal and rice  ")

            val sent = api.sent!!
            assertArrayEquals(image.jpeg, Base64.getDecoder().decode(sent.imageBase64))
            assertEquals("image/jpeg", sent.mediaType)
            assertEquals("dal and rice", sent.mealHint)
        }

    @Test
    fun `a blank hint is not sent and a long one is cut to 200`() =
        runTest {
            repo.scan(image, "   ")
            assertNull(api.sent!!.mealHint)

            repo.scan(image, "x".repeat(300))
            assertEquals(200, api.sent!!.mealHint!!.length)
        }

    @Test
    fun `the answer is mapped, with a database match and an estimate told apart`() =
        runTest {
            val result = (repo.scan(image, "") as ScanOutcome.Success).result

            assertEquals(2, result.scansLeftToday)
            assertEquals("Portions are rough", result.notes)
            val rice = result.items[0]
            assertEquals(ScanSource.USDA_MATCH, rice.source)
            assertEquals("Rice, white, cooked", rice.matchedName)
            assertEquals("USDA:168878", rice.foodRef)
            assertTrue(!rice.lowConfidence)
            val curry = result.items[1]
            assertEquals(ScanSource.AI_ESTIMATE, curry.source)
            assertNull(curry.foodRef)
            assertTrue(curry.lowConfidence)
        }

    @Test
    fun `a signed out user is told to log in and nothing is sent`() =
        runTest {
            val signedOut = PhotoScanRepositoryImpl(api, FakeTokenStore(loggedIn = false))

            assertEquals(ScanError.SIGNED_OUT, failure(signedOut.scan(image, "")).error)
            assertNull(api.sent)
        }

    @Test
    fun `network problems are told apart`() =
        runTest {
            api.error = IOException("offline")
            assertEquals(ScanError.OFFLINE, failure(repo.scan(image, "")).error)

            api.error = SocketTimeoutException("slow")
            assertEquals(ScanError.TIMEOUT, failure(repo.scan(image, "")).error)

            api.error = IllegalStateException("bug")
            assertEquals(ScanError.UNKNOWN, failure(repo.scan(image, "")).error)
        }

    @Test
    fun `a 429 about being busy is not the same as the daily limit`() =
        runTest {
            api.error = httpError(429, """{"detail":"The AI service is busy, try again in a minute"}""")
            assertEquals(ScanError.BUSY, failure(repo.scan(image, "")).error)

            api.error = httpError(429, """{"detail":"You have used today's photo scans"}""")
            assertEquals(ScanError.LIMIT_REACHED, failure(repo.scan(image, "")).error)

            api.error = httpError(429)
            assertEquals(ScanError.LIMIT_REACHED, failure(repo.scan(image, "")).error)
        }

    @Test
    fun `other server answers map to friendly errors`() =
        runTest {
            api.error = httpError(503)
            val unavailable = failure(repo.scan(image, ""))
            assertEquals(ScanError.UNAVAILABLE, unavailable.error)
            assertTrue(unavailable.message.contains("search"))

            api.error = httpError(401)
            assertEquals(ScanError.SIGNED_OUT, failure(repo.scan(image, "")).error)

            api.error = httpError(400)
            assertEquals(ScanError.INVALID_IMAGE, failure(repo.scan(image, "")).error)

            api.error = httpError(413)
            assertEquals(ScanError.INVALID_IMAGE, failure(repo.scan(image, "")).error)

            api.error = httpError(500)
            assertEquals(ScanError.UNKNOWN, failure(repo.scan(image, "")).error)
        }

    @Test
    fun `values from the server are clamped and a match without a name counts as an estimate`() =
        runTest {
            api.response =
                PhotoScanResponseDto(
                    items =
                        listOf(
                            ScanItemDto("  Odd  ", 99999.0, 7.0, -5.0, -1.0, -1.0, -1.0, "USDA_MATCH", matchedName = null),
                            ScanItemDto("   ", 100.0, 0.5, 10.0, 1.0, 1.0, 1.0, "AI_ESTIMATE"),
                        ),
                    scansLeftToday = -3,
                    plan = "PREMIUM",
                )

            val result = (repo.scan(image, "") as ScanOutcome.Success).result

            assertEquals(1, result.items.size)
            val item = result.items[0]
            assertEquals("Odd", item.name)
            assertEquals(2000.0, item.estimatedGrams, 0.0)
            assertEquals(1.0, item.confidence, 0.0)
            assertEquals(0.0, item.kcalPer100g, 0.0)
            assertEquals(ScanSource.AI_ESTIMATE, item.source)
            assertEquals(0, result.scansLeftToday)
            assertTrue(result.premium)
        }
}
