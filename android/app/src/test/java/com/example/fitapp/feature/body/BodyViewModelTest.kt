package com.example.fitapp.feature.body

import app.cash.turbine.test
import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeBodyRepository
import com.example.fitapp.fakes.FakeDeviceInfo
import com.example.fitapp.fakes.FakeProfileRepository
import com.example.fitapp.fakes.userProfile
import com.example.fitapp.fakes.weight
import com.example.fitapp.feature.body.domain.Measurement
import com.example.fitapp.feature.body.domain.MeasurementType
import com.example.fitapp.feature.body.ui.BodyViewModel
import com.example.fitapp.feature.profile.domain.Units
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class BodyViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 17)
    private val body = FakeBodyRepository()
    private val profiles = FakeProfileRepository().apply { profile.value = userProfile() }

    private fun vm() = BodyViewModel(body, profiles, FakeDeviceInfo(today = today))

    @Test
    fun `a good weight is saved for today and the field clears`() =
        runTest {
            val vm = vm()
            vm.state.test {
                vm.onWeightText("80,5")
                vm.onLogWeight()

                val s = expectMostRecentItem()
                assertEquals(listOf(today to 80.5), body.loggedWeights)
                assertEquals("", s.weightText)
                assertNull(s.weightError)
                assertEquals("Weight saved", s.message)
            }
        }

    @Test
    fun `a bad weight shows a message and saves nothing`() =
        runTest {
            val vm = vm()
            vm.state.test {
                vm.onWeightText("5")
                vm.onLogWeight()

                val s = expectMostRecentItem()
                assertEquals(emptyList<Pair<LocalDate, Double>>(), body.loggedWeights)
                assertEquals("5", s.weightText)
                assertEquals("Weight must be between 20 kg and 500 kg", s.weightError)
            }
        }

    @Test
    fun `typing clears the error`() =
        runTest {
            val vm = vm()
            vm.state.test {
                vm.onWeightText("abc")
                vm.onLogWeight()
                assertEquals("Enter your weight as a number", expectMostRecentItem().weightError)

                vm.onWeightText("80")

                assertNull(expectMostRecentItem().weightError)
            }
        }

    @Test
    fun `pounds are converted to kilograms before saving`() =
        runTest {
            profiles.profile.value = userProfile(units = Units.IMPERIAL)
            val vm = vm()
            vm.state.test {
                vm.onWeightText("176.4")
                vm.onLogWeight()
                expectMostRecentItem()

                assertEquals(listOf(today to 80.0), body.loggedWeights)
            }
        }

    @Test
    fun `a failed save keeps what was typed and shows why`() =
        runTest {
            body.logResult = AppResult.Failure(ApiFailure.Unauthorized())
            val vm = vm()
            vm.state.test {
                vm.onWeightText("80")
                vm.onLogWeight()

                val s = expectMostRecentItem()
                assertEquals("80", s.weightText)
                assertEquals(ApiFailure.Unauthorized().message, s.weightError)
            }
        }

    @Test
    fun `a measurement is saved for the chosen body part in centimetres`() =
        runTest {
            val vm = vm()
            vm.state.test {
                vm.onMeasurementType(MeasurementType.CHEST)
                vm.onMeasurementText("101.5")
                vm.onLogMeasurement()

                val s = expectMostRecentItem()
                assertEquals(listOf(Triple(today, MeasurementType.CHEST, 101.5)), body.loggedMeasurements)
                assertEquals("", s.measurementText)
                assertEquals("Chest saved", s.message)
            }
        }

    @Test
    fun `a bad measurement is rejected with the unit in the message`() =
        runTest {
            val vm = vm()
            vm.state.test {
                vm.onMeasurementText("2")
                vm.onLogMeasurement()

                val s = expectMostRecentItem()
                assertEquals(emptyList<Triple<LocalDate, MeasurementType, Double>>(), body.loggedMeasurements)
                assertEquals("Measurement must be between 5 cm and 300 cm", s.measurementError)
            }
        }

    @Test
    fun `history is newest first and deleting says so`() =
        runTest {
            body.weights.value = listOf(weight(today.minusDays(2), 81.0), weight(today, 80.0), weight(today.minusDays(1), 80.5))
            body.measurements.value = listOf(Measurement("m1", today, MeasurementType.WAIST, 85.0))
            val vm = vm()
            vm.state.test {
                val s = expectMostRecentItem()
                assertEquals(listOf(80.0, 80.5, 81.0), s.weights.map { it.weightKg })
                assertEquals(1, s.measurements.size)

                vm.onDeleteWeight("w-1")
                assertEquals("Weigh-in deleted", expectMostRecentItem().message)
                vm.onMessageShown()
                assertNull(expectMostRecentItem().message)
                vm.onDeleteMeasurement("m1")
                expectMostRecentItem()

                assertEquals(listOf("weight:w-1", "measurement:m1"), body.deleted)
            }
        }
}
