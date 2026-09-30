// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.quorum;

import com.github.trex_paxos.core.ids.NodeId;
import java.util.List;
import java.util.Objects;

/// Why a configuration or proposed reconfiguration was refused by the quorum gate.
public sealed interface QuorumError {
  record R1Violation(List<NodeId> commit, List<NodeId> viewChange) implements QuorumError {
    public R1Violation {
      commit = List.copyOf(Objects.requireNonNull(commit, "commit"));
      viewChange = List.copyOf(Objects.requireNonNull(viewChange, "viewChange"));
    }
  }

  record R2Violation(R2Direction direction, List<NodeId> viewChange, List<NodeId> commit) implements QuorumError {
    public R2Violation {
      Objects.requireNonNull(direction, "direction");
      viewChange = List.copyOf(Objects.requireNonNull(viewChange, "viewChange"));
      commit = List.copyOf(Objects.requireNonNull(commit, "commit"));
    }
  }

  record SelfIntersectionViolation(Role role, List<NodeId> first, List<NodeId> second) implements QuorumError {
    public SelfIntersectionViolation {
      Objects.requireNonNull(role, "role");
      first = List.copyOf(Objects.requireNonNull(first, "first"));
      second = List.copyOf(Objects.requireNonNull(second, "second"));
    }
  }

  record FenceRestartViolation(List<NodeId> fence, List<NodeId> restart) implements QuorumError {
    public FenceRestartViolation {
      fence = List.copyOf(Objects.requireNonNull(fence, "fence"));
      restart = List.copyOf(Objects.requireNonNull(restart, "restart"));
    }
  }

  record MembershipCapExceeded(int cap) implements QuorumError {}
}
