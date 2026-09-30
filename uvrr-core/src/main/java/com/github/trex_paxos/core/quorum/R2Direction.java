// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.quorum;

/// Which direction of an era boundary a cross-era R2 violation occurred on (§8.7.4).
public enum R2Direction {
  /// ViewChange_current ⌢ Commit_next
  FORWARD,
  /// ViewChange_next ⌢ Commit_current
  REVERSE
}
