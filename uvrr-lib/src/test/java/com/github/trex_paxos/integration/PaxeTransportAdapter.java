// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.integration;

import com.github.trex_paxos.NodeId;
import com.github.trex_paxos.network.Channel;
import com.github.trex_paxos.network.NetworkLayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/// Wires PAXE into the consensus stack: the host side of the transport seam.
///
/// PAXE names no consensus types and the consensus stack knows nothing about
/// encryption, so something has to translate between them. This is that
/// something, and it lives in the consensus library's test tree because the stack
/// is what it exists to serve: a test of "does a real Paxos cluster run over PAXE"
/// is this library's test, not the transport's.
///
/// The translation is narrow and total. Consensus speaks `NodeId` and `Channel`;
/// PAXE speaks `PeerId` and `Channel`. A consensus node id is a short, so it maps
/// into the u16 peer space exactly, and the channel ids are the same numbers on
/// both sides.
final class PaxeTransportAdapter implements NetworkLayer {

    private final com.github.trex_paxos.paxe.PaxeTransport transport;
    private final Map<Channel, com.github.trex_paxos.paxe.Channel> channels = new HashMap<>();
    private final Map<com.github.trex_paxos.paxe.Channel, Channel> reverse = new HashMap<>();
    private final Map<NodeId, com.github.trex_paxos.paxe.PeerId> peers = new HashMap<>();

    PaxeTransportAdapter(com.github.trex_paxos.paxe.PaxeTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport cannot be null");
    }

    /// Registers a channel pair. The ids are the same number on both sides, so
    /// this is an identity map that exists to keep the types apart.
    PaxeTransportAdapter withChannel(Channel consensus) {
        var paxe = new com.github.trex_paxos.paxe.Channel(consensus.id());
        channels.put(consensus, paxe);
        reverse.put(paxe, consensus);
        return this;
    }

    @Override
    public <T> void subscribe(Channel channel, Consumer<T> handler, String name) {
        transport.subscribe(paxeChannel(channel), handler, name);
    }

    @Override
    public <T> void send(Channel channel, NodeId to, T msg) {
        transport.send(paxeChannel(channel), peerOf(to), msg);
    }

    @Override
    public void start() {
        transport.start();
    }

    @Override
    public void close() {
        try {
            transport.close();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("the PAXE transport could not close", e);
        }
    }

    private com.github.trex_paxos.paxe.Channel paxeChannel(Channel channel) {
        var paxe = channels.get(channel);
        if (paxe == null) {
            throw new IllegalArgumentException("No PAXE channel registered for " + channel);
        }
        return paxe;
    }

    /// Bridges the consensus library's pickler onto PAXE's.
    ///
    /// Both sides declare a `Pickler`, independently, and that is the seam doing
    /// its job: PAXE does not know the consensus payload type and the consensus
    /// library does not know how to seal. The adapter is the only place the two
    /// meet, so the shape of the payload stays the host's business throughout.
    static <T> com.github.trex_paxos.paxe.Pickler<T> bridge(
            com.github.trex_paxos.Pickler<T> consensus) {
        return new com.github.trex_paxos.paxe.Pickler<>() {
            @Override
            public void serialize(T value, java.nio.ByteBuffer buffer) {
                consensus.serialize(value, buffer);
            }

            @Override
            public T deserialize(java.nio.ByteBuffer buffer) {
                return consensus.deserialize(buffer);
            }

            @Override
            public int sizeOf(T value) {
                return consensus.sizeOf(value);
            }
        };
    }

    private com.github.trex_paxos.paxe.PeerId peerOf(NodeId id) {
        return peers.computeIfAbsent(id, node ->
                new com.github.trex_paxos.paxe.PeerId(node.id() & 0xFFFF));
    }
}