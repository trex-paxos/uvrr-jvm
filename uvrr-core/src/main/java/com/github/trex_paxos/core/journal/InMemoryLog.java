// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.journal;

import com.github.trex_paxos.core.configuration.Configuration;
import com.github.trex_paxos.core.ids.Slot;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/// Thread-safe in-memory log implementing Journal and JournalView.
public final class InMemoryLog implements Journal, JournalView {
  private final TreeMap<Slot, LogEntry> entries;

  public InMemoryLog() {
    this.entries = new TreeMap<>();
  }

  public InMemoryLog(TreeMap<Slot, LogEntry> entries) {
    this.entries = new TreeMap<>(entries);
  }

  @Override
  public synchronized JournalView view() {
    return new InMemoryLog(new TreeMap<>(this.entries));
  }

  @Override
  public synchronized Optional<Slot> accepted() {
    if (entries.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(entries.lastKey());
  }

  @Override
  public synchronized Optional<LogEntry> get(Slot slot) {
    Objects.requireNonNull(slot, "slot");
    return Optional.ofNullable(entries.get(slot));
  }

  @Override
  public synchronized List<LogEntry> iterRange(Slot from, Slot to) {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (from.compareTo(to) > 0 || entries.isEmpty()) {
      return List.of();
    }
    return List.copyOf(entries.subMap(from, true, to, true).values());
  }

  @Override
  public synchronized RetainedBounds retained() {
    if (entries.isEmpty()) {
      return new RetainedBounds(Slot.NONE, Slot.NONE);
    }
    return new RetainedBounds(entries.firstKey(), entries.lastKey());
  }

  @Override
  public synchronized RangeOutcome copyOut(Slot from, Slot to, List<LogEntry> into) {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(into, "into");
    if (entries.isEmpty()) {
      return new RangeOutcome.Complete();
    }
    Slot first = entries.firstKey();
    if (from.compareTo(first) < 0) {
      return new RangeOutcome.UnavailablePrefix(from, first);
    }
    if (from.compareTo(to) <= 0) {
      into.addAll(entries.subMap(from, true, to, true).values());
    }
    return new RangeOutcome.Complete();
  }

  @Override
  public synchronized void accept(List<LogEntry> newEntries) throws JournalException {
    Objects.requireNonNull(newEntries, "newEntries");
    if (newEntries.isEmpty()) {
      return;
    }
    Slot expected = accepted().flatMap(Slot::next).orElse(Configuration.VOID_SLOT);
    if (!newEntries.get(0).slot().equals(expected)) {
      throw new JournalException(new JournalError.NonContiguous(expected, newEntries.get(0).slot()));
    }
    Slot prev = newEntries.get(0).slot();
    for (int i = 1; i < newEntries.size(); i++) {
      Slot curr = newEntries.get(i).slot();
      var nextExpected = prev.next();
      if (nextExpected.isEmpty() || !curr.equals(nextExpected.get())) {
        throw new JournalException(new JournalError.NonContiguous(prev.next().orElse(Slot.NONE), curr));
      }
      prev = curr;
    }
    for (LogEntry entry : newEntries) {
      entries.put(entry.slot(), entry);
    }
  }

  @Override
  public synchronized void installSuffix(Slot from, List<LogEntry> suffix) throws JournalException {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(suffix, "suffix");
    if (suffix.isEmpty()) {
      throw new JournalException(new JournalError.EmptySuffix());
    }
    Slot expected;
    if (accepted().isPresent() && from.compareTo(accepted().get()) > 0) {
      expected = accepted().get().next().orElseThrow(() -> new JournalException(new JournalError.SlotExhausted()));
    } else {
      expected = from;
    }
    for (LogEntry entry : suffix) {
      if (entry.slot().compareTo(expected) < 0) {
        throw new JournalException(new JournalError.SlotOccupied(entry.slot()));
      }
      if (!entry.slot().equals(expected)) {
        throw new JournalException(new JournalError.NonContiguous(expected, entry.slot()));
      }
      expected = entry.slot().next().orElseThrow(() -> new JournalException(new JournalError.SlotExhausted()));
    }
    // Truncate uncommitted tail from 'from' onward
    entries.tailMap(from, true).clear();
    for (LogEntry entry : suffix) {
      entries.put(entry.slot(), entry);
    }
  }
}
