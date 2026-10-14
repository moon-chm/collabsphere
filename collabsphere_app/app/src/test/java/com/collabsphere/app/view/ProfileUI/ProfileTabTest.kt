package com.collabsphere.app.view.ProfileUI

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileTabTest {
    @Test
    fun `navigation arguments select the matching tab`() {
        assertEquals(ProfileTab.CAREER, ProfileTab.fromArg("career"))
        assertEquals(ProfileTab.SETTINGS, ProfileTab.fromArg("SETTINGS"))
        assertEquals(ProfileTab.PROFILE, ProfileTab.fromArg(null))
        assertEquals(ProfileTab.PROFILE, ProfileTab.fromArg("unknown"))
    }
}
