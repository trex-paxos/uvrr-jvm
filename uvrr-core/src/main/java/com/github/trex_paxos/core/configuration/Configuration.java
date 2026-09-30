// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.configuration;

import com.github.trex_paxos.core.ids.Era;
import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.ids.View;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// An era, an ordered member list, and a weight per member (§8.7.1).
public record Configuration(Era era, List<Member> order) {
  public static final int MAX_MEMBERS = 16;
  public static final int MAX_WEIGHT = 2;
  public static final Slot VOID_SLOT = new Slot(1);
  public static final Slot INIT_SLOT = new Slot(2);

  public Configuration {
    Objects.requireNonNull(era, "era");
    Objects.requireNonNull(order, "order");
    order = List.copyOf(order);
  }

  /// The void configuration: era 0, empty order, total weight 0.
  public static Configuration voidConfig() {
    return new Configuration(Era.INITIAL, List.of());
  }

  public long len() {
    return order.size();
  }

  public boolean isEmpty() {
    return order.isEmpty();
  }

  public long total() {
    return order.stream().mapToLong(m -> m.weight().value()).sum();
  }

  public Optional<Weight> weightOf(NodeId node) {
    for (Member m : order) {
      if (m.node().equals(node)) {
        return Optional.of(m.weight());
      }
    }
    return Optional.empty();
  }

  public Optional<Long> indexOf(NodeId node) {
    for (int i = 0; i < order.size(); i++) {
      if (order.get(i).node().equals(node)) {
        return Optional.of((long) i);
      }
    }
    return Optional.empty();
  }

  public List<NodeId> voters() {
    return order.stream()
        .filter(m -> m.weight().value() > 0)
        .map(Member::node)
        .toList();
  }

  public Optional<NodeId> primary(View view) {
    List<NodeId> voters = voters();
    if (voters.isEmpty()) {
      return Optional.empty();
    }
    int index = (int) (view.value() % voters.size());
    return Optional.of(voters.get(index));
  }

  public Optional<Long> weightOfSet(Collection<NodeId> members) {
    var seen = new HashSet<NodeId>();
    long sum = 0;
    for (NodeId node : members) {
      if (!seen.add(node)) {
        return Optional.empty(); // duplicate rejected
      }
      var weightOpt = weightOf(node);
      if (weightOpt.isEmpty()) {
        return Optional.empty(); // unknown node rejected
      }
      sum += weightOpt.get().value();
    }
    return Optional.of(sum);
  }

  private Era successorEra() throws ConfigException {
    if (order.isEmpty()) {
      throw new ConfigException(new ConfigError.NotInitialised());
    }
    return era.next().orElseThrow(() -> new ConfigException(new ConfigError.EraExhausted()));
  }

  /// Folds op committed at slot at onto this configuration.
  public Configuration apply(SystemOperation op, Slot at) throws ConfigException {
    if (op instanceof SystemOperation.Void) {
      if (!order.isEmpty()) {
        throw new ConfigException(new ConfigError.AlreadyInitialised());
      }
      if (!at.equals(VOID_SLOT)) {
        throw new ConfigException(new ConfigError.WrongGenesisSlot(VOID_SLOT, at));
      }
      return voidConfig();
    }
    if (op instanceof SystemOperation.Init init) {
      if (!order.isEmpty()) {
        throw new ConfigException(new ConfigError.AlreadyInitialised());
      }
      if (!at.equals(INIT_SLOT)) {
        throw new ConfigException(new ConfigError.WrongGenesisSlot(INIT_SLOT, at));
      }
      if (init.order().isEmpty()) {
        throw new ConfigException(new ConfigError.EmptyInitOrder());
      }
      if (init.order().size() > MAX_MEMBERS) {
        throw new ConfigException(new ConfigError.MembershipCapExceeded(MAX_MEMBERS));
      }
      var seen = new HashSet<NodeId>();
      for (NodeId node : init.order()) {
        if (!seen.add(node)) {
          throw new ConfigException(new ConfigError.DuplicateNode(node));
        }
      }
      var members = init.order().stream()
          .map(node -> new Member(node, Weight.UNIT))
          .toList();
      return new Configuration(new Era(1), members);
    }
    if (op instanceof SystemOperation.Batch batch) {
      return applyBatch(batch.ops());
    }
    if (op instanceof SystemOperation.Nominate) {
      throw new ConfigException(new ConfigError.SolitaryNomination());
    }

    Era nextEra = successorEra();
    Configuration next = applyInEra(op);
    return new Configuration(nextEra, next.order());
  }

  private Configuration applyInEra(SystemOperation op) throws ConfigException {
    if (op instanceof SystemOperation.Increment inc) {
      var optIndex = indexOf(inc.node());
      if (optIndex.isEmpty()) {
        throw new ConfigException(new ConfigError.NotAMember(inc.node()));
      }
      int index = optIndex.get().intValue();
      int currentWeight = order.get(index).weight().value();
      if (currentWeight >= MAX_WEIGHT) {
        throw new ConfigException(new ConfigError.WeightCapExceeded(inc.node(), MAX_WEIGHT));
      }
      var newOrder = new ArrayList<>(order);
      newOrder.set(index, new Member(inc.node(), new Weight(currentWeight + 1)));
      return new Configuration(era, newOrder);
    }
    if (op instanceof SystemOperation.Decrement dec) {
      var optIndex = indexOf(dec.node());
      if (optIndex.isEmpty()) {
        throw new ConfigException(new ConfigError.NotAMember(dec.node()));
      }
      int index = optIndex.get().intValue();
      int currentWeight = order.get(index).weight().value();
      if (currentWeight == 0) {
        throw new ConfigException(new ConfigError.WeightUnderflow(dec.node()));
      }
      if (total() == 1) {
        throw new ConfigException(new ConfigError.TotalWouldBeZero());
      }
      var newOrder = new ArrayList<>(order);
      newOrder.set(index, new Member(dec.node(), new Weight(currentWeight - 1)));
      return new Configuration(era, newOrder);
    }
    if (op instanceof SystemOperation.DoubleOp) {
      for (Member m : order) {
        if (m.weight().value() * 2 > MAX_WEIGHT) {
          throw new ConfigException(new ConfigError.WeightCapExceeded(m.node(), MAX_WEIGHT));
        }
      }
      var newOrder = order.stream()
          .map(m -> new Member(m.node(), new Weight(m.weight().value() * 2)))
          .toList();
      return new Configuration(era, newOrder);
    }
    if (op instanceof SystemOperation.HalveOp) {
      for (Member m : order) {
        if (m.weight().value() % 2 != 0) {
          throw new ConfigException(new ConfigError.OddWeight(m.node()));
        }
      }
      var newOrder = order.stream()
          .map(m -> new Member(m.node(), new Weight(m.weight().value() / 2)))
          .toList();
      return new Configuration(era, newOrder);
    }
    if (op instanceof SystemOperation.Join join) {
      if (weightOf(join.node()).isPresent()) {
        throw new ConfigException(new ConfigError.DuplicateNode(join.node()));
      }
      if (order.size() >= MAX_MEMBERS) {
        throw new ConfigException(new ConfigError.MembershipCapExceeded(MAX_MEMBERS));
      }
      if (join.position() > order.size()) {
        throw new ConfigException(new ConfigError.PositionOutOfRange(join.position(), order.size()));
      }
      var newOrder = new ArrayList<>(order);
      newOrder.add((int) join.position(), new Member(join.node(), Weight.LEARNER));
      return new Configuration(era, newOrder);
    }
    if (op instanceof SystemOperation.Leave leave) {
      var optIndex = indexOf(leave.node());
      if (optIndex.isEmpty()) {
        throw new ConfigException(new ConfigError.NotAMember(leave.node()));
      }
      int index = optIndex.get().intValue();
      if (order.get(index).weight().value() != 0) {
        throw new ConfigException(new ConfigError.NonZeroWeight(leave.node()));
      }
      var newOrder = new ArrayList<>(order);
      newOrder.remove(index);
      return new Configuration(era, newOrder);
    }
    if (op instanceof SystemOperation.Nominate nom) {
      if (nom.offset() == 0) {
        throw new ConfigException(new ConfigError.ZeroNominationOffset());
      }
      return this;
    }
    throw new IllegalStateException("Unexpected in-era operation: " + op);
  }

  private Configuration applyBatch(List<SystemOperation> ops) throws ConfigException {
    if (ops.isEmpty()) {
      throw new ConfigException(new ConfigError.EmptyBatch());
    }
    for (SystemOperation op : ops) {
      if (op instanceof SystemOperation.Void || op instanceof SystemOperation.Init) {
        throw new ConfigException(new ConfigError.GenesisNotPlannable());
      }
      if (op instanceof SystemOperation.Batch) {
        throw new ConfigException(new ConfigError.NestedBatch());
      }
    }
    boolean solitaryScaling = (ops.size() == 1
        && (ops.get(0) instanceof SystemOperation.DoubleOp || ops.get(0) instanceof SystemOperation.HalveOp));
    if (!solitaryScaling && ops.stream().anyMatch(op -> op instanceof SystemOperation.DoubleOp || op instanceof SystemOperation.HalveOp)) {
      throw new ConfigException(new ConfigError.ScalingNotSolitary());
    }
    Era nextEra = successorEra();
    Configuration current = new Configuration(this.era, this.order);
    for (SystemOperation op : ops) {
      current = current.applyInEra(op);
    }
    if (!solitaryScaling) {
      long moved = massMoved(this, current);
      if (moved > 1) {
        throw new ConfigException(new ConfigError.BatchMassMoved(moved));
      }
    }
    return new Configuration(nextEra, current.order());
  }

  /// Total mass moved between two configurations over union of memberships: Σ |W_before - W_after|.
  public static long massMoved(Configuration before, Configuration after) {
    var nodes = new LinkedHashSet<NodeId>();
    for (Member m : before.order()) nodes.add(m.node());
    for (Member m : after.order()) nodes.add(m.node());
    long sum = 0;
    for (NodeId node : nodes) {
      long wBefore = before.weightOf(node).map(w -> (long) w.value()).orElse(0L);
      long wAfter = after.weightOf(node).map(w -> (long) w.value()).orElse(0L);
      sum += Math.abs(wBefore - wAfter);
    }
    return sum;
  }

  /// Partitions an operation stream into legal eras using the reduce-left partitioner (§5).
  public List<EraStep> plan(List<SystemOperation> ops) throws ConfigException {
    var steps = new ArrayList<EraStep>();
    var batchStart = this;
    var taken = new ArrayList<SystemOperation>();
    var current = this;

    for (SystemOperation op : ops) {
      if (op instanceof SystemOperation.Void || op instanceof SystemOperation.Init) {
        throw new ConfigException(new ConfigError.GenesisNotPlannable());
      }
      var candidate = new ArrayList<>(taken);
      candidate.add(op);
      try {
        var next = batchStart.apply(new SystemOperation.Batch(candidate), Slot.NONE);
        taken = candidate;
        current = next;
      } catch (ConfigException e) {
        if (taken.isEmpty()) {
          throw e;
        }
        steps.add(new EraStep(taken, current));
        batchStart = current;
        var single = List.of(op);
        var next = batchStart.apply(new SystemOperation.Batch(single), Slot.NONE);
        taken = new ArrayList<>(single);
        current = next;
      }
    }
    if (!taken.isEmpty()) {
      steps.add(new EraStep(taken, current));
    }
    return steps;
  }

  /// The durable record of this configuration as a Snapshot.
  public Snapshot toSnapshot() {
    return new Snapshot(era, order);
  }
}
