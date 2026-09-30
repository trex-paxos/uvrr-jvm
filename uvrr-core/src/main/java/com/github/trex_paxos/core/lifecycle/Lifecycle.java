// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.lifecycle;

import com.github.trex_paxos.core.ids.NodeId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Typestate driver and boot gate for the lifecycle state machine.
public final class Lifecycle {

    private Lifecycle() {}

    /// What the boot read decided.
    public sealed interface BootOutcome {
        record First(FirstSession session) implements BootOutcome {}
        record Clean(CleanSession session) implements BootOutcome {}
        record Crashed(CrashedSession session) implements BootOutcome {}
    }

    /// The first life: no durable identity yet.
    public static final class FirstSession {
        private final LifecycleStore store;

        public FirstSession(LifecycleStore store) {
            this.store = Objects.requireNonNull(store, "store cannot be null");
        }

        public LifecycleStore store() {
            return store;
        }

        public RunningSession latch(NodeId identity) throws Exception {
            List<CopyState> list = new ArrayList<>(4);
            for (int i = 0; i < 4; i++) {
                list.add(new CopyState(identity, Marker.JOINING));
            }
            store.commit(new SuperblockCopies(list));
            return new RunningSession(store);
        }
    }

    /// Clean start after a vouched controlled halt.
    public static final class CleanSession {
        private final LifecycleStore store;
        private final Vouched vouched;
        private final SuperblockCopies copies;

        public CleanSession(LifecycleStore store, Vouched vouched, SuperblockCopies copies) {
            this.store = Objects.requireNonNull(store, "store cannot be null");
            this.vouched = Objects.requireNonNull(vouched, "vouched cannot be null");
            this.copies = Objects.requireNonNull(copies, "copies cannot be null");
        }

        public Vouched vouched() {
            return vouched;
        }

        public RunningSession latch() throws Exception {
            store.commit(copies);
            return new RunningSession(store);
        }
    }

    /// Crashed identity awaiting reincarnation and seating.
    public static final class CrashedSession {
        private final LifecycleStore store;
        private final Bumped pair;
        private final SuperblockCopies copies;

        public CrashedSession(LifecycleStore store, Bumped pair, SuperblockCopies copies) {
            this.store = Objects.requireNonNull(store, "store cannot be null");
            this.pair = Objects.requireNonNull(pair, "pair cannot be null");
            this.copies = Objects.requireNonNull(copies, "copies cannot be null");
        }

        public Bumped pair() {
            return pair;
        }

        public RunningSession latch(Rejoined token) throws Exception {
            Objects.requireNonNull(token, "token cannot be null");
            store.commit(copies);
            return new RunningSession(store);
        }
    }

    /// A running node.
    public static final class RunningSession {
        private final LifecycleStore store;

        public RunningSession(LifecycleStore store) {
            this.store = Objects.requireNonNull(store, "store cannot be null");
        }

        public LifecycleStore store() {
            return store;
        }

        public HaltingSession beginHalt(SuperblockCopies currentCopies) throws Exception {
            SuperblockCopies stopping = currentCopies.beginStop();
            store.commit(stopping);
            return new HaltingSession(store, stopping);
        }
    }

    /// Halting node waiting to drain.
    public static final class HaltingSession {
        private final LifecycleStore store;
        private final SuperblockCopies stoppingCopies;

        public HaltingSession(LifecycleStore store, SuperblockCopies stoppingCopies) {
            this.store = Objects.requireNonNull(store, "store cannot be null");
            this.stoppingCopies = Objects.requireNonNull(stoppingCopies, "stoppingCopies cannot be null");
        }

        public DrainingSession drain() throws Exception {
            store.drain();
            return new DrainingSession(store, stoppingCopies);
        }
    }

    /// Draining node ready to finish halt.
    public static final class DrainingSession {
        private final LifecycleStore store;
        private final SuperblockCopies stoppingCopies;

        public DrainingSession(LifecycleStore store, SuperblockCopies stoppingCopies) {
            this.store = Objects.requireNonNull(store, "store cannot be null");
            this.stoppingCopies = Objects.requireNonNull(stoppingCopies, "stoppingCopies cannot be null");
        }

        public HaltedSession finishHalt() throws Exception {
            SuperblockCopies stopped = stoppingCopies.finishStop();
            store.commit(stopped);
            return new HaltedSession(store);
        }
    }

    /// Halted node.
    public static final class HaltedSession {
        private final LifecycleStore store;

        public HaltedSession(LifecycleStore store) {
            this.store = Objects.requireNonNull(store, "store cannot be null");
        }

        public LifecycleStore store() {
            return store;
        }
    }

    /// Boot classification driving the typestate path.
    public static BootOutcome boot(LifecycleStore store) throws Exception {
        Objects.requireNonNull(store, "store cannot be null");
        Optional<SuperblockCopies> opt = store.readCopies();
        if (opt.isEmpty()) {
            return new BootOutcome.First(new FirstSession(store));
        }

        SuperblockCopies copies = opt.get();
        SuperblockCopies.RestartResult result = copies.restart();
        return switch (result.decision()) {
            case RestartDecision.Continue(var identity) ->
                    new BootOutcome.Clean(new CleanSession(store, new Vouched(identity), result.copies()));
            case RestartDecision.Bump(var oldId, var newId) ->
                    new BootOutcome.Crashed(new CrashedSession(store, new Bumped(oldId, newId), result.copies()));
        };
    }
}
