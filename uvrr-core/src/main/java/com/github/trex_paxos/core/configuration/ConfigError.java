// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.configuration;

import com.github.trex_paxos.core.ids.Era;
import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.ids.Slot;

/// Why a configuration operation or fold transition was refused.
public sealed interface ConfigError {
  record NotInitialised() implements ConfigError {}
  record AlreadyInitialised() implements ConfigError {}
  record WrongGenesisSlot(Slot expected, Slot actual) implements ConfigError {}
  record EmptyInitOrder() implements ConfigError {}
  record DuplicateNode(NodeId node) implements ConfigError {}
  record NotAMember(NodeId node) implements ConfigError {}
  record WeightCapExceeded(NodeId node, int cap) implements ConfigError {}
  record WeightUnderflow(NodeId node) implements ConfigError {}
  record OddWeight(NodeId node) implements ConfigError {}
  record NonZeroWeight(NodeId node) implements ConfigError {}
  record PositionOutOfRange(long position, long len) implements ConfigError {}
  record MembershipCapExceeded(int cap) implements ConfigError {}
  record TotalWouldBeZero() implements ConfigError {}
  record SolitaryNomination() implements ConfigError {}
  record ZeroNominationOffset() implements ConfigError {}
  record EraExhausted() implements ConfigError {}
  record EmptyBatch() implements ConfigError {}
  record GenesisNotPlannable() implements ConfigError {}
  record NestedBatch() implements ConfigError {}
  record ScalingNotSolitary() implements ConfigError {}
  record BatchMassMoved(long moved) implements ConfigError {}
  record SnapshotVoidWithMembers() implements ConfigError {}
  record SnapshotEmptyOrder(Era era) implements ConfigError {}
  record SnapshotZeroTotal(Era era) implements ConfigError {}
}
