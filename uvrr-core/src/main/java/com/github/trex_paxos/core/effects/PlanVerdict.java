// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.effects;

/// The leader's verdict on an admin submission
/// (`docs/uvrr-protocols.md`, the solver chapter): accepted, or rejected with
/// the named reason.
public sealed interface PlanVerdict {

    /// The submission was accepted.
    record Accepted() implements PlanVerdict {}

    /// The submission was refused; the reason names why.
    record Rejected(String reason) implements PlanVerdict {
        public Rejected {
            if (reason == null) {
                throw new NullPointerException("reason cannot be null");
            }
        }
    }
}
