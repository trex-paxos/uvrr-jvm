// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.journal;

import com.github.trex_paxos.core.ids.Slot;
import java.util.List;
import java.util.Optional;

/// The read-only snapshot handed to a planned transition (§4).
public interface JournalView {
  record RetainedBounds(Slot first, Slot last) {}

  /// Greatest accepted slot, or empty before genesis (§1.3).
  Optional<Slot> accepted();

  /// The entry at slot, or empty if absent or not retained.
  Optional<LogEntry> get(Slot slot);

  /// Ordered iteration over a slot range, clipped to retained entries.
  List<LogEntry> iterRange(Slot from, Slot to);

  /// Physical presence bounds: first and last slot held.
  RetainedBounds retained();

  /// Copies a slot range into into.
  RangeOutcome copyOut(Slot from, Slot to, List<LogEntry> into);
}
