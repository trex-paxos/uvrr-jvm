// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.msg;

public sealed interface DirectMessage extends TrexMessage 
permits AcceptResponse, Catchup, CatchupResponse, PrepareResponse {
  /// @return the node in the cluster that this message is intended for.
  short to();
}
