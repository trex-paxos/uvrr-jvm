// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.replica;

import com.github.trex_paxos.core.observe.Diagnostic;
import com.github.trex_paxos.core.observe.Observation;
import com.github.trex_paxos.core.progress.ProgressSnapshot;

import java.util.Objects;

/// A cheap, cloneable read handle onto the replica's published progress (B1).
public final class Observer {

    private final Observation<ProgressSnapshot> shared;
    private final Observation<Diagnostic> diagnostics;

    public Observer(Observation<ProgressSnapshot> shared, Observation<Diagnostic> diagnostics) {
        this.shared = Objects.requireNonNull(shared, "shared cannot be null");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics cannot be null");
    }

    /// The latest published snapshot.
    public ProgressSnapshot read() {
        return shared.read();
    }

    /// The latest published transition's drop outcome.
    public Diagnostic readDiagnostic() {
        return diagnostics.read();
    }
}
