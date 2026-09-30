// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.journal;

import com.github.trex_paxos.core.ids.Slot;
import java.util.List;

/// The write side of the journal: append entries and install view selections (§4).
public interface Journal {
  /// Obtains the snapshot a transition will be planned against.
  JournalView view();

  /// Appends contiguous entries to the tail of the journal.
  void accept(List<LogEntry> entries) throws JournalException;

  /// Logically installs the suffix chosen by a view change.
  void installSuffix(Slot from, List<LogEntry> suffix) throws JournalException;
}
