package com.example.fitapp.feature.gamification.domain

import com.example.fitapp.core.network.dto.CelebrationsDto
import com.example.fitapp.core.sync.CelebrationSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds what the server said to celebrate until the screen has shown it. It lives in memory only:
 * if the app is killed before the user sees a celebration it is gone, but the XP itself is not (the
 * server already has it and the stats are re-fetched).
 */
@Singleton
class CelebrationCenter
    @Inject
    constructor() : CelebrationSink {
        private val nextId = AtomicLong(1)
        private val _queue = MutableStateFlow<List<Celebration>>(emptyList())

        val queue: StateFlow<List<Celebration>> = _queue.asStateFlow()

        override fun offer(celebrations: CelebrationsDto) {
            val items = mutableListOf<Celebration>()

            val paid = celebrations.xpEvents.filter { it.amount > 0 }
            if (paid.isNotEmpty()) {
                val lines =
                    paid.groupBy { it.reason }
                        .map { (reason, events) -> XpLine(xpReasonLabel(reason), events.sumOf { it.amount }) }
                items += Celebration.XpGained(nextId.getAndIncrement(), paid.sumOf { it.amount }, lines)
            }
            celebrations.levelChange?.let {
                items += Celebration.LevelUp(nextId.getAndIncrement(), it.from, it.to, it.title)
            }
            celebrations.badges.forEach {
                items += Celebration.BadgeUnlocked(nextId.getAndIncrement(), it.code, it.name, it.description)
            }
            if (items.isNotEmpty()) _queue.update { it + items }
        }

        fun dismiss(id: Long) {
            _queue.update { list -> list.filterNot { it.id == id } }
        }
    }
