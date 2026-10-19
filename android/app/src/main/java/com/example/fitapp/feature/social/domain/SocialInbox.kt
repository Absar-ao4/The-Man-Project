package com.example.fitapp.feature.social.domain

import com.example.fitapp.core.notifications.AppNotification
import com.example.fitapp.core.notifications.NotificationChannels
import java.time.Duration
import java.time.Instant

/** One thing worth telling the user about: a friend request, a duel challenge or a duel that just ended. */
data class InboxItem(
    val key: String,
    val kind: Kind,
    val title: String,
    val text: String,
) {
    enum class Kind { REQUEST, DUEL_INVITE, DUEL_RESULT }
}

/**
 * Turns what the server last said about friend requests and duels into notifications. Pure: [SocialInboxChecker] does the
 * fetching and remembers which items it already announced.
 */
object SocialInbox {
    /** A finished duel is news for this long; older ones are history. */
    val RESULT_WINDOW: Duration = Duration.ofDays(3)

    /** Notifications are one per item up to this many, then a single summary. */
    const val MAX_SEPARATE = 3

    private const val SUMMARY_ID = 3290
    private const val ID_BASE = 3300
    private const val ID_RANGE = 0x7FFF
    const val ROUTE = "social"

    fun items(
        incoming: List<FriendRequest>,
        duels: List<Duel>,
        now: Instant,
    ): List<InboxItem> {
        val requests =
            incoming.map {
                InboxItem("request:${it.id}", InboxItem.Kind.REQUEST, "New friend request", "${it.from.name} (@${it.from.username}) wants to be your friend.")
            }
        val invites =
            duels
                .filter { it.status == DuelStatus.PENDING && !it.youAreChallenger }
                .map { InboxItem("duel-invite:${it.id}", InboxItem.Kind.DUEL_INVITE, "Duel challenge", "${it.challenger.name} challenged you. ${it.metric.label}, over 7 days.") }
        val results =
            duels
                .filter { it.status == DuelStatus.FINISHED && endedRecently(it, now) }
                .map { duel ->
                    val title =
                        when (duel.winnerId) {
                            null -> "The duel ended in a tie"
                            duel.me.id -> "You won the duel"
                            else -> "You lost the duel"
                        }
                    InboxItem("duel-result:${duel.id}", InboxItem.Kind.DUEL_RESULT, title, "${duel.other.name}: you ${duel.myScore}, them ${duel.theirScore}.")
                }
        return requests + invites + results
    }

    /** One notification per new item, or one summary when there are many at once. */
    fun notifications(fresh: List<InboxItem>): List<AppNotification> =
        when {
            fresh.isEmpty() -> emptyList()
            fresh.size <= MAX_SEPARATE ->
                fresh.map { AppNotification(idFor(it.key), NotificationChannels.SOCIAL, it.title, it.text, ROUTE) }
            else ->
                listOf(
                    AppNotification(SUMMARY_ID, NotificationChannels.SOCIAL, "${fresh.size} new updates", "Friend requests and duels are waiting in the Social tab.", ROUTE),
                )
        }

    /** A stable id per item, so announcing it twice would replace the first notification instead of stacking. */
    fun idFor(key: String): Int = ID_BASE + (key.hashCode() and ID_RANGE)

    private fun endedRecently(
        duel: Duel,
        now: Instant,
    ): Boolean {
        val ended = duel.endsAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
        return !ended.isAfter(now) && Duration.between(ended, now) <= RESULT_WINDOW
    }
}
