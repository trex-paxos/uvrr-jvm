// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.plan;

import com.github.trex_paxos.core.configuration.ConfigException;
import com.github.trex_paxos.core.configuration.Configuration;
import com.github.trex_paxos.core.configuration.Member;
import com.github.trex_paxos.core.configuration.SystemOperation;
import com.github.trex_paxos.core.ids.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// A computed reconfiguration: initial membership and one batch of operations per era.
public record Plan(
        List<Member> initial,
        List<List<SystemOperation>> steps
) {
    public Plan {
        Objects.requireNonNull(initial, "initial cannot be null");
        Objects.requireNonNull(steps, "steps cannot be null");
        initial = List.copyOf(initial);
        List<List<SystemOperation>> copy = new ArrayList<>(steps.size());
        for (List<SystemOperation> step : steps) {
            copy.add(List.copyOf(step));
        }
        steps = List.copyOf(copy);
    }

    /// Checks the plan against current committed configuration.
    public Optional<PlanRejection> validateAgainst(Configuration current) {
        Objects.requireNonNull(current, "current cannot be null");
        List<Member> committed = current.order();

        if (initial.size() != committed.size()) {
            return Optional.of(new PlanRejection.InitialMembership(initial, committed));
        }

        for (int i = 0; i < initial.size(); i++) {
            if (!initial.get(i).node().equals(committed.get(i).node())) {
                return Optional.of(new PlanRejection.InitialMembership(initial, committed));
            }
        }

        for (int i = 0; i < initial.size(); i++) {
            if (!initial.get(i).weight().equals(committed.get(i).weight())) {
                return Optional.of(new PlanRejection.InitialWeight(
                        initial.get(i).node(),
                        initial.get(i).weight(),
                        committed.get(i).weight()
                ));
            }
        }

        Configuration config = current;
        for (int i = 0; i < steps.size(); i++) {
            List<SystemOperation> ops = steps.get(i);
            try {
                config = config.apply(new SystemOperation.Batch(ops), Slot.NONE);
            } catch (ConfigException e) {
                return Optional.of(new PlanRejection.Step(i, e.error()));
            }
        }

        return Optional.empty();
    }
}
