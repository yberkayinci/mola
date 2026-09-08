package com.yberkayinci.mola.usage

object CooldownPolicy {
    const val DEFAULT_COOLDOWN_MILLIS: Long = 15L * 60L * 1_000L

    fun shouldShow(
        nowMillis: Long,
        lastShownAtMillis: Long?,
        cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
    ): Boolean = remainingMillis(nowMillis, lastShownAtMillis, cooldownMillis) == 0L

    fun remainingMillis(
        nowMillis: Long,
        lastShownAtMillis: Long?,
        cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
    ): Long {
        if (lastShownAtMillis == null) return 0L
        if (nowMillis < lastShownAtMillis) return 0L

        val cooldown = cooldownMillis.coerceAtLeast(0L)
        val elapsed = nowMillis - lastShownAtMillis
        return (cooldown - elapsed).coerceAtLeast(0L)
    }
}
