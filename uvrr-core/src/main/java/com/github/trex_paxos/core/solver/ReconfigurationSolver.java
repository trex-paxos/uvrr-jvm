// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.solver;

import com.github.trex_paxos.core.configuration.ConfigError;
import com.github.trex_paxos.core.configuration.ConfigException;
import com.github.trex_paxos.core.configuration.Configuration;
import com.github.trex_paxos.core.configuration.EraStep;
import com.github.trex_paxos.core.configuration.Member;
import com.github.trex_paxos.core.configuration.Snapshot;
import com.github.trex_paxos.core.configuration.SystemOperation;
import com.github.trex_paxos.core.configuration.Weight;
import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.ids.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Constructive operator planning for strict weighted majorities.
public final class ReconfigurationSolver {

    private ReconfigurationSolver() {}

    public static long availableMass(Configuration c, List<NodeId> live) {
        long sum = 0;
        for (Member m : c.order()) {
            if (live.contains(m.node())) {
                sum += m.weight().value();
            }
        }
        return sum;
    }

    private static void checkAvailable(Configuration c, List<NodeId> live, boolean target) throws SolveException {
        long mass = availableMass(c, live);
        if (2 * mass <= c.total()) {
            throw new SolveException(new SolveError.NoAvailableMajority(target, mass, c.total()));
        }
    }

    public static List<EraStep> withNominations(List<EraStep> steps, Configuration start, View view) {
        Configuration previous = start;
        View running = view;
        List<EraStep> riders = new ArrayList<>(steps.size());

        for (EraStep step : steps) {
            View from = running;
            Optional<NodeId> leader = previous.primary(from);
            int voters = (int) step.config().order().stream()
                    .filter(m -> m.weight().value() > 0)
                    .count();
            int offset = 1;
            boolean elects = false;

            for (int u = 1; u <= voters; u++) {
                long num = from.value() + u;
                if (num <= 0xFFFF_FFFFL) {
                    if (leader.isPresent() && step.config().primary(new View(num)).equals(leader)) {
                        offset = u;
                        elects = true;
                        break;
                    }
                }
            }

            boolean scaling = step.ops().stream()
                    .anyMatch(op -> op instanceof SystemOperation.DoubleOp || op instanceof SystemOperation.HalveOp);

            List<SystemOperation> modifiedOps = new ArrayList<>(step.ops());
            if (!scaling && (elects || !step.config().primary(from).equals(leader))) {
                modifiedOps.add(new SystemOperation.Nominate(from, offset));
            }

            long nextRunning = from.value() + offset;
            running = (nextRunning <= 0xFFFF_FFFFL) ? new View(nextRunning) : from;

            Configuration config = step.config();
            riders.add(new EraStep(modifiedOps, config));
            previous = config;
        }

        return riders;
    }

    private static final class Builder {
        Configuration current;
        final List<SystemOperation> ops = new ArrayList<>();

        Builder(Configuration start) {
            this.current = start;
        }

        void push(SystemOperation op) throws SolveException {
            try {
                current = current.apply(op, Slot.NONE);
                ops.add(op);
            } catch (ConfigException e) {
                throw new SolveException(new SolveError.ConfigurationError(e.error()));
            }
        }

        void weight(NodeId node, int target) throws SolveException {
            int w = current.weightOf(node).map(Weight::value).orElse(0);
            while (w != target) {
                push(w < target ? new SystemOperation.Increment(node) : new SystemOperation.Decrement(node));
                w = current.weightOf(node).map(Weight::value).orElse(0);
            }
        }
    }

    public static List<EraStep> solve(
            Configuration current,
            Configuration target,
            List<NodeId> live,
            View view
    ) throws SolveException {
        checkAvailable(current, live, false);
        checkAvailable(target, live, true);

        if (current.order().equals(target.order())) {
            return Collections.emptyList();
        }

        boolean sameOrder = current.order().size() == target.order().size();
        if (sameOrder) {
            for (int i = 0; i < current.order().size(); i++) {
                if (!current.order().get(i).node().equals(target.order().get(i).node())) {
                    sameOrder = false;
                    break;
                }
            }
        }

        Builder b = new Builder(current);
        if (sameOrder) {
            SystemOperation[] scales = {new SystemOperation.DoubleOp(), new SystemOperation.HalveOp()};
            for (SystemOperation scale : scales) {
                try {
                    Configuration c = current.apply(scale, Slot.NONE);
                    if (c.order().equals(target.order())) {
                        return withNominations(current.plan(List.of(scale)), current, view);
                    }
                } catch (ConfigException ignored) {}
            }

            boolean[][] passes = {
                    {false, false},
                    {true, true},
                    {true, false},
                    {false, true}
            };

            for (boolean[] pass : passes) {
                boolean isLive = pass[0];
                boolean increase = pass[1];
                for (Member m : target.order()) {
                    int w = b.current.weightOf(m.node()).map(Weight::value).orElse(0);
                    if (live.contains(m.node()) == isLive && (w < m.weight().value()) == increase) {
                        b.weight(m.node(), m.weight().value());
                    }
                }
            }
        } else {
            int prefix = 0;
            int minLen = Math.min(current.order().size(), target.order().size());
            while (prefix < minLen) {
                Member before = current.order().get(prefix);
                Member after = target.order().get(prefix);
                if (before.node().equals(after.node()) && before.weight().equals(after.weight())) {
                    prefix++;
                } else {
                    break;
                }
            }

            Optional<Configuration> prefixOnlyOpt = Optional.empty();
            try {
                Snapshot snap = new Snapshot(current.era(), current.order().subList(0, prefix));
                Configuration p = snap.inflate();
                if (availableMass(p, live) * 2 > p.total()) {
                    prefixOnlyOpt = Optional.of(p);
                }
            } catch (Exception ignored) {}

            if (prefixOnlyOpt.isPresent()) {
                for (boolean isLive : new boolean[]{false, true}) {
                    for (int i = prefix; i < current.order().size(); i++) {
                        Member m = current.order().get(i);
                        if (live.contains(m.node()) == isLive) {
                            b.weight(m.node(), 0);
                        }
                    }
                }
                for (int i = prefix; i < current.order().size(); i++) {
                    b.push(new SystemOperation.Leave(current.order().get(i).node()));
                }
                for (int i = prefix; i < target.order().size(); i++) {
                    Member m = target.order().get(i);
                    b.push(new SystemOperation.Join(m.node(), i));
                    if (live.contains(m.node())) {
                        b.weight(m.node(), m.weight().value());
                    }
                }
                for (int i = prefix; i < target.order().size(); i++) {
                    Member m = target.order().get(i);
                    if (!live.contains(m.node())) {
                        b.weight(m.node(), m.weight().value());
                    }
                }
            } else {
                NodeId anchor = current.order().stream()
                        .filter(m -> m.weight().value() > 0 && live.contains(m.node()))
                        .findFirst()
                        .orElseThrow()
                        .node();

                for (boolean isLive : new boolean[]{false, true}) {
                    for (Member m : current.order()) {
                        if (!m.node().equals(anchor) && live.contains(m.node()) == isLive) {
                            b.weight(m.node(), 0);
                        }
                    }
                }
                b.weight(anchor, 1);
                for (Member m : current.order()) {
                    if (!m.node().equals(anchor)) {
                        b.push(new SystemOperation.Leave(m.node()));
                    }
                }

                NodeId targetAnchor = target.order().stream()
                        .filter(m -> m.node().equals(anchor) && m.weight().value() > 0)
                        .map(Member::node)
                        .findFirst()
                        .or(() -> target.order().stream()
                                .filter(m -> m.weight().value() > 0 && live.contains(m.node()))
                                .map(Member::node)
                                .findFirst())
                        .orElseThrow();

                if (!targetAnchor.equals(anchor)) {
                    b.push(new SystemOperation.Join(targetAnchor, 0));
                    b.weight(targetAnchor, 1);
                    b.weight(anchor, 0);
                    b.push(new SystemOperation.Leave(anchor));
                }

                for (int pos = 0; pos < target.order().size(); pos++) {
                    Member m = target.order().get(pos);
                    if (!m.node().equals(targetAnchor)) {
                        b.push(new SystemOperation.Join(m.node(), pos));
                    }
                }

                for (boolean isLive : new boolean[]{true, false}) {
                    for (Member m : target.order()) {
                        if (live.contains(m.node()) == isLive) {
                            b.weight(m.node(), m.weight().value());
                        }
                    }
                }
            }
        }

        if (sameOrder) {
            try {
                return withNominations(current.plan(b.ops), current, view);
            } catch (ConfigException e) {
                throw new SolveException(new SolveError.ConfigurationError(e.error()));
            }
        } else {
            Configuration c = current;
            List<EraStep> steps = new ArrayList<>();
            for (SystemOperation op : b.ops) {
                try {
                    c = c.apply(new SystemOperation.Batch(List.of(op)), Slot.NONE);
                    steps.add(new EraStep(List.of(op), c));
                } catch (ConfigException e) {
                    throw new SolveException(new SolveError.ConfigurationError(e.error()));
                }
            }
            return withNominations(steps, current, view);
        }
    }

    public static List<SystemOperation> forcedSteps(
            Configuration config,
            NodeId oldId,
            NodeId newId
    ) {
        List<SystemOperation> eras = new ArrayList<>();
        if (oldId.equals(newId)) {
            return eras;
        }

        Optional<List<SystemOperation>> fiveSteps = fiveNodeWeightedSteps(config, oldId, newId);
        if (fiveSteps.isPresent()) {
            return fiveSteps.get();
        }

        Optional<Integer> oldWeight = config.weightOf(oldId).map(Weight::value);
        Optional<Integer> newWeight = config.weightOf(newId).map(Weight::value);
        Optional<Long> oldPosition = config.indexOf(oldId);
        long appendPosition = config.len();

        Optional<Integer> newWeightNow = newWeight;
        if (oldWeight.isPresent()) {
            int weight = oldWeight.get();
            for (int i = 0; i < Math.max(0, weight - 1); i++) {
                eras.add(new SystemOperation.Batch(List.of(new SystemOperation.Decrement(oldId))));
            }
            if (weight >= 1) {
                List<SystemOperation> crossing = new ArrayList<>();
                crossing.add(new SystemOperation.Decrement(oldId));
                if (newWeightNow.isEmpty()) {
                    crossing.add(new SystemOperation.Join(newId, oldPosition.orElse(appendPosition)));
                    newWeightNow = Optional.of(0);
                }
                eras.add(new SystemOperation.Batch(crossing));
            }
        }

        if (oldWeight.isPresent()) {
            if (newWeightNow.isEmpty()) {
                eras.add(new SystemOperation.Batch(List.of(
                        new SystemOperation.Join(newId, oldPosition.orElse(appendPosition)),
                        new SystemOperation.Leave(oldId)
                )));
                eras.add(new SystemOperation.Batch(List.of(new SystemOperation.Increment(newId))));
            } else if (newWeightNow.get() == 0) {
                eras.add(new SystemOperation.Batch(List.of(
                        new SystemOperation.Increment(newId),
                        new SystemOperation.Leave(oldId)
                )));
            } else {
                eras.add(new SystemOperation.Batch(List.of(new SystemOperation.Leave(oldId))));
            }
        } else {
            if (newWeight.isEmpty()) {
                eras.add(new SystemOperation.Batch(List.of(
                        new SystemOperation.Join(newId, appendPosition)
                )));
                eras.add(new SystemOperation.Batch(List.of(new SystemOperation.Increment(newId))));
            } else if (newWeight.get() == 0) {
                eras.add(new SystemOperation.Batch(List.of(new SystemOperation.Increment(newId))));
            }
        }

        return eras;
    }

    private static Optional<List<SystemOperation>> fiveNodeWeightedSteps(
            Configuration config,
            NodeId oldId,
            NodeId newId
    ) {
        List<Member> survivors = config.order().stream()
                .filter(m -> !m.node().equals(oldId) && !m.node().equals(newId))
                .toList();
        if (survivors.size() != 4) {
            return Optional.empty();
        }

        Optional<Integer> oldWeight = config.weightOf(oldId).map(Weight::value);
        Optional<Integer> newWeight = config.weightOf(newId).map(Weight::value);
        List<SystemOperation> ops = new ArrayList<>();

        if (survivors.stream().allMatch(m -> m.weight().value() == 1)
                && oldWeight.equals(Optional.of(1))
                && newWeight.isEmpty()) {
            ops.add(new SystemOperation.DoubleOp());
            oldWeight = Optional.of(2);
        } else if (!survivors.stream().allMatch(m -> m.weight().value() == 2)) {
            return Optional.empty();
        }

        if (newWeight.isEmpty()) {
            ops.add(new SystemOperation.Join(newId, config.indexOf(oldId).orElse(config.len())));
            newWeight = Optional.of(0);
        }
        if (newWeight.equals(Optional.of(0))) {
            ops.add(new SystemOperation.Increment(newId));
            newWeight = Optional.of(1);
        }
        if (oldWeight.isPresent()) {
            for (int i = 0; i < oldWeight.get(); i++) {
                ops.add(new SystemOperation.Decrement(oldId));
            }
            ops.add(new SystemOperation.Leave(oldId));
        }
        if (newWeight.equals(Optional.of(1))) {
            ops.add(new SystemOperation.Increment(newId));
        }
        ops.add(new SystemOperation.HalveOp());

        try {
            List<EraStep> planned = config.plan(ops);
            List<SystemOperation> result = planned.stream()
                    .map(step -> (SystemOperation) new SystemOperation.Batch(step.ops()))
                    .toList();
            return Optional.of(result);
        } catch (ConfigException e) {
            return Optional.empty();
        }
    }

    public static List<EraStep> solveReplacement(
            Configuration current,
            NodeId oldId,
            NodeId newId,
            List<NodeId> live,
            View view
    ) throws SolveException {
        if (oldId.equals(newId)
                || current.weightOf(oldId).isEmpty()
                || current.weightOf(newId).isPresent()) {
            throw new SolveException(new SolveError.InvalidReplacement());
        }

        Snapshot snapshot = current.toSnapshot();
        List<Member> replacedOrder = new ArrayList<>();
        for (Member m : snapshot.order()) {
            if (m.node().equals(oldId)) {
                replacedOrder.add(new Member(newId, m.weight()));
            } else {
                replacedOrder.add(m);
            }
        }
        Configuration target;
        try {
            target = new Snapshot(snapshot.era(), replacedOrder).inflate();
        } catch (ConfigException e) {
            throw new SolveException(new SolveError.ConfigurationError(e.error()));
        }

        checkAvailable(current, live, false);
        checkAvailable(target, live, true);

        List<SystemOperation> forced = forcedSteps(current, oldId, newId);
        Configuration c = current;
        List<EraStep> steps = new ArrayList<>();
        boolean valid = true;

        for (SystemOperation op : forced) {
            try {
                Configuration next = c.apply(op, Slot.NONE);
                if (availableMass(next, live) * 2 > next.total()) {
                    List<SystemOperation> subOps = (op instanceof SystemOperation.Batch b) ? b.ops() : List.of(op);
                    steps.add(new EraStep(subOps, next));
                    c = next;
                } else {
                    valid = false;
                    break;
                }
            } catch (ConfigException e) {
                valid = false;
                break;
            }
        }

        if (valid && c.order().equals(target.order())) {
            return withNominations(steps, current, view);
        } else {
            return solve(current, target, live, view);
        }
    }
}
