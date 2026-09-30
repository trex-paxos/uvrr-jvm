// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.replica;

import com.github.trex_paxos.core.ids.Tick;

import java.util.Objects;

/// One host event with the host tick attached (§6, S4).
public record TimedInput(Tick at, Input event) {
    public TimedInput {
        Objects.requireNonNull(at, "at cannot be null");
        Objects.requireNonNull(event, "event cannot be null");
    }
}
