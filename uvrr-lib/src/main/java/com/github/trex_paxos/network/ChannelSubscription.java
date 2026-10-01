// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.network;

import java.nio.ByteBuffer;

public record ChannelSubscription(java.util.function.Consumer<ByteBuffer> handler, String name) {
  public void accept(ByteBuffer data) {
    handler.accept(data);
  }
}
