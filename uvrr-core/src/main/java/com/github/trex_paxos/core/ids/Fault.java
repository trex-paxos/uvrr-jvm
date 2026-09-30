// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.ids;

/// Why a node has declared itself unfit to participate (§5 invariant 5, §12).
public enum Fault {
  /// A planned transition would have produced a state unreachable from the current one.
  ILLEGAL_TRANSITION,
  /// A persistence attempt returned an indeterminate outcome (S3).
  INDETERMINATE_PERSISTENCE,
  /// The declared quorum strategy failed a closed intersection obligation (Q1: R1, R2, §8.3).
  QUORUM_OBLIGATION,
  /// Progress and journal disagreed about the accepted frontier.
  PROGRESS_JOURNAL_DIVERGENCE,
  /// The host declared this node unfit (§12).
  HOST_DECLARED;

  /// Always true. Faults are sticky (§5 invariant 5).
  public boolean isSticky() {
    return true;
  }
}
