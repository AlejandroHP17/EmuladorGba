package pelkidev.com.mx.emuladorgba.domain

class ControlLatch(
    val latched: Set<GbaButton> = emptySet(),
    private val starts: Map<GbaButton, List<Long>> = emptyMap(),
    private val downAt: Map<GbaButton, Long> = emptyMap(),
) {
    fun down(button: GbaButton, atMillis: Long): ControlLatch {
        if (button in DIRECTIONS) return this
        val stillLatched = if (button in latched) latched - button else latched
        val keptStarts = if (button in latched) starts - button else starts
        return ControlLatch(stillLatched, keptStarts, downAt + (button to atMillis))
    }

    fun up(button: GbaButton): ControlLatch {
        if (button in DIRECTIONS) return this
        val started = downAt[button] ?: return this
        val previous = starts[button].orEmpty()
        val gap = if (previous.isEmpty()) 0L else started - previous.last()
        val streak = if (previous.isEmpty() || gap < GAP_MS) previous + started else listOf(started)
        val open = downAt - button
        return if (streak.size >= TAPS_TO_LATCH) {
            ControlLatch(latched + button, starts - button, open)
        } else {
            ControlLatch(latched, starts + (button to streak), open)
        }
    }

    private companion object {
        const val GAP_MS = 500L
        const val TAPS_TO_LATCH = 3
        val DIRECTIONS = setOf(GbaButton.UP, GbaButton.DOWN, GbaButton.LEFT, GbaButton.RIGHT)
    }
}
