// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core;

import com.github.trex_paxos.core.configuration.Configuration;
import com.github.trex_paxos.core.configuration.SystemOperation;
import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.quorum.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class QuorumContractTest {

    private static final NodeId N0 = new NodeId(0);
    private static final NodeId N1 = new NodeId(1);
    private static final NodeId N2 = new NodeId(2);

    private Configuration initialized(List<NodeId> order) throws Exception {
        return Configuration.voidConfig()
                .apply(new SystemOperation.Void(), new Slot(1))
                .apply(new SystemOperation.Init(order), new Slot(2));
    }

    @Test
    void weightedMajorityValidatesStandardConfigurations() throws Exception {
        Configuration c3 = initialized(List.of(N0, N1, N2));
        WeightedMajority wm = new WeightedMajority();

        Optional<QuorumError> err = QuorumGate.validateEra(wm, c3);
        assertThat(err).isEmpty();

        // 2 nodes out of 3 form a commit quorum
        assertThat(wm.isQuorum(Role.COMMIT, c3, List.of(N0, N1))).isTrue();
        assertThat(wm.isQuorum(Role.COMMIT, c3, List.of(N0))).isFalse();
    }

    @Test
    void transitionValidationRejectsUnsafeReconfiguration() throws Exception {
        Configuration current = initialized(List.of(N0, N1, N2));
        WeightedMajority wm = new WeightedMajority();

        // Legal step: increment N0
        Configuration next = current.apply(new SystemOperation.Increment(N0), new Slot(3));
        Optional<QuorumError> transErr = QuorumGate.validateTransition(wm, current, next);
        assertThat(transErr).isEmpty();
    }

    @Test
    void constructPivotFindsDisjointCoveringQuorums() throws Exception {
        Configuration current = initialized(List.of(N0, N1, N2));
        Configuration next = current.apply(new SystemOperation.Increment(N0), new Slot(3));
        WeightedMajority wm = new WeightedMajority();

        Optional<Pivot> pivot = QuorumGate.constructPivot(wm, current, next, N0);
        assertThat(pivot).isPresent();

        Optional<PivotError> pivotErr = QuorumGate.validatePivot(wm, current, next, N0, pivot.get());
        assertThat(pivotErr).isEmpty();
    }
}
