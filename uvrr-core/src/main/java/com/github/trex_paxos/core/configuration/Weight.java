// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.configuration;

/// Voting weight of one member (§8.4; rules §1).
/// Domain is {0, 1, 2}. Weight 0 is a learner.
public record Weight(int value) implements Comparable<Weight> {
  public static final int MAX_WEIGHT = 2;
  public static final Weight LEARNER = new Weight(0);
  public static final Weight UNIT = new Weight(1);
  public static final Weight DOUBLE = new Weight(2);

  public Weight {
    if (value < 0 || value > MAX_WEIGHT) {
      throw new IllegalArgumentException("Weight must be 0, 1, or 2: " + value);
    }
  }

  public boolean isLearner() {
    return value == 0;
  }

  @Override
  public int compareTo(Weight that) {
    return Integer.compare(this.value, that.value);
  }
}
