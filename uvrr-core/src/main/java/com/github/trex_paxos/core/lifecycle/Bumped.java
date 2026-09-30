// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.lifecycle;

import com.github.trex_paxos.core.ids.NodeId;

import java.util.Objects;

/// The crashed classification's replacement pair.
public record Bumped(NodeId oldId, NodeId newId) {
    public Bumped {
        Objects.requireNonNull(oldId, "oldId cannot be null");
        Objects.requireNonNull(newId, "newId cannot be null");
    }
}
