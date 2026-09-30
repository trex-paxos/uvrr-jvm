// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.plan;

import com.github.trex_paxos.core.configuration.ConfigError;
import com.github.trex_paxos.core.configuration.Member;
import com.github.trex_paxos.core.configuration.Weight;
import com.github.trex_paxos.core.ids.NodeId;

import java.util.List;
import java.util.Objects;

/// Why a plan may not be stepped through.
public sealed interface PlanRejection {

    record InitialMembership(List<Member> plan, List<Member> committed) implements PlanRejection {
        public InitialMembership {
            plan = List.copyOf(plan);
            committed = List.copyOf(committed);
        }
    }

    record InitialWeight(NodeId node, Weight plan, Weight committed) implements PlanRejection {
        public InitialWeight {
            Objects.requireNonNull(node, "node cannot be null");
            Objects.requireNonNull(plan, "plan cannot be null");
            Objects.requireNonNull(committed, "committed cannot be null");
        }
    }

    record Step(int index, ConfigError refusal) implements PlanRejection {
        public Step {
            Objects.requireNonNull(refusal, "refusal cannot be null");
        }
    }
}
