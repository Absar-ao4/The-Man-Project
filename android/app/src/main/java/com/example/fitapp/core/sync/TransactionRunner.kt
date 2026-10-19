package com.example.fitapp.core.sync

import androidx.room.withTransaction
import com.example.fitapp.core.database.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton

/** Runs a block as one database transaction. An interface so the engine can be tested without Room. */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

@Singleton
class RoomTransactionRunner
    @Inject
    constructor(
        private val database: AppDatabase,
    ) : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction { block() }
    }
