// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core;

import com.github.trex_paxos.core.effects.Effect;
import com.github.trex_paxos.core.effects.Stability;
import com.github.trex_paxos.core.ids.*;
import com.github.trex_paxos.core.journal.InMemoryLog;
import com.github.trex_paxos.core.progress.Status;
import com.github.trex_paxos.core.quorum.WeightedMajority;
import com.github.trex_paxos.core.replica.Input;
import com.github.trex_paxos.core.replica.PlannedTransition;
import com.github.trex_paxos.core.replica.PublishOutcome;
import com.github.trex_paxos.core.replica.Replica;
import com.github.trex_paxos.core.replica.TimedInput;
import com.github.trex_paxos.core.replica.ViewChangeKnobs;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReplicaContractTest {

    @Test
    void provisionConstructsGenesisState() throws Exception {
        NodeId own = new NodeId(0);
        List<NodeId> order = List.of(own, new NodeId(1), new NodeId(2));
        InMemoryLog journal = new InMemoryLog();
        WeightedMajority strategy = new WeightedMajority();

        Replica<InMemoryLog, WeightedMajority> replica = Replica.provision(
                own,
                order,
                strategy,
                journal,
                Stability.VOLATILE,
                ViewChangeKnobs.INERT
        );

        assertThat(replica.progress().current()).isEqualTo(new Ballot(new Era(1), View.INITIAL));
        assertThat(replica.progress().status()).isEqualTo(Status.JOINING);
        assertThat(replica.progress().accepted()).isEqualTo(new Slot(2));
        assertThat(replica.progress().committed()).isEqualTo(new Slot(2));
    }

    @Test
    void tickPromotesGenesisToNormal() throws Exception {
        NodeId own = new NodeId(0);
        List<NodeId> order = List.of(own, new NodeId(1), new NodeId(2));
        InMemoryLog journal = new InMemoryLog();
        WeightedMajority strategy = new WeightedMajority();

        Replica<InMemoryLog, WeightedMajority> replica = Replica.provision(
                own,
                order,
                strategy,
                journal,
                Stability.VOLATILE,
                ViewChangeKnobs.INERT
        );

        TimedInput tick = new TimedInput(new Tick(100), new Input.Tick());
        PlannedTransition plan = replica.plan(tick, journal.view());
        PublishOutcome outcome = replica.publish(plan);

        assertThat(replica.progress().status()).isEqualTo(Status.NORMAL);
        assertThat(outcome.effects()).isNotEmpty();
    }
}
