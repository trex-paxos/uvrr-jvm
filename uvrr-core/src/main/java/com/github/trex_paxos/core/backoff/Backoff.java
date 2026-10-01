// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.backoff;

/// The recommended randomized-timeout schedule, as pure arithmetic (S4).
public final class Backoff {

    /// The window cap, in milliseconds: the backstop past which the doubling stops.
    public static final long CAP_MILLIS = 5_000L;

    private Backoff() {}

    /// The recommended timeout unit for a measured round trip: twice the RTT, in milliseconds.
    public static long unitFromRtt(long rttMillis) {
        if (rttMillis < 0) {
            return 0;
        }
        if (rttMillis > Long.MAX_VALUE / 2) {
            return Long.MAX_VALUE;
        }
        return rttMillis * 2;
    }

    /// The suspicion window for attempt under unitMillis, split into its fixed
    /// and uniform-random halves, in milliseconds.
    public static Window window(long unitMillis, long attempt) {
        if (unitMillis <= 0 || attempt < 0) {
            return new Window(0, 0);
        }
        long clampedAttempt = Math.min(attempt, 63);
        long scale = 1L << clampedAttempt;
        long whole;
        if (clampedAttempt >= 63 || unitMillis > Long.MAX_VALUE / scale) {
            whole = CAP_MILLIS;
        } else {
            whole = Math.min(unitMillis * scale, CAP_MILLIS);
        }
        return new Window(whole / 2, whole / 2);
    }
}
