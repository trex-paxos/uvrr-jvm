// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.timeout;

/// A timeout flavour, one per state.
public enum Timeout {
    CLUSTER("cluster"),
    WITNESS("witness"),
    UNKNOWN("unknown"),
    BOOTED("booted"),
    CRASHED("crashed"),
    STEADY("steady"),
    STOPPING("stopping"),
    STOPPING_NOT_FLUSHED("stopping-not-flushed");

    private final String wireName;

    Timeout(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
