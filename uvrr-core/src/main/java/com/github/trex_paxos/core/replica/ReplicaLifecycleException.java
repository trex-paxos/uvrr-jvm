// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.replica;

import java.util.Objects;

/// Exception thrown when lifecycle operation fails.
public final class ReplicaLifecycleException extends Exception {

    private final LifecycleRefusal refusal;

    public ReplicaLifecycleException(LifecycleRefusal refusal) {
        super(refusal.toString());
        this.refusal = Objects.requireNonNull(refusal, "refusal cannot be null");
    }

    public LifecycleRefusal refusal() {
        return refusal;
    }
}
