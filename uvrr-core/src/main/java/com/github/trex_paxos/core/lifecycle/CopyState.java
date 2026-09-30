// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.lifecycle;

import com.github.trex_paxos.core.ids.NodeId;

import java.util.Objects;

/// One superblock copy: the identity it records and its marker.
public record CopyState(NodeId identity, Marker marker) {
    public CopyState {
        Objects.requireNonNull(identity, "identity cannot be null");
        Objects.requireNonNull(marker, "marker cannot be null");
    }
}
