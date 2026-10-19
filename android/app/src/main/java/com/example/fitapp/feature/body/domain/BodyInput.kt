package com.example.fitapp.feature.body.domain

import com.example.fitapp.feature.profile.domain.Units

/** What came of reading a number the user typed: the value in kg or cm, or a message to show under the field. */
sealed interface InputResult {
    data class Ok(val value: Double) : InputResult

    data class Invalid(val message: String) : InputResult
}

/** Checks the weigh-in and measurement fields in the units the person uses and hands back kilograms and centimetres. */
object BodyInput {
    fun weight(
        text: String,
        units: Units,
    ): InputResult {
        val imperial = units == Units.IMPERIAL
        val kg = UnitFormat.parseWeightKg(text, imperial) ?: return InputResult.Invalid("Enter your weight as a number")
        if (kg < BodyLimits.MIN_WEIGHT_KG || kg > BodyLimits.MAX_WEIGHT_KG) {
            return InputResult.Invalid(
                "Weight must be between ${UnitFormat.weight(BodyLimits.MIN_WEIGHT_KG, imperial)} and ${UnitFormat.weight(BodyLimits.MAX_WEIGHT_KG, imperial)}",
            )
        }
        return InputResult.Ok(kg)
    }

    fun length(
        text: String,
        units: Units,
    ): InputResult {
        val imperial = units == Units.IMPERIAL
        val cm = UnitFormat.parseLengthCm(text, imperial) ?: return InputResult.Invalid("Enter the measurement as a number")
        if (cm < BodyLimits.MIN_LENGTH_CM || cm > BodyLimits.MAX_LENGTH_CM) {
            return InputResult.Invalid(
                "Measurement must be between ${UnitFormat.length(BodyLimits.MIN_LENGTH_CM, imperial)} and ${UnitFormat.length(BodyLimits.MAX_LENGTH_CM, imperial)}",
            )
        }
        return InputResult.Ok(cm)
    }
}
