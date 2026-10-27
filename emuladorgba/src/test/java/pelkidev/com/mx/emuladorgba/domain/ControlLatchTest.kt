package pelkidev.com.mx.emuladorgba.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlLatchTest {

    @Test
    fun threeQuickTaps_latchOnThirdRelease() {
        val beforeRelease = taps(GbaButton.A, 0, 499).down(GbaButton.A, 998)
        assertFalse(beforeRelease.latched.contains(GbaButton.A))
        assertTrue(beforeRelease.up(GbaButton.A).latched.contains(GbaButton.A))
    }

    @Test
    fun nextTap_releasesTheLatch() {
        val latched = taps(GbaButton.A, 0, 100, 200)
        val released = latched.down(GbaButton.A, 300)
        assertFalse(released.latched.contains(GbaButton.A))
        assertFalse(released.up(GbaButton.A).latched.contains(GbaButton.A))
    }

    @Test
    fun oneOrTwoTaps_doNotLatch() {
        assertFalse(taps(GbaButton.A, 0).latched.contains(GbaButton.A))
        assertFalse(taps(GbaButton.A, 0, 100).latched.contains(GbaButton.A))
    }

    @Test
    fun gapOf500ms_doesNotLatch() {
        assertFalse(taps(GbaButton.A, 0, 500, 1000).latched.contains(GbaButton.A))
        assertFalse(taps(GbaButton.A, 0, 100, 600).latched.contains(GbaButton.A))
    }

    @Test
    fun directions_doNotLatch() {
        for (arrow in listOf(GbaButton.UP, GbaButton.DOWN, GbaButton.LEFT, GbaButton.RIGHT)) {
            assertFalse(taps(arrow, 0, 100, 200).latched.contains(arrow))
        }
    }

    @Test
    fun directionTaps_leaveAnotherLatch() {
        val latched = taps(GbaButton.A, 0, 100, 200)
        val after = taps(latched, GbaButton.LEFT, 300, 400, 500)
        assertTrue(after.latched.contains(GbaButton.A))
        assertFalse(after.latched.contains(GbaButton.LEFT))
    }

    @Test
    fun oneButton_doesNotChangeAnother() {
        val latched = taps(GbaButton.A, 0, 100, 200)
        val afterOther = latched.down(GbaButton.B, 250).up(GbaButton.B)
        assertTrue(afterOther.latched.contains(GbaButton.A))
        assertFalse(afterOther.latched.contains(GbaButton.B))

        val both = taps(latched, GbaButton.B, 300, 400, 500)
        assertTrue(both.latched.contains(GbaButton.A))
        assertTrue(both.latched.contains(GbaButton.B))
    }

    private fun taps(button: GbaButton, vararg starts: Long): ControlLatch =
        taps(ControlLatch(), button, *starts)

    private fun taps(start: ControlLatch, button: GbaButton, vararg starts: Long): ControlLatch {
        var latch = start
        for (at in starts) {
            latch = latch.down(button, at).up(button)
        }
        return latch
    }
}
