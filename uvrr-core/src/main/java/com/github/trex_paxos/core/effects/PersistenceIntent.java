// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.effects;

import java.util.Objects;

/// The durability requirement of one published transition (§6, §7).
public record PersistenceIntent(
        long revision,
        ProgressIntent progress,
        JournalIntent journal
) {
    public PersistenceIntent {
        Objects.requireNonNull(progress, "progress cannot be null");
        Objects.requireNonNull(journal, "journal cannot be null");
    }
}
