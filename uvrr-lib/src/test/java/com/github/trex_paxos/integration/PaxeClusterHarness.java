// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.integration;

import com.github.trex_paxos.CommandPickler;
import com.github.trex_paxos.NodeId;
import com.github.trex_paxos.network.Channel;
import com.github.trex_paxos.network.SystemChannel;
import com.github.trex_paxos.network.NetworkAddress;
import com.github.trex_paxos.network.NetworkLayer;
import com.github.trex_paxos.network.PickleMsg;
import com.github.trex_paxos.paxe.ClusterKeyManager;
import com.github.trex_paxos.paxe.PaxeNetwork;

import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/// Stands up a PAXE-encrypted cluster on loopback, for the integration tests.
///
/// This is the host side of the transport seam, so it belongs with the tests that
/// need a real encrypted network rather than with the transport: the pickler it
/// registers is the *consensus* one, which is exactly what PAXE no longer knows
/// anything about. PAXE's own harness, in the paxe-jvm repository, registers the
/// transport's trivial fixtures instead.
///
/// Ports are assigned by binding to 0 and reading back, so tests never collide.
final class PaxeClusterHarness implements AutoCloseable {

    private static final long STARTUP_TIMEOUT_SECONDS = 5;

    private final List<PaxeNetwork> networks = new ArrayList<>();
    private final byte[] clusterPsk;
    private final Map<NodeId, NetworkAddress> addressMap = new HashMap<>();

    PaxeClusterHarness() {
        this(generateClusterPsk());
    }

    PaxeClusterHarness(byte[] clusterPsk) {
        if (clusterPsk.length != ClusterKeyManager.CLUSTER_PSK_SIZE) {
            throw new IllegalArgumentException(
                    "The cluster PSK must be " + ClusterKeyManager.CLUSTER_PSK_SIZE + " bytes");
        }
        this.clusterPsk = clusterPsk.clone();
    }

    static byte[] generateClusterPsk() {
        var psk = new byte[ClusterKeyManager.CLUSTER_PSK_SIZE];
        new SecureRandom().nextBytes(psk);
        return psk;
    }

    /// One member's transport, already started and adapted to the consensus layer.
    NetworkLayer createTransport(short nodeId) throws Exception {
        var port = freePort();
        var id = new NodeId(nodeId);
        addressMap.put(id, new NetworkAddress("127.0.0.1", port));
        Supplier<com.github.trex_paxos.paxe.NodeEndpoints> membership = () -> {
            var paxeAddresses = new HashMap<com.github.trex_paxos.paxe.PeerId,
                    com.github.trex_paxos.paxe.NetworkAddress>();
            addressMap.forEach((node, address) -> paxeAddresses.put(
                    new com.github.trex_paxos.paxe.PeerId(node.id() & 0xFFFF),
                    new com.github.trex_paxos.paxe.NetworkAddress(address.host(), address.port())));
            return new com.github.trex_paxos.paxe.NodeEndpoints(paxeAddresses);
        };

        var paxe = new PaxeNetwork.Builder(new ClusterKeyManager(clusterPsk), port,
                        new com.github.trex_paxos.paxe.PeerId(nodeId & 0xFFFF), membership)
                .withPickler(new com.github.trex_paxos.paxe.Channel(SystemChannel.CONSENSUS.id()),
                        PaxeTransportAdapter.bridge(PickleMsg.instance))
                // The old hardcoded builder registered a pickler for both default
                // channels; PAXE registers none, so the host states both.
                .withPickler(new com.github.trex_paxos.paxe.Channel(SystemChannel.PROXY.id()),
                        PaxeTransportAdapter.bridge(CommandPickler.instance))
                .build();
        networks.add(paxe);
        return new PaxeTransportAdapter(paxe)
                .withChannel(SystemChannel.CONSENSUS.value())
                .withChannel(SystemChannel.PROXY.value());
    }

    private static int freePort() throws Exception {
        try (DatagramChannel probe = DatagramChannel.open()) {
            probe.socket().bind(new InetSocketAddress(0));
            return probe.socket().getLocalPort();
        }
    }

    void waitForNetworkEstablishment() throws Exception {
        var startups = networks.stream()
                .map(network -> CompletableFuture.runAsync(network::start))
                .toList();
        CompletableFuture.allOf(startups.toArray(CompletableFuture[]::new))
                .get(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        networks.forEach(PaxeNetwork::close);
        networks.clear();
        addressMap.clear();
    }
}
