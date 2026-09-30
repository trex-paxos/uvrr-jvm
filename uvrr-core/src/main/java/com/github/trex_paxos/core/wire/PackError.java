// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.wire;

/// Why a pack operation could not complete.
public sealed interface PackError {
  /// The destination buffer was smaller than needed.
  record BufferTooSmall(int needed, int provided) implements PackError {}
}
