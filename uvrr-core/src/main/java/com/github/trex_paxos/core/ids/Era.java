// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.ids;

import com.github.trex_paxos.core.wire.Pack;
import com.github.trex_paxos.core.wire.PackWriter;
import com.github.trex_paxos.core.wire.UnpackCursor;
import com.github.trex_paxos.core.wire.UnpackException;

import java.util.Optional;

/// Configuration generation (§8.7.1).
public record Era(long value) implements Comparable<Era>, Pack {
  public static final Era INITIAL = new Era(0);

  public Era {
    if (value < 0 || value > 0xFFFF_FFFFL) {
      throw new IllegalArgumentException("Era must fit in 32 unsigned bits: " + value);
    }
  }

  /// The next configuration generation, or empty if exhausted.
  public Optional<Era> next() {
    if (value < 0xFFFF_FFFFL) {
      return Optional.of(new Era(value + 1));
    }
    return Optional.empty();
  }

  @Override
  public int compareTo(Era that) {
    return Long.compare(this.value, that.value);
  }

  @Override
  public int packedLen() {
    return 4;
  }

  @Override
  public void pack(PackWriter w) {
    w.u32(value);
  }

  public static Era unpack(UnpackCursor c) throws UnpackException {
    return new Era(c.u32());
  }
}
