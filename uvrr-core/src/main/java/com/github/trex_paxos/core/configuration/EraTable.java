// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.configuration;

import com.github.trex_paxos.core.ids.Era;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.quorum.Pivot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// The derived, log-independent record of configuration history (§8.7.1).
/// Retains exactly three eras: {current - 1, current, current + 1}.
public final class EraTable {
  private final List<EraRecord> records;

  public EraTable(List<EraRecord> records) {
    Objects.requireNonNull(records, "records");
    if (records.isEmpty()) {
      throw new IllegalArgumentException("EraTable must hold at least one record");
    }
    this.records = List.copyOf(records);
  }

  /// The void era 0 alone.
  public static EraTable genesis() {
    return new EraTable(List.of(new EraRecord(
        Era.INITIAL,
        Configuration.voidConfig(),
        0L,
        Slot.NONE,
        new SystemOperation.Void(),
        Optional.empty())));
  }

  public List<EraRecord> records() {
    return records;
  }

  public EraRecord current() {
    return records.getLast();
  }

  public Optional<EraRecord> record(Era era) {
    for (int i = records.size() - 1; i >= 0; i--) {
      var r = records.get(i);
      if (r.era().equals(era)) {
        return Optional.of(r);
      }
    }
    return Optional.empty();
  }

  public Optional<EraRecord> get(Era era) {
    return record(era);
  }

  public Optional<EraTable> withTransitionPivot(Era era, Pivot pivot) {
    var newRecords = new ArrayList<>(records);
    for (int i = newRecords.size() - 1; i >= 0; i--) {
      var r = newRecords.get(i);
      if (r.era().equals(era)) {
        newRecords.set(i, new EraRecord(r.era(), r.config(), r.total(), r.establishedBy(), r.establishingOperation(), Optional.of(pivot)));
        return Optional.of(new EraTable(newRecords));
      }
    }
    return Optional.empty();
  }

  public EraTable extend(SystemOperation op, Slot at) throws ConfigException {
    var config = current().config().apply(op, at);
    var era = config.era();
    var newRecord = new EraRecord(era, config, config.total(), at, op, Optional.empty());
    var newRecords = new ArrayList<>(records);
    newRecords.add(newRecord);
    long cutoff = era.value() == 0 ? 0 : era.value() - 1;
    newRecords.removeIf(r -> r.era().value() < cutoff);
    return new EraTable(newRecords);
  }
}
