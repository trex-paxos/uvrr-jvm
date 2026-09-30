// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.journal;

import java.util.Objects;

/// Checked exception thrown when a journal mutation is refused.
public final class JournalException extends Exception {
  private final JournalError error;

  public JournalException(JournalError error) {
    super(error.toString());
    this.error = Objects.requireNonNull(error, "error");
  }

  public JournalError error() {
    return error;
  }
}
