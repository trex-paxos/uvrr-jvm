// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.invariant;

import com.github.trex_paxos.core.ids.Slot;

/// What a message's header slot may legally carry, per tag (rule 7).
public enum HeaderSlotRole {
    /// The header slot names a log position holding an operation. Slot(0) is illegal.
    OPERATION,
    /// The header slot carries the frontier the message speaks about. Every value is legal.
    FRONTIER,
    /// The header slot carries no meaning and must be the sentinel Slot(0).
    ABSENT;

    /// Whether slot is a legal header value under this role.
    public boolean admits(Slot slot) {
        return switch (this) {
            case OPERATION -> !slot.equals(Slot.NONE);
            case FRONTIER -> true;
            case ABSENT -> slot.equals(Slot.NONE);
        };
    }
}
