package com.collabsphere.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.collabsphere.app.ui.theme.CollabSphereTheme
import com.collabsphere.app.view.OnboardingScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun continueAdvancesPagesAndGetStartedFinishesOnboarding() {
        var finished = false
        compose.setContent {
            CollabSphereTheme(darkTheme = false) {
                OnboardingScreen(onFinish = { finished = true })
            }
        }

        compose.onNodeWithText("Where teams\nthink together").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Chat at the\nspeed of thought.").assertIsDisplayed()

        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Everything\nin reach").assertIsDisplayed()
        compose.onNodeWithText("Get started").performClick()
        compose.runOnIdle { assertTrue(finished) }
    }
}
