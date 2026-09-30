// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.lifecycle;

import com.github.trex_paxos.core.ids.NodeId;

import java.util.Objects;

/// What a restart decided.
public sealed interface RestartDecision {

    record Continue(NodeId identity) implements RestartDecision {
        public Continue { Objects.requireNonNull(identity, "identity cannot be null"); }
    }

    record Bump(NodeId oldId, NodeId newId) implements RestartDecision {
        public Bump {
            Objects.requireNonNull(oldId, "oldId cannot be null");
            Objects.requireNonNull(newId, "newId cannot be null");
        }
    }
}
