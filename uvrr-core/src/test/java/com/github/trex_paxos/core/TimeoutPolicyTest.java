// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core;

import com.github.trex_paxos.core.ids.Ballot;
import com.github.trex_paxos.core.ids.Era;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.ids.Tick;
import com.github.trex_paxos.core.ids.View;
import com.github.trex_paxos.core.progress.Status;
import com.github.trex_paxos.core.timeout.Opinion;
import com.github.trex_paxos.core.timeout.State;
import com.github.trex_paxos.core.timeout.Timeout;
import com.github.trex_paxos.core.timeout.TimeoutMatcher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeoutPolicyTest {

    @Test
    void timeoutMatcherMatrix() {
        // In the cluster on steady timeout emits Heartbeat
        Opinion opHeartbeat = TimeoutMatcher.matcher(State.IN_THE_CLUSTER, Timeout.STEADY);
        assertThat(opHeartbeat).isInstanceOf(Opinion.Heartbeat.class);

        // Steady on steady timeout starts view change
        Opinion opPrimary = TimeoutMatcher.matcher(State.STEADY, Timeout.STEADY);
        assertThat(opPrimary).isInstanceOf(Opinion.StartViewChange.class);

        // In the cluster on cluster timeout retransmits
        Opinion opRetransmit = TimeoutMatcher.matcher(State.IN_THE_CLUSTER, Timeout.CLUSTER);
        assertThat(opRetransmit).isInstanceOf(Opinion.Retransmit.class);

        // Stopping not flushed emits Sorry with runbook
        Opinion opSorry = TimeoutMatcher.matcher(State.STOPPING_NOT_FLUSHED, Timeout.STEADY);
        assertThat(opSorry).isInstanceOf(Opinion.Sorry.class);
        assertThat(((Opinion.Sorry) opSorry).runbook()).isEqualTo(TimeoutMatcher.UNFLUSHED_RUNBOOK);
    }
}
