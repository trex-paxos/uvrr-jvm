// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.quorum;

import com.github.trex_paxos.core.ids.NodeId;
import java.util.Objects;

/// Why a host-supplied pivot was refused (§8.7.6).
public sealed interface PivotError {
  record DuplicateMember(NodeId node) implements PivotError {
    public DuplicateMember {
      Objects.requireNonNull(node, "node");
    }
  }

  record IntersectionNotLeader() implements PivotError {}
  record LeaderAbsent() implements PivotError {}
  record QiNotLegal() implements PivotError {}
  record QiiNotLegalCurrent() implements PivotError {}
  record QiiNotLegalNext() implements PivotError {}
}
