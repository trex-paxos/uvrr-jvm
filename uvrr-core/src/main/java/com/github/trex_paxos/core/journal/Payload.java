// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.journal;

import com.github.trex_paxos.core.configuration.SystemOperation;
import com.github.trex_paxos.core.ids.OperationId;
import com.github.trex_paxos.core.wire.Malformed;
import com.github.trex_paxos.core.wire.Pack;
import com.github.trex_paxos.core.wire.PackWriter;
import com.github.trex_paxos.core.wire.UnpackCursor;
import com.github.trex_paxos.core.wire.UnpackError;
import com.github.trex_paxos.core.wire.UnpackException;
import java.util.Arrays;
import java.util.Objects;

/// The payload of an accepted log entry (§11.1, §8.7.2).
public sealed interface Payload extends Pack {
  int PAYLOAD_OPERATION = 1;
  int PAYLOAD_SYSTEM = 2;

  record OperationPayload(OperationId id, byte[] payload) implements Payload {
    public OperationPayload {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(payload, "payload");
      payload = payload.clone();
    }

    @Override
    public byte[] payload() {
      return payload.clone();
    }

    @Override
    public int packedLen() {
      return 1 + 16 + 4 + payload.length;
    }

    @Override
    public void pack(PackWriter w) {
      w.u8(PAYLOAD_OPERATION);
      w.u128(id.msb(), id.lsb());
      w.opaque(payload);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof OperationPayload that)) return false;
      return id.equals(that.id) && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
      return 31 * id.hashCode() + Arrays.hashCode(payload);
    }
  }

  record SystemPayload(SystemOperation op) implements Payload {
    public SystemPayload {
      Objects.requireNonNull(op, "op");
    }

    @Override
    public int packedLen() {
      return 1 + op.packedLen();
    }

    @Override
    public void pack(PackWriter w) {
      w.u8(PAYLOAD_SYSTEM);
      op.pack(w);
    }
  }

  static Payload unpack(UnpackCursor c) throws UnpackException {
    int disc = c.u8();
    return switch (disc) {
      case PAYLOAD_OPERATION -> {
        var id = c.u128();
        byte[] bytes = c.opaque();
        yield new OperationPayload(id, bytes);
      }
      case PAYLOAD_SYSTEM -> new SystemPayload(SystemOperation.unpack(c));
      default -> throw new UnpackException(new UnpackError.MalformedError(new Malformed.OutOfDomain()));
    };
  }
}
