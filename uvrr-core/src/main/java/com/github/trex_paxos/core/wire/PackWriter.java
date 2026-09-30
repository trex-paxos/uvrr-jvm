// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.wire;

import java.util.Objects;

/// Sequential big-endian writer into a pre-allocated byte buffer.
public final class PackWriter {
  private final byte[] buf;
  private int at;

  public PackWriter(byte[] buf, int needed) {
    Objects.requireNonNull(buf, "buf");
    if (buf.length < needed) {
      throw new PackException(new PackError.BufferTooSmall(needed, buf.length));
    }
    this.buf = buf;
    this.at = 0;
  }

  public int written() {
    return at;
  }

  public void u8(int value) {
    buf[at++] = (byte) value;
  }

  public void u16(int value) {
    buf[at++] = (byte) (value >>> 8);
    buf[at++] = (byte) value;
  }

  public void u32(long value) {
    buf[at++] = (byte) (value >>> 24);
    buf[at++] = (byte) (value >>> 16);
    buf[at++] = (byte) (value >>> 8);
    buf[at++] = (byte) value;
  }

  public void u64(long value) {
    buf[at++] = (byte) (value >>> 56);
    buf[at++] = (byte) (value >>> 48);
    buf[at++] = (byte) (value >>> 40);
    buf[at++] = (byte) (value >>> 32);
    buf[at++] = (byte) (value >>> 24);
    buf[at++] = (byte) (value >>> 16);
    buf[at++] = (byte) (value >>> 8);
    buf[at++] = (byte) value;
  }

  public void u128(long msb, long lsb) {
    u64(msb);
    u64(lsb);
  }

  public void bool(boolean value) {
    u8(value ? 1 : 0);
  }

  public void bytes(byte[] src) {
    Objects.requireNonNull(src, "src");
    System.arraycopy(src, 0, buf, at, src.length);
    at += src.length;
  }

  public void opaque(byte[] value) {
    Objects.requireNonNull(value, "value");
    u32(value.length);
    bytes(value);
  }
}
