// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.lifecycle;

/// The quorum read's verdict.
public enum RestartClass {
    /// 2-of-4 copies hold Marker.STOPPED.
    STOPPED,
    /// No stopped quorum.
    NOT_STOPPED
}
