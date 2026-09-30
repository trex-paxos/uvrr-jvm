// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.configuration;

import java.util.List;
import java.util.Objects;

/// One committed era of a planned reconfiguration.
public record EraStep(List<SystemOperation> ops, Configuration config) {
  public EraStep {
    Objects.requireNonNull(ops, "ops");
    Objects.requireNonNull(config, "config");
    ops = List.copyOf(ops);
  }
}
