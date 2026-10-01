// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.configuration;

import com.github.trex_paxos.core.ids.NodeId;
import java.util.Objects;

/// One member of a configuration: who it is and what it votes with (§8.7.1).
public record Member(NodeId node, Weight weight) {
  public Member {
    Objects.requireNonNull(node, "node");
    Objects.requireNonNull(weight, "weight");
  }
}
