// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.lifecycle;

import com.github.trex_paxos.core.ids.NodeId;

import java.util.Objects;

/// Why a restart refused.
public sealed interface RestartRefusal {

    record QuorumLost() implements RestartRefusal {}

    record Exhausted(NodeId identity) implements RestartRefusal {
        public Exhausted { Objects.requireNonNull(identity, "identity cannot be null"); }
    }
}
