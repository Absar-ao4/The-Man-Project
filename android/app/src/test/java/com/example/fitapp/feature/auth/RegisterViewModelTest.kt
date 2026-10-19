package com.example.fitapp.feature.auth

import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeAuthRepository
import com.example.fitapp.feature.auth.domain.RegisterUseCase
import com.example.fitapp.feature.auth.ui.AuthStatus
import com.example.fitapp.feature.auth.ui.RegisterViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RegisterViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeAuthRepository()

    private fun viewModel() =
        RegisterViewModel(RegisterUseCase(repo)).apply {
            onNameChange("Alice")
            onUsernameChange("alice_99")
            onEmailChange("alice@example.com")
            onPasswordChange("long-enough-password")
        }

    @Test
    fun `invalid fields are flagged without calling the server`() =
        runTest {
            val vm = RegisterViewModel(RegisterUseCase(repo))
            vm.onEmailChange("nope")
            vm.onPasswordChange("short")
            vm.submit()

            val s = vm.state.value
            assertNotNull(s.nameError)
            assertNotNull(s.usernameError)
            assertNotNull(s.emailError)
            assertNotNull(s.passwordError)
            assertEquals(0, repo.registerCalls)
        }

    @Test
    fun `valid form registers`() =
        runTest {
            val vm = viewModel()
            vm.submit()
            assertEquals(1, repo.registerCalls)
            assertEquals(AuthStatus.Success, vm.state.value.status)
        }

    @Test
    fun `server conflict is placed next to the offending field`() =
        runTest {
            repo.registerResult =
                AppResult.Failure(
                    ApiFailure.Conflict("Username is already taken", mapOf("username" to "is already taken")),
                )
            val vm = viewModel()
            vm.submit()

            val s = vm.state.value
            assertEquals("Username is already taken", s.usernameError)
            assertNull(s.emailError)
            assertEquals(AuthStatus.Idle, s.status)
        }

    @Test
    fun `failures without field details become a banner`() =
        runTest {
            repo.registerResult = AppResult.Failure(ApiFailure.Timeout)
            val vm = viewModel()
            vm.submit()
            assertEquals(AuthStatus.Error(ApiFailure.Timeout.message), vm.state.value.status)
        }
}
