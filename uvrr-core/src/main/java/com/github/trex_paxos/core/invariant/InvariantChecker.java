// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.invariant;

import com.github.trex_paxos.core.ids.Fault;
import com.github.trex_paxos.core.progress.Progress;
import com.github.trex_paxos.core.progress.Status;
import com.github.trex_paxos.core.wire.Tag;

import java.util.Objects;
import java.util.Optional;

/// Mechanical enforcement of the closed invariants.
public final class InvariantChecker {

    private InvariantChecker() {}

    /// The per-tag header-slot table (rule 7 of legal).
    public static HeaderSlotRole headerSlotRole(Tag tag) {
        return switch (tag) {
            case PREPARE, PREPARE_OK, FUSE, FUSE_OK -> HeaderSlotRole.OPERATION;
            case COMMIT, DO_VIEW_CHANGE, START_VIEW, GET_STATE, NEW_STATE, COMMIT_BATCH -> HeaderSlotRole.FRONTIER;
            case START_VIEW_CHANGE, PLANNED_VIEW_CHANGE, REINCARNATION, GOSSIP_REQUEST -> HeaderSlotRole.ABSENT;
        };
    }

    /// The closed transition-legality checker.
    /// Returns Optional.empty() if legal, or Optional.of(Fault) if illegal.
    public static Optional<Fault> legal(Progress oldProgress, Progress newProgress, InputKind input) {
        Objects.requireNonNull(oldProgress, "oldProgress cannot be null");
        Objects.requireNonNull(newProgress, "newProgress cannot be null");
        Objects.requireNonNull(input, "input cannot be null");

        if (oldProgress.fault().isPresent()) {
            return oldProgress.fault();
        }

        if (rule1FrontiersViolated(oldProgress, newProgress)
                || rule2ViewSuccessionViolated(oldProgress, newProgress)
                || rule3RetainedViolated(oldProgress, newProgress, input)
                || rule4RevisionViolated(oldProgress, newProgress)
                || rule6EraSlotViolated(newProgress)
                || rule7HeaderSlotViolated(input)) {
            return Optional.of(Fault.ILLEGAL_TRANSITION);
        }

        return Optional.empty();
    }

    private static boolean rule1FrontiersViolated(Progress oldProgress, Progress newProgress) {
        try {
            newProgress.checkFrontierChain();
        } catch (Exception e) {
            return true;
        }

        if (newProgress.committed().compareTo(oldProgress.committed()) < 0
                || newProgress.applied().compareTo(oldProgress.applied()) < 0
                || newProgress.checkpoint().compareTo(oldProgress.checkpoint()) < 0) {
            return true;
        }

        return newProgress.accepted().compareTo(oldProgress.accepted()) < 0
                && newProgress.retained().equals(oldProgress.retained());
    }

    private static boolean rule2ViewSuccessionViolated(Progress oldProgress, Progress newProgress) {
        return !newProgress.current().equals(oldProgress.current())
                && !oldProgress.current().isLegalSuccessor(newProgress.current());
    }

    private static boolean rule3RetainedViolated(Progress oldProgress, Progress newProgress, InputKind input) {
        if (newProgress.retained().equals(oldProgress.retained())) {
            return false;
        }

        if (newProgress.status() == Status.NORMAL && newProgress.retained().equals(newProgress.current())) {
            return false;
        }

        boolean reselects = switch (input) {
            case InputKind.PeerMessage(Tag tag, _) -> switch (tag) {
                case DO_VIEW_CHANGE, START_VIEW, NEW_STATE -> true;
                case PREPARE, PREPARE_OK, COMMIT, START_VIEW_CHANGE, PLANNED_VIEW_CHANGE,
                     GET_STATE, REINCARNATION, GOSSIP_REQUEST, FUSE, FUSE_OK, COMMIT_BATCH -> false;
            };
            case InputKind.ClientRequest _, InputKind.Tick _, InputKind.StabilityConfirmed _,
                 InputKind.Applied _, InputKind.Checkpointed _, InputKind.Reconfiguration _,
                 InputKind.Admin _ -> false;
        };

        return !reselects;
    }

    private static boolean rule4RevisionViolated(Progress oldProgress, Progress newProgress) {
        long oldRev = oldProgress.revision();
        if (oldRev == Long.MAX_VALUE) {
            return true;
        }
        return oldRev + 1 != newProgress.revision();
    }

    private static boolean rule6EraSlotViolated(Progress newProgress) {
        try {
            newProgress.checkEraDiscipline();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean rule7HeaderSlotViolated(InputKind input) {
        return switch (input) {
            case InputKind.PeerMessage(Tag tag, var slot) -> !headerSlotRole(tag).admits(slot);
            case InputKind.ClientRequest _, InputKind.Tick _, InputKind.StabilityConfirmed _,
                 InputKind.Applied _, InputKind.Checkpointed _, InputKind.Reconfiguration _,
                 InputKind.Admin _ -> false;
        };
    }
}
