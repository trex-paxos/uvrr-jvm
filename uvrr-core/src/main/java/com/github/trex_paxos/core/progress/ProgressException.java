// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.progress;

import java.util.Objects;

/// Checked exception thrown when a progress transition or construction is refused.
public final class ProgressException extends Exception {
  private final ProgressError error;

  public ProgressException(ProgressError error) {
    super(error.toString());
    this.error = Objects.requireNonNull(error, "error");
  }

  public ProgressError error() {
    return error;
  }
}
