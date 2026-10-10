package com.collabsphere.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import com.collabsphere.app.model.UserRepo
import com.collabsphere.app.ui.theme.CollabSphereTheme
import com.collabsphere.app.view.LoginScreen
import com.collabsphere.app.viewmodel.LoginViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class LoginScreenEdgeCaseTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyLoginSubmitNeverCallsTheRepository() {
        val repo = mockk<UserRepo>(relaxed = true)
        val preferences = mockk<UserPreferences>(relaxed = true)
        coEvery { preferences.userIdFlow } returns flowOf(-1)
        coEvery { preferences.userNameFlow } returns flowOf("")
        coEvery { preferences.userEmailFlow } returns flowOf("")
        val viewModel = LoginViewModel(repo, preferences, mockk(relaxed = true))

        compose.setContent {
            CollabSphereTheme(darkTheme = false) {
                LoginScreen(viewModel = viewModel, onNavigateToRegister = {})
            }
        }

        compose.onNodeWithText("Login").performClick()
        compose.onNodeWithText("Email cannot be empty").assertIsDisplayed()
        compose.onNodeWithText("Password cannot be empty").assertIsDisplayed()
        compose.runOnIdle {
            coVerify(exactly = 0) { repo.loginRemote(any(), any()) }
        }
    }
}
