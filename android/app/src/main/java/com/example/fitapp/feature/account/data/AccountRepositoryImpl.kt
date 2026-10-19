package com.example.fitapp.feature.account.data

import android.content.Context
import com.example.fitapp.core.database.AppDatabase
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.ExtrasApi
import com.example.fitapp.core.network.dto.DeleteAccountRequestDto
import com.example.fitapp.core.network.safeApiCall
import com.example.fitapp.core.notifications.NotificationPreferences
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.account.domain.AccountRepository
import com.example.fitapp.feature.account.domain.ShareableFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val api: ExtrasApi,
        private val tokens: TokenStore,
        private val database: AppDatabase,
        private val notifications: NotificationPreferences,
    ) : AccountRepository {
        override suspend fun exportData(): AppResult<ShareableFile> =
            withContext(Dispatchers.IO) {
                when (val response = safeApiCall { api.exportData() }) {
                    is AppResult.Failure -> response
                    is AppResult.Success -> {
                        FileExports.clear(context)
                        val file = File(FileExports.dir(context), "the-man-project-export-${LocalDate.now()}.json")
                        // The body is copied to the file as it arrives. A broken connection part way is reported, never saved half done.
                        val written =
                            runCatching {
                                response.value.use { body -> body.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } } }
                            }
                        if (written.isSuccess) {
                            AppResult.Success(FileExports.shareable(context, file, "application/json"))
                        } else {
                            file.delete()
                            AppResult.Failure(ApiFailure.Unknown("The download was interrupted. Please try again."))
                        }
                    }
                }
            }

        override suspend fun deleteAccount(password: String): AppResult<Unit> =
            when (val result = safeApiCall { api.deleteAccount(DeleteAccountRequestDto(password)) }) {
                is AppResult.Failure -> result
                is AppResult.Success -> {
                    // The account is gone on the server. Everything about it is removed here too, and the session is ended last:
                    // signing out sends the app back to the login screen, which cancels the screen's work, so the wipe must be
                    // finished (and cannot be cancelled) before that happens. Ending the session also turns the reminder alarms off.
                    withContext(NonCancellable + Dispatchers.IO) {
                        database.clearAllTables()
                        FileExports.clear(context)
                        notifications.saveSeenSocialIds(emptySet())
                    }
                    tokens.clear()
                    AppResult.Success(Unit)
                }
            }
    }