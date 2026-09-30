// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.solver;

import java.util.Objects;

/// Exception thrown when solving a reconfiguration schedule fails.
public final class SolveException extends Exception {

    private final SolveError error;

    public SolveException(SolveError error) {
        super(error.toString());
        this.error = Objects.requireNonNull(error, "error cannot be null");
    }

    public SolveError error() {
        return error;
    }
}
