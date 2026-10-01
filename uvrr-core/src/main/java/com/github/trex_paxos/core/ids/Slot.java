// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.ids;

import com.github.trex_paxos.core.wire.Pack;
import com.github.trex_paxos.core.wire.PackWriter;
import com.github.trex_paxos.core.wire.UnpackCursor;
import com.github.trex_paxos.core.wire.UnpackException;

import java.util.Optional;

/// Log position (§1.3). Slot 0 is NONE; history starts at slot 1.
public record Slot(long value) implements Comparable<Slot>, Pack {
  public static final Slot NONE = new Slot(0);

  public Slot {
    if (value < 0) {
      throw new IllegalArgumentException("Slot must be non-negative: " + value);
    }
  }

  /// The next log position, or empty if exhausted.
  public Optional<Slot> next() {
    if (value < Long.MAX_VALUE) {
      return Optional.of(new Slot(value + 1));
    }
    return Optional.empty();
  }

  /// The preceding log position, or empty at Slot.NONE.
  public Optional<Slot> prev() {
    if (value > 0) {
      return Optional.of(new Slot(value - 1));
    }
    return Optional.empty();
  }

  /// The number of positions from this up to other, or empty if other is behind this.
  public Optional<Long> distanceTo(Slot other) {
    if (other.value >= this.value) {
      return Optional.of(other.value - this.value);
    }
    return Optional.empty();
  }

  @Override
  public int compareTo(Slot that) {
    return Long.compare(this.value, that.value);
  }

  @Override
  public int packedLen() {
    return 8;
  }

  @Override
  public void pack(PackWriter w) {
    w.u64(value);
  }

  public static Slot unpack(UnpackCursor c) throws UnpackException {
    return new Slot(c.u64());
  }
}
