// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.quorum;

/// Quorum family roles (§8.7.4, §8.3).
public enum Role {
  /// QII: normal-operation commit family (§8.7.4).
  COMMIT,
  /// QI: view-change family (§8.7.4).
  VIEW_CHANGE,
  /// R_g: restart family (§8.3).
  RESTART,
  /// F_g: StartViewChange fence family (§8.3).
  FENCE
}
