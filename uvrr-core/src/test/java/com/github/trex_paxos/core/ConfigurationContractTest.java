// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core;

import com.github.trex_paxos.core.configuration.*;
import com.github.trex_paxos.core.ids.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationContractTest {

    private static final NodeId N0 = new NodeId(10);
    private static final NodeId N1 = new NodeId(11);
    private static final NodeId N2 = new NodeId(12);

    private Configuration initialized(List<NodeId> order) throws ConfigException {
        Configuration voidConfig = Configuration.voidConfig();
        Configuration voided = voidConfig.apply(new SystemOperation.Void(), new Slot(1));
        return voided.apply(new SystemOperation.Init(order), new Slot(2));
    }

    @Test
    void genesisEstablishesEra0And1() throws Exception {
        Configuration c = initialized(List.of(N0, N1, N2));
        assertThat(c.era()).isEqualTo(new Era(1));
        assertThat(c.len()).isEqualTo(3);
        assertThat(c.total()).isEqualTo(3);
        assertThat(c.order().stream().map(Member::node).toList()).containsExactly(N0, N1, N2);
    }

    @Test
    void weightOperationsObeyDomain() throws Exception {
        Configuration c = initialized(List.of(N0, N1, N2));

        // Increment N0 to 2
        Configuration inc = c.apply(new SystemOperation.Increment(N0), new Slot(3));
        assertThat(inc.era()).isEqualTo(new Era(2));
        assertThat(inc.weightOf(N0)).contains(new Weight(2));
        assertThat(inc.total()).isEqualTo(4);

        // Incrementing N0 past MAX_WEIGHT (2) is refused
        assertThatThrownBy(() -> inc.apply(new SystemOperation.Increment(N0), new Slot(4)))
                .isInstanceOf(ConfigException.class)
                .satisfies(e -> {
                    ConfigException ce = (ConfigException) e;
                    assertThat(ce.error()).isInstanceOf(ConfigError.WeightCapExceeded.class);
                });

        // Decrement N0 back to 1
        Configuration dec = inc.apply(new SystemOperation.Decrement(N0), new Slot(4));
        assertThat(dec.weightOf(N0)).contains(new Weight(1));

        // Decrement N0 to 0 (learner)
        Configuration learner = dec.apply(new SystemOperation.Decrement(N0), new Slot(5));
        assertThat(learner.weightOf(N0)).contains(new Weight(0));

        // Decrement N0 below 0 is refused
        assertThatThrownBy(() -> learner.apply(new SystemOperation.Decrement(N0), new Slot(6)))
                .isInstanceOf(ConfigException.class)
                .satisfies(e -> {
                    ConfigException ce = (ConfigException) e;
                    assertThat(ce.error()).isInstanceOf(ConfigError.WeightUnderflow.class);
                });
    }

    @Test
    void halveOddWeightRefused() throws Exception {
        Configuration c = initialized(List.of(N0, N1, N2));
        // all weights are 1 (odd)
        assertThatThrownBy(() -> c.apply(new SystemOperation.HalveOp(), new Slot(3)))
                .isInstanceOf(ConfigException.class)
                .satisfies(e -> {
                    ConfigException ce = (ConfigException) e;
                    assertThat(ce.error()).isInstanceOf(ConfigError.OddWeight.class);
                });
    }

    @Test
    void doubleAndHalveScaleAllWeights() throws Exception {
        Configuration c = initialized(List.of(N0, N1, N2));
        // double from 1 to 2
        Configuration doubled = c.apply(new SystemOperation.DoubleOp(), new Slot(3));
        assertThat(doubled.weightOf(N0)).contains(new Weight(2));
        assertThat(doubled.weightOf(N1)).contains(new Weight(2));
        assertThat(doubled.weightOf(N2)).contains(new Weight(2));
        assertThat(doubled.total()).isEqualTo(6);

        // halve back to 1
        Configuration halved = doubled.apply(new SystemOperation.HalveOp(), new Slot(4));
        assertThat(halved.weightOf(N0)).contains(new Weight(1));
        assertThat(halved.total()).isEqualTo(3);
    }

    @Test
    void joinAndLeaveLearner() throws Exception {
        Configuration c = initialized(List.of(N0, N1));
        NodeId n3 = new NodeId(13);

        Configuration joined = c.apply(new SystemOperation.Join(n3, 2), new Slot(3));
        assertThat(joined.len()).isEqualTo(3);
        assertThat(joined.weightOf(n3)).contains(new Weight(0));

        Configuration left = joined.apply(new SystemOperation.Leave(n3), new Slot(4));
        assertThat(left.len()).isEqualTo(2);
        assertThat(left.weightOf(n3)).isEmpty();
    }

    @Test
    void nonLearnerCannotLeave() throws Exception {
        Configuration c = initialized(List.of(N0, N1, N2));
        // N0 is voting member (weight 1), cannot leave directly
        assertThatThrownBy(() -> c.apply(new SystemOperation.Leave(N0), new Slot(3)))
                .isInstanceOf(ConfigException.class)
                .satisfies(e -> {
                    ConfigException ce = (ConfigException) e;
                    assertThat(ce.error()).isInstanceOf(ConfigError.NonZeroWeight.class);
                });
    }

    @Test
    void batchAppliesMultipleOperationsInOneEra() throws Exception {
        Configuration c = initialized(List.of(N0, N1, N2));
        NodeId n3 = new NodeId(13);

        SystemOperation batch = new SystemOperation.Batch(List.of(
                new SystemOperation.Join(n3, 3),
                new SystemOperation.Increment(n3)
        ));

        Configuration batched = c.apply(batch, new Slot(3));
        assertThat(batched.era()).isEqualTo(new Era(2));
        assertThat(batched.len()).isEqualTo(4);
        assertThat(batched.weightOf(n3)).contains(new Weight(1));
        assertThat(batched.total()).isEqualTo(4);
    }

    @Test
    void eraTableMaintainsResidentHistory() throws Exception {
        EraTable table = EraTable.genesis();
        table = table.extend(new SystemOperation.Void(), new Slot(1));
        table = table.extend(new SystemOperation.Init(List.of(N0, N1, N2)), new Slot(2));

        assertThat(table.current().era()).isEqualTo(new Era(1));
        assertThat(table.record(new Era(1))).isPresent();
        assertThat(table.record(new Era(0))).isPresent();

        table = table.extend(new SystemOperation.Increment(N0), new Slot(3));
        assertThat(table.current().era()).isEqualTo(new Era(2));
        assertThat(table.records()).hasSize(2); // holds {1, 2}
        assertThat(table.record(new Era(2))).isPresent();
        assertThat(table.record(new Era(1))).isPresent();
        assertThat(table.record(new Era(0))).isEmpty();

        table = table.extend(new SystemOperation.Increment(N1), new Slot(4));
        assertThat(table.current().era()).isEqualTo(new Era(3));
        assertThat(table.records()).hasSize(2); // retains window {2, 3}
        assertThat(table.record(new Era(3))).isPresent();
        assertThat(table.record(new Era(2))).isPresent();
        assertThat(table.record(new Era(1))).isEmpty();
    }
}
