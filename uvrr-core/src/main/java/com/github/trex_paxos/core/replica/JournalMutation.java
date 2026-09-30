// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.replica;

import com.github.trex_paxos.core.effects.JournalIntent;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.journal.LogEntry;

import java.util.List;
import java.util.Objects;

/// The journal half of a planned transition (§4's two mutations, or none).
public sealed interface JournalMutation {

    record None() implements JournalMutation {}

    record Accept(List<LogEntry> entries) implements JournalMutation {
        public Accept {
            Objects.requireNonNull(entries, "entries cannot be null");
            entries = List.copyOf(entries);
        }
    }

    record InstallSuffix(Slot from, List<LogEntry> suffix) implements JournalMutation {
        public InstallSuffix {
            Objects.requireNonNull(from, "from cannot be null");
            Objects.requireNonNull(suffix, "suffix cannot be null");
            suffix = List.copyOf(suffix);
        }
    }

    default JournalIntent intent() {
        return switch (this) {
            case None _ -> new JournalIntent.Unchanged();
            case Accept(var entries) -> {
                if (entries.isEmpty()) {
                    yield new JournalIntent.Unchanged();
                } else {
                    yield new JournalIntent.Accept(entries.getFirst().slot(), entries.getLast().slot());
                }
            }
            case InstallSuffix(var from, var suffix) -> {
                Slot through = suffix.isEmpty() ? from : suffix.getLast().slot();
                yield new JournalIntent.InstallSuffix(from, through);
            }
        };
    }
}
