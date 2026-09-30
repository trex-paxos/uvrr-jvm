// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.journal;

import com.github.trex_paxos.core.ids.Slot;

/// Why a journal mutation was refused.
public sealed interface JournalError {
  record NonContiguous(Slot expected, Slot actual) implements JournalError {}
  record SlotOccupied(Slot slot) implements JournalError {}
  record SlotExhausted() implements JournalError {}
  record EmptySuffix() implements JournalError {}
}
