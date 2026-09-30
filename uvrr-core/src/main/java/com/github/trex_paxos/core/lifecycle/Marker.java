// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.lifecycle;

/// One superblock copy's marker.
public enum Marker {
    /// The stop command was received.
    STOPPING,
    /// The Stopping -> drain -> Stopped transition completed.
    STOPPED,
    /// The Stopped -> boot, 2-of-4 -> Restarting transition completed.
    RESTARTING,
    /// The crash -> boot, no 2-of-4 Stopped -> Joining transition completed.
    JOINING
}
