// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core;

import com.github.trex_paxos.core.configuration.SystemOperation;
import com.github.trex_paxos.core.ids.Era;
import com.github.trex_paxos.core.ids.OperationId;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.journal.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalContractTest {

    @Test
    void emptyJournalHasNoAcceptedOrBase() {
        InMemoryLog log = new InMemoryLog();
        JournalView view = log.view();
        assertThat(view.accepted()).isEmpty();
        assertThat(view.get(new Slot(1))).isEmpty();
    }

    @Test
    void installSuffixRequiresContiguousSequence() throws Exception {
        InMemoryLog log = new InMemoryLog();
        LogEntry e1 = new LogEntry(new Slot(1), Era.INITIAL, new Payload.SystemPayload(new SystemOperation.Void()));
        LogEntry e2 = new LogEntry(new Slot(2), new Era(1), new Payload.SystemPayload(new SystemOperation.Init(List.of())));

        // install contiguous prefix [1, 2]
        log.installSuffix(new Slot(1), List.of(e1, e2));
        assertThat(log.view().accepted()).contains(new Slot(2));
        assertThat(log.view().retained().first()).isEqualTo(new Slot(1));
        assertThat(log.view().retained().last()).isEqualTo(new Slot(2));

        // installing gap at slot 4 is refused
        LogEntry e4 = new LogEntry(new Slot(4), new Era(1), new Payload.OperationPayload(new OperationId(1, 1), new byte[]{1}));
        assertThatThrownBy(() -> log.installSuffix(new Slot(4), List.of(e4)))
                .isInstanceOf(JournalException.class)
                .satisfies(e -> {
                    JournalException je = (JournalException) e;
                    assertThat(je.error()).isInstanceOf(JournalError.NonContiguous.class);
                });
    }

    @Test
    void acceptAppendsLogEntries() throws Exception {
        InMemoryLog log = new InMemoryLog();
        LogEntry e1 = new LogEntry(new Slot(1), Era.INITIAL, new Payload.SystemPayload(new SystemOperation.Void()));
        log.installSuffix(new Slot(1), List.of(e1));

        LogEntry e2 = new LogEntry(new Slot(2), new Era(1), new Payload.OperationPayload(new OperationId(1, 2), new byte[]{2}));
        log.accept(List.of(e2));

        assertThat(log.view().accepted()).contains(new Slot(2));
        assertThat(log.view().get(new Slot(2))).contains(e2);

        // iterRange returns the entries in order
        List<LogEntry> entries = log.view().iterRange(new Slot(1), new Slot(3));
        assertThat(entries).containsExactly(e1, e2);
    }
}
