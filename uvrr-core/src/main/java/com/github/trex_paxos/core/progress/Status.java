// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.progress;

import java.util.Optional;

/// Process-control status of the replica (§1.3).
public enum Status {
  /// Actively participating; requires current == retained (§1.3).
  NORMAL(0, "normal"),
  /// A view change is in progress; requires current >= retained (§1.3).
  VIEW_CHANGE(1, "view_change"),
  /// A node restarting with persisted knowledge.
  RESTARTING(2, "restarting"),
  /// Re-applying a transferred or restored history.
  REPLAYING(3, "replaying"),
  /// A node joining the cluster fresh (provision).
  JOINING(4, "joining");

  private final int word;
  private final String statusName;

  Status(int word, String statusName) {
    this.word = word;
    this.statusName = statusName;
  }

  public int toWord() {
    return word;
  }

  public String statusName() {
    return statusName;
  }

  public static Optional<Status> fromWord(long word) {
    if (word == 0) return Optional.of(NORMAL);
    if (word == 1) return Optional.of(VIEW_CHANGE);
    if (word == 2) return Optional.of(RESTARTING);
    if (word == 3) return Optional.of(REPLAYING);
    if (word == 4) return Optional.of(JOINING);
    return Optional.empty();
  }
}
