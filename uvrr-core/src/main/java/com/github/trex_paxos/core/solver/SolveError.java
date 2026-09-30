// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.solver;

import com.github.trex_paxos.core.configuration.ConfigError;

import java.util.Objects;

/// Why no executable weighted-majority plan was returned.
public sealed interface SolveError {

    record NoAvailableMajority(boolean target, long available, long total) implements SolveError {}

    record ConfigurationError(ConfigError error) implements SolveError {
        public ConfigurationError {
            Objects.requireNonNull(error, "error cannot be null");
        }
    }

    record InvalidReplacement() implements SolveError {}
}
