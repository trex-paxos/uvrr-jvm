// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.ids;

import java.util.Optional;

/// The sysadmin-assigned system identifier: the durable high half of a lawful node
/// identity. Zero is unrepresentable (1..65535).
public record SystemId(int value) implements Comparable<SystemId> {
  public SystemId {
    if (value <= 0 || value > 0xFFFF) {
      throw new IllegalArgumentException("SystemId must be between 1 and 65535: " + value);
    }
  }

  /// Lawful constructor: zero is refused, returning empty.
  public static Optional<SystemId> of(int value) {
    if (value <= 0 || value > 0xFFFF) {
      return Optional.empty();
    }
    return Optional.of(new SystemId(value));
  }

  public int get() {
    return value;
  }

  @Override
  public int compareTo(SystemId that) {
    return Integer.compare(this.value, that.value);
  }
}
