package com.example.fitapp.feature.auth

import app.cash.turbine.test
import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeAuthRepository
import com.example.fitapp.feature.auth.domain.LoginUseCase
import com.example.fitapp.feature.auth.ui.AuthStatus
import com.example.fitapp.feature.auth.ui.LoginViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeAuthRepository()

    private fun viewModel() = LoginViewModel(LoginUseCase(repo))

    @Test
    fun `empty form shows field errors and never calls the server`() =
        runTest {
            val vm = viewModel()
            vm.submit()

            val state = vm.state.value
            assertEquals("Enter a valid email address", state.emailError)
            assertEquals("Enter your password", state.passwordError)
            assertEquals(0, repo.loginCalls)
        }

    @Test
    fun `typing clears that field's error`() =
        runTest {
            val vm = viewModel()
            vm.submit()
            vm.onEmailChange("a")
            assertNull(vm.state.value.emailError)
        }

    @Test
    fun `successful login passes through submitting then success`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repo.gate = gate
            val vm = viewModel()
            vm.onEmailChange("  alice@example.com ")
            vm.onPasswordChange("secret-password")

            vm.state.test {
                awaitItem() // current form
                vm.submit()
                assertTrue(awaitItem().isSubmitting)
                gate.complete(Unit)
                assertEquals(AuthStatus.Success, awaitItem().status)
            }
            assertEquals("alice@example.com" to "secret-password", repo.lastLogin)
        }

    @Test
    fun `server rejection is shown as a message`() =
        runTest {
            repo.loginResult = AppResult.Failure(ApiFailure.Unauthorized("Invalid email or password"))
            val vm = viewModel()
            vm.onEmailChange("alice@example.com")
            vm.onPasswordChange("wrong")
            vm.submit()

            assertEquals(AuthStatus.Error("Invalid email or password"), vm.state.value.status)
            assertEquals(false, vm.state.value.isSubmitting)
        }

    @Test
    fun `offline failure explains the problem and editing dismisses it`() =
        runTest {
            repo.loginResult = AppResult.Failure(ApiFailure.Offline)
            val vm = viewModel()
            vm.onEmailChange("alice@example.com")
            vm.onPasswordChange("whatever")
            vm.submit()
            assertTrue((vm.state.value.status as AuthStatus.Error).message.contains("offline"))

            vm.onPasswordChange("whatever2")
            assertEquals(AuthStatus.Idle, vm.state.value.status)
        }

    @Test
    fun `a second tap while submitting is ignored`() =
        runTest {
            repo.gate = CompletableDeferred()
            val vm = viewModel()
            vm.onEmailChange("alice@example.com")
            vm.onPasswordChange("secret-password")
            vm.submit()
            vm.submit()
            assertEquals(1, repo.loginCalls)
        }
}
