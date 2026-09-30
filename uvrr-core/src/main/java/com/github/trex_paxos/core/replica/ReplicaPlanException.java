// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.replica;

import java.util.Objects;

/// Exception thrown when planning a transition fails.
public final class ReplicaPlanException extends Exception {

    private final PlanRefusal refusal;

    public ReplicaPlanException(PlanRefusal refusal) {
        super(refusal.toString());
        this.refusal = Objects.requireNonNull(refusal, "refusal cannot be null");
    }

    public PlanRefusal refusal() {
        return refusal;
    }
}
