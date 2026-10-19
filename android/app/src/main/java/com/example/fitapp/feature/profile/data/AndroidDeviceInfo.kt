package com.example.fitapp.feature.profile.data

import com.example.fitapp.feature.profile.domain.DeviceInfo
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

class AndroidDeviceInfo
    @Inject
    constructor() : DeviceInfo {
        override fun timezoneId(): String = ZoneId.systemDefault().id

        override fun currencyCode(): String =
            runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault("USD")

        override fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())

        override fun currentHour(): Int = java.time.LocalTime.now(ZoneId.systemDefault()).hour
    }
