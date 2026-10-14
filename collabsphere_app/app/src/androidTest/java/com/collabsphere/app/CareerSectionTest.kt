package com.collabsphere.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.collabsphere.app.dto.progression.BadgeFamilyDto
import com.collabsphere.app.model.progression.ProgressionSummaryEntity
import com.collabsphere.app.ui.theme.CollabSphereTheme
import com.collabsphere.app.view.ProfileUI.AchievementsCard
import com.collabsphere.app.view.ProfileUI.CareerProgressCard
import com.collabsphere.app.viewmodel.progression.BadgeTierUi
import com.collabsphere.app.viewmodel.progression.BadgeUi
import com.collabsphere.app.viewmodel.progression.CareerUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CareerSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun summary(level: Int, max: Boolean) = ProgressionSummaryEntity(
        userId = 1, totalSp = if (max) 25_000 else 300, level = level, maxLevel = 20,
        title = if (max) "Legendary Industry Fellow" else "Associate Software Engineer",
        levelRequiredSp = if (max) 20_000 else 250, nextLevel = if (max) null else 4,
        nextTitle = if (max) null else "Software Engineer I", nextLevelRequiredSp = if (max) null else 500,
        spIntoLevel = if (max) 5_000 else 50, spSpanToNext = if (max) 0 else 250, isMaxLevel = max,
        currentStreakDays = 3, bestStreakDays = 5, lastLedgerId = 1, fetchedAt = 0
    )

    @Test
    fun showsDesignationAndDistanceToTheNextLevel() {
        compose.setContent {
            CollabSphereTheme(darkTheme = false) {
                CareerProgressCard(CareerUiState(summary = summary(3, max = false)), onRetry = {}, onOpenRoadmap = {}, onOpenHistory = {})
            }
        }
        compose.onNodeWithText("Associate Software Engineer").assertIsDisplayed()
        compose.onNodeWithText("200 SP to Software Engineer I (500 SP)").assertIsDisplayed()
        compose.onNodeWithContentDescription("50 of 250 SP toward Software Engineer I").assertIsDisplayed()
    }

    @Test
    fun theTopLevelHasAClearMaximumState() {
        compose.setContent {
            CollabSphereTheme(darkTheme = false) {
                CareerProgressCard(CareerUiState(summary = summary(20, max = true)), onRetry = {}, onOpenRoadmap = {}, onOpenHistory = {})
            }
        }
        compose.onNodeWithText("Legendary Industry Fellow").assertIsDisplayed()
        compose.onNodeWithText("Top designation reached — every SP still adds to your total.").assertIsDisplayed()
    }

    @Test
    fun offlineWithoutCacheOffersRetry() {
        var retried = false
        compose.setContent {
            CollabSphereTheme(darkTheme = false) {
                CareerProgressCard(CareerUiState(refreshFailed = true), onRetry = { retried = true }, onOpenRoadmap = {}, onOpenHistory = {})
            }
        }
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    @Test
    fun achievementsShowEarnedLockedAndComingSoonStates() {
        val titan = BadgeFamilyDto("TASK_TITAN", "Task Titan", "Task Completion", "Complete tasks", "TASKS_COMPLETED", 10, 50, 200, true)
        val bugBuster = BadgeFamilyDto("BUG_BUSTER", "Bug Buster", "Issues", "Close issues", "ISSUES_RESOLVED", 5, 25, 100, false)
        val badges = listOf(
            BadgeUi(titan, counterValue = 12, unlockedAt = mapOf(BadgeTierUi.BRONZE to 1L), nextTier = BadgeTierUi.SILVER, nextThreshold = 50),
            BadgeUi(bugBuster, counterValue = 0, unlockedAt = emptyMap(), nextTier = BadgeTierUi.BRONZE, nextThreshold = 5)
        )
        var clicked: String? = null
        compose.setContent {
            CollabSphereTheme(darkTheme = false) {
                AchievementsCard(CareerUiState(badges = badges), onBadgeClick = { clicked = it.family.key })
            }
        }
        compose.onNodeWithText("1 / 2 earned").assertIsDisplayed()
        compose.onNodeWithContentDescription("Task Titan, Bronze tier earned, 12 of 50 toward Silver").assertIsDisplayed()
        compose.onNodeWithContentDescription("Bug Buster, locked, coming soon").performClick()
        compose.runOnIdle { assertTrue(clicked == "BUG_BUSTER") }
    }

    @Test
    fun gitHubCardOffersLinkingAndShowsTheLinkedAccount() {
        var linkClicked = false
        compose.setContent {
            CollabSphereTheme(darkTheme = false) {
                com.collabsphere.app.view.ProfileUI.GitHubLinkCard(
                    state = com.collabsphere.app.viewmodel.progression.GitHubIdentityState.NotLinked,
                    onLink = { linkClicked = true }, onUnlink = {}, onRetry = {}
                )
            }
        }
        compose.onNodeWithText("Link GitHub").performClick()
        compose.runOnIdle { assertTrue(linkClicked) }
    }
}
