// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.paxe;

/**
 * Simple container for a network and its associated port.
 * Used to return both from NetworkTestHarness.createNetwork
 */
public record NetworkWithTempPort(PaxeNetwork network, int port) {

}
