package com.example.fitapp.feature.lockin

import com.example.fitapp.core.database.LockinProgramEntity
import com.example.fitapp.feature.lockin.data.toDomain
import com.example.fitapp.feature.lockin.domain.LockinMethod
import com.example.fitapp.feature.profile.domain.FitnessLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockinMappersTest {
    private val days =
        """[{"dayIndex":0,"name":"Chest and Back","focus":"Volume","estimatedMinutes":60,"exercises":[
            {"exerciseId":"e1","exerciseName":"Barbell Bench Press","primaryMuscle":"CHEST","sets":10,"repMin":10,"repMax":10,
             "restSeconds":60,"rpe":7.5,"method":"SUPERSET","group":"A","drops":0,"cues":["Brace.","Control."]},
            {"exerciseName":"Mystery Lift","sets":3,"repMin":8,"repMax":12,"restSeconds":90,"method":"SOMETHING_NEW"}
        ]}]"""

    private fun entity(
        minLevel: String = "INTERMEDIATE",
        daysJson: String = days,
    ) = LockinProgramEntity("p1", "German Volume Training", "Ten sets of ten.", "How it works.", minLevel, 60, 0, 1, daysJson, 1L)

    @Test
    fun theDaysAndLinesAreReadFromTheStoredJson() {
        val program = entity().toDomain()

        assertEquals("German Volume Training", program.name)
        assertEquals(FitnessLevel.INTERMEDIATE, program.minLevel)
        assertTrue(program.hasDetail)
        val day = program.days.single()
        assertEquals("Chest and Back", day.name)
        assertEquals(13, day.totalSets)
        val bench = day.lines[0]
        assertEquals("e1", bench.exerciseId)
        assertEquals(LockinMethod.SUPERSET, bench.method)
        assertEquals("A", bench.group)
        assertEquals("10", bench.repsLabel)
        assertEquals(listOf("Brace.", "Control."), bench.cues)
    }

    @Test
    fun anUnknownMethodFallsBackToStraightSetsAndAnUnknownExerciseHasNoId() {
        val mystery = entity().toDomain().days.single().lines[1]

        assertEquals(LockinMethod.STRAIGHT, mystery.method)
        assertNull(mystery.exerciseId)
        assertEquals("8-12", mystery.repsLabel)
        assertEquals(8.0, mystery.rpe, 0.0) // the default when the server does not say
    }

    @Test
    fun brokenJsonGivesAProgramWithoutDaysInsteadOfACrash() {
        val program = entity(daysJson = "not json").toDomain()

        assertTrue(program.days.isEmpty())
        assertFalse(program.hasDetail)
    }

    @Test
    fun anUnknownLevelIsTreatedAsTheStrictest() {
        assertEquals(FitnessLevel.ADVANCED, entity(minLevel = "LEGENDARY").toDomain().minLevel)
    }

    @Test
    fun availabilityFollowsTheFitnessLevelOrder() {
        val program = entity(minLevel = "INTERMEDIATE").toDomain()

        assertFalse(program.isAvailableTo(FitnessLevel.BEGINNER))
        assertTrue(program.isAvailableTo(FitnessLevel.INTERMEDIATE))
        assertTrue(program.isAvailableTo(FitnessLevel.ADVANCED))
        assertEquals("Needs intermediate level or above.", program.lockedReason(FitnessLevel.BEGINNER))
        assertNull(program.lockedReason(FitnessLevel.ADVANCED))
    }
}
