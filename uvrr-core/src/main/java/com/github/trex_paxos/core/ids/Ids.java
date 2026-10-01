// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.ids;

import java.util.Optional;

/// Helper functions on identity types.
public final class Ids {
  private Ids() {}

  /// Least v' > current with v' % members == index, or empty on View overflow (§8.7.7 step 4).
  public static Optional<View> nextViewSelecting(View current, long index, long members) {
    if (members <= 0 || index < 0 || index >= members) {
      return Optional.empty();
    }
    long floor = current.value() + 1;
    if (floor > 0xFFFF_FFFFL) {
      return Optional.empty();
    }
    long residue = floor % members;
    long gap = (residue <= index) ? (index - residue) : (members - (residue - index));
    long selecting = floor + gap;
    if (selecting > 0xFFFF_FFFFL) {
      return Optional.empty();
    }
    return Optional.of(new View(selecting));
  }
}
