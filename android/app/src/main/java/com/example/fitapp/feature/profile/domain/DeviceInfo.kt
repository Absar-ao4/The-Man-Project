package com.example.fitapp.feature.profile.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

/** Device facts used to pre-fill the profile. An interface so tests are deterministic. */
interface DeviceInfo {
    fun timezoneId(): String

    fun currencyCode(): String

    fun today(): LocalDate

    /** Hour of the day, 0 to 23, in the device's time zone. */
    fun currentHour(): Int
}

/** The device's date, and again when it changes. A screen left open past midnight moves on to the new day by itself. */
fun DeviceInfo.todayFlow(checkEveryMillis: Long = 60_000L): Flow<LocalDate> =
    flow {
        while (true) {
            emit(today())
            delay(checkEveryMillis)
        }
    }.distinctUntilChanged()
