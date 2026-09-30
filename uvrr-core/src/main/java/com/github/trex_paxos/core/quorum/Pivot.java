// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.quorum;

import com.github.trex_paxos.core.ids.NodeId;
import java.util.List;
import java.util.Objects;

/// The concrete vote sets of a non-stop reconfiguration (§8.7.6–§8.7.7).
public record Pivot(List<NodeId> qI, List<NodeId> qII) {
  public Pivot {
    Objects.requireNonNull(qI, "qI");
    Objects.requireNonNull(qII, "qII");
    qI = List.copyOf(qI);
    qII = List.copyOf(qII);
  }
}
