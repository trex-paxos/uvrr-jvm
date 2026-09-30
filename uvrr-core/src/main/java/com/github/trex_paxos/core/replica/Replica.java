// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.replica;

import com.github.trex_paxos.core.configuration.ConfigError;
import com.github.trex_paxos.core.configuration.ConfigException;
import com.github.trex_paxos.core.configuration.Configuration;
import com.github.trex_paxos.core.configuration.EraRecord;
import com.github.trex_paxos.core.configuration.EraTable;
import com.github.trex_paxos.core.configuration.Member;
import com.github.trex_paxos.core.configuration.SystemOperation;
import com.github.trex_paxos.core.effects.Effect;
import com.github.trex_paxos.core.effects.JournalIntent;
import com.github.trex_paxos.core.effects.PersistenceIntent;
import com.github.trex_paxos.core.effects.PlanVerdict;
import com.github.trex_paxos.core.effects.ProgressIntent;
import com.github.trex_paxos.core.effects.Stability;
import com.github.trex_paxos.core.effects.StabilityResult;
import com.github.trex_paxos.core.ids.Ballot;
import com.github.trex_paxos.core.ids.Era;
import com.github.trex_paxos.core.ids.Fault;
import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.ids.Operation;
import com.github.trex_paxos.core.ids.OperationId;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.ids.Tick;
import com.github.trex_paxos.core.ids.View;
import com.github.trex_paxos.core.invariant.HeaderSlotRole;
import com.github.trex_paxos.core.invariant.InputKind;
import com.github.trex_paxos.core.invariant.InvariantChecker;
import com.github.trex_paxos.core.journal.Journal;
import com.github.trex_paxos.core.journal.JournalError;
import com.github.trex_paxos.core.journal.JournalException;
import com.github.trex_paxos.core.journal.JournalView;
import com.github.trex_paxos.core.journal.LogEntry;
import com.github.trex_paxos.core.journal.Payload;
import com.github.trex_paxos.core.lifecycle.Bumped;
import com.github.trex_paxos.core.lifecycle.Rejoined;
import com.github.trex_paxos.core.lifecycle.Vouched;
import com.github.trex_paxos.core.message.Body;
import com.github.trex_paxos.core.message.EraProof;
import com.github.trex_paxos.core.message.EvidenceKind;
import com.github.trex_paxos.core.message.Message;
import com.github.trex_paxos.core.observe.Diagnostic;
import com.github.trex_paxos.core.observe.Observation;
import com.github.trex_paxos.core.plan.Plan;
import com.github.trex_paxos.core.plan.PlanRejection;
import com.github.trex_paxos.core.progress.Progress;
import com.github.trex_paxos.core.progress.ProgressError;
import com.github.trex_paxos.core.progress.ProgressException;
import com.github.trex_paxos.core.progress.ProgressSnapshot;
import com.github.trex_paxos.core.progress.Status;
import com.github.trex_paxos.core.quorum.QuorumError;
import com.github.trex_paxos.core.quorum.QuorumGate;
import com.github.trex_paxos.core.quorum.QuorumStrategy;
import com.github.trex_paxos.core.quorum.Role;
import com.github.trex_paxos.core.wire.Header;
import com.github.trex_paxos.core.wire.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/// The replica state machine: identity, published progress, journal, strategy,
/// stability mode, observation handle, and the one outstanding transition.
public final class Replica<J extends Journal, Q extends QuorumStrategy> {

    public static final Slot VOID_SLOT = new Slot(1);
    public static final Slot INIT_SLOT = new Slot(2);

    private final NodeId own;
    private Progress progress;
    private final J journal;
    private final Q strategy;
    private final Stability stability;
    private final Observation<ProgressSnapshot> observation;
    private final Observation<Diagnostic> diagnostics;
    private final Map<Slot, Proposal> proposals = new TreeMap<>();
    private ParkedTransition parked = null;
    private final ViewChangeKnobs knobs;
    private Tick primaryActivity;
    private Bookkeeping.ViewChangeVolatile viewChange = null;
    private Bookkeeping.TransferVolatile transfer = null;
    private Bookkeeping.StalledStartView stalled = null;
    private final List<NodeId> witnesses = new ArrayList<>();

    public record Evidence(
            Ballot retained,
            Slot accepted,
            Slot committed,
            List<LogEntry> suffix,
            EvidenceKind evidence,
            EraProof eraProof
    ) {
        public Evidence {
            suffix = List.copyOf(suffix);
        }
    }

    private static final class Proposal {
        final Set<NodeId> oks = new HashSet<>();
    }

    private record ParkedTransition(
            long base,
            Progress candidate,
            JournalMutation journal,
            List<Effect> effects,
            InputKind kind,
            Bookkeeping bookkeeping,
            Diagnostic diagnostic
    ) {}

    private Replica(
            NodeId own,
            Progress progress,
            J journal,
            Q strategy,
            Stability stability,
            ViewChangeKnobs knobs
    ) {
        this.own = own;
        this.progress = progress;
        this.journal = journal;
        this.strategy = strategy;
        this.stability = stability;
        this.knobs = knobs;
        this.primaryActivity = new Tick(0);
        this.observation = new Observation<>(progress.toSnapshot());
        this.diagnostics = new Observation<>(new Diagnostic.None());
    }

    /// Provision genesis history and start fenced in Joining status.
    public static <J extends Journal, Q extends QuorumStrategy> Replica<J, Q> provision(
            NodeId own,
            List<NodeId> order,
            Q strategy,
            J journal,
            Stability stability,
            ViewChangeKnobs knobs
    ) throws Exception {
        Objects.requireNonNull(own, "own cannot be null");
        Objects.requireNonNull(order, "order cannot be null");
        Objects.requireNonNull(strategy, "strategy cannot be null");
        Objects.requireNonNull(journal, "journal cannot be null");
        Objects.requireNonNull(stability, "stability cannot be null");
        Objects.requireNonNull(knobs, "knobs cannot be null");

        if (!order.contains(own)) {
            throw new ReplicaLifecycleException(new LifecycleRefusal.NotAMember(own));
        }

        if (journal.view().accepted().isPresent()) {
            throw new ReplicaLifecycleException(new LifecycleRefusal.JournalNotEmpty());
        }

        EraTable table;
        try {
            table = EraTable.genesis();
            table = table.extend(new SystemOperation.Void(), VOID_SLOT);
            table = table.extend(new SystemOperation.Init(order), INIT_SLOT);
        } catch (ConfigException e) {
            throw new ReplicaLifecycleException(new LifecycleRefusal.Configuration(e.error()));
        }

        var qErr = QuorumGate.validateEra(strategy, table.current().config());
        if (qErr.isPresent()) {
            throw new ReplicaLifecycleException(new LifecycleRefusal.Quorum(qErr.get()));
        }

        LogEntry voidEntry = new LogEntry(VOID_SLOT, Era.INITIAL, new Payload.SystemPayload(new SystemOperation.Void()));
        LogEntry initEntry = new LogEntry(INIT_SLOT, new Era(1), new Payload.SystemPayload(new SystemOperation.Init(order)));
        try {
            journal.installSuffix(VOID_SLOT, List.of(voidEntry, initEntry));
        } catch (JournalException e) {
            throw new ReplicaLifecycleException(new LifecycleRefusal.JournalRefusal(e.error()));
        }

        Ballot genesisView = new Ballot(new Era(1), View.INITIAL);
        Progress initialProgress;
        try {
            initialProgress = Progress.reconstitute(
                    genesisView,
                    genesisView,
                    Status.JOINING,
                    INIT_SLOT,
                    INIT_SLOT,
                    INIT_SLOT,
                    Slot.NONE,
                    0,
                    table,
                    Optional.empty()
            );
        } catch (ProgressException e) {
            throw new ReplicaLifecycleException(new LifecycleRefusal.ProgressRefusal(e.error()));
        }

        return new Replica<>(own, initialProgress, journal, strategy, stability, knobs);
    }

    /// Fresh joiner constructor.
    public static <J extends Journal, Q extends QuorumStrategy> Replica<J, Q> join(
            NodeId own,
            Q strategy,
            J journal,
            PersistedProgress persisted,
            EraTable config,
            Stability stability,
            ViewChangeKnobs knobs
    ) throws Exception {
        Slot frontier = journal.view().accepted().orElse(Slot.NONE);
        if (!frontier.equals(INIT_SLOT) || !persisted.accepted().equals(INIT_SLOT)) {
            throw new ReplicaLifecycleException(new LifecycleRefusal.NotAFreshJoiner());
        }
        return reopenFenced(own, strategy, journal, persisted, config, stability, knobs);
    }

    /// Same-identity resume after vouched clean halt.
    public static <J extends Journal, Q extends QuorumStrategy> Replica<J, Q> resume(
            Vouched proof,
            NodeId own,
            Q strategy,
            J journal,
            PersistedProgress persisted,
            EraTable config,
            Stability stability,
            ViewChangeKnobs knobs
    ) throws Exception {
        Objects.requireNonNull(proof, "proof cannot be null");
        return reopenFenced(own, strategy, journal, persisted, config, stability, knobs);
    }

    /// Reincarnation of crashed identity.
    public static <J extends Journal, Q extends QuorumStrategy> Replica<J, Q> reincarnate(
            Bumped pair,
            NodeId own,
            Q strategy,
            J journal,
            PersistedProgress persisted,
            EraTable config,
            Stability stability,
            ViewChangeKnobs knobs
    ) throws Exception {
        Objects.requireNonNull(pair, "pair cannot be null");
        return reopenFenced(own, strategy, journal, persisted, config, stability, knobs);
    }

    private static <J extends Journal, Q extends QuorumStrategy> Replica<J, Q> reopenFenced(
            NodeId own,
            Q strategy,
            J journal,
            PersistedProgress persisted,
            EraTable config,
            Stability stability,
            ViewChangeKnobs knobs
    ) throws Exception {
        var qErr = QuorumGate.validateEra(strategy, config.current().config());
        if (qErr.isPresent()) {
            throw new ReplicaLifecycleException(new LifecycleRefusal.Quorum(qErr.get()));
        }

        Slot frontier = journal.view().accepted().orElse(Slot.NONE);
        if (!frontier.equals(persisted.accepted())) {
            throw new ReplicaLifecycleException(new LifecycleRefusal.ProgressJournalDivergence(persisted.accepted(), frontier));
        }

        Progress progress;
        try {
            progress = Progress.reconstitute(
                    persisted.current(),
                    persisted.retained(),
                    Status.RESTARTING,
                    persisted.accepted(),
                    persisted.committed(),
                    persisted.applied(),
                    persisted.checkpoint(),
                    persisted.revision(),
                    config,
                    persisted.fault()
            );
        } catch (ProgressException e) {
            throw new ReplicaLifecycleException(new LifecycleRefusal.ProgressRefusal(e.error()));
        }

        return new Replica<>(own, progress, journal, strategy, stability, knobs);
    }

    public NodeId own() { return own; }
    public Progress progress() { return progress; }
    public J journal() { return journal; }
    public Q strategy() { return strategy; }
    public Stability stability() { return stability; }
    public Observer observer() { return new Observer(observation, diagnostics); }
    public List<NodeId> witnesses() { return Collections.unmodifiableList(witnesses); }

    public Optional<Rejoined> rejoined() {
        boolean seated = progress.status() == Status.NORMAL
                && progress.config().current().config().weightOf(own).map(w -> w.value() >= 1).orElse(false);
        return seated ? Optional.of(Rejoined.mint()) : Optional.empty();
    }

    public Optional<NodeId> primaryOf(Ballot view) {
        return progress.config().get(view.era()).flatMap(record -> record.config().primary(view.view()));
    }

    public List<NodeId> backups() {
        return progress.config().current().config().order().stream()
                .map(Member::node)
                .filter(node -> !node.equals(own))
                .toList();
    }

    /// Phase 1: Plan candidate transition.
    public PlannedTransition plan(TimedInput input, JournalView journalView) throws Exception {
        if (progress.fault().isPresent()) {
            throw new ReplicaPlanException(new PlanRefusal.Faulted(progress.fault().get()));
        }

        Slot frontier = journalView.accepted().orElse(Slot.NONE);
        if (!frontier.equals(progress.accepted())) {
            throw new ReplicaPlanException(new PlanRefusal.JournalViewDivergence(progress.accepted(), frontier));
        }

        return switch (input.event()) {
            case Input.StabilityConfirmation(long rev, var res) -> planConfirmation(rev, res);
            case Input.Tick _ -> {
                refuseIfParked();
                yield planTick(journalView, input.at());
            }
            case Input.Propose(var op) -> {
                refuseIfParked();
                yield planPropose(journalView, op);
            }
            case Input.Peer(var from, var msg) -> {
                refuseIfParked();
                yield planPeer(journalView, from, msg, input.at(), input.event().kind());
            }
            case Input.Applied(var slot) -> {
                refuseIfParked();
                yield planApplied(journalView, slot);
            }
            case Input.Checkpointed(var through) -> {
                refuseIfParked();
                yield planCheckpointed(through);
            }
            case Input.SubmitPlan(var plan) -> {
                refuseIfParked();
                yield planSubmitPlan(journalView, plan, input.event().kind());
            }
            case Input.AdminForceView(var target) -> {
                refuseIfParked();
                yield planAdminForceView(journalView, target, input.at());
            }
            case Input.Reconfigure(var op, var pivot) -> {
                refuseIfParked();
                yield planReconfigure(journalView, op, pivot);
            }
            case Input.Reincarnate(var oldId) -> {
                refuseIfParked();
                yield planReincarnate(journalView, oldId, input.event().kind());
            }
        };
    }

    /// Phase 2: Publish accepted transition.
    public PublishOutcome publish(PlannedTransition planned) throws Exception {
        long expected = (parked != null) ? parked.base : progress.revision();
        if (planned.base() != expected) {
            throw new ReplicaPublishException(new PublishRefusal.RevisionMismatch(expected, planned.base()));
        }

        if (planned.fault().isPresent()) {
            faultNode(planned.fault().get());
            throw new ReplicaPublishException(new PublishRefusal.IllegalCandidate(planned.fault().get()));
        }

        Optional<Fault> legalFault = InvariantChecker.legal(progress, planned.candidate(), planned.kind());
        if (legalFault.isPresent()) {
            faultNode(legalFault.get());
            throw new ReplicaPublishException(new PublishRefusal.IllegalCandidate(legalFault.get()));
        }

        if (planned.completion() || stability == Stability.VOLATILE) {
            this.parked = null;
            install(planned.journal(), planned.candidate(), planned.bookkeeping(), planned.diagnostic());
            return new PublishOutcome.Published(progress.revision(), planned.effects());
        }

        if (this.parked != null) {
            throw new ReplicaPublishException(new PublishRefusal.TransitionOutstanding());
        }

        this.parked = new ParkedTransition(
                planned.base(),
                planned.candidate(),
                planned.journal(),
                planned.effects(),
                planned.kind(),
                planned.bookkeeping(),
                planned.diagnostic()
        );
        return new PublishOutcome.Parked(planned.base(), List.of(new Effect.Persist(planned.intent())));
    }

    /// One-step transition combining plan and publish.
    public PublishOutcome step(TimedInput input) throws Exception {
        PlannedTransition transition = plan(input, journal.view());
        return publish(transition);
    }

    private void refuseIfParked() throws ReplicaPlanException {
        if (parked != null) {
            throw new ReplicaPlanException(new PlanRefusal.TransitionOutstanding());
        }
    }

    private void faultNode(Fault fault) {
        try {
            this.progress = progress.withFault(fault).withRevision(progress.revision() + 1);
            this.observation.write(progress.toSnapshot());
        } catch (Exception ignored) {}
    }

    private void install(
            JournalMutation mutation,
            Progress candidate,
            Bookkeeping bookkeeping,
            Diagnostic diagnostic
    ) throws Exception {
        switch (mutation) {
            case JournalMutation.None _ -> {}
            case JournalMutation.Accept(var entries) -> {
                try {
                    journal.accept(entries);
                } catch (JournalException e) {
                    faultNode(Fault.PROGRESS_JOURNAL_DIVERGENCE);
                    throw new ReplicaPublishException(new PublishRefusal.JournalRefused(e.error()));
                }
            }
            case JournalMutation.InstallSuffix(var from, var suffix) -> {
                try {
                    journal.installSuffix(from, suffix);
                } catch (JournalException e) {
                    faultNode(Fault.PROGRESS_JOURNAL_DIVERGENCE);
                    throw new ReplicaPublishException(new PublishRefusal.JournalRefused(e.error()));
                }
            }
        }

        this.progress = candidate;
        this.observation.write(this.progress.toSnapshot());
        this.diagnostics.write(diagnostic);

        for (var entry : bookkeeping.proposals()) {
            proposals.computeIfAbsent(entry.getKey(), k -> new Proposal()).oks.addAll(entry.getValue());
        }

        bookkeeping.viewChange().ifPresent(vc -> {
            switch (vc) {
                case Bookkeeping.ViewChangeUpdate.Unchanged _ -> {}
                case Bookkeeping.ViewChangeUpdate.Set(var state) -> this.viewChange = state;
                case Bookkeeping.ViewChangeUpdate.Clear _ -> {
                    this.viewChange = null;
                    this.proposals.clear();
                }
            }
        });

        bookkeeping.transfer().ifPresent(tr -> {
            switch (tr) {
                case Bookkeeping.TransferUpdate.Unchanged _ -> {}
                case Bookkeeping.TransferUpdate.Set(var state) -> this.transfer = state;
                case Bookkeeping.TransferUpdate.Clear _ -> this.transfer = null;
            }
        });

        bookkeeping.stalled().ifPresent(st -> {
            switch (st) {
                case Bookkeeping.StalledUpdate.Unchanged _ -> {}
                case Bookkeeping.StalledUpdate.Set(var state) -> this.stalled = state;
                case Bookkeeping.StalledUpdate.Clear _ -> this.stalled = null;
            }
        });

        bookkeeping.activity().ifPresent(act -> this.primaryActivity = act);
        bookkeeping.witnesses().ifPresent(witnessList -> {
            for (NodeId w : witnessList) {
                if (!witnesses.contains(w)) {
                    witnesses.add(w);
                }
            }
        });
    }

    private Progress installCandidate(
            Ballot view,
            Slot accepted,
            Slot committed,
            Slot applied,
            EraTable config
    ) throws Exception {
        return Progress.reconstitute(
                view,
                view,
                Status.NORMAL,
                accepted,
                committed,
                applied,
                progress.checkpoint(),
                progress.revision() + 1,
                config,
                Optional.empty()
        );
    }

    private PlannedTransition candidatePlan(
            Progress candidate,
            JournalMutation mutation,
            List<Effect> effects,
            InputKind kind,
            boolean completion
    ) {
        boolean progressChanged = !candidate.current().equals(progress.current())
                || !candidate.retained().equals(progress.retained())
                || candidate.status() != progress.status()
                || !candidate.accepted().equals(progress.accepted())
                || !candidate.committed().equals(progress.committed())
                || !candidate.applied().equals(progress.applied())
                || !candidate.checkpoint().equals(progress.checkpoint())
                || candidate.fault().isPresent();

        ProgressIntent pIntent = progressChanged ? ProgressIntent.RECORD : ProgressIntent.UNCHANGED;
        JournalIntent jIntent = mutation.intent();
        PersistenceIntent intent = new PersistenceIntent(progress.revision(), pIntent, jIntent);

        return new PlannedTransition(
                progress.revision(),
                candidate,
                mutation,
                intent,
                effects,
                kind,
                completion,
                Bookkeeping.EMPTY,
                new Diagnostic.None(),
                Optional.empty()
        );
    }

    private PlannedTransition dropPlan(Diagnostic diagnostic, InputKind kind) {
        Progress candidate = progress.withRevision(progress.revision() + 1);
        return candidatePlan(candidate, new JournalMutation.None(), List.of(), kind, false)
                .withDiagnostic(diagnostic);
    }

    private PlannedTransition planConfirmation(long revision, StabilityResult result) throws Exception {
        if (parked == null) {
            throw new ReplicaPlanException(new PlanRefusal.NoTransitionOutstanding());
        }
        if (parked.base != revision) {
            throw new ReplicaPlanException(new PlanRefusal.ConfirmationMismatch(parked.base, revision));
        }

        return switch (result) {
            case StabilityResult.Stable _ -> {
                PlannedTransition pt = candidatePlan(parked.candidate, parked.journal, parked.effects, parked.kind, true)
                        .withBookkeeping(parked.bookkeeping)
                        .withDiagnostic(parked.diagnostic);
                yield pt;
            }
            case StabilityResult.Failed _ -> {
                Progress candidate = progress.withRevision(progress.revision() + 1);
                yield candidatePlan(candidate, new JournalMutation.None(), List.of(), parked.kind, true);
            }
            case StabilityResult.Indeterminate _ -> {
                Progress candidate = progress.withFault(Fault.INDETERMINATE_PERSISTENCE).withRevision(progress.revision() + 1);
                yield candidatePlan(candidate, new JournalMutation.None(), List.of(), parked.kind, true);
            }
        };
    }

    private PlannedTransition planTick(JournalView journal, Tick at) throws Exception {
        Ballot current = progress.current();
        boolean promotable = (progress.status() == Status.RESTARTING || progress.status() == Status.JOINING)
                && current.equals(progress.retained())
                && current.view().equals(View.INITIAL)
                && progress.accepted().equals(INIT_SLOT)
                && progress.committed().equals(INIT_SLOT)
                && journal.get(VOID_SLOT).isPresent()
                && journal.get(INIT_SLOT).isPresent()
                && primaryOf(current).equals(Optional.of(own));

        if (promotable) {
            Progress candidate = progress.withStatus(Status.NORMAL).withRevision(progress.revision() + 1);
            List<Effect> effects = broadcastCommit(progress.committed());
            return candidatePlan(candidate, new JournalMutation.None(), effects, new InputKind.Tick(), false)
                    .withBookkeeping(new Bookkeeping(
                            List.of(), Optional.empty(), Optional.empty(), Optional.empty(),
                            Optional.of(at), Optional.empty()
                    ));
        }

        // Suspicion of silent primary
        boolean votingMember = progress.config().current().config().weightOf(own).map(w -> w.value() >= 1).orElse(false);
        boolean suspects = knobs.primaryTimeout() != 0
                && votingMember
                && (progress.status() == Status.NORMAL || progress.status() == Status.RESTARTING)
                && (at.value() > primaryActivity.value() + knobs.primaryTimeout());

        if (suspects) {
            View nextView = current.view().next().orElseThrow();
            Ballot target = new Ballot(current.era(), nextView);
            return enterViewChange(journal, target, new HashSet<>(), at, new InputKind.Tick());
        }

        Progress candidate = progress.withRevision(progress.revision() + 1);
        return candidatePlan(candidate, new JournalMutation.None(), List.of(), new InputKind.Tick(), false);
    }

    private PlannedTransition enterViewChange(
            JournalView journal,
            Ballot target,
            Set<NodeId> initialFences,
            Tick at,
            InputKind kind
    ) throws Exception {
        Progress candidate = progress.withViewChange(target).withRevision(progress.revision() + 1);
        Message svc = new Message(
                new Header(Tag.START_VIEW_CHANGE, target, Slot.NONE),
                new Body.StartViewChange()
        );
        List<Effect> effects = backups().stream()
                .map(to -> (Effect) new Effect.Send(to, target.era(), svc))
                .toList();

        Set<NodeId> fences = new HashSet<>(initialFences);
        fences.add(own);

        Bookkeeping.ViewChangeVolatile vcState = new Bookkeeping.ViewChangeVolatile(
                target, fences, new HashMap<>(), Optional.empty()
        );

        return candidatePlan(candidate, new JournalMutation.None(), effects, kind, false)
                .withBookkeeping(new Bookkeeping(
                        List.of(),
                        Optional.of(new Bookkeeping.ViewChangeUpdate.Set(vcState)),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                ));
    }

    private PlannedTransition planPropose(JournalView journal, Operation operation) throws Exception {
        Ballot current = progress.current();
        EraRecord record = progress.config().current();
        boolean isPrimary = progress.status() == Status.NORMAL
                && record.config().primary(current.view()).equals(Optional.of(own));

        if (!isPrimary) {
            throw new ReplicaPlanException(new PlanRefusal.NotPrimary(current, record.config().primary(current.view())));
        }

        Slot slot = progress.accepted().next().orElseThrow(() -> new ReplicaPlanException(new PlanRefusal.SlotSpaceExhausted()));
        Era era = record.era();

        LogEntry entry = new LogEntry(
                slot,
                era,
                new Payload.OperationPayload(operation.id(), operation.payload())
        );

        Progress candidate = progress.withAccepted(slot).withRevision(progress.revision() + 1);

        Message prepare = new Message(
                new Header(Tag.PREPARE, current, slot),
                new Body.Prepare(entry, progress.committed())
        );

        List<Effect> effects = backups().stream()
                .map(to -> (Effect) new Effect.Send(to, era, prepare))
                .toList();

        Bookkeeping bookkeeping = new Bookkeeping(
                List.of(Map.entry(slot, List.of())),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        return candidatePlan(candidate, new JournalMutation.Accept(List.of(entry)), effects, new InputKind.ClientRequest(), false)
                .withBookkeeping(bookkeeping);
    }

    private PlannedTransition planPeer(
            JournalView journal,
            NodeId from,
            Message message,
            Tick at,
            InputKind kind
    ) throws Exception {
        Header header = message.header();
        Ballot current = progress.current();

        return switch (message.body()) {
            case Body.Prepare(var entry, var piggybacked) -> planPeerPrepare(journal, from, header, entry, piggybacked, kind);
            case Body.PrepareOk _ -> planPeerPrepareOk(journal, from, header, kind);
            case Body.Commit(var committed) -> planPeerCommit(journal, from, header, committed, kind);
            case Body.StartViewChange _ -> planPeerStartViewChange(journal, from, header, at, kind);
            case Body.DoViewChange(var ret, var acc, var com, var suf, var ev, var ep) ->
                    planPeerDoViewChange(journal, from, header, ret, acc, com, suf, ev, ep, at, kind);
            case Body.StartView(var suf, var acc, var com, var ep) ->
                    planPeerStartView(journal, from, header, suf, acc, com, ep, at, kind);
            case Body.GetState(var fromSlot) -> planPeerGetState(journal, from, header, fromSlot, kind);
            case Body.NewState(var entries, var through, var com, var more) ->
                    planPeerNewState(journal, from, header, entries, through, com, more, kind);
            case Body.Reincarnation(var oldId, var newId, var com, var prep) ->
                    planPeerReincarnation(journal, from, header, oldId, newId, com, prep, kind);
            case Body.PlannedViewChange _ -> dropPlan(new Diagnostic.None(), kind);
            case Body.Fuse _ -> dropPlan(new Diagnostic.FuseRefusal(), kind);
            case Body.FuseOk _ -> dropPlan(new Diagnostic.FuseRefusal(), kind);
            case Body.CommitBatch _ -> dropPlan(new Diagnostic.FuseRefusal(), kind);
            case Body.GossipRequest _ -> dropPlan(new Diagnostic.None(), kind);
        };
    }

    private PlannedTransition planPeerPrepare(
            JournalView journal,
            NodeId from,
            Header header,
            LogEntry entry,
            Slot piggybacked,
            InputKind kind
    ) throws Exception {
        Ballot current = progress.current();
        if (!header.view().equals(current)) {
            return dropPlan(new Diagnostic.ViewMismatch(header.view(), current), kind);
        }

        Optional<NodeId> primary = primaryOf(current);
        if (!primary.equals(Optional.of(from))) {
            return dropPlan(new Diagnostic.SenderNotPrimary(from, header.view()), kind);
        }

        boolean eraLegal = entry.era().equals(header.view().era())
                || header.view().era().next().equals(Optional.of(entry.era()));
        if (!eraLegal) {
            return dropPlan(new Diagnostic.EraDiscipline(entry.era(), header.view()), kind);
        }

        if (!header.slot().equals(entry.slot())) {
            return dropPlan(new Diagnostic.PrepareSlotMismatch(header.slot(), entry.slot()), kind);
        }

        Slot accepted = progress.accepted();
        if (entry.slot().compareTo(accepted) <= 0) {
            Optional<LogEntry> held = journal.get(entry.slot());
            if (held.isEmpty()) {
                throw new ReplicaPlanException(new PlanRefusal.JournalEntryUnavailable(entry.slot()));
            }
            if (!held.get().equals(entry)) {
                return dropPlan(new Diagnostic.ConflictingEntry(entry.slot()), kind);
            }

            Slot newCommitted = progress.committed().compareTo(piggybacked) < 0
                    ? (piggybacked.compareTo(accepted) < 0 ? piggybacked : accepted)
                    : progress.committed();

            Progress candidate = progress.withCommitted(newCommitted).withRevision(progress.revision() + 1);
            Message prepareOk = new Message(
                    new Header(Tag.PREPARE_OK, current, entry.slot()),
                    new Body.PrepareOk()
            );
            List<Effect> effects = new ArrayList<>();
            effects.add(new Effect.Send(from, current.era(), prepareOk));
            effects.addAll(applyEffects(journal, progress.committed(), newCommitted));

            return candidatePlan(candidate, new JournalMutation.None(), effects, kind, false);
        }

        Slot next = accepted.next().orElseThrow();
        if (!entry.slot().equals(next)) {
            return dropPlan(new Diagnostic.GapDetected(next, entry.slot()), kind);
        }

        Slot newCommitted = progress.committed().compareTo(piggybacked) < 0
                ? (piggybacked.compareTo(entry.slot()) < 0 ? piggybacked : entry.slot())
                : progress.committed();

        Progress candidate = progress.withAccepted(entry.slot())
                .withCommitted(newCommitted)
                .withStatus(Status.NORMAL)
                .withRevision(progress.revision() + 1);

        Message prepareOk = new Message(
                new Header(Tag.PREPARE_OK, current, entry.slot()),
                new Body.PrepareOk()
        );
        List<Effect> effects = new ArrayList<>();
        effects.add(new Effect.Send(from, current.era(), prepareOk));
        effects.addAll(applyEffects(journal, progress.committed(), newCommitted));

        return candidatePlan(candidate, new JournalMutation.Accept(List.of(entry)), effects, kind, false);
    }

    private PlannedTransition planPeerPrepareOk(
            JournalView journal,
            NodeId from,
            Header header,
            InputKind kind
    ) throws Exception {
        Ballot current = progress.current();
        if (progress.status() != Status.NORMAL || !primaryOf(current).equals(Optional.of(own))) {
            return dropPlan(new Diagnostic.PrepareOkNotPrimary(from, header.slot()), kind);
        }

        Slot slot = header.slot();
        if (slot.compareTo(progress.committed()) <= 0 || slot.compareTo(progress.accepted()) > 0) {
            return dropPlan(new Diagnostic.SlotNotOutstanding(slot, from), kind);
        }

        Proposal proposal = proposals.computeIfAbsent(slot, k -> new Proposal());
        if (proposal.oks.contains(from)) {
            return dropPlan(new Diagnostic.DuplicatePrepareOk(slot, from), kind);
        }

        List<NodeId> voters = new ArrayList<>(proposal.oks);
        voters.add(from);
        voters.add(own);

        boolean quorum = strategy.isQuorum(Role.COMMIT, progress.config().current().config(), voters);
        if (quorum && slot.compareTo(progress.committed()) > 0) {
            Slot newCommitted = slot;
            Progress candidate = progress.withCommitted(newCommitted).withRevision(progress.revision() + 1);

            List<Effect> effects = new ArrayList<>();
            effects.addAll(broadcastCommit(newCommitted));
            effects.addAll(applyEffects(journal, progress.committed(), newCommitted));

            return candidatePlan(candidate, new JournalMutation.None(), effects, kind, false)
                    .withBookkeeping(new Bookkeeping(
                            List.of(Map.entry(slot, List.of(from))),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty()
                    ));
        }

        Progress candidate = progress.withRevision(progress.revision() + 1);
        return candidatePlan(candidate, new JournalMutation.None(), List.of(), kind, false)
                .withBookkeeping(new Bookkeeping(
                        List.of(Map.entry(slot, List.of(from))),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                ));
    }

    private PlannedTransition planPeerCommit(
            JournalView journal,
            NodeId from,
            Header header,
            Slot committed,
            InputKind kind
    ) throws Exception {
        Ballot current = progress.current();
        if (!header.view().equals(current)) {
            return dropPlan(new Diagnostic.ViewMismatch(header.view(), current), kind);
        }

        Slot newCommitted = committed.compareTo(progress.accepted()) < 0 ? committed : progress.accepted();
        if (newCommitted.compareTo(progress.committed()) <= 0) {
            return dropPlan(new Diagnostic.None(), kind);
        }

        Progress candidate = progress.withCommitted(newCommitted).withRevision(progress.revision() + 1);
        List<Effect> effects = applyEffects(journal, progress.committed(), newCommitted);

        return candidatePlan(candidate, new JournalMutation.None(), effects, kind, false);
    }

    private PlannedTransition planPeerStartViewChange(
            JournalView journal,
            NodeId from,
            Header header,
            Tick at,
            InputKind kind
    ) throws Exception {
        Ballot target = header.view();
        if (target.compareTo(progress.current()) <= 0) {
            return dropPlan(new Diagnostic.StaleViewChange(target, progress.current()), kind);
        }

        Set<NodeId> fences = (viewChange != null && viewChange.target().equals(target))
                ? new HashSet<>(viewChange.fences())
                : new HashSet<>();
        fences.add(from);

        return enterViewChange(journal, target, fences, at, kind);
    }

    private PlannedTransition planPeerDoViewChange(
            JournalView journal,
            NodeId from,
            Header header,
            Ballot retained,
            Slot accepted,
            Slot committed,
            List<LogEntry> suffix,
            EvidenceKind evidenceKind,
            EraProof eraProof,
            Tick at,
            InputKind kind
    ) throws Exception {
        Ballot target = header.view();
        if (viewChange == null || !viewChange.target().equals(target)) {
            return dropPlan(new Diagnostic.StaleEvidence(target, progress.current()), kind);
        }

        if (!primaryOf(target).equals(Optional.of(own))) {
            return dropPlan(new Diagnostic.EvidenceNotCollected(from, target), kind);
        }

        Map<NodeId, Evidence> evidenceMap = new HashMap<>(viewChange.evidence());
        evidenceMap.put(from, new Evidence(retained, accepted, committed, suffix, evidenceKind, eraProof));

        List<NodeId> voters = new ArrayList<>(evidenceMap.keySet());
        voters.add(own);

        boolean quorum = strategy.isQuorum(Role.VIEW_CHANGE, progress.config().current().config(), voters);
        if (quorum) {
            // Rank and install winner
            Evidence best = new Evidence(
                    progress.retained(),
                    progress.accepted(),
                    progress.committed(),
                    List.of(),
                    EvidenceKind.ORDINARY,
                    new EraProof(new SystemOperation.Init(progress.config().current().config().order().stream().map(Member::node).toList()), INIT_SLOT)
            );
            for (Evidence ev : evidenceMap.values()) {
                if (ev.retained().compareTo(best.retained()) > 0
                        || (ev.retained().equals(best.retained()) && ev.accepted().compareTo(best.accepted()) > 0)) {
                    best = ev;
                }
            }

            Progress candidate = installCandidate(target, best.accepted(), best.committed(), progress.applied(), progress.config());

            Message startView = new Message(
                    new Header(Tag.START_VIEW, target, best.accepted()),
                    new Body.StartView(best.suffix(), best.accepted(), best.committed(), best.eraProof())
            );

            List<Effect> effects = backups().stream()
                    .map(to -> (Effect) new Effect.Send(to, target.era(), startView))
                    .toList();

            return candidatePlan(candidate, new JournalMutation.InstallSuffix(progress.accepted(), best.suffix()), effects, kind, false)
                    .withBookkeeping(new Bookkeeping(
                            List.of(),
                            Optional.of(new Bookkeeping.ViewChangeUpdate.Clear()),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.of(at),
                            Optional.empty()
                    ));
        }

        Progress candidate = progress.withRevision(progress.revision() + 1);
        Bookkeeping.ViewChangeVolatile updated = new Bookkeeping.ViewChangeVolatile(
                target, viewChange.fences(), evidenceMap, viewChange.selected()
        );
        return candidatePlan(candidate, new JournalMutation.None(), List.of(), kind, false)
                .withBookkeeping(new Bookkeeping(
                        List.of(),
                        Optional.of(new Bookkeeping.ViewChangeUpdate.Set(updated)),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                ));
    }

    private PlannedTransition planPeerStartView(
            JournalView journal,
            NodeId from,
            Header header,
            List<LogEntry> suffix,
            Slot accepted,
            Slot committed,
            EraProof eraProof,
            Tick at,
            InputKind kind
    ) throws Exception {
        Ballot target = header.view();
        if (target.compareTo(progress.current()) < 0) {
            return dropPlan(new Diagnostic.StartViewFromStaleView(target, progress.current()), kind);
        }

        if (!primaryOf(target).equals(Optional.of(from))) {
            return dropPlan(new Diagnostic.StartViewNotFromPrimary(from, target), kind);
        }

        // Check if suffix conflicts with committed local slot
        for (LogEntry e : suffix) {
            if (e.slot().compareTo(progress.committed()) <= 0) {
                Optional<LogEntry> held = journal.get(e.slot());
                if (held.isPresent() && !held.get().equals(e)) {
                    Progress candidate = progress.withFault(Fault.ILLEGAL_TRANSITION).withRevision(progress.revision() + 1);
                    return candidatePlan(candidate, new JournalMutation.None(), List.of(), kind, false)
                            .withFault(Fault.ILLEGAL_TRANSITION);
                }
            }
        }

        // Check if suffix has gap
        Slot startSlot = suffix.isEmpty() ? accepted : suffix.getFirst().slot();
        if (startSlot.compareTo(progress.accepted().next().orElse(startSlot)) > 0) {
            Message offer = new Message(header, new Body.StartView(suffix, accepted, committed, eraProof));
            Bookkeeping bookkeeping = new Bookkeeping(
                    List.of(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(new Bookkeeping.StalledUpdate.Set(new Bookkeeping.StalledStartView(from, offer))),
                    Optional.empty(),
                    Optional.empty()
            );
            return dropPlan(new Diagnostic.GapDetected(progress.accepted().next().orElse(startSlot), startSlot), kind)
                    .withBookkeeping(bookkeeping);
        }

        Progress candidate = installCandidate(target, accepted, committed, progress.applied(), progress.config());

        List<Effect> effects = applyEffects(journal, progress.committed(), committed);

        return candidatePlan(candidate, new JournalMutation.InstallSuffix(startSlot, suffix), effects, kind, false)
                .withBookkeeping(new Bookkeeping(
                        List.of(),
                        Optional.of(new Bookkeeping.ViewChangeUpdate.Clear()),
                        Optional.empty(),
                        Optional.of(new Bookkeeping.StalledUpdate.Clear()),
                        Optional.of(at),
                        Optional.empty()
                ));
    }

    private PlannedTransition planPeerGetState(
            JournalView journal,
            NodeId from,
            Header header,
            Slot fromSlot,
            InputKind kind
    ) throws Exception {
        List<LogEntry> entries = new ArrayList<>();
        Slot cur = fromSlot;
        while (cur.compareTo(progress.accepted()) <= 0 && entries.size() < 100) {
            journal.get(cur).ifPresent(entries::add);
            Optional<Slot> next = cur.next();
            if (next.isEmpty()) break;
            cur = next.get();
        }

        Slot through = entries.isEmpty() ? fromSlot : entries.getLast().slot();
        boolean more = through.compareTo(progress.accepted()) < 0;

        Message response = new Message(
                new Header(Tag.NEW_STATE, progress.current(), through),
                new Body.NewState(entries, through, progress.committed(), more)
        );

        List<Effect> effects = List.of(new Effect.Send(from, progress.current().era(), response));
        Progress candidate = progress.withRevision(progress.revision() + 1);
        return candidatePlan(candidate, new JournalMutation.None(), effects, kind, false);
    }

    private PlannedTransition planPeerNewState(
            JournalView journal,
            NodeId from,
            Header header,
            List<LogEntry> entries,
            Slot through,
            Slot committed,
            boolean more,
            InputKind kind
    ) throws Exception {
        if (entries.isEmpty()) {
            return dropPlan(new Diagnostic.None(), kind);
        }

        Slot first = entries.getFirst().slot();
        Slot expected = progress.accepted().next().orElse(first);
        if (!first.equals(expected)) {
            return dropPlan(new Diagnostic.GapDetected(expected, first), kind);
        }

        Slot newAccepted = entries.getLast().slot();
        Slot newCommitted = committed.compareTo(newAccepted) < 0 ? committed : newAccepted;

        Progress candidate = progress.withAccepted(newAccepted)
                .withCommitted(newCommitted)
                .withRevision(progress.revision() + 1);

        List<Effect> effects = applyEffects(journal, progress.committed(), newCommitted);
        return candidatePlan(candidate, new JournalMutation.Accept(entries), effects, kind, false);
    }

    private PlannedTransition planPeerReincarnation(
            JournalView journal,
            NodeId from,
            Header header,
            NodeId oldId,
            NodeId newId,
            Slot committed,
            Slot prepared,
            InputKind kind
    ) throws Exception {
        Ballot current = progress.current();
        if (progress.status() != Status.NORMAL || !primaryOf(current).equals(Optional.of(own))) {
            return dropPlan(new Diagnostic.ReincarnationRefused(from, current), kind);
        }

        List<LogEntry> missed = new ArrayList<>();
        Slot cur = prepared.next().orElse(prepared);
        while (cur.compareTo(progress.committed()) <= 0 && missed.size() < 100) {
            journal.get(cur).ifPresent(missed::add);
            Optional<Slot> next = cur.next();
            if (next.isEmpty()) break;
            cur = next.get();
        }

        Slot through = missed.isEmpty() ? prepared : missed.getLast().slot();
        Message statePush = new Message(
                new Header(Tag.NEW_STATE, header.view(), through),
                new Body.NewState(missed, through, progress.committed(), through.compareTo(progress.committed()) < 0)
        );

        List<Effect> effects = List.of(new Effect.Send(newId, header.view().era(), statePush));
        Progress candidate = progress.withRevision(progress.revision() + 1);
        return candidatePlan(candidate, new JournalMutation.None(), effects, kind, false);
    }

    private PlannedTransition planApplied(JournalView journal, Slot slot) throws Exception {
        Slot expected = progress.applied().next().orElseThrow();
        if (!slot.equals(expected) || slot.compareTo(progress.committed()) > 0) {
            throw new ReplicaPlanException(new PlanRefusal.UnexpectedApplied(Optional.of(expected), slot));
        }

        Slot walk = slot;
        while (walk.compareTo(progress.committed()) < 0) {
            Optional<Slot> nextSlot = walk.next();
            if (nextSlot.isEmpty()) break;
            Optional<LogEntry> entry = journal.get(nextSlot.get());
            if (entry.isPresent() && entry.get().payload() instanceof Payload.SystemPayload) {
                walk = nextSlot.get();
            } else {
                break;
            }
        }

        Progress candidate = progress.withApplied(walk).withRevision(progress.revision() + 1);
        return candidatePlan(candidate, new JournalMutation.None(), List.of(), new InputKind.Applied(), false);
    }

    private PlannedTransition planCheckpointed(Slot through) throws Exception {
        if (through.compareTo(progress.applied()) > 0) {
            throw new ReplicaPlanException(new PlanRefusal.CheckpointExceedsApplied(progress.applied(), through));
        }

        Progress candidate = progress.withCheckpoint(through).withRevision(progress.revision() + 1);
        return candidatePlan(candidate, new JournalMutation.None(), List.of(), new InputKind.Checkpointed(), false);
    }

    private PlannedTransition planSubmitPlan(JournalView journal, Plan plan, InputKind kind) throws Exception {
        Optional<PlanRejection> rejection = plan.validateAgainst(progress.config().current().config());
        PlanVerdict verdict = rejection.map(r -> (PlanVerdict) new PlanVerdict.Rejected(r.toString()))
                .orElse(new PlanVerdict.Accepted());

        List<Effect> effects = List.of(new Effect.AdminResponse(verdict));
        Progress candidate = progress.withRevision(progress.revision() + 1);
        return candidatePlan(candidate, new JournalMutation.None(), effects, kind, false);
    }

    private PlannedTransition planAdminForceView(JournalView journal, Ballot target, Tick at) throws Exception {
        if (target.era().compareTo(progress.current().era()) != 0
                || target.view().compareTo(progress.current().view()) <= 0) {
            throw new ReplicaPlanException(new PlanRefusal.AdminForceViewRefused("Target view must advance in current era"));
        }
        return enterViewChange(journal, target, new HashSet<>(), at, new InputKind.Admin());
    }

    private PlannedTransition planReconfigure(JournalView journal, SystemOperation op, Optional<Pivot> pivot) throws Exception {
        Ballot current = progress.current();
        if (progress.status() != Status.NORMAL || !primaryOf(current).equals(Optional.of(own))) {
            throw new ReplicaPlanException(new PlanRefusal.NotPrimary(current, primaryOf(current)));
        }

        Slot slot = progress.accepted().next().orElseThrow();
        LogEntry entry = new LogEntry(slot, progress.config().current().era(), new Payload.SystemPayload(op));
        Progress candidate = progress.withAccepted(slot).withRevision(progress.revision() + 1);

        Message prepare = new Message(
                new Header(Tag.PREPARE, current, slot),
                new Body.Prepare(entry, progress.committed())
        );

        List<Effect> effects = backups().stream()
                .map(to -> (Effect) new Effect.Send(to, entry.era(), prepare))
                .toList();

        return candidatePlan(candidate, new JournalMutation.Accept(List.of(entry)), effects, new InputKind.Reconfiguration(), false)
                .withBookkeeping(new Bookkeeping(
                        List.of(Map.entry(slot, List.of())),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                ));
    }

    private PlannedTransition planReincarnate(JournalView journal, NodeId oldId, InputKind kind) throws Exception {
        Ballot current = progress.current();
        List<Effect> effects = backups().stream()
                .map(to -> (Effect) new Effect.Send(
                        to,
                        current.era(),
                        new Message(
                                new Header(Tag.REINCARNATION, current, Slot.NONE),
                                new Body.Reincarnation(oldId, own, progress.committed(), progress.accepted())
                        )
                ))
                .toList();

        Progress candidate = progress.withRevision(progress.revision() + 1);
        return candidatePlan(candidate, new JournalMutation.None(), effects, kind, false);
    }

    private List<Effect> broadcastCommit(Slot committed) {
        Ballot current = progress.current();
        Message commit = new Message(
                new Header(Tag.COMMIT, current, committed),
                new Body.Commit(committed)
        );
        return backups().stream()
                .map(to -> (Effect) new Effect.Send(to, current.era(), commit))
                .toList();
    }

    private List<Effect> applyEffects(JournalView journal, Slot fromCommitted, Slot toCommitted) {
        List<Effect> effects = new ArrayList<>();
        Slot cur = fromCommitted.next().orElse(fromCommitted);
        while (cur.compareTo(toCommitted) <= 0) {
            Optional<LogEntry> entry = journal.get(cur);
            if (entry.isPresent() && entry.get().payload() instanceof Payload.OperationPayload op) {
                effects.add(new Effect.Apply(cur, op.id(), op.payload()));
            }
            Optional<Slot> next = cur.next();
            if (next.isEmpty()) break;
            cur = next.get();
        }
        return effects;
    }
}
