package com.example.fitapp.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.account.domain.AccountRepository
import com.example.fitapp.feature.account.domain.ReportFormat
import com.example.fitapp.feature.account.domain.ReportPeriod
import com.example.fitapp.feature.account.domain.ReportRepository
import com.example.fitapp.feature.account.domain.ShareableFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What is being made right now. Only one thing at a time. */
enum class DataTask { PDF, CSV, EXPORT }

data class DeleteDialogState(
    val password: String = "",
    val error: String? = null,
    val busy: Boolean = false,
)

data class DataUiState(
    val period: ReportPeriod = ReportPeriod.MONTH,
    val busy: DataTask? = null,
    /** Null while the delete dialog is closed. */
    val deleteDialog: DeleteDialogState? = null,
)

sealed interface DataEvent {
    /** A file is ready: the screen opens the system share sheet. */
    data class Share(val file: ShareableFile) : DataEvent

    data class Message(val text: String) : DataEvent
}

interface DataActions {
    fun onPeriod(period: ReportPeriod)

    fun onReport(format: ReportFormat)

    fun onExportData()

    fun onOpenDelete()

    fun onDeletePassword(text: String)

    fun onConfirmDelete()

    fun onCancelDelete()
}

@HiltViewModel
class DataViewModel
    @Inject
    constructor(
        private val account: AccountRepository,
        private val reports: ReportRepository,
    ) : ViewModel(), DataActions {
        private val _state = MutableStateFlow(DataUiState())
        val state: StateFlow<DataUiState> = _state.asStateFlow()

        private val eventChannel = Channel<DataEvent>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()

        override fun onPeriod(period: ReportPeriod) {
            _state.update { it.copy(period = period) }
        }

        override fun onReport(format: ReportFormat) {
            if (_state.value.busy != null) return
            _state.update { it.copy(busy = if (format == ReportFormat.PDF) DataTask.PDF else DataTask.CSV) }
            viewModelScope.launch {
                val result = reports.build(_state.value.period, format)
                _state.update { it.copy(busy = null) }
                deliver(result)
            }
        }

        override fun onExportData() {
            if (_state.value.busy != null) return
            _state.update { it.copy(busy = DataTask.EXPORT) }
            viewModelScope.launch {
                val result = account.exportData()
                _state.update { it.copy(busy = null) }
                deliver(result)
            }
        }

        private suspend fun deliver(result: AppResult<ShareableFile>) {
            when (result) {
                is AppResult.Success -> eventChannel.send(DataEvent.Share(result.value))
                is AppResult.Failure -> eventChannel.send(DataEvent.Message(result.failure.message))
            }
        }

        override fun onOpenDelete() {
            _state.update { it.copy(deleteDialog = DeleteDialogState()) }
        }

        override fun onDeletePassword(text: String) {
            _state.update { s -> s.copy(deleteDialog = s.deleteDialog?.copy(password = text, error = null)) }
        }

        override fun onCancelDelete() {
            if (_state.value.deleteDialog?.busy == true) return
            _state.update { it.copy(deleteDialog = null) }
        }

        override fun onConfirmDelete() {
            val dialog = _state.value.deleteDialog ?: return
            if (dialog.busy) return
            if (dialog.password.isBlank()) {
                _state.update { it.copy(deleteDialog = dialog.copy(error = "Enter your password to confirm.")) }
                return
            }
            _state.update { it.copy(deleteDialog = dialog.copy(busy = true, error = null)) }
            viewModelScope.launch {
                when (val result = account.deleteAccount(dialog.password)) {
                    // The account and the data on this phone are gone and the session has ended: the app returns to sign in by itself.
                    is AppResult.Success -> _state.update { it.copy(deleteDialog = null) }
                    is AppResult.Failure ->
                        _state.update { s ->
                            s.copy(deleteDialog = s.deleteDialog?.copy(busy = false, error = deleteMessage(result.failure)))
                        }
                }
            }
        }

        private fun deleteMessage(failure: ApiFailure): String = if (failure is ApiFailure.Invalid) "That password is not correct." else failure.message
    }