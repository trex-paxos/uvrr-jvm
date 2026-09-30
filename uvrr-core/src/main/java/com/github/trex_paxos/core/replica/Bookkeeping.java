// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.replica;

import com.github.trex_paxos.core.ids.Ballot;
import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.ids.Tick;
import com.github.trex_paxos.core.message.Message;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Volatile bookkeeping updates applied at transition installation.
record Bookkeeping(
        List<Map.Entry<Slot, List<NodeId>>> proposals,
        Optional<ViewChangeUpdate> viewChange,
        Optional<TransferUpdate> transfer,
        Optional<StalledUpdate> stalled,
        Optional<Tick> activity,
        Optional<List<NodeId>> witnesses
) {
    public static final Bookkeeping EMPTY = new Bookkeeping(
            List.of(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
    );

    public sealed interface ViewChangeUpdate {
        record Unchanged() implements ViewChangeUpdate {}
        record Set(ViewChangeVolatile state) implements ViewChangeUpdate {}
        record Clear() implements ViewChangeUpdate {}
    }

    public sealed interface TransferUpdate {
        record Unchanged() implements TransferUpdate {}
        record Set(TransferVolatile state) implements TransferUpdate {}
        record Clear() implements TransferUpdate {}
    }

    public sealed interface StalledUpdate {
        record Unchanged() implements StalledUpdate {}
        record Set(StalledStartView state) implements StalledUpdate {}
        record Clear() implements StalledUpdate {}
    }

    public record TransferVolatile(Ballot view, NodeId to, Slot next) {}
    public record StalledStartView(NodeId from, Message message) {}
    public record ViewChangeVolatile(
            Ballot target,
            java.util.Set<NodeId> fences,
            Map<NodeId, Replica.Evidence> evidence,
            Optional<Map.Entry<NodeId, Replica.Evidence>> selected
    ) {}
}
