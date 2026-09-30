// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.lifecycle;

import com.github.trex_paxos.core.ids.NodeId;

import java.util.Objects;

/// Proof that a quorum read vouched the previous process's drain.
public record Vouched(NodeId identity) {
    public Vouched {
        Objects.requireNonNull(identity, "identity cannot be null");
    }
}
