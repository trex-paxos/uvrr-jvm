// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.ids;

import java.util.Arrays;
import java.util.Objects;

/// One host operation at the application boundary (§11.1, B2).
public record Operation(OperationId id, byte[] payload) {
  public Operation {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(payload, "payload");
    payload = payload.clone();
  }

  @Override
  public byte[] payload() {
    return payload.clone();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Operation that)) return false;
    return id.equals(that.id) && Arrays.equals(payload, that.payload);
  }

  @Override
  public int hashCode() {
    return 31 * id.hashCode() + Arrays.hashCode(payload);
  }

  @Override
  public String toString() {
    return "Operation[id=" + id + ", payloadLen=" + payload.length + "]";
  }
}
