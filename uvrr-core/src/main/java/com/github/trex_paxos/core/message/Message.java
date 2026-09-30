// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.message;

import com.github.trex_paxos.core.wire.Header;
import com.github.trex_paxos.core.wire.Malformed;
import com.github.trex_paxos.core.wire.Pack;
import com.github.trex_paxos.core.wire.PackWriter;
import com.github.trex_paxos.core.wire.UnpackCursor;
import com.github.trex_paxos.core.wire.UnpackError;
import com.github.trex_paxos.core.wire.UnpackException;

import java.util.Objects;

/// One protocol datagram: the fixed header and the body it authorizes.
public record Message(
        Header header,
        Body body
) implements Pack {
    public Message {
        Objects.requireNonNull(header, "header cannot be null");
        Objects.requireNonNull(body, "body cannot be null");
    }

    @Override
    public int packedLen() {
        return header.packedLen() + body.packedLen();
    }

    @Override
    public void pack(PackWriter w) {
        header.pack(w);
        body.pack(w);
    }

    public static Message unpack(UnpackCursor c) throws UnpackException {
        Header header = Header.unpack(c);
        Body body = Body.unpack(c);
        if (body.tag() != header.tag()) {
            throw new UnpackException(new UnpackError.MalformedError(new Malformed.OutOfDomain()));
        }
        return new Message(header, body);
    }
}
