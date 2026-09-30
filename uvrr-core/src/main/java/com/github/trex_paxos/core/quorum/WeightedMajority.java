// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.quorum;

import com.github.trex_paxos.core.configuration.Configuration;
import com.github.trex_paxos.core.ids.NodeId;
import java.util.List;
import java.util.Optional;

/// Strict weighted majority strategy (§8.4).
public final class WeightedMajority implements QuorumStrategy {
  public static final WeightedMajority INSTANCE = new WeightedMajority();

  @Override
  public boolean isQuorum(Role role, Configuration config, List<NodeId> members) {
    long threshold = config.total() / 2 + 1;
    return config.weightOfSet(members)
        .map(w -> w >= threshold)
        .orElse(false);
  }

  @Override
  public Optional<Long> threshold(Role role, Configuration config) {
    return Optional.of(config.total() / 2 + 1);
  }
}
