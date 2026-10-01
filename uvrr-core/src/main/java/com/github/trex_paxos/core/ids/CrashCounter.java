// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.ids;

import java.util.Optional;

/// The crash bump counter: the low half of a lawful node identity (1..65535).
public record CrashCounter(int value) implements Comparable<CrashCounter> {
  public CrashCounter {
    if (value <= 0 || value > 0xFFFF) {
      throw new IllegalArgumentException("CrashCounter must be between 1 and 65535: " + value);
    }
  }

  /// Lawful constructor: zero is refused, returning empty.
  public static Optional<CrashCounter> of(int value) {
    if (value <= 0 || value > 0xFFFF) {
      return Optional.empty();
    }
    return Optional.of(new CrashCounter(value));
  }

  public int get() {
    return value;
  }

  @Override
  public int compareTo(CrashCounter that) {
    return Integer.compare(this.value, that.value);
  }
}
