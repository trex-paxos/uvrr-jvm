// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos;

public enum NoOperation implements AbstractCommand {
  NOOP;

  @Override
  public String toString() {
    return "NOOP";
  }
}
