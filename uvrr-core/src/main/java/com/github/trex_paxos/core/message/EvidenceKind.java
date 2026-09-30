// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.message;

import com.github.trex_paxos.core.wire.Pack;
import com.github.trex_paxos.core.wire.PackWriter;
import com.github.trex_paxos.core.wire.UnpackCursor;
import com.github.trex_paxos.core.wire.UnpackError;
import com.github.trex_paxos.core.wire.UnpackException;

/// Whether view-change evidence is ordinary or planned (§8.7.7).
public enum EvidenceKind implements Pack {
    /// Evidence produced by an ordinary §9.1 view change.
    ORDINARY(1),
    /// Evidence solicited by PlannedViewChange during overlap mode (§8.7.7).
    PLANNED(2);

    private final int discriminant;

    EvidenceKind(int discriminant) {
        this.discriminant = discriminant;
    }

    public int discriminant() {
        return discriminant;
    }

    @Override
    public int packedLen() {
        return 1;
    }

    @Override
    public void pack(PackWriter w) {
        w.u8(discriminant);
    }

    public static EvidenceKind unpack(UnpackCursor c) throws UnpackException {
        int val = c.u8();
        return switch (val) {
            case 1 -> ORDINARY;
            case 2 -> PLANNED;
            default -> throw new UnpackException(new UnpackError.MalformedError(new com.github.trex_paxos.core.wire.Malformed.OutOfDomain()));
        };
    }
}
