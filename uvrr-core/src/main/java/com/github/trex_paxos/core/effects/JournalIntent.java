// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.effects;

import com.github.trex_paxos.core.ids.Slot;

import java.util.Objects;

/// Which slots of the journal a transition wrote and therefore owes the
/// stability barrier (§4, §6's journal_change?).
public sealed interface JournalIntent {

    /// The transition wrote no journal slots.
    record Unchanged() implements JournalIntent {}

    /// Acceptance of the contiguous range `from..through` (normal operation).
    record Accept(Slot from, Slot through) implements JournalIntent {
        public Accept {
            Objects.requireNonNull(from, "from cannot be null");
            Objects.requireNonNull(through, "through cannot be null");
        }
    }

    /// A view selection replacing history from `from` through the installed frontier (§4, §9).
    record InstallSuffix(Slot from, Slot through) implements JournalIntent {
        public InstallSuffix {
            Objects.requireNonNull(from, "from cannot be null");
            Objects.requireNonNull(through, "through cannot be null");
        }
    }
}
