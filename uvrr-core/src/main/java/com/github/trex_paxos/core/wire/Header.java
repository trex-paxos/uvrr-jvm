// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.wire;

import com.github.trex_paxos.core.ids.Ballot;
import com.github.trex_paxos.core.ids.Era;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.ids.View;
import java.util.Objects;

/// The 20-byte prefix of every protocol datagram (W1).
/// Big-endian: (tag: u32, era: u32, view: u32, slot: u64).
public record Header(Tag tag, Ballot view, Slot slot) implements Pack {
  public static final int LEN = 20;

  public Header {
    Objects.requireNonNull(tag, "tag");
    Objects.requireNonNull(view, "view");
    Objects.requireNonNull(slot, "slot");
  }

  @Override
  public int packedLen() {
    return LEN;
  }

  @Override
  public void pack(PackWriter w) {
    w.u32(tag.asU32());
    w.u32(view.era().value());
    w.u32(view.view().value());
    w.u64(slot.value());
  }

  public static Header unpack(UnpackCursor c) throws UnpackException {
    long rawTag = c.u32();
    Tag tag = Tag.fromU32(rawTag)
        .orElseThrow(() -> new UnpackException(new UnpackError.MalformedError(new Malformed.UnknownTag(rawTag))));
    long era = c.u32();
    long view = c.u32();
    long slot = c.u64();
    return new Header(tag, new Ballot(new Era(era), new View(view)), new Slot(slot));
  }
}
