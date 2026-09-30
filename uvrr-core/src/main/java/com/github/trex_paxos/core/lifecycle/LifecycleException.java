// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.lifecycle;

import java.util.Objects;

/// Exception thrown when a lifecycle operation fails.
public final class LifecycleException extends Exception {

    private final RestartRefusal refusal;

    public LifecycleException(RestartRefusal refusal) {
        super(refusal.toString());
        this.refusal = Objects.requireNonNull(refusal, "refusal cannot be null");
    }

    public RestartRefusal refusal() {
        return refusal;
    }
}
