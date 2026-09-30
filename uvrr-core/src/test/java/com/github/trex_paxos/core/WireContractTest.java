// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core;

import com.github.trex_paxos.core.ids.*;
import com.github.trex_paxos.core.wire.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WireContractTest {

    @Test
    void headerGoldenVector() throws Exception {
        assertThat(Header.LEN).isEqualTo(20);

        Header header = new Header(
                Tag.PREPARE,
                new Ballot(new Era(0x01020304L), new View(0x05060708L)),
                new Slot(0x090a0b0c0d0e0f10L)
        );

        byte[] bytes = header.packToBytes();

        byte[] expected = new byte[]{
                0x00, 0x00, 0x00, 0x02,
                0x01, 0x02, 0x03, 0x04,
                0x05, 0x06, 0x07, 0x08,
                0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10
        };

        assertThat(bytes).isEqualTo(expected);
        assertThat(bytes.length).isEqualTo(Header.LEN);
        assertThat(header.packedLen()).isEqualTo(Header.LEN);

        Header decoded = Header.unpack(new UnpackCursor(bytes));
        assertThat(decoded).isEqualTo(header);
    }

    @Test
    void operationIdGoldenVectorIsBigEndian() throws Exception {
        OperationId id = new OperationId(0x0011223344556677L, 0x8899aabbccddeeffL);
        byte[] bytes = id.packToBytes();

        byte[] expected = new byte[]{
                0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
                (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb,
                (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff
        };

        assertThat(bytes).isEqualTo(expected);
        assertThat(id.packedLen()).isEqualTo(16);

        OperationId decoded = OperationId.unpack(new UnpackCursor(bytes));
        assertThat(decoded).isEqualTo(id);
    }

    @Test
    void headerRoundTripForAllTags() throws Exception {
        for (Tag tag : Tag.values()) {
            Header header = new Header(
                    tag,
                    new Ballot(new Era(42), new View(99)),
                    new Slot(100500)
            );
            byte[] bytes = header.packToBytes();
            Header decoded = Header.unpack(new UnpackCursor(bytes));
            assertThat(decoded).isEqualTo(header);
        }
    }

    @Test
    void unpackCursorRejectsTruncatedBuffer() {
        byte[] truncated = new byte[19];
        truncated[3] = 0x02; // Tag.PREPARE discriminant
        UnpackCursor cursor = new UnpackCursor(truncated);
        assertThatThrownBy(() -> Header.unpack(cursor))
                .isInstanceOf(UnpackException.class)
                .satisfies(e -> {
                    UnpackException ue = (UnpackException) e;
                    assertThat(ue.error()).isInstanceOf(UnpackError.Incomplete.class);
                });
    }

    @Test
    void unpackCursorRejectsTrailingBytes() {
        byte[] withTrailing = new byte[21];
        withTrailing[3] = 0x02; // tag prepare
        UnpackCursor cursor = new UnpackCursor(withTrailing);
        assertThatThrownBy(() -> {
            Header.unpack(cursor);
            cursor.ensureExhausted();
        }).isInstanceOf(UnpackException.class)
                .satisfies(e -> {
                    UnpackException ue = (UnpackException) e;
                    assertThat(ue.error()).isInstanceOf(UnpackError.MalformedError.class);
                });
    }
}
