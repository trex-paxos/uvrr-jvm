// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.configuration;

import java.util.Objects;

/// Checked exception thrown when a configuration fold transition is refused.
public final class ConfigException extends Exception {
  private final ConfigError error;

  public ConfigException(ConfigError error) {
    super(error.toString());
    this.error = Objects.requireNonNull(error, "error");
  }

  public ConfigError error() {
    return error;
  }
}
