// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.ids;

import com.github.trex_paxos.core.wire.Pack;
import com.github.trex_paxos.core.wire.PackWriter;
import com.github.trex_paxos.core.wire.UnpackCursor;
import com.github.trex_paxos.core.wire.UnpackException;

import java.util.Optional;

/// Primary-succession number within a configuration (§1.2).
public record View(long value) implements Comparable<View>, Pack {
  public static final View INITIAL = new View(0);

  public View {
    if (value < 0 || value > 0xFFFF_FFFFL) {
      throw new IllegalArgumentException("View must fit in 32 unsigned bits: " + value);
    }
  }

  /// The immediately following view, or empty if exhausted.
  public Optional<View> next() {
    if (value < 0xFFFF_FFFFL) {
      return Optional.of(new View(value + 1));
    }
    return Optional.empty();
  }

  @Override
  public int compareTo(View that) {
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

  public static View unpack(UnpackCursor c) throws UnpackException {
    return new View(c.u32());
  }
}
