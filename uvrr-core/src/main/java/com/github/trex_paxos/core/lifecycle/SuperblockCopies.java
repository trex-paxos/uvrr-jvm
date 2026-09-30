// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.lifecycle;

import com.github.trex_paxos.core.ids.NodeId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// The four superblock copies a restart reads.
public record SuperblockCopies(List<CopyState> copies) {

    public record Classified(RestartClass restartClass, NodeId identity) {}
    public record RestartResult(RestartDecision decision, SuperblockCopies copies) {}

    public SuperblockCopies {
        Objects.requireNonNull(copies, "copies cannot be null");
        if (copies.size() != 4) {
            throw new IllegalArgumentException("SuperblockCopies must hold exactly 4 copies");
        }
        copies = List.copyOf(copies);
    }

    /// The quorum read.
    public Optional<Classified> classify() {
        record Cohort(int count, int stopped) {}
        Map<NodeId, Cohort> cohorts = new HashMap<>();

        for (CopyState copy : copies) {
            if (copy.identity().value() == 0) {
                continue;
            }
            Cohort existing = cohorts.getOrDefault(copy.identity(), new Cohort(0, 0));
            int stoppedInc = (copy.marker() == Marker.STOPPED) ? 1 : 0;
            cohorts.put(copy.identity(), new Cohort(existing.count + 1, existing.stopped + stoppedInc));
        }

        NodeId bestIdentity = null;
        Cohort bestCohort = null;

        for (Map.Entry<NodeId, Cohort> entry : cohorts.entrySet()) {
            if (entry.getValue().count >= 2) {
                if (bestIdentity == null || entry.getKey().compareTo(bestIdentity) > 0) {
                    bestIdentity = entry.getKey();
                    bestCohort = entry.getValue();
                }
            }
        }

        if (bestIdentity == null) {
            return Optional.empty();
        }

        RestartClass restartClass = (bestCohort.stopped >= 2) ? RestartClass.STOPPED : RestartClass.NOT_STOPPED;
        return Optional.of(new Classified(restartClass, bestIdentity));
    }

    /// A restart: quorum read, decision, and rewritten copies.
    public RestartResult restart() throws LifecycleException {
        Classified classified = classify().orElseThrow(() -> new LifecycleException(new RestartRefusal.QuorumLost()));
        return switch (classified.restartClass()) {
            case STOPPED -> new RestartResult(
                    new RestartDecision.Continue(classified.identity()),
                    rewrite(classified.identity(), Marker.RESTARTING)
            );
            case NOT_STOPPED -> {
                NodeId newId = classified.identity().nextLife()
                        .orElseThrow(() -> new LifecycleException(new RestartRefusal.Exhausted(classified.identity())));
                yield new RestartResult(
                        new RestartDecision.Bump(classified.identity(), newId),
                        rewrite(newId, Marker.JOINING)
                );
            }
        };
    }

    /// The stop command: Running -> Stopping.
    public SuperblockCopies beginStop() {
        return rewrite(readIdentity(), Marker.STOPPING);
    }

    /// The drain's proof: Stopping -> Stopped.
    public SuperblockCopies finishStop() {
        return rewrite(readIdentity(), Marker.STOPPED);
    }

    private NodeId readIdentity() {
        return copies.stream()
                .map(CopyState::identity)
                .max(NodeId::compareTo)
                .orElseThrow();
    }

    private SuperblockCopies rewrite(NodeId identity, Marker marker) {
        List<CopyState> list = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            list.add(new CopyState(identity, marker));
        }
        return new SuperblockCopies(list);
    }
}
