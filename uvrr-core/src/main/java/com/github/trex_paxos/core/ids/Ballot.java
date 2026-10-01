// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.ids;

import com.github.trex_paxos.core.wire.Pack;
import com.github.trex_paxos.core.wire.PackWriter;
import com.github.trex_paxos.core.wire.UnpackCursor;
import com.github.trex_paxos.core.wire.UnpackException;

import java.util.Objects;
import java.util.Optional;

/// A view together with the configuration generation that authorises it (§1.2, §8.7.3, W1).
public record Ballot(Era era, View view) implements Comparable<Ballot>, Pack {
  public static final Ballot INITIAL = new Ballot(Era.INITIAL, View.INITIAL);

  public Ballot {
    Objects.requireNonNull(era, "era");
    Objects.requireNonNull(view, "view");
  }

  /// Next view in the same era, or empty on view overflow.
  public Optional<Ballot> nextInEra() {
    return view.next().map(v -> new Ballot(this.era, v));
  }

  /// Next view in the next era, or empty on either overflow.
  public Optional<Ballot> nextInNextEra() {
    return era.next().flatMap(e -> view.next().map(v -> new Ballot(e, v)));
  }

  /// Whether next is a legal successor of this (§8.7.3: view strictly up, era equal or +1).
  public boolean isLegalSuccessor(Ballot next) {
    boolean viewAdvances = next.view.compareTo(this.view) > 0;
    boolean eraLegal = this.era.next()
        .map(eraPlusOne -> next.era.equals(this.era) || next.era.equals(eraPlusOne))
        .orElseGet(() -> next.era.equals(this.era));
    return viewAdvances && eraLegal;
  }

  @Override
  public int compareTo(Ballot that) {
    int cmp = this.era.compareTo(that.era);
    if (cmp != 0) {
      return cmp;
    }
    return this.view.compareTo(that.view);
  }

  @Override
  public int packedLen() {
    return era.packedLen() + view.packedLen();
  }

  @Override
  public void pack(PackWriter w) {
    era.pack(w);
    view.pack(w);
  }

  public static Ballot unpack(UnpackCursor c) throws UnpackException {
    return new Ballot(Era.unpack(c), View.unpack(c));
  }
}
