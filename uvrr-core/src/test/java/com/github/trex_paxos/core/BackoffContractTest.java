// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core;

import com.github.trex_paxos.core.backoff.Backoff;
import com.github.trex_paxos.core.backoff.Window;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffContractTest {

    private long referenceWindow(long unit, long attempt) {
        long whole = unit;
        for (long i = 0; i < attempt; i++) {
            if (whole >= Backoff.CAP_MILLIS) {
                break;
            }
            if (whole > Long.MAX_VALUE / 2) {
                whole = Long.MAX_VALUE;
            } else {
                whole *= 2;
            }
        }
        return Math.min(whole, Backoff.CAP_MILLIS);
    }

    @Test
    void unitIsTwiceRoundTrip() {
        for (long rtt = 0; rtt <= 2048; rtt++) {
            assertThat(Backoff.unitFromRtt(rtt)).isEqualTo(2 * rtt);
        }
        assertThat(Backoff.unitFromRtt(Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void windowsDoubleFromUnitAndSplitEvenly() {
        for (long unit = 0; unit <= 64; unit++) {
            for (long attempt = 0; attempt <= 16; attempt++) {
                Window w = Backoff.window(unit, attempt);
                long whole = referenceWindow(unit, attempt);
                assertThat(w.fixedMillis()).isEqualTo(w.jitterMillis());
                assertThat(w.fixedMillis()).isEqualTo(whole / 2);
            }
        }
    }

    @Test
    void workedExampleMatchesRecommendation() {
        // 10 ms RTT -> 20 ms unit
        long unit = Backoff.unitFromRtt(10);
        assertThat(unit).isEqualTo(20);

        long[][] expected = {
                {10, 10},   // attempt 0: 20 ms
                {20, 20},   // attempt 1: 40 ms
                {40, 40},   // attempt 2: 80 ms
                {80, 80},   // attempt 3: 160 ms
                {160, 160}, // attempt 4: 320 ms
                {320, 320}, // attempt 5: 640 ms
                {640, 640}, // attempt 6: 1280 ms
                {1280, 1280}, // attempt 7: 2560 ms
                {2500, 2500}, // attempt 8: 5000 ms (capped)
                {2500, 2500}, // attempt 9: 5000 ms
        };

        for (int attempt = 0; attempt < expected.length; attempt++) {
            Window w = Backoff.window(unit, attempt);
            assertThat(w.fixedMillis()).isEqualTo(expected[attempt][0]);
            assertThat(w.jitterMillis()).isEqualTo(expected[attempt][1]);
        }
    }
}
