// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.harness;

import com.github.trex_paxos.core.configuration.EraTable;
import com.github.trex_paxos.core.effects.Effect;
import com.github.trex_paxos.core.effects.PlanVerdict;
import com.github.trex_paxos.core.effects.Stability;
import com.github.trex_paxos.core.effects.StabilityResult;
import com.github.trex_paxos.core.ids.Ballot;
import com.github.trex_paxos.core.ids.CrashCounter;
import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.ids.Operation;
import com.github.trex_paxos.core.ids.OperationId;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.ids.SystemId;
import com.github.trex_paxos.core.ids.Tick;
import com.github.trex_paxos.core.journal.InMemoryLog;
import com.github.trex_paxos.core.journal.LogEntry;
import com.github.trex_paxos.core.lifecycle.Bumped;
import com.github.trex_paxos.core.lifecycle.CopyState;
import com.github.trex_paxos.core.lifecycle.Lifecycle;
import com.github.trex_paxos.core.lifecycle.Lifecycle.RunningSession;
import com.github.trex_paxos.core.lifecycle.Marker;
import com.github.trex_paxos.core.lifecycle.Rejoined;
import com.github.trex_paxos.core.lifecycle.SuperblockCopies;
import com.github.trex_paxos.core.lifecycle.Vouched;
import com.github.trex_paxos.core.message.Body;
import com.github.trex_paxos.core.message.Message;
import com.github.trex_paxos.core.plan.Plan;
import com.github.trex_paxos.core.progress.ProgressSnapshot;
import com.github.trex_paxos.core.progress.Status;
import com.github.trex_paxos.core.quorum.WeightedMajority;
import com.github.trex_paxos.core.replica.Input;
import com.github.trex_paxos.core.replica.LifecycleRefusal;
import com.github.trex_paxos.core.replica.Observer;
import com.github.trex_paxos.core.replica.PersistedProgress;
import com.github.trex_paxos.core.replica.PlanRefusal;
import com.github.trex_paxos.core.replica.PlannedTransition;
import com.github.trex_paxos.core.replica.PublishOutcome;
import com.github.trex_paxos.core.replica.PublishRefusal;
import com.github.trex_paxos.core.replica.Replica;
import com.github.trex_paxos.core.replica.ReplicaLifecycleException;
import com.github.trex_paxos.core.replica.ReplicaPlanException;
import com.github.trex_paxos.core.replica.ReplicaPublishException;
import com.github.trex_paxos.core.replica.TimedInput;
import com.github.trex_paxos.core.replica.ViewChangeKnobs;
import com.github.trex_paxos.core.wire.Tag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.IntStream;

/// The scripted step-through harness: the deterministic host the Definition of
/// Done runs on.
///
/// The harness is the host (`docs/architecture.md`: the host owns time,
/// transport, storage and packetization). It drives `Replica`s through the
/// plan/publish pipeline one explicit step at a time, a delivery, an injection,
/// a tick, a restart, and the *script* is the only source of nondeterminism: no
/// threads, no clock reads, no RNG. Same script, same trace, every run, on every
/// platform.
///
/// The legality gate runs after every step: any node that faulted since the last
/// scan must have been declared in advance with `expectFault`, and an undeclared
/// fault fails the step carrying the full trace. "Illegal transition forces the
/// node crashed" is made loud rather than accepted silently.
///
/// Mirror of the Rust reference harness at `lua-lunet/uvrr-core`
/// `tests/harness/mod.rs`.
public final class Harness {

    /// The trace ring's capacity. Bounded, so a runaway script cannot exhaust
    /// memory; the dump carries the most recent steps, which is where a failure
    /// lives.
    public static final int TRACE_CAPACITY = 4_096;

    /// The number of marker copies one gate holds. Four, always: the boot gate's
    /// quorum is two of four.
    public static final int GATE_COPIES = 4;

    /// One live node: the replica, its observation handle, the effects released
    /// but not yet executed by the harness, and the one outstanding persistence
    /// intent an external-stability mode parks behind (S2).
    static final class Node {
        final Replica<InMemoryLog, WeightedMajority> replica;
        final Observer observer;
        final List<Effect> pendingEffects = new ArrayList<>();
        Long outstandingIntent;
        final List<PlanVerdict> adminVerdicts = new ArrayList<>();

        Node(Replica<InMemoryLog, WeightedMajority> replica) {
            this.replica = Objects.requireNonNull(replica, "replica cannot be null");
            this.observer = replica.observer();
        }
    }

    /// What the harness's "disk" recorded at crash time: the published progress
    /// record, the journal and the configuration. A parked candidate of an
    /// outstanding intent is volatile and dies with the node, correctly: in an
    /// external-stability mode nothing unpersisted is durable.
    record Disk(PersistedProgress persisted, TreeMap<Slot, LogEntry> journal, EraTable config) {}

    /// What one step did to a node. Refusals are data, not failures: a script
    /// asserts them, and every refusal is a named variant.
    public sealed interface StepOutcome {
        /// The transition installed and released these effects (volatile, or a
        /// completion).
        record Published(long revision, List<Effect> effects) implements StepOutcome {}

        /// The transition is parked behind an outstanding persistence intent
        /// (S2). The released effects are the intent itself.
        record Parked(long revision) implements StepOutcome {}

        record PlanRefused(PlanRefusal refusal) implements StepOutcome {}

        record PublishRefused(PublishRefusal refusal) implements StepOutcome {}

        /// The node is down, or the identity is outside the cluster: the host
        /// cannot deliver to it. Traffic to a down node is recorded and counted.
        record NodeDown() implements StepOutcome {}

        record LifecycleRefused(LifecycleRefusal refusal) implements StepOutcome {}

        /// The identity is not provisioned and cannot be: a crash shape the
        /// script declared as refused.
        record Refused(String reason) implements StepOutcome {}
    }

    /// One delivery, as the trace and the compliance multiset see it.
    public record DeliveryOutcome(NodeId from, NodeId to, StepOutcome outcome) {}

    /// One host-clock tick to every live node, in slot order, then one drain.
    public record TickAllOutcome(List<DeliveryOutcome> ticks, List<DeliveryOutcome> deliveries) {}

    /// One executed apply, as the harness records it: the slot, the operation's
    /// identity, and the payload bytes. The apply log is the second pair of eyes
    /// on committed-prefix agreement.
    public record AppliedRecord(Slot slot, OperationId operationId, byte[] payload) {
        public AppliedRecord {
            Objects.requireNonNull(slot, "slot cannot be null");
            Objects.requireNonNull(operationId, "operationId cannot be null");
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AppliedRecord that)) return false;
            return slot.equals(that.slot)
                    && operationId.equals(that.operationId)
                    && Arrays.equals(payload, that.payload);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * slot.hashCode() + operationId.hashCode()) + Arrays.hashCode(payload);
        }

        @Override
        public String toString() {
            return "Applied[slot=" + slot.value()
                    + ", operationId=" + operationId
                    + ", payloadLen=" + payload.length + "]";
        }
    }

    private final List<Node> nodes = new ArrayList<>();
    private final Map<NodeId, Integer> slots = new HashMap<>();
    private final List<NodeId> slotIds = new ArrayList<>();
    private final List<List<AppliedRecord>> applied = new ArrayList<>();
    private final List<Optional<Disk>> disks = new ArrayList<>();
    private final List<List<String>> gateLogs = new ArrayList<>();
    private final List<Optional<RunningSession>> sessions = new ArrayList<>();
    private final List<Boolean> declaredFaults = new ArrayList<>();
    private final List<Boolean> faultedKnown = new ArrayList<>();
    private final Deque<String> trace = new ArrayDeque<>();
    private final Network network = new Network();
    private final Path gateRoot;

    private Tick tick;
    private final Stability stability;
    private final ViewChangeKnobs knobs;
    private long stepSeq;
    private long opSeq;

    private Harness(Tick start, Stability stability, ViewChangeKnobs knobs, Path gateRoot) {
        this.tick = Objects.requireNonNull(start, "start cannot be null");
        this.stability = Objects.requireNonNull(stability, "stability cannot be null");
        this.knobs = Objects.requireNonNull(knobs, "knobs cannot be null");
        this.gateRoot = Objects.requireNonNull(gateRoot, "gateRoot cannot be null");
    }

    /// A fresh cluster of `n` members under the default knobs, starting at tick
    /// zero. The roster identities are deterministic: member `i + 1` is
    /// `(i + 1):1`, packed.
    public static Harness provision(int n) {
        return provision(n, new ViewChangeKnobs(3, Integer.MAX_VALUE), Stability.VOLATILE, new Tick(0));
    }

    /// A fresh cluster under explicit knobs and stability, for the suites that
    /// exercise the deferred modes and the timeout policy.
    public static Harness provision(int n, ViewChangeKnobs knobs, Stability stability, Tick start) {
        var harness = new Harness(start, stability, knobs, createGateRoot());
        harness.assemble(n);
        return harness;
    }

    private static Path createGateRoot() {
        try {
            return Files.createTempDirectory("uvrr-harness-gate-");
        } catch (IOException e) {
            throw new UncheckedIOException("the gate root could not be created", e);
        }
    }

    /// Assembles `n` replicas over one genesis history, in roster order, each
    /// with its own journal and gate.
    private void assemble(int n) {
        var order = new ArrayList<NodeId>(n);
        for (var index = 0; index < n; index++) {
            order.add(new NodeId(new SystemId(index + 1), new CrashCounter(1)));
        }
        for (var id : order) {
            try {
                var replica = Replica.provision(
                        id, order, WeightedMajority.INSTANCE, new InMemoryLog(), stability, knobs);
                addNode(id, replica);
                latchFirst(id);
            } catch (Exception e) {
                throw new IllegalStateException("the cluster could not be provisioned", e);
            }
        }
    }

    private void addNode(NodeId id, Replica<InMemoryLog, WeightedMajority> replica) {
        var index = nodes.size();
        slots.put(id, index);
        slotIds.add(id);
        nodes.add(new Node(replica));
        applied.add(new ArrayList<>());
        disks.add(Optional.empty());
        gateLogs.add(new ArrayList<>());
        sessions.add(Optional.empty());
        declaredFaults.add(false);
        faultedKnown.add(replica.progress().fault().isPresent());
        try {
            TmpGate.open(gateDir(index), gateLogs.get(index));
        } catch (IOException e) {
            throw new UncheckedIOException("the gate for " + id + " could not be opened", e);
        }
    }

    private Path gateDir(int index) {
        return gateRoot.resolve("n" + index);
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /// The harness clock. Only `tick` and `tickAll` advance it.
    public Tick now() {
        return tick;
    }

    /// The configured stability every node runs.
    public Stability stability() {
        return stability;
    }

    /// The view-change knobs every node runs (W5). They thread through restarts,
    /// because a restarted node plays by the cluster's rules, not fresh ones.
    public ViewChangeKnobs knobs() {
        return knobs;
    }

    public boolean isUp(NodeId id) {
        var index = slots.get(id);
        return index != null && nodes.get(index) != null;
    }

    /// The roster: every identity the cluster knows, in slot order.
    public List<NodeId> roster() {
        return List.copyOf(slotIds);
    }

    /// The genesis configuration the harness provisioned.
    public List<NodeId> genesisOrder() {
        var order = new ArrayList<NodeId>();
        for (var index = 0; index < nodes.size(); index++) {
            if (disks.get(index).isEmpty() && nodes.get(index) != null) {
                order.add(slotIds.get(index));
            }
        }
        return order;
    }

    public Optional<ProgressSnapshot> snapshot(NodeId id) {
        var index = slots.get(id);
        if (index == null || nodes.get(index) == null) {
            return Optional.empty();
        }
        return Optional.of(nodes.get(index).replica.progress().toSnapshot());
    }

    /// The node's status, empty when it is down.
    public Optional<Status> status(NodeId id) {
        return snapshot(id).map(s -> Status.fromWord(s.status()).orElseThrow());
    }

    public Optional<Ballot> currentView(NodeId id) {
        return snapshot(id).map(s -> new Ballot(new com.github.trex_paxos.core.ids.Era(s.era()),
                new com.github.trex_paxos.core.ids.View(s.view())));
    }

    public List<LogEntry> journalEntries(NodeId id) {
        var index = slots.get(id);
        if (index == null || nodes.get(index) == null) {
            return List.of();
        }
        var view = nodes.get(index).replica.journal().view();
        var bounds = view.retained();
        if (bounds.first().value() == 0 || bounds.last().value() == 0) {
            return List.of();
        }
        return view.iterRange(bounds.first(), bounds.last());
    }

    public List<AppliedRecord> applied(NodeId id) {
        var index = slots.get(id);
        return index == null ? List.of() : List.copyOf(applied.get(index));
    }

    public List<NodeId> witnesses(NodeId id) {
        var index = slots.get(id);
        return index == null || nodes.get(index) == null
                ? List.of()
                : List.copyOf(nodes.get(index).replica.witnesses());
    }

    public Optional<com.github.trex_paxos.core.observe.Diagnostic> diagnostic(NodeId id) {
        var index = slots.get(id);
        if (index == null || nodes.get(index) == null) {
            return Optional.empty();
        }
        return Optional.of(nodes.get(index).observer.readDiagnostic());
    }

    public List<Network.Envelope> queued() {
        return network.queued();
    }

    public List<Network.Envelope> held() {
        return network.held();
    }

    public int queuedLen() {
        return network.queuedLen();
    }

    public int heldLen() {
        return network.heldLen();
    }

    public long undeliverableCount() {
        return network.undeliverableCount();
    }

    public long droppedCount() {
        return network.droppedCount();
    }

    /// The datagrams queued for `id`, in queue order.
    public List<Network.Envelope> queuedFor(NodeId id) {
        return network.queued().stream().filter(Network.addressedTo(id)).toList();
    }

    /// The datagrams queued for `id` with the given tag and header slot.
    public List<Network.Envelope> queuedFor(NodeId id, Tag tag, Slot slot) {
        return network.queued().stream().filter(Network.addressedTo(id, tag, slot)).toList();
    }

    /// The gate's operation log for `id`, in order.
    public List<String> gateOps(NodeId id) {
        var index = slots.get(id);
        return index == null ? List.of() : List.copyOf(gateLogs.get(index));
    }

    /// The marker schedule as the compliance grammar states it: each round the
    /// gate wrote, interleaved with the `drain` the halt schedule forces between
    /// its rounds.
    public List<String> markerSchedule(NodeId id) {
        return TmpGate.schedule(gateOps(id));
    }

    public List<PlanVerdict> adminVerdicts(NodeId id) {
        var index = slots.get(id);
        return index == null ? List.of() : List.copyOf(nodes.get(index).adminVerdicts);
    }

    /// The step trace, in order.
    public List<String> stepTrace() {
        return List.copyOf(trace);
    }

    /// The step trace as one string, as printed on failure.
    public String traceDump() {
        return String.join("\n", trace);
    }

    // ------------------------------------------------------------------
    // The step machinery
    // ------------------------------------------------------------------

    /// One step: plan, publish, route the released effects, record the trace
    /// line, run the legality gate. Every input a node receives comes through
    /// here, so the gate stands after every step by construction.
    public StepOutcome drive(NodeId id, String summary, Input input) {
        var index = slots.get(id);
        if (index == null) {
            record(summary + " -> Refused(unprovisioned identity)");
            return new StepOutcome.Refused("unprovisioned identity: " + id);
        }
        if (nodes.get(index) == null) {
            record(summary + " -> NodeDown");
            return new StepOutcome.NodeDown();
        }
        var node = nodes.get(index);
        var timed = new TimedInput(tick, input);
        StepOutcome outcome;
        List<Effect> released = List.of();
        try {
            var acceptedBefore = node.replica.progress().accepted();
            PlannedTransition planned = node.replica.plan(timed, node.replica.journal().view());
            PublishOutcome published = node.replica.publish(planned);
            switch (published) {
                case PublishOutcome.Published(var revision, var effects) -> {
                    released = effects;
                    outcome = new StepOutcome.Published(revision, effects);
                }
                case PublishOutcome.Parked(var revision, var effects) -> {
                    released = effects;
                    outcome = new StepOutcome.Parked(revision);
                }
            }
            if (node.replica.progress().accepted().compareTo(acceptedBefore) > 0) {
                // Opportunistic reclamation (S1): an append is the lazy moment,
                // and the published checkpoint is the sole authorization; with
                // none, this is a no-op however old the history.
                reclaimedSlots.add(node.replica.progress().accepted().value());
            }
        } catch (ReplicaPlanException e) {
            outcome = new StepOutcome.PlanRefused(e.refusal());
        } catch (ReplicaPublishException e) {
            outcome = new StepOutcome.PublishRefused(e.refusal());
        } catch (Exception e) {
            throw new IllegalStateException("the step on " + id + " failed", e);
        }
        for (var effect : released) {
            routeEffect(index, effect);
        }
        record(summary + " -> " + render(outcome));
        settlePending();
        return outcome;
    }

    private static String render(StepOutcome outcome) {
        return switch (outcome) {
            case StepOutcome.Published(var revision, var effects) ->
                    "Published(rev=" + revision + ", effects=" + effects.size() + ")";
            case StepOutcome.Parked(var revision) -> "Parked(rev=" + revision + ")";
            case StepOutcome.PlanRefused(var refusal) -> "PlanRefused(" + refusal + ")";
            case StepOutcome.PublishRefused(var refusal) -> "PublishRefused(" + refusal + ")";
            case StepOutcome.LifecycleRefused(var refusal) -> "LifecycleRefused(" + refusal + ")";
            case StepOutcome.NodeDown() -> "NodeDown";
            case StepOutcome.Refused(var reason) -> "Refused(" + reason + ")";
        };
    }

    private final List<Long> reclaimedSlots = new ArrayList<>();

    /// The frontiers at which reclamation was authorised by a published
    /// checkpoint. The core exposes no reclamation call of its own yet, so the
    /// harness records the authorisation and the core's own retention stands.
    public List<Long> reclaimedSlots() {
        return List.copyOf(reclaimedSlots);
    }

    /// Routes one released effect to where the host would take it: `Send` to the
    /// network, `Apply` to the node's pending list, `Persist` to the node's
    /// outstanding intent, `AdminResponse` to the node's verdicts.
    private void routeEffect(int from, Effect effect) {
        switch (effect) {
            case Effect.Send(var to, var era, var message) ->
                    network.route(new Network.Envelope(slotIds.get(from), to, era, message));
            case Effect.Apply apply -> nodes.get(from).pendingEffects.add(apply);
            case Effect.Persist(var intent) -> nodes.get(from).outstandingIntent = intent.revision();
            case Effect.AdminResponse(var verdict) -> nodes.get(from).adminVerdicts.add(verdict);
        }
    }

    /// Appends one trace line and runs the legality gate. Every harness action,
    /// delivery, injection, tick, lifecycle and network change ends here, so the
    /// gate's "after every step" is structural, not remembered.
    private void record(String body) {
        stepSeq++;
        pushTrace("#" + stepSeq + " t=" + tick.value() + " " + body);
        scanFaults();
    }

    private void pushTrace(String line) {
        if (trace.size() == TRACE_CAPACITY) {
            trace.removeFirst();
        }
        trace.addLast(line);
    }

    /// The legality gate: any node that faulted since the last scan must have
    /// been declared. An undeclared fault fails the step with the full trace.
    private void scanFaults() {
        for (var index = 0; index < nodes.size(); index++) {
            var node = nodes.get(index);
            if (node == null || faultedKnown.get(index)) {
                continue;
            }
            var fault = node.replica.progress().fault();
            if (fault.isEmpty()) {
                continue;
            }
            faultedKnown.set(index, true);
            if (declaredFaults.get(index)) {
                declaredFaults.set(index, false);
                continue;
            }
            throw new IllegalStateException(
                    "an undeclared fault forced " + slotIds.get(index) + " crashed: " + fault.get()
                            + "\n" + traceDump());
        }
    }

    /// Declares that the next fault on `id` is intended. The declaration is
    /// consumed when the fault is observed.
    public void expectFault(NodeId id) {
        declaredFaults.set(requireSlot(id), true);
    }

    private int requireSlot(NodeId id) {
        var index = slots.get(id);
        if (index == null) {
            throw new IllegalArgumentException("the script named an identity outside the cluster: " + id);
        }
        return index;
    }

    // ------------------------------------------------------------------
    // Clock
    // ------------------------------------------------------------------

    /// Advances the harness clock by one tick. The clock never goes backwards
    /// (harness contract §7).
    public void advanceClock() {
        tick = new Tick(tick.value() + 1);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    private TmpGate openGate(int index) {
        try {
            return TmpGate.open(gateDir(index), gateLogs.get(index));
        } catch (IOException e) {
            throw new UncheckedIOException("the gate for slot " + index + " could not be opened", e);
        }
    }

    private SuperblockCopies readCopies(int index) {
        return openGate(index).readCopies().orElseThrow(() ->
                new IllegalStateException("the gate for slot " + index + " holds no durable identity"));
    }

    /// Boots the first life of `id`, latching the durable `Joining` round at the
    /// provision identity.
    private void latchFirst(NodeId id) {
        var index = requireSlot(id);
        var boot = TmpGate.boot(openGate(index));
        if (!(boot instanceof Lifecycle.BootOutcome.First first)) {
            throw new IllegalStateException("the first boot of " + id + " read a gate that was not blank");
        }
        try {
            sessions.set(index, Optional.of(first.session().latch(id)));
        } catch (Exception e) {
            throw new IllegalStateException("the latch of " + id + " failed", e);
        }
    }

    /// Kills the node: volatile state dies, the durable markers remain as they
    /// are, and the disk is recorded for the next boot.
    public void crash(NodeId id) {
        var index = slots.get(id);
        if (index == null || nodes.get(index) == null) {
            return;
        }
        var replica = nodes.get(index).replica;
        disks.set(index, Optional.of(new Disk(
                PersistedProgress.from(replica.progress()),
                snapshotJournal(index),
                replica.progress().config())));
        nodes.set(index, null);
        sessions.set(index, Optional.empty());
        faultedKnown.set(index, false);
        record("crash " + id);
    }

    /// The controlled shutdown: both marker rounds with the drain strictly
    /// between them. The next clean boot continues the same identity.
    public void halt(NodeId id) {
        var index = slots.get(id);
        if (index == null || nodes.get(index) == null) {
            record("halt " + id + " -> NodeDown");
            return;
        }
        var session = sessions.get(index).orElseThrow(() ->
                new IllegalStateException("the halt of " + id + " found no live gate session"));
        try {
            session.beginHalt(readCopies(index))
                    .drain()
                    .finishHalt();
        } catch (Exception e) {
            throw new IllegalStateException("the halt of " + id + " failed", e);
        }
        sessions.set(index, Optional.empty());
        crashVolatile(index);
        record("halt " + id);
    }

    private void crashVolatile(int index) {
        nodes.set(index, null);
        faultedKnown.set(index, false);
    }

    /// Reopens the node over its recorded disk and gate. A clean boot continues
    /// the same identity; a crashed one bumps to the next life.
    public StepOutcome restart(NodeId id) {
        var index = slots.get(id);
        if (index == null) {
            record("restart " + id + " -> Refused(unprovisioned identity)");
            return new StepOutcome.Refused("unprovisioned identity: " + id);
        }
        var boot = TmpGate.boot(openGate(index));
        return switch (boot) {
            case Lifecycle.BootOutcome.First(_) -> {
                latchFirst(id);
                yield reopened(index, id, Optional.empty(), null);
            }
            case Lifecycle.BootOutcome.Clean(var clean) -> {
                var disk = disks.get(index);
                var outcome = reopened(index, id, disk, clean.vouched());
                try {
                    sessions.set(index, Optional.of(clean.latch()));
                } catch (Exception e) {
                    throw new IllegalStateException("the latch of " + id + " failed", e);
                }
                yield outcome;
            }
            case Lifecycle.BootOutcome.Crashed(var crashed) ->
                    bump(index, id, crashed.pair());
        };
    }

    /// Restarts the node explicitly as a new identity, the incarnation path the
    /// boot gate admits when the recorded disk cannot vouch for the old life.
    public StepOutcome restartAs(NodeId id, NodeId newIdentity) {
        var index = requireSlot(id);
        var disk = disks.get(index).orElseThrow(() ->
                new IllegalStateException("the restart of " + id + " found no recorded disk"));
        var journal = new InMemoryLog(disk.journal());
        try {
            var replica = Replica.reincarnate(
                    new Bumped(id, newIdentity),
                    newIdentity,
                    WeightedMajority.INSTANCE,
                    journal,
                    disk.persisted(),
                    disk.config(),
                    stability,
                    knobs);
            install(index, newIdentity, replica, journal);
            record("restart-as " + id + " -> " + newIdentity);
            return new StepOutcome.Published(replica.progress().revision(), List.of());
        } catch (Exception e) {
            var refusal = new StepOutcome.LifecycleRefused(refusalOf(e));
            record("restart-as " + id + " -> " + render(refusal));
            return refusal;
        }
    }

    private StepOutcome bump(int index, NodeId id, Bumped pair) {
        // The durable `Joining` round at the bumped pair is written by the gate
        // before any wire emission from the reincarnated node; the latch itself
        // defers to the seated witness.
        var boot = TmpGate.boot(openGate(index));
        if (boot instanceof Lifecycle.BootOutcome.Crashed crashed) {
            try {
                openGate(index).commit(joiningCopies(pair.newId()));
            } catch (Exception e) {
                throw new IllegalStateException("the bumped round of " + pair.newId() + " failed", e);
            }
            return restartAs(pair.newId() == null ? id : id, pair.newId());
        }
        throw new IllegalStateException("the bump of " + id + " found no crashed classification");
    }

    private StepOutcome reopened(int index, NodeId id, Optional<Disk> disk, Vouched vouched) {
        if (vouched != null) {
            // A vouched clean halt continues the identity; the engine-side resume
            // is the same reopen, fenced.
        }
        if (disk.isEmpty()) {
            try {
                var replica = Replica.provision(
                        id, roster(), WeightedMajority.INSTANCE, new InMemoryLog(), stability, knobs);
                install(index, id, replica, new InMemoryLog());
                record("restart " + id + " -> provision");
                return new StepOutcome.Published(replica.progress().revision(), List.of());
            } catch (Exception e) {
                var refusal = new StepOutcome.LifecycleRefused(refusalOf(e));
                record("restart " + id + " -> " + render(refusal));
                return refusal;
            }
        }
        var recorded = disk.get();
        var journal = new InMemoryLog(recorded.journal());
        try {
            var replica = Replica.resume(
                    vouched != null ? vouched : new Vouched(id),
                    id,
                    WeightedMajority.INSTANCE,
                    journal,
                    recorded.persisted(),
                    recorded.config(),
                    stability,
                    knobs);
            install(index, id, replica, journal);
            record("restart " + id);
            return new StepOutcome.Published(replica.progress().revision(), List.of());
        } catch (Exception e) {
            var refusal = new StepOutcome.LifecycleRefused(refusalOf(e));
            record("restart " + id + " -> " + render(refusal));
            return refusal;
        }
    }

    private static LifecycleRefusal refusalOf(Exception e) {
        if (e instanceof ReplicaLifecycleException lifecycle) {
            return lifecycle.refusal();
        }
        throw new IllegalStateException("the reopen failed unexpectedly", e);
    }

    private void install(int index, NodeId id, Replica<InMemoryLog, WeightedMajority> replica, InMemoryLog journal) {
        nodes.set(index, new Node(replica));
        faultedKnown.set(index, replica.progress().fault().isPresent());
        applied.get(index).clear();
        pendingEffectsClear(index);
    }

    private void pendingEffectsClear(int index) {
        // The node is reconstructed, so its pending applies die with it.
        slots.put(id(index), index);
    }

    private NodeId id(int index) {
        return slotIds.get(index);
    }

    /// An outside identity boots over the deployment's genesis knowledge, as a
    /// joining member.
    public StepOutcome bootAs(NodeId id) {
        if (slots.containsKey(id)) {
            record("boot " + id + " -> Refused(already a member)");
            return new StepOutcome.Refused("already a member: " + id);
        }
        try {
            var journal = new InMemoryLog();
            var replica = Replica.join(
                    id,
                    WeightedMajority.INSTANCE,
                    journal,
                    genesisProgress(),
                    genesisConfig(),
                    stability,
                    knobs);
            addNode(id, replica);
            latchFirst(id);
            record("boot " + id);
            return new StepOutcome.Published(replica.progress().revision(), List.of());
        } catch (Exception e) {
            var refusal = new StepOutcome.LifecycleRefused(refusalOf(e));
            record("boot " + id + " -> " + render(refusal));
            return refusal;
        }
    }

    private PersistedProgress genesisProgress() {
        return new PersistedProgress(
                new Ballot(new com.github.trex_paxos.core.ids.Era(1), com.github.trex_paxos.core.ids.View.INITIAL),
                new Ballot(new com.github.trex_paxos.core.ids.Era(1), com.github.trex_paxos.core.ids.View.INITIAL),
                Status.JOINING,
                Replica.INIT_SLOT,
                Replica.INIT_SLOT,
                Replica.INIT_SLOT,
                Slot.NONE,
                0,
                Optional.empty());
    }

    private EraTable genesisConfig() {
        var order = genesisOrder();
        try {
            var table = EraTable.genesis();
            table = table.extend(new com.github.trex_paxos.core.configuration.SystemOperation.Void(),
                    Replica.VOID_SLOT);
            return table.extend(new com.github.trex_paxos.core.configuration.SystemOperation.Init(order),
                    Replica.INIT_SLOT);
        } catch (Exception e) {
            throw new IllegalStateException("the genesis configuration could not be built", e);
        }
    }

    /// The journal as the disk holds it: every retained entry, by slot.
    private TreeMap<Slot, LogEntry> snapshotJournal(int index) {
        var entries = new TreeMap<Slot, LogEntry>();
        for (var entry : journalEntries(slotIds.get(index))) {
            entries.put(entry.slot(), entry);
        }
        return entries;
    }

    private static SuperblockCopies joiningCopies(NodeId identity) {
        return new SuperblockCopies(IntStream.range(0, GATE_COPIES)
                .mapToObj(_ -> new CopyState(identity, Marker.JOINING))
                .toList());
    }

    /// The reincarnated node announces its replacement pair to the cluster. The
    /// announcement exists only after the bumped pair's durable round.
    public StepOutcome reincarnate(NodeId id, NodeId oldId) {
        return drive(id, "reincarnate " + oldId + "->" + id, new Input.Reincarnate(oldId));
    }

    /// Settles a reincarnation's deferred latch once the engine's seated
    /// observation mints the witness (`docs/uvrr-io-obligations.md`, the boot
    /// gate chapter §3).
    private void settlePending() {
        for (var index = 0; index < nodes.size(); index++) {
            if (nodes.get(index) == null) {
                continue;
            }
            var replica = nodes.get(index).replica;
            Optional<Rejoined> token = replica.rejoined();
            if (token.isEmpty()) {
                continue;
            }
            var identity = slotIds.get(index);
            openGate(index).commit(joiningCopies(identity));
            sessions.set(index, Optional.of(new RunningSession(openGate(index))));
        }
    }

    // ------------------------------------------------------------------
    // Network operations
    // ------------------------------------------------------------------

    /// Delivers the oldest queued datagram, one step.
    public Optional<DeliveryOutcome> deliverNext() {
        return network.pollNext().map(this::deliverEnvelope);
    }

    /// Delivers the oldest queued datagram addressed to `id`, one step.
    public Optional<DeliveryOutcome> deliverTo(NodeId id) {
        return network.pollMatching(Network.addressedTo(id)).map(this::deliverEnvelope);
    }

    /// Drains the queue, one step per datagram, in queue order.
    public List<DeliveryOutcome> deliverAll() {
        var outcomes = new ArrayList<DeliveryOutcome>();
        Optional<DeliveryOutcome> next;
        while ((next = deliverNext()).isPresent()) {
            outcomes.add(next.get());
        }
        return outcomes;
    }

    /// Delivers the oldest queued datagram addressed to `id` with the given tag
    /// and slot, the way a script stages an out-of-order or retransmitted
    /// delivery.
    public Optional<DeliveryOutcome> deliverToMatching(NodeId id, Tag tag, Slot slot) {
        return network.pollMatching(Network.addressedTo(id, tag, slot)).map(this::deliverEnvelope);
    }

    private DeliveryOutcome deliverEnvelope(Network.Envelope envelope) {
        var summary = "net deliver n" + envelope.from().value() + "->n" + envelope.to().value()
                + " " + envelope.message().header().tag().wireName()
                + " e" + envelope.era().value();
        var outcome = drive(envelope.to(), summary, new Input.Peer(envelope.from(), envelope.message()));
        if (outcome instanceof StepOutcome.NodeDown) {
            network.countUndeliverable();
        }
        return new DeliveryOutcome(envelope.from(), envelope.to(), outcome);
    }

    /// Force-feeds a raw message to a node, bypassing the queues and the
    /// partition. `from` is the transport-attributed sender the core's guards
    /// and quorum counting see, so a fabrication names its claimed author.
    public StepOutcome inject(NodeId from, NodeId id, Message message) {
        return drive(id,
                "inject n" + id.value() + " from n" + from.value()
                        + " " + message.header().tag().wireName(),
                new Input.Peer(from, message));
    }

    /// Splits the cluster; traffic across the split is held, counted and
    /// deliverable after `heal`.
    public void partition(List<NodeId> a, List<NodeId> b) {
        network.partition(a, b);
        record("partition " + a + " | " + b);
    }

    /// Heals the partition, releasing every held datagram that now flows.
    public int heal() {
        var released = network.heal();
        record("heal released=" + released);
        return released;
    }

    /// Drops every held datagram, counting them.
    public int dropHeld() {
        var count = network.dropHeld();
        record("drop held=" + count);
        return count;
    }

    /// Drops every datagram queued for `id`, counting them.
    public int dropQueuedFor(NodeId id) {
        var count = network.dropQueuedFor(id);
        record("drop queued for " + id + "=" + count);
        return count;
    }

    // ------------------------------------------------------------------
    // Host operations
    // ------------------------------------------------------------------

    /// Delivers one timer event to the named node.
    public StepOutcome tick(NodeId id) {
        advanceClock();
        return drive(id, "tick n" + id.value(), new Input.Tick());
    }

    /// One timer event to every live node, in slot order, then one drain of the
    /// network.
    public TickAllOutcome tickAll() {
        advanceClock();
        var ticks = new ArrayList<DeliveryOutcome>();
        for (var index = 0; index < nodes.size(); index++) {
            var id = slotIds.get(index);
            if (nodes.get(index) == null) {
                record("tick n" + id.value() + " -> NodeDown");
                ticks.add(new DeliveryOutcome(id, id, new StepOutcome.NodeDown()));
                continue;
            }
            var outcome = drive(id, "tick n" + id.value(), new Input.Tick());
            ticks.add(new DeliveryOutcome(id, id, outcome));
        }
        return new TickAllOutcome(List.copyOf(ticks), deliverAll());
    }

    /// Submits one operation for ordering from the named node.
    public StepOutcome propose(NodeId id, Operation operation) {
        return drive(id, "propose n" + id.value(), new Input.Propose(operation));
    }

    /// Submits one operation for ordering, minting the operation identity from
    /// the harness's own sequence so a run is reproducible.
    public StepOutcome propose(NodeId id, byte[] payload) {
        return propose(id, new Operation(new OperationId(0, opSeq++), payload));
    }

    /// Submits one typed cluster operation for ordering from the named node,
    /// over the ordinary consensus pipeline.
    public StepOutcome reconfigure(NodeId id,
                                   com.github.trex_paxos.core.configuration.SystemOperation op) {
        return drive(id, "reconfigure n" + id.value(), new Input.Reconfigure(op, Optional.empty()));
    }

    /// Submits one plan over the node's admin ingress.
    public StepOutcome submitPlan(NodeId id, Plan plan) {
        return drive(id, "submit-plan n" + id.value(), new Input.SubmitPlan(plan));
    }

    /// An admin ingress that forces the node's view, for the failure suites.
    public StepOutcome forceView(NodeId id, Ballot target) {
        return drive(id, "force-view n" + id.value() + " -> " + target, new Input.AdminForceView(target));
    }

    /// Executes the `Apply` effects the harness is holding for the node, in slot
    /// order, recording each into the apply log and reporting it applied.
    public List<AppliedRecord> executeApplyEffects(NodeId id) {
        var index = slots.get(id);
        if (index == null || nodes.get(index) == null) {
            return List.of();
        }
        var held = new ArrayList<>(nodes.get(index).pendingEffects);
        nodes.get(index).pendingEffects.clear();
        var executed = new ArrayList<AppliedRecord>();
        held.stream()
                .sorted(Comparator.comparing(effect -> ((Effect.Apply) effect).slot()))
                .forEach(effect -> {
                    var apply = (Effect.Apply) effect;
                    var record = new AppliedRecord(apply.slot(), apply.operationId(), apply.payload());
                    executed.add(record);
                    applied.get(index).add(record);
                    drive(id, "apply " + apply.slot().value(), new Input.Applied(apply.slot()));
                });
        return executed;
    }

    /// Reports a slot applied to the node's own bookkeeping, outside the apply
    /// log: the host application telling the core what it has done.
    public StepOutcome reportApplied(NodeId id, Slot slot) {
        return drive(id, "applied n" + id.value() + " s" + slot.value(), new Input.Applied(slot));
    }

    /// Checkpoints the node's journal through `through`.
    public StepOutcome checkpoint(NodeId id, Slot through) {
        return drive(id, "checkpoint n" + id.value() + " s" + through.value(),
                new Input.Checkpointed(through));
    }

    /// Delivers a stability result for the node's outstanding persistence intent.
    public StepOutcome confirm(NodeId id, long revision, StabilityResult result) {
        return drive(id, "confirm n" + id.value() + " rev" + revision,
                new Input.StabilityConfirmation(revision, result));
    }

    /// The outside node gossips to every live node: the datagram is attributed
    /// to the roster's one-past-the-end identity.
    public List<DeliveryOutcome> gossip(NodeId from, Slot prepared, Slot committed) {
        var message = new Message(
                new com.github.trex_paxos.core.wire.Header(
                        Tag.GOSSIP_REQUEST,
                        new Ballot(new com.github.trex_paxos.core.ids.Era(1), com.github.trex_paxos.core.ids.View.INITIAL),
                        prepared),
                new Body.GossipRequest(prepared, committed));
        var outcomes = new ArrayList<DeliveryOutcome>();
        for (var index = 0; index < nodes.size(); index++) {
            if (nodes.get(index) == null) {
                continue;
            }
            outcomes.add(deliverEnvelope(new Network.Envelope(from, slotIds.get(index),
                    new com.github.trex_paxos.core.ids.Era(1), message)));
        }
        record("gossip from n" + from.value() + " prepared=" + prepared.value()
                + " committed=" + committed.value());
        return outcomes;
    }
}
