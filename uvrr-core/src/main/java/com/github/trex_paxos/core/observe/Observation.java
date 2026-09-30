// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.observe;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/// Lockless observation of published progress from outside the transition interval.
public final class Observation<T> {

    private final AtomicReference<T> current;

    public Observation(T initial) {
        Objects.requireNonNull(initial, "initial cannot be null");
        this.current = new AtomicReference<>(initial);
    }

    public void write(T snapshot) {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        current.set(snapshot);
    }

    public T read() {
        return current.get();
    }
}
