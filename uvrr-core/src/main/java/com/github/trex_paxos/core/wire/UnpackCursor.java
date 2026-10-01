// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.wire;

import com.github.trex_paxos.core.ids.OperationId;
import java.util.Objects;

/// Sequential big-endian reader over a byte slice.
public final class UnpackCursor {
  private final byte[] buf;
  private int at;

  public UnpackCursor(byte[] buf) {
    this.buf = Objects.requireNonNull(buf, "buf");
    this.at = 0;
  }

  public int at() {
    return at;
  }

  public int remaining() {
    return buf.length - at;
  }

  public void ensure(int count) throws UnpackException {
    if (count < 0 || at + count > buf.length) {
      throw new UnpackException(new UnpackError.Incomplete(at + count));
    }
  }

  public int u8() throws UnpackException {
    ensure(1);
    return buf[at++] & 0xFF;
  }

  public int u16() throws UnpackException {
    ensure(2);
    int b0 = buf[at++] & 0xFF;
    int b1 = buf[at++] & 0xFF;
    return (b0 << 8) | b1;
  }

  public long u32() throws UnpackException {
    ensure(4);
    long b0 = buf[at++] & 0xFFL;
    long b1 = buf[at++] & 0xFFL;
    long b2 = buf[at++] & 0xFFL;
    long b3 = buf[at++] & 0xFFL;
    return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
  }

  public long u64() throws UnpackException {
    ensure(8);
    long b0 = buf[at++] & 0xFFL;
    long b1 = buf[at++] & 0xFFL;
    long b2 = buf[at++] & 0xFFL;
    long b3 = buf[at++] & 0xFFL;
    long b4 = buf[at++] & 0xFFL;
    long b5 = buf[at++] & 0xFFL;
    long b6 = buf[at++] & 0xFFL;
    long b7 = buf[at++] & 0xFFL;
    return (b0 << 56) | (b1 << 48) | (b2 << 40) | (b3 << 32)
        | (b4 << 24) | (b5 << 16) | (b6 << 8) | b7;
  }

  public OperationId u128() throws UnpackException {
    long msb = u64();
    long lsb = u64();
    return new OperationId(msb, lsb);
  }

  public boolean bool() throws UnpackException {
    int val = u8();
    if (val == 0) return false;
    if (val == 1) return true;
    throw new UnpackException(new UnpackError.MalformedError(new Malformed.OutOfDomain()));
  }

  public byte[] bytes(int len) throws UnpackException {
    ensure(len);
    byte[] res = new byte[len];
    System.arraycopy(buf, at, res, 0, len);
    at += len;
    return res;
  }

  public byte[] opaque() throws UnpackException {
    long len = u32();
    if (len > Integer.MAX_VALUE) {
      throw new UnpackException(new UnpackError.MalformedError(new Malformed.LengthPrefixOverflow()));
    }
    int ilen = (int) len;
    ensure(ilen);
    return bytes(ilen);
  }

  public void ensureExhausted() throws UnpackException {
    if (at < buf.length) {
      throw new UnpackException(new UnpackError.MalformedError(new Malformed.TrailingBytes(buf.length - at)));
    }
  }
}
