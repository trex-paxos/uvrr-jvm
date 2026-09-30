// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.observe;

import com.github.trex_paxos.core.configuration.ConfigError;
import com.github.trex_paxos.core.ids.Ballot;
import com.github.trex_paxos.core.ids.Era;
import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.progress.Status;

import java.util.Objects;

/// Why a published transition dropped its peer input, or None.
public sealed interface Diagnostic {

    record None() implements Diagnostic {}

    record UnevaluableEra(Era era) implements Diagnostic {
        public UnevaluableEra { Objects.requireNonNull(era, "era cannot be null"); }
    }

    record SenderNotPrimary(NodeId sender, Ballot view) implements Diagnostic {
        public SenderNotPrimary {
            Objects.requireNonNull(sender, "sender cannot be null");
            Objects.requireNonNull(view, "view cannot be null");
        }
    }

    record ViewMismatch(Ballot got, Ballot current) implements Diagnostic {
        public ViewMismatch {
            Objects.requireNonNull(got, "got cannot be null");
            Objects.requireNonNull(current, "current cannot be null");
        }
    }

    record StatusGate(Ballot got, Ballot current, Status status) implements Diagnostic {
        public StatusGate {
            Objects.requireNonNull(got, "got cannot be null");
            Objects.requireNonNull(current, "current cannot be null");
            Objects.requireNonNull(status, "status cannot be null");
        }
    }

    record EraDiscipline(Era entry, Ballot view) implements Diagnostic {
        public EraDiscipline {
            Objects.requireNonNull(entry, "entry cannot be null");
            Objects.requireNonNull(view, "view cannot be null");
        }
    }

    record PrepareSlotMismatch(Slot header, Slot entry) implements Diagnostic {
        public PrepareSlotMismatch {
            Objects.requireNonNull(header, "header cannot be null");
            Objects.requireNonNull(entry, "entry cannot be null");
        }
    }

    record InvalidSystemOperation(Slot slot, ConfigError error) implements Diagnostic {
        public InvalidSystemOperation {
            Objects.requireNonNull(slot, "slot cannot be null");
            Objects.requireNonNull(error, "error cannot be null");
        }
    }

    record ConflictingEntry(Slot slot) implements Diagnostic {
        public ConflictingEntry { Objects.requireNonNull(slot, "slot cannot be null"); }
    }

    record GapDetected(Slot expected, Slot got) implements Diagnostic {
        public GapDetected {
            Objects.requireNonNull(expected, "expected cannot be null");
            Objects.requireNonNull(got, "got cannot be null");
        }
    }

    record StaleTransfer(NodeId sender, Ballot view) implements Diagnostic {
        public StaleTransfer {
            Objects.requireNonNull(sender, "sender cannot be null");
            Objects.requireNonNull(view, "view cannot be null");
        }
    }

    record MalformedTransfer() implements Diagnostic {}

    record TransferNotServed(NodeId sender, Ballot view) implements Diagnostic {
        public TransferNotServed {
            Objects.requireNonNull(sender, "sender cannot be null");
            Objects.requireNonNull(view, "view cannot be null");
        }
    }

    record PrepareOkNotPrimary(NodeId sender, Slot slot) implements Diagnostic {
        public PrepareOkNotPrimary {
            Objects.requireNonNull(sender, "sender cannot be null");
            Objects.requireNonNull(slot, "slot cannot be null");
        }
    }

    record SlotNotOutstanding(Slot slot, NodeId sender) implements Diagnostic {
        public SlotNotOutstanding {
            Objects.requireNonNull(slot, "slot cannot be null");
            Objects.requireNonNull(sender, "sender cannot be null");
        }
    }

    record DuplicatePrepareOk(Slot slot, NodeId sender) implements Diagnostic {
        public DuplicatePrepareOk {
            Objects.requireNonNull(slot, "slot cannot be null");
            Objects.requireNonNull(sender, "sender cannot be null");
        }
    }

    record UnknownSender(NodeId sender) implements Diagnostic {
        public UnknownSender { Objects.requireNonNull(sender, "sender cannot be null"); }
    }

    record LearnerSender(NodeId sender) implements Diagnostic {
        public LearnerSender { Objects.requireNonNull(sender, "sender cannot be null"); }
    }

    record ReincarnationRefused(NodeId sender, Ballot view) implements Diagnostic {
        public ReincarnationRefused {
            Objects.requireNonNull(sender, "sender cannot be null");
            Objects.requireNonNull(view, "view cannot be null");
        }
    }

    record StaleViewChange(Ballot got, Ballot current) implements Diagnostic {
        public StaleViewChange {
            Objects.requireNonNull(got, "got cannot be null");
            Objects.requireNonNull(current, "current cannot be null");
        }
    }

    record StaleEvidence(Ballot got, Ballot current) implements Diagnostic {
        public StaleEvidence {
            Objects.requireNonNull(got, "got cannot be null");
            Objects.requireNonNull(current, "current cannot be null");
        }
    }

    record EvidenceNotCollected(NodeId sender, Ballot view) implements Diagnostic {
        public EvidenceNotCollected {
            Objects.requireNonNull(sender, "sender cannot be null");
            Objects.requireNonNull(view, "view cannot be null");
        }
    }

    record StartViewNotFromPrimary(NodeId sender, Ballot view) implements Diagnostic {
        public StartViewNotFromPrimary {
            Objects.requireNonNull(sender, "sender cannot be null");
            Objects.requireNonNull(view, "view cannot be null");
        }
    }

    record StartViewFromStaleView(Ballot got, Ballot current) implements Diagnostic {
        public StartViewFromStaleView {
            Objects.requireNonNull(got, "got cannot be null");
            Objects.requireNonNull(current, "current cannot be null");
        }
    }

    record MalformedViewChange() implements Diagnostic {}

    record PlanAborted(int step) implements Diagnostic {}

    record FuseRefusal() implements Diagnostic {}
}
