// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.wire;

/// Value that can be encoded into the normative big-endian binary format.
public interface Pack {
  /// The exact byte count required to encode this value.
  int packedLen();

  /// Writes this value into w.
  void pack(PackWriter w);

  /// Encodes this value into a freshly allocated byte array.
  default byte[] packToBytes() {
    byte[] buf = new byte[packedLen()];
    PackWriter w = new PackWriter(buf, buf.length);
    pack(w);
    return buf;
  }

  /// Encodes this value into buf.
  default int packInto(byte[] buf) {
    PackWriter w = new PackWriter(buf, packedLen());
    pack(w);
    return w.written();
  }
}
