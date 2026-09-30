// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.replica;

import java.util.Objects;

/// Exception thrown when publishing a transition fails.
public final class ReplicaPublishException extends Exception {

    private final PublishRefusal refusal;

    public ReplicaPublishException(PublishRefusal refusal) {
        super(refusal.toString());
        this.refusal = Objects.requireNonNull(refusal, "refusal cannot be null");
    }

    public PublishRefusal refusal() {
        return refusal;
    }
}
