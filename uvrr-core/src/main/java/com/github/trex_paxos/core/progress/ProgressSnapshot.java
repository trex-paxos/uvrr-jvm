// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.progress;

/// Plain-old-data projection of Progress published for observation (B1, §12).
public record ProgressSnapshot(
    long era,
    long view,
    long retainedEra,
    long retainedView,
    int status,
    boolean faulted,
    long accepted,
    long committed,
    long applied,
    long checkpoint,
    long revision) {}
