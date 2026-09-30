// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.lifecycle;

/// Proof that the engine itself observed the reincarnated node seated.
public final class Rejoined {

    private static final Rejoined INSTANCE = new Rejoined();

    private Rejoined() {}

    /// The one minter: the replica's seated observation.
    public static Rejoined mint() {
        return INSTANCE;
    }
}
