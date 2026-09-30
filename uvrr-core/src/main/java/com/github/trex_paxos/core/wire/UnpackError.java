// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.wire;

/// Why an unpack operation could not complete.
public sealed interface UnpackError {
  /// Ran out of input. Needed is total bytes required from buffer start.
  record Incomplete(int needed) implements UnpackError {}

  /// The bytes are not a valid message.
  record MalformedError(Malformed reason) implements UnpackError {}
}
