// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.effects;

/// The host's declared durability profile (§7's table).
public enum Stability {
    /// State is installed in process memory; safety relies on VRR-2012 quorum
    /// memory and restart (§7). Effects release at `publish`.
    VOLATILE,
    /// The host accepted a write for later durability; until a barrier
    /// completes, safety remains the Volatile case (§7).
    DEFERRED,
    /// The host confirms a host-selected local crash/power-loss barrier (§7).
    FORCED,
    /// The host confirms its wider transaction, potentially including
    /// application state (§7, §11.1).
    EXTERNAL_TRANSACTION
}
