// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.ids;

import com.github.trex_paxos.core.wire.Pack;
import com.github.trex_paxos.core.wire.PackWriter;
import com.github.trex_paxos.core.wire.UnpackCursor;
import com.github.trex_paxos.core.wire.UnpackException;

import java.util.Objects;
import java.util.Optional;

/// Replica identity within a configuration: packed 32-bit value.
/// High 16 bits: SystemId, Low 16 bits: CrashCounter.
public record NodeId(long value) implements Comparable<NodeId>, Pack {
  public NodeId {
    if (value < 0 || value > 0xFFFF_FFFFL) {
      throw new IllegalArgumentException("NodeId must fit in 32 unsigned bits: " + value);
    }
  }

  /// Lawful constructor from durable system id and crash counter.
  public NodeId(SystemId system, CrashCounter crash) {
    this((((long) Objects.requireNonNull(system, "system").get()) << 16)
        | Objects.requireNonNull(crash, "crash").get());
  }

  /// Decodes raw 32-bit integer.
  public static NodeId fromU32(long value) {
    return new NodeId(value & 0xFFFF_FFFFL);
  }

  /// The system half of the pair, or empty if high 16 bits are zero.
  public Optional<SystemId> systemId() {
    return SystemId.of((int) ((value >>> 16) & 0xFFFF));
  }

  /// The crash counter half of the pair, or empty if low 16 bits are zero.
  public Optional<CrashCounter> crashCounter() {
    return CrashCounter.of((int) (value & 0xFFFF));
  }

  /// Whether the bit pattern is a lawful identity: both halves non-zero.
  public boolean isLawful() {
    return ((value >>> 16) & 0xFFFF) != 0 && (value & 0xFFFF) != 0;
  }

  /// The next life of the same system: crash counter incremented.
  public Optional<NodeId> nextLife() {
    var sysOpt = systemId();
    var crashOpt = crashCounter();
    if (sysOpt.isEmpty() || crashOpt.isEmpty()) {
      return Optional.empty();
    }
    int nextCrash = crashOpt.get().get() + 1;
    if (nextCrash > 0xFFFF) {
      return Optional.empty();
    }
    return Optional.of(new NodeId(sysOpt.get(), new CrashCounter(nextCrash)));
  }

  @Override
  public int compareTo(NodeId that) {
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

  public static NodeId unpack(UnpackCursor c) throws UnpackException {
    return new NodeId(c.u32());
  }

  @Override
  public String toString() {
    return String.format("%05d%05d", (int) ((value >>> 16) & 0xFFFF), (int) (value & 0xFFFF));
  }
}
