// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.wire;

import java.util.Objects;

/// Exception thrown when a pack buffer is too small.
public final class PackException extends RuntimeException {
  private final PackError error;

  public PackException(PackError error) {
    super(error.toString());
    this.error = Objects.requireNonNull(error, "error");
  }

  public PackError error() {
    return error;
  }
}
