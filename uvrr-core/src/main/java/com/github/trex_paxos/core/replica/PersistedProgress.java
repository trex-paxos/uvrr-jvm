// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.replica;

import com.github.trex_paxos.core.ids.Ballot;
import com.github.trex_paxos.core.ids.Fault;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.progress.Progress;
import com.github.trex_paxos.core.progress.Status;

import java.util.Objects;
import java.util.Optional;

/// The durable half of Progress, as the host persists and returns it (§5).
public record PersistedProgress(
        Ballot current,
        Ballot retained,
        Status status,
        Slot accepted,
        Slot committed,
        Slot applied,
        Slot checkpoint,
        long revision,
        Optional<Fault> fault
) {
    public PersistedProgress {
        Objects.requireNonNull(current, "current cannot be null");
        Objects.requireNonNull(retained, "retained cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(accepted, "accepted cannot be null");
        Objects.requireNonNull(committed, "committed cannot be null");
        Objects.requireNonNull(applied, "applied cannot be null");
        Objects.requireNonNull(checkpoint, "checkpoint cannot be null");
        Objects.requireNonNull(fault, "fault cannot be null");
    }

    public static PersistedProgress from(Progress progress) {
        Objects.requireNonNull(progress, "progress cannot be null");
        return new PersistedProgress(
                progress.current(),
                progress.retained(),
                progress.status(),
                progress.accepted(),
                progress.committed(),
                progress.applied(),
                progress.checkpoint(),
                progress.revision(),
                progress.fault()
        );
    }
}
