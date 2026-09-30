// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.msg;

public sealed interface BroadcastMessage extends TrexMessage permits
    Accept,
    Fixed,
    Prepare {
}
