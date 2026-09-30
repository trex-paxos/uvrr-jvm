// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.quorum;

import com.github.trex_paxos.core.configuration.Configuration;
import com.github.trex_paxos.core.ids.NodeId;
import java.util.List;
import java.util.Optional;

/// A quorum policy (Q1).
public interface QuorumStrategy {
  /// Whether members form a quorum for role under config.
  boolean isQuorum(Role role, Configuration config, List<NodeId> members);

  /// Smallest quorum weight threshold for role under config, if threshold-expressible.
  Optional<Long> threshold(Role role, Configuration config);
}
