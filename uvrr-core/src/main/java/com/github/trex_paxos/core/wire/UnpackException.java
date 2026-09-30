// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.wire;

import java.util.Objects;

/// Checked exception thrown by unpack operations.
public final class UnpackException extends Exception {
  private final UnpackError error;

  public UnpackException(UnpackError error) {
    super(error.toString());
    this.error = Objects.requireNonNull(error, "error");
  }

  public UnpackError error() {
    return error;
  }
}
