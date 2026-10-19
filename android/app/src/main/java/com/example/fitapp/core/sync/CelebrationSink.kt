package com.example.fitapp.core.sync

import com.example.fitapp.core.network.dto.CelebrationsDto

/** Where the sync engine hands over what a push response said to celebrate. */
fun interface CelebrationSink {
    fun offer(celebrations: CelebrationsDto)

    companion object {
        val None = CelebrationSink { }
    }
}
