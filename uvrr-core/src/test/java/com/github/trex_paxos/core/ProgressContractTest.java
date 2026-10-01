// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core;

import com.github.trex_paxos.core.configuration.EraTable;
import com.github.trex_paxos.core.configuration.SystemOperation;
import com.github.trex_paxos.core.ids.*;
import com.github.trex_paxos.core.invariant.HeaderSlotRole;
import com.github.trex_paxos.core.invariant.InputKind;
import com.github.trex_paxos.core.invariant.InvariantChecker;
import com.github.trex_paxos.core.progress.Progress;
import com.github.trex_paxos.core.progress.ProgressError;
import com.github.trex_paxos.core.progress.ProgressException;
import com.github.trex_paxos.core.progress.Status;
import com.github.trex_paxos.core.wire.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgressContractTest {

    private EraTable makeTable() throws Exception {
        EraTable table = EraTable.genesis();
        table = table.extend(new SystemOperation.Void(), new Slot(1));
        return table.extend(new SystemOperation.Init(List.of(new NodeId(0), new NodeId(1), new NodeId(2))), new Slot(2));
    }

    @Test
    void frontierChainInvariantsAreEnforced() throws Exception {
        EraTable table = makeTable();
        Ballot v = new Ballot(new Era(1), new View(0));

        // Legal: checkpoint <= applied <= committed <= accepted
        Progress p = Progress.reconstitute(
                v, v, Status.NORMAL,
                new Slot(5), new Slot(4), new Slot(3), new Slot(2),
                1L, table, Optional.empty()
        );
        assertThat(p.accepted()).isEqualTo(new Slot(5));

        // Illegal: applied > committed
        assertThatThrownBy(() -> Progress.reconstitute(
                v, v, Status.NORMAL,
                new Slot(5), new Slot(3), new Slot(4), new Slot(2),
                1L, table, Optional.empty()
        )).isInstanceOf(ProgressException.class)
                .satisfies(e -> {
                    ProgressException pe = (ProgressException) e;
                    assertThat(pe.error()).isInstanceOf(ProgressError.FrontierChain.class);
                });
    }

    @Test
    void statusRelationInvariantsAreEnforced() throws Exception {
        EraTable table = makeTable();
        Ballot v1 = new Ballot(new Era(1), new View(1));
        Ballot v2 = new Ballot(new Era(1), new View(2));

        // NORMAL requires current == retained
        assertThatThrownBy(() -> Progress.reconstitute(
                v2, v1, Status.NORMAL,
                new Slot(2), new Slot(2), new Slot(2), new Slot(0),
                1L, table, Optional.empty()
        )).isInstanceOf(ProgressException.class)
                .satisfies(e -> {
                    ProgressException pe = (ProgressException) e;
                    assertThat(pe.error()).isInstanceOf(ProgressError.StatusViewRelation.class);
                });

        // VIEW_CHANGE allows current >= retained
        Progress vc = Progress.reconstitute(
                v2, v1, Status.VIEW_CHANGE,
                new Slot(2), new Slot(2), new Slot(2), new Slot(0),
                1L, table, Optional.empty()
        );
        assertThat(vc.status()).isEqualTo(Status.VIEW_CHANGE);
    }

    @Test
    void headerSlotRoleClassification() {
        assertThat(InvariantChecker.headerSlotRole(Tag.PREPARE)).isEqualTo(HeaderSlotRole.OPERATION);
        assertThat(InvariantChecker.headerSlotRole(Tag.COMMIT)).isEqualTo(HeaderSlotRole.FRONTIER);
        assertThat(InvariantChecker.headerSlotRole(Tag.START_VIEW_CHANGE)).isEqualTo(HeaderSlotRole.ABSENT);
    }

    @Test
    void legalTransitionChecks() throws Exception {
        EraTable table = EraTable.genesis();
        Progress p = Progress.genesis(table);

        Progress next = p.withStatus(Status.NORMAL);
        Optional<Fault> fault = InvariantChecker.legal(p, next, new InputKind.Tick());
        assertThat(fault).isEmpty();
    }
}
