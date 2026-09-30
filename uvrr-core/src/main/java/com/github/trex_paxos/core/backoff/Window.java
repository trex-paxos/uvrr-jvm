// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.backoff;

/// The suspicion window for attempt under unitMillis, split into its fixed
/// and uniform-random halves, in milliseconds.
public record Window(long fixedMillis, long jitterMillis) {}
