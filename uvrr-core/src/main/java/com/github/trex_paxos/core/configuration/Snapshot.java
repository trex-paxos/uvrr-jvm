// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.configuration;

import com.github.trex_paxos.core.ids.Era;
import com.github.trex_paxos.core.ids.NodeId;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// The serialized form of a Configuration.
public record Snapshot(Era era, List<Member> order) {

    public Snapshot {
        Objects.requireNonNull(era, "era cannot be null");
        Objects.requireNonNull(order, "order cannot be null");
        order = List.copyOf(order);
    }

    /// Inflates the snapshot into a Configuration, re-checking every invariant.
    public Configuration inflate() throws ConfigException {
        Set<NodeId> seen = new HashSet<>();
        for (Member member : order) {
            if (!seen.add(member.node())) {
                throw new ConfigException(new ConfigError.DuplicateNode(member.node()));
            }
        }

        for (Member member : order) {
            if (member.weight().value() > Configuration.MAX_WEIGHT) {
                throw new ConfigException(new ConfigError.WeightCapExceeded(member.node(), Configuration.MAX_WEIGHT));
            }
        }

        if (era.equals(Era.INITIAL)) {
            if (!order.isEmpty()) {
                throw new ConfigException(new ConfigError.SnapshotVoidWithMembers());
            }
            return Configuration.voidConfig();
        }

        if (order.isEmpty()) {
            throw new ConfigException(new ConfigError.SnapshotEmptyOrder(era));
        }

        long total = order.stream().mapToLong(m -> m.weight().value()).sum();
        if (total == 0) {
            throw new ConfigException(new ConfigError.SnapshotZeroTotal(era));
        }

        return new Configuration(era, order);
    }
}
