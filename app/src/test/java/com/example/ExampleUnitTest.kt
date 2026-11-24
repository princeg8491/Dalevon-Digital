package com.example

import com.example.data.model.ThreadPost
import com.example.data.model.formatCompactCount
import com.example.data.model.formatRelativeTime
import com.example.data.model.sanitizeUsername
import com.example.data.preferences.DarkThemeMode
import com.example.data.preferences.OrbitThemeConfig
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ExampleUnitTest {

    @Test
    fun formatRelativeTime_formatsSecondsMinutesHoursDaysWeeks() {
        val now = 1_750_000_000_000L
        assertEquals("30s", formatRelativeTime(Timestamp(Date(now - 30_000L)), now))
        assertEquals("12m", formatRelativeTime(Timestamp(Date(now - 12 * 60_000L)), now))
        assertEquals("5h", formatRelativeTime(Timestamp(Date(now - 5 * 3_600_000L)), now))
        assertEquals("3d", formatRelativeTime(Timestamp(Date(now - 3 * 86_400_000L)), now))
        assertEquals("2w", formatRelativeTime(Timestamp(Date(now - 14 * 86_400_000L)), now))
    }

    @Test
    fun formatCompactCount_formatsThousandsAndMillions() {
        assertEquals("42", formatCompactCount(42L))
        assertEquals("1.3K", formatCompactCount(1_250L))
        assertEquals("2K", formatCompactCount(2_000L))
        assertEquals("3.5M", formatCompactCount(3_500_000L))
    }

    @Test
    fun sanitizeUsername_stripsInvalidCharactersAndProvidesFallback() {
        assertEquals("alex.dev", sanitizeUsername("Alex.Dev!! ", "uid123"))
        assertTrue(sanitizeUsername("!", "abc999").startsWith("orbit_"))
    }

    @Test
    fun threadPost_distinguishesTopLevelFromReplies() {
        val root = ThreadPost(id = "p1", parentPostId = "")
        val reply = ThreadPost(id = "p2", parentPostId = "p1", rootPostId = "p1")
        assertTrue(root.isTopLevel)
        assertFalse(reply.isTopLevel)
    }

    @Test
    fun themeConfig_resolvesSystemLightAndDarkModes() {
        val systemCfg = OrbitThemeConfig(dynamicWallpaperEnabled = true, darkThemeMode = DarkThemeMode.SYSTEM)
        assertTrue(systemCfg.resolveDarkTheme(systemInDarkTheme = true))
        assertFalse(systemCfg.resolveDarkTheme(systemInDarkTheme = false))

        val lightCfg = OrbitThemeConfig(darkThemeMode = DarkThemeMode.LIGHT)
        assertFalse(lightCfg.resolveDarkTheme(systemInDarkTheme = true))

        val darkCfg = OrbitThemeConfig(darkThemeMode = DarkThemeMode.DARK)
        assertTrue(darkCfg.resolveDarkTheme(systemInDarkTheme = false))
    }

    @Test
    fun buildConversationId_isSymmetricAndDeterministic() {
        val idAB = com.example.data.model.buildConversationId("user_b", "user_a")
        val idBA = com.example.data.model.buildConversationId("user_a", "user_b")
        assertEquals(idAB, idBA)
        assertEquals("dm_user_a_user_b", idAB)
    }

    @Test
    fun classifyFoldableLayout_detectsPixel11ProFoldCoverAndInnerSquareAndPostures() {
        val cover = com.example.ui.adaptive.classifyFoldableLayout(
            windowWidthDp = androidx.compose.ui.unit.Dp(412f),
            windowHeightDp = androidx.compose.ui.unit.Dp(915f)
        )
        assertEquals(com.example.ui.adaptive.FoldScreenProfile.COVER_COMPACT, cover.screenProfile)
        assertFalse(cover.useNavigationRail)
        assertFalse(cover.supportsTwoPane)

        val innerSquare = com.example.ui.adaptive.classifyFoldableLayout(
            windowWidthDp = androidx.compose.ui.unit.Dp(840f),
            windowHeightDp = androidx.compose.ui.unit.Dp(860f)
        )
        assertEquals(com.example.ui.adaptive.FoldScreenProfile.PIXEL_FOLD_SQUARE, innerSquare.screenProfile)
        assertTrue(innerSquare.isPixelFoldInnerSquare)
        assertTrue(innerSquare.useNavigationRail)
        assertTrue(innerSquare.supportsTwoPane)

        val book = com.example.ui.adaptive.classifyFoldableLayout(
            windowWidthDp = androidx.compose.ui.unit.Dp(840f),
            windowHeightDp = androidx.compose.ui.unit.Dp(860f),
            posture = com.example.ui.adaptive.FoldablePosture.BOOK,
            isHingeSeparating = true
        )
        assertTrue(book.isBookPosture)
        assertTrue(book.supportsTwoPane)

        val tabletop = com.example.ui.adaptive.classifyFoldableLayout(
            windowWidthDp = androidx.compose.ui.unit.Dp(860f),
            windowHeightDp = androidx.compose.ui.unit.Dp(840f),
            posture = com.example.ui.adaptive.FoldablePosture.TABLETOP,
            isHingeSeparating = true
        )
        assertTrue(tabletop.isTabletop)
    }
}
