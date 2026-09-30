// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.ids;

import com.github.trex_paxos.core.wire.Pack;
import com.github.trex_paxos.core.wire.PackWriter;
import com.github.trex_paxos.core.wire.UnpackCursor;
import com.github.trex_paxos.core.wire.UnpackException;

/// An operation's identity at the application boundary (§11.1, B2): 128 host-assigned bits.
public record OperationId(long msb, long lsb) implements Comparable<OperationId>, Pack {
  @Override
  public int compareTo(OperationId that) {
    int cmp = Long.compareUnsigned(this.msb, that.msb);
    if (cmp != 0) {
      return cmp;
    }
    return Long.compareUnsigned(this.lsb, that.lsb);
  }

  @Override
  public int packedLen() {
    return 16;
  }

  @Override
  public void pack(PackWriter w) {
    w.u64(msb);
    w.u64(lsb);
  }

  public static OperationId unpack(UnpackCursor c) throws UnpackException {
    long msb = c.u64();
    long lsb = c.u64();
    return new OperationId(msb, lsb);
  }
}
