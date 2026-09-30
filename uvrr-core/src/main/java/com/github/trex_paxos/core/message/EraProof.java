// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.message;

import com.github.trex_paxos.core.configuration.SystemOperation;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.wire.Pack;
import com.github.trex_paxos.core.wire.PackWriter;
import com.github.trex_paxos.core.wire.UnpackCursor;
import com.github.trex_paxos.core.wire.UnpackException;

import java.util.Objects;

/// Proof the sender is entitled to speak for an era (§8.7.8): the operation
/// that established the era and the slot at which that operation committed.
public record EraProof(
        SystemOperation op,
        Slot committedAt
) implements Pack {
    public EraProof {
        Objects.requireNonNull(op, "op cannot be null");
        Objects.requireNonNull(committedAt, "committedAt cannot be null");
    }

    @Override
    public int packedLen() {
        return op.packedLen() + committedAt.packedLen();
    }

    @Override
    public void pack(PackWriter w) {
        op.pack(w);
        committedAt.pack(w);
    }

    public static EraProof unpack(UnpackCursor c) throws UnpackException {
        SystemOperation op = SystemOperation.unpack(c);
        Slot committedAt = Slot.unpack(c);
        return new EraProof(op, committedAt);
    }
}
