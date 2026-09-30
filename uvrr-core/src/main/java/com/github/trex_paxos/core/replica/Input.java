// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.replica;

import com.github.trex_paxos.core.configuration.SystemOperation;
import com.github.trex_paxos.core.effects.StabilityResult;
import com.github.trex_paxos.core.ids.Ballot;
import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.ids.Operation;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.invariant.InputKind;
import com.github.trex_paxos.core.message.Message;
import com.github.trex_paxos.core.plan.Plan;

import java.util.Objects;
import java.util.Optional;

/// The input alphabet of §6, extended by durability and lifecycle inputs.
public sealed interface Input {

    record Peer(NodeId from, Message message) implements Input {
        public Peer {
            Objects.requireNonNull(from, "from cannot be null");
            Objects.requireNonNull(message, "message cannot be null");
        }
    }

    record Propose(Operation operation) implements Input {
        public Propose {
            Objects.requireNonNull(operation, "operation cannot be null");
        }
    }

    record Tick() implements Input {}

    record StabilityConfirmation(long revision, StabilityResult result) implements Input {
        public StabilityConfirmation {
            Objects.requireNonNull(result, "result cannot be null");
        }
    }

    record Applied(Slot slot) implements Input {
        public Applied {
            Objects.requireNonNull(slot, "slot cannot be null");
        }
    }

    record Checkpointed(Slot through) implements Input {
        public Checkpointed {
            Objects.requireNonNull(through, "through cannot be null");
        }
    }

    record Reconfigure(SystemOperation op, Optional<Pivot> pivot) implements Input {
        public Reconfigure {
            Objects.requireNonNull(op, "op cannot be null");
            Objects.requireNonNull(pivot, "pivot cannot be null");
        }
    }

    record AdminForceView(Ballot target) implements Input {
        public AdminForceView {
            Objects.requireNonNull(target, "target cannot be null");
        }
    }

    record Reincarnate(NodeId oldId) implements Input {
        public Reincarnate {
            Objects.requireNonNull(oldId, "oldId cannot be null");
        }
    }

    record SubmitPlan(Plan plan) implements Input {
        public SubmitPlan {
            Objects.requireNonNull(plan, "plan cannot be null");
        }
    }

    default InputKind kind() {
        return switch (this) {
            case Peer(var from, var message) -> new InputKind.PeerMessage(message.header().tag(), message.header().slot());
            case Propose _ -> new InputKind.ClientRequest();
            case Tick _ -> new InputKind.Tick();
            case StabilityConfirmation _ -> new InputKind.StabilityConfirmed();
            case Applied _ -> new InputKind.Applied();
            case Checkpointed _ -> new InputKind.Checkpointed();
            case Reconfigure _ -> new InputKind.Reconfiguration();
            case AdminForceView _, Reincarnate _, SubmitPlan _ -> new InputKind.Admin();
        };
    }
}
