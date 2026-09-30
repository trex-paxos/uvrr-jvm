// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.timeout;

import java.util.Objects;

/// What the node may do when the waiting time is exceeded.
public sealed interface Opinion {

    record Retransmit() implements Opinion {}

    record DoNothing() implements Opinion {}

    record Heartbeat() implements Opinion {}

    record StartViewChange() implements Opinion {}

    record Sorry(String runbook) implements Opinion {
        public Sorry {
            Objects.requireNonNull(runbook, "runbook cannot be null");
        }
    }
}
