// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.invariant;

import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.wire.Tag;

import java.util.Objects;

/// What drove a transition, summarised to exactly what the legality rules need.
public sealed interface InputKind {

    /// A protocol datagram from a peer, summarised by its header (W1).
    record PeerMessage(Tag tag, Slot slot) implements InputKind {
        public PeerMessage {
            Objects.requireNonNull(tag, "tag cannot be null");
            Objects.requireNonNull(slot, "slot cannot be null");
        }
    }

    /// An operation proposed to the primary by the host (§6, §11.1).
    record ClientRequest() implements InputKind {}

    /// A host timer event.
    record Tick() implements InputKind {}

    /// The host confirmed the stability of a persistence intent (§7, S2).
    record StabilityConfirmed() implements InputKind {}

    /// The application acknowledged an applied slot (§11.1).
    record Applied() implements InputKind {}

    /// The host checkpointed application state.
    record Checkpointed() implements InputKind {}

    /// A reconfiguration operation committed (§8.7.2).
    record Reconfiguration() implements InputKind {}

    /// The host acted on the node directly (§12's host-declared fault, shutdown, fencing).
    record Admin() implements InputKind {}
}
