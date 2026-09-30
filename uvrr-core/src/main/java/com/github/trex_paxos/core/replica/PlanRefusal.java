// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.replica;

import com.github.trex_paxos.core.ids.Ballot;
import com.github.trex_paxos.core.ids.Fault;
import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.plan.PlanRejection;
import com.github.trex_paxos.core.progress.ProgressError;

import java.util.Objects;
import java.util.Optional;

/// Why plan refused an input.
public sealed interface PlanRefusal {

    record Faulted(Fault fault) implements PlanRefusal {
        public Faulted { Objects.requireNonNull(fault, "fault cannot be null"); }
    }

    record TransitionOutstanding() implements PlanRefusal {}

    record NoTransitionOutstanding() implements PlanRefusal {}

    record ConfirmationMismatch(long expected, long got) implements PlanRefusal {}

    record JournalViewDivergence(Slot progress, Slot journal) implements PlanRefusal {
        public JournalViewDivergence {
            Objects.requireNonNull(progress, "progress cannot be null");
            Objects.requireNonNull(journal, "journal cannot be null");
        }
    }

    record ProgressRefusal(ProgressError error) implements PlanRefusal {
        public ProgressRefusal { Objects.requireNonNull(error, "error cannot be null"); }
    }

    record NotPrimary(Ballot view, Optional<NodeId> primary) implements PlanRefusal {
        public NotPrimary {
            Objects.requireNonNull(view, "view cannot be null");
            Objects.requireNonNull(primary, "primary cannot be null");
        }
    }

    record UnexpectedApplied(Optional<Slot> expected, Slot got) implements PlanRefusal {
        public UnexpectedApplied {
            Objects.requireNonNull(expected, "expected cannot be null");
            Objects.requireNonNull(got, "got cannot be null");
        }
    }

    record CheckpointExceedsApplied(Slot applied, Slot through) implements PlanRefusal {
        public CheckpointExceedsApplied {
            Objects.requireNonNull(applied, "applied cannot be null");
            Objects.requireNonNull(through, "through cannot be null");
        }
    }

    record JournalEntryUnavailable(Slot slot) implements PlanRefusal {
        public JournalEntryUnavailable { Objects.requireNonNull(slot, "slot cannot be null"); }
    }

    record SlotSpaceExhausted() implements PlanRefusal {}

    record PlanSubmissionRefused(PlanRejection rejection) implements PlanRefusal {
        public PlanSubmissionRefused { Objects.requireNonNull(rejection, "rejection cannot be null"); }
    }

    record AdminForceViewRefused(String reason) implements PlanRefusal {
        public AdminForceViewRefused { Objects.requireNonNull(reason, "reason cannot be null"); }
    }
}
