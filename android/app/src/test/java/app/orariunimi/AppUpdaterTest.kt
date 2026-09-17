package app.orariunimi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {
    @Test
    fun comparesSemanticVersionsNumerically() {
        assertTrue(AppUpdater.isNewer("1.4.0", "1.3.1"))
        assertTrue(AppUpdater.isNewer("1.10.0", "1.9.9"))
        assertFalse(AppUpdater.isNewer("1.3.1", "1.3.1"))
        assertFalse(AppUpdater.isNewer("1.3.0", "1.3.1"))
    }

    @Test
    fun acceptsDifferentVersionLengths() {
        assertTrue(AppUpdater.isNewer("2.0.1", "2"))
        assertFalse(AppUpdater.isNewer("2.0", "2.0.0"))
        assertFalse(AppUpdater.isNewer("not-a-version", "1.0.0"))
    }
}
