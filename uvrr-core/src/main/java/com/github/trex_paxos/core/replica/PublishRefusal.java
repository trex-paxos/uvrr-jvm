// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.replica;

import com.github.trex_paxos.core.ids.Fault;
import com.github.trex_paxos.core.journal.JournalError;

import java.util.Objects;

/// Why publish refused an offered plan.
public sealed interface PublishRefusal {

    record RevisionMismatch(long expected, long got) implements PublishRefusal {}

    record IllegalCandidate(Fault fault) implements PublishRefusal {
        public IllegalCandidate { Objects.requireNonNull(fault, "fault cannot be null"); }
    }

    record TransitionOutstanding() implements PublishRefusal {}

    record JournalRefused(JournalError error) implements PublishRefusal {
        public JournalRefused { Objects.requireNonNull(error, "error cannot be null"); }
    }
}
