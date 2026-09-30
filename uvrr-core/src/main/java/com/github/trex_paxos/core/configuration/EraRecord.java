// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.configuration;

import com.github.trex_paxos.core.ids.Era;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.quorum.Pivot;
import java.util.Objects;
import java.util.Optional;

/// One era's configuration history record (§8.7.1).
public record EraRecord(
    Era era,
    Configuration config,
    long total,
    Slot establishedBy,
    SystemOperation establishingOperation,
    Optional<Pivot> pivot) {
  public EraRecord {
    Objects.requireNonNull(era, "era");
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(establishedBy, "establishedBy");
    Objects.requireNonNull(establishingOperation, "establishingOperation");
    Objects.requireNonNull(pivot, "pivot");
  }
}
