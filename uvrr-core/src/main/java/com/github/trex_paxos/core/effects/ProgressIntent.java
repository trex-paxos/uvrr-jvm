// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.effects;

/// Which part of the durable Progress record a transition changed and
/// therefore owes the stability barrier (§5, §6's progress_change?).
public enum ProgressIntent {
    /// The transition leaves the durable progress fields unchanged.
    UNCHANGED,
    /// The full progress record must reach the declared stability level before
    /// the transition's other effects may be released.
    RECORD
}
