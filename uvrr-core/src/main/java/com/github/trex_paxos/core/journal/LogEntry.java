// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.journal;

import com.github.trex_paxos.core.ids.Era;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.wire.Pack;
import com.github.trex_paxos.core.wire.PackWriter;
import com.github.trex_paxos.core.wire.UnpackCursor;
import com.github.trex_paxos.core.wire.UnpackException;
import java.util.Objects;

/// One accepted protocol history position (§1.3, §8.7.3).
public record LogEntry(Slot slot, Era era, Payload payload) implements Pack {
  public LogEntry {
    Objects.requireNonNull(slot, "slot");
    Objects.requireNonNull(era, "era");
    Objects.requireNonNull(payload, "payload");
  }

  @Override
  public int packedLen() {
    return 8 + 4 + payload.packedLen();
  }

  @Override
  public void pack(PackWriter w) {
    w.u64(slot.value());
    w.u32(era.value());
    payload.pack(w);
  }

  public static LogEntry unpack(UnpackCursor c) throws UnpackException {
    var slot = new Slot(c.u64());
    var era = new Era(c.u32());
    var payload = Payload.unpack(c);
    return new LogEntry(slot, era, payload);
  }
}
