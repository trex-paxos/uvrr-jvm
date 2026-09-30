// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.quorum;

import com.github.trex_paxos.core.configuration.Configuration;
import com.github.trex_paxos.core.configuration.Member;
import com.github.trex_paxos.core.ids.NodeId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/// Closed intersection gates for configurations and transitions (§8.3, §8.7.4, §8.7.6, Q1).
public final class QuorumGate {
  private QuorumGate() {}

  public record DisjointWitness(List<NodeId> first, List<NodeId> second) {}

  private static List<NodeId> membersOf(List<NodeId> universe, int mask) {
    var result = new ArrayList<NodeId>();
    for (int bit = 0; bit < universe.size(); bit++) {
      if ((mask & (1 << bit)) != 0) {
        result.add(universe.get(bit));
      }
    }
    return result;
  }

  private static boolean disjointQuorumsExist(
      QuorumStrategy strategy,
      Role roleA, Configuration configA,
      Role roleB, Configuration configB,
      List<NodeId> universe) {
    int n = universe.size();
    int full = (1 << n) - 1;
    for (int mask = 0; mask <= full; mask++) {
      var setA = membersOf(universe, mask);
      var setB = membersOf(universe, full ^ mask);
      if (strategy.isQuorum(roleA, configA, setA) && strategy.isQuorum(roleB, configB, setB)) {
        return true;
      }
    }
    return false;
  }

  private static Optional<DisjointWitness> findDisjointPair(
      QuorumStrategy strategy,
      Role roleA, Configuration configA,
      Role roleB, Configuration configB,
      List<NodeId> universe) {
    if (!disjointQuorumsExist(strategy, roleA, configA, roleB, configB, universe)) {
      return Optional.empty();
    }
    int n = universe.size();
    int full = (1 << n) - 1;
    for (int size = 0; size <= n; size++) {
      for (int mask = 0; mask <= full; mask++) {
        if (Integer.bitCount(mask) != size) continue;
        var setA = membersOf(universe, mask);
        if (!strategy.isQuorum(roleA, configA, setA)) continue;
        int complement = full ^ mask;
        for (int inner = 0; inner <= (n - size); inner++) {
          for (int sub = 0; sub <= complement; sub++) {
            if ((sub & ~complement) != 0 || Integer.bitCount(sub) != inner) continue;
            var setB = membersOf(universe, sub);
            if (strategy.isQuorum(roleB, configB, setB)) {
              return Optional.of(new DisjointWitness(setA, setB));
            }
          }
        }
      }
    }
    return Optional.empty();
  }

  /// Validates within-era quorum obligations: R1 (Commit ⌢ ViewChange),
  /// ViewChange self-intersection, and Fence ⌢ Restart.
  public static Optional<QuorumError> validateEra(QuorumStrategy strategy, Configuration config) {
    Objects.requireNonNull(strategy, "strategy");
    Objects.requireNonNull(config, "config");
    if (config.len() > Configuration.MAX_MEMBERS) {
      return Optional.of(new QuorumError.MembershipCapExceeded(Configuration.MAX_MEMBERS));
    }
    var universe = config.order().stream().map(Member::node).toList();

    var r1 = findDisjointPair(strategy, Role.COMMIT, config, Role.VIEW_CHANGE, config, universe);
    if (r1.isPresent()) {
      return Optional.of(new QuorumError.R1Violation(r1.get().first(), r1.get().second()));
    }

    var self = findDisjointPair(strategy, Role.VIEW_CHANGE, config, Role.VIEW_CHANGE, config, universe);
    if (self.isPresent()) {
      return Optional.of(new QuorumError.SelfIntersectionViolation(Role.VIEW_CHANGE, self.get().first(), self.get().second()));
    }

    var fenceRestart = findDisjointPair(strategy, Role.FENCE, config, Role.RESTART, config, universe);
    if (fenceRestart.isPresent()) {
      return Optional.of(new QuorumError.FenceRestartViolation(fenceRestart.get().first(), fenceRestart.get().second()));
    }

    return Optional.empty();
  }

  /// Validates cross-era R2 obligation in both forward and reverse directions (§8.7.4).
  public static Optional<QuorumError> validateTransition(
      QuorumStrategy strategy, Configuration current, Configuration next) {
    Objects.requireNonNull(strategy, "strategy");
    Objects.requireNonNull(current, "current");
    Objects.requireNonNull(next, "next");
    if (current.len() > Configuration.MAX_MEMBERS || next.len() > Configuration.MAX_MEMBERS) {
      return Optional.of(new QuorumError.MembershipCapExceeded(Configuration.MAX_MEMBERS));
    }
    var set = new TreeSet<NodeId>();
    for (Member m : current.order()) set.add(m.node());
    for (Member m : next.order()) set.add(m.node());
    var universe = new ArrayList<>(set);
    if (universe.size() > Configuration.MAX_MEMBERS) {
      return Optional.of(new QuorumError.MembershipCapExceeded(Configuration.MAX_MEMBERS));
    }

    var forward = findDisjointPair(strategy, Role.VIEW_CHANGE, current, Role.COMMIT, next, universe);
    if (forward.isPresent()) {
      return Optional.of(new QuorumError.R2Violation(R2Direction.FORWARD, forward.get().first(), forward.get().second()));
    }

    var reverse = findDisjointPair(strategy, Role.VIEW_CHANGE, next, Role.COMMIT, current, universe);
    if (reverse.isPresent()) {
      return Optional.of(new QuorumError.R2Violation(R2Direction.REVERSE, reverse.get().first(), reverse.get().second()));
    }

    return Optional.empty();
  }

  /// Constructs the concrete pivot for a non-stop reconfiguration (§8.7.6).
  public static Optional<Pivot> constructPivot(
      QuorumStrategy strategy, Configuration current, Configuration next, NodeId leader) {
    Objects.requireNonNull(strategy, "strategy");
    Objects.requireNonNull(current, "current");
    Objects.requireNonNull(next, "next");
    Objects.requireNonNull(leader, "leader");

    var set = new TreeSet<NodeId>();
    for (Member m : current.order()) set.add(m.node());
    for (Member m : next.order()) set.add(m.node());
    var universe = new ArrayList<>(set);
    int n = universe.size();
    if (n > Configuration.MAX_MEMBERS) {
      return Optional.empty();
    }
    int full = (1 << n) - 1;
    for (int mask = 0; mask <= full; mask++) {
      var qII = membersOf(universe, mask);
      if (!qII.contains(leader)) continue;
      if (!strategy.isQuorum(Role.COMMIT, current, qII)) continue;
      if (!strategy.isQuorum(Role.COMMIT, next, qII)) continue;

      var qISet = new TreeSet<NodeId>();
      for (NodeId node : universe) {
        if (!qII.contains(node)) {
          qISet.add(node);
        }
      }
      qISet.add(leader);
      var qI = new ArrayList<>(qISet);
      if (!strategy.isQuorum(Role.VIEW_CHANGE, current, qI)) continue;
      if (qI.size() + qII.size() != universe.size() + 1) continue;

      return Optional.of(new Pivot(qI, qII));
    }
    return Optional.empty();
  }

  /// Validates a pivot against the pivot condition (§8.7.6).
  public static Optional<PivotError> validatePivot(
      QuorumStrategy strategy, Configuration current, Configuration next, NodeId leader, Pivot pivot) {
    Objects.requireNonNull(strategy, "strategy");
    Objects.requireNonNull(current, "current");
    Objects.requireNonNull(next, "next");
    Objects.requireNonNull(leader, "leader");
    Objects.requireNonNull(pivot, "pivot");

    var seenQI = new HashSet<NodeId>();
    for (NodeId node : pivot.qI()) {
      if (!seenQI.add(node)) return Optional.of(new PivotError.DuplicateMember(node));
    }
    var seenQII = new HashSet<NodeId>();
    for (NodeId node : pivot.qII()) {
      if (!seenQII.add(node)) return Optional.of(new PivotError.DuplicateMember(node));
    }

    if (!pivot.qI().contains(leader) || !pivot.qII().contains(leader)) {
      return Optional.of(new PivotError.LeaderAbsent());
    }

    var shared = new ArrayList<NodeId>();
    for (NodeId node : pivot.qI()) {
      if (pivot.qII().contains(node)) shared.add(node);
    }
    if (shared.size() != 1 || !shared.getFirst().equals(leader)) {
      return Optional.of(new PivotError.IntersectionNotLeader());
    }

    if (!strategy.isQuorum(Role.VIEW_CHANGE, current, pivot.qI())) {
      return Optional.of(new PivotError.QiNotLegal());
    }
    if (!strategy.isQuorum(Role.COMMIT, current, pivot.qII())) {
      return Optional.of(new PivotError.QiiNotLegalCurrent());
    }
    if (!strategy.isQuorum(Role.COMMIT, next, pivot.qII())) {
      return Optional.of(new PivotError.QiiNotLegalNext());
    }

    return Optional.empty();
  }
}
