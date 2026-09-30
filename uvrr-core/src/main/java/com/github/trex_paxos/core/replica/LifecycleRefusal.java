// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.replica;

import com.github.trex_paxos.core.configuration.ConfigError;
import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.journal.JournalError;
import com.github.trex_paxos.core.progress.ProgressError;
import com.github.trex_paxos.core.quorum.QuorumError;

import java.util.Objects;

/// Why construction, provision, join, resume, or reincarnate, was refused.
public sealed interface LifecycleRefusal {

    record NotAMember(NodeId node) implements LifecycleRefusal {
        public NotAMember { Objects.requireNonNull(node, "node cannot be null"); }
    }

    record Configuration(ConfigError error) implements LifecycleRefusal {
        public Configuration { Objects.requireNonNull(error, "error cannot be null"); }
    }

    record Quorum(QuorumError error) implements LifecycleRefusal {
        public Quorum { Objects.requireNonNull(error, "error cannot be null"); }
    }

    record ProgressRefusal(ProgressError error) implements LifecycleRefusal {
        public ProgressRefusal { Objects.requireNonNull(error, "error cannot be null"); }
    }

    record JournalRefusal(JournalError error) implements LifecycleRefusal {
        public JournalRefusal { Objects.requireNonNull(error, "error cannot be null"); }
    }

    record JournalNotEmpty() implements LifecycleRefusal {}

    record NotAFreshJoiner() implements LifecycleRefusal {}

    record ProgressJournalDivergence(Slot progress, Slot journal) implements LifecycleRefusal {
        public ProgressJournalDivergence {
            Objects.requireNonNull(progress, "progress cannot be null");
            Objects.requireNonNull(journal, "journal cannot be null");
        }
    }
}
