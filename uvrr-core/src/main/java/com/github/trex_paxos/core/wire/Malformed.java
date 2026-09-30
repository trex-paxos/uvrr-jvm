// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.wire;

/// The ways a byte sequence fails to be a well-formed protocol message.
public sealed interface Malformed {
  /// The tag was outside the known Tag table.
  record UnknownTag(long tag) implements Malformed {}

  /// A length prefix exceeded valid bounds.
  record LengthPrefixOverflow() implements Malformed {}

  /// Unconsumed trailing bytes remained after decoding.
  record TrailingBytes(int unread) implements Malformed {}

  /// A field required to be zero was non-zero.
  record ReservedFieldNonZero() implements Malformed {}

  /// A field had an invalid value outside its defined domain (e.g. invalid boolean or enum).
  record OutOfDomain() implements Malformed {}
}
