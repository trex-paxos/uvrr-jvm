// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.replica;

/// The host's view-change knobs (W5).
public record ViewChangeKnobs(
        long primaryTimeout,
        int viewChangeBudget
) {
    public static final ViewChangeKnobs NO_VIEW_CHANGE = new ViewChangeKnobs(0, Integer.MAX_VALUE);
    public static final ViewChangeKnobs INERT = NO_VIEW_CHANGE;
}
