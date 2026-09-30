// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.ids;

import com.github.trex_paxos.core.wire.Pack;
import com.github.trex_paxos.core.wire.PackWriter;
import com.github.trex_paxos.core.wire.UnpackCursor;
import com.github.trex_paxos.core.wire.UnpackException;

/// Host-supplied event value carried by every input (§6.1, S4).
public record Tick(long value) implements Comparable<Tick>, Pack {
  public Tick {
    if (value < 0) {
      throw new IllegalArgumentException("Tick must be non-negative: " + value);
    }
  }

  @Override
  public int compareTo(Tick that) {
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

  public static Tick unpack(UnpackCursor c) throws UnpackException {
    return new Tick(c.u64());
  }
}
