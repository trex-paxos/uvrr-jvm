// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.journal;

import com.github.trex_paxos.core.ids.Slot;

/// Outcome of copying out a slot range from the journal.
public sealed interface RangeOutcome {
  record Complete() implements RangeOutcome {}
  record UnavailablePrefix(Slot requested, Slot retainedFirst) implements RangeOutcome {}
}
