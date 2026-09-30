// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.effects;

import com.github.trex_paxos.core.ids.Era;
import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.ids.OperationId;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.message.Message;

import java.util.Arrays;
import java.util.Objects;

/// What a published transition asks the host to do. The core performs none of
/// it (SANS-I/O); the host owns transport, storage, and the application.
public sealed interface Effect {

    /// Send `message` to `to`. `era` is the era authorizing THIS copy: overlap
    /// mode sends era `e` to one set and era `e+1` to another (§8.7.7), so the
    /// era is a transport-visible fact the host must not have to decode (W1).
    record Send(NodeId to, Era era, Message message) implements Effect {
        public Send {
            Objects.requireNonNull(to, "to cannot be null");
            Objects.requireNonNull(era, "era cannot be null");
            Objects.requireNonNull(message, "message cannot be null");
        }
    }

    /// Apply the committed operation at `slot` to the host application, in
    /// slot order (§11.1). The operation's identity rides along exactly as
    /// the proposing host assigned it: the core never inspects it and never
    /// deduplicates on it.
    record Apply(Slot slot, OperationId operationId, byte[] payload) implements Effect {
        public Apply {
            Objects.requireNonNull(slot, "slot cannot be null");
            Objects.requireNonNull(operationId, "operationId cannot be null");
            Objects.requireNonNull(payload, "payload cannot be null");
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Apply apply = (Apply) o;
            return slot.equals(apply.slot)
                    && operationId.equals(apply.operationId)
                    && Arrays.equals(payload, apply.payload);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(slot, operationId);
            result = 31 * result + Arrays.hashCode(payload);
            return result;
        }

        @Override
        public String toString() {
            return "Apply[slot=" + slot + ", operationId=" + operationId + ", payloadLen=" + payload.length + "]";
        }
    }

    /// What must be stable before this transition may publish (S2/S3).
    record Persist(PersistenceIntent intent) implements Effect {
        public Persist {
            Objects.requireNonNull(intent, "intent cannot be null");
        }
    }

    /// The verdict on a plan submitted over the node's admin ingress: the host renders it
    /// as the one JSON response line back to the operator.
    record AdminResponse(PlanVerdict verdict) implements Effect {
        public AdminResponse {
            Objects.requireNonNull(verdict, "verdict cannot be null");
        }
    }
}
