// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.progress;

import com.github.trex_paxos.core.configuration.EraTable;
import com.github.trex_paxos.core.ids.Ballot;
import com.github.trex_paxos.core.ids.Era;
import com.github.trex_paxos.core.ids.Fault;
import com.github.trex_paxos.core.ids.Slot;
import java.util.Objects;
import java.util.Optional;

/// The compact protocol record (§5). Immutable; every transition produces a validated new value.
public final class Progress {
  private final Ballot current;
  private final Ballot retained;
  private final Status status;
  private final Slot accepted;
  private final Slot committed;
  private final Slot applied;
  private final Slot checkpoint;
  private final long revision;
  private final EraTable config;
  private final Optional<Fault> fault;

  private Progress(
      Ballot current,
      Ballot retained,
      Status status,
      Slot accepted,
      Slot committed,
      Slot applied,
      Slot checkpoint,
      long revision,
      EraTable config,
      Optional<Fault> fault) {
    this.current = Objects.requireNonNull(current, "current");
    this.retained = Objects.requireNonNull(retained, "retained");
    this.status = Objects.requireNonNull(status, "status");
    this.accepted = Objects.requireNonNull(accepted, "accepted");
    this.committed = Objects.requireNonNull(committed, "committed");
    this.applied = Objects.requireNonNull(applied, "applied");
    this.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
    this.revision = revision;
    this.config = Objects.requireNonNull(config, "config");
    this.fault = Objects.requireNonNull(fault, "fault");
  }

  /// The genesis record: Ballot.INITIAL, fenced at Joining, every frontier at Slot.NONE, revision 0.
  public static Progress genesis(EraTable config) throws ProgressException {
    return reconstitute(
        Ballot.INITIAL,
        Ballot.INITIAL,
        Status.JOINING,
        Slot.NONE,
        Slot.NONE,
        Slot.NONE,
        Slot.NONE,
        0L,
        config,
        Optional.empty());
  }

  /// Reconstitutes a progress record, checking all invariants.
  public static Progress reconstitute(
      Ballot current,
      Ballot retained,
      Status status,
      Slot accepted,
      Slot committed,
      Slot applied,
      Slot checkpoint,
      long revision,
      EraTable config,
      Optional<Fault> fault) throws ProgressException {
    var p = new Progress(current, retained, status, accepted, committed, applied, checkpoint, revision, config, fault);
    p.check();
    return p;
  }

  /// Full invariant set check (§1.3, §8.7.3).
  public void check() throws ProgressException {
    checkFrontierChain();
    checkStatusRelation();
    checkEraDiscipline();
  }

  public void checkFrontierChain() throws ProgressException {
    if (checkpoint.compareTo(applied) <= 0
        && applied.compareTo(committed) <= 0
        && committed.compareTo(accepted) <= 0) {
      return;
    }
    throw new ProgressException(new ProgressError.FrontierChain());
  }

  public void checkStatusRelation() throws ProgressException {
    boolean holds = switch (status) {
      case NORMAL -> current.equals(retained);
      case VIEW_CHANGE -> current.compareTo(retained) >= 0;
      case RESTARTING, JOINING, REPLAYING -> true;
    };
    if (!holds) {
      throw new ProgressException(new ProgressError.StatusViewRelation());
    }
  }

  public void checkEraDiscipline() throws ProgressException {
    var eraAccepted = eraOfSlot(config, accepted);
    if (eraAccepted.isEmpty()) {
      throw new ProgressException(new ProgressError.EraSlotDiscipline());
    }
    boolean inWindow = eraAccepted.get().equals(current.era())
        || (current.era().next().isPresent() && current.era().next().get().equals(eraAccepted.get()));
    if (!inWindow) {
      throw new ProgressException(new ProgressError.EraSlotDiscipline());
    }
  }

  private static Optional<Era> eraOfSlot(EraTable table, Slot slot) {
    var current = table.current();
    if (current.establishedBy().compareTo(slot) <= 0) {
      return Optional.of(current.era());
    }
    if (current.era().value() == 0) {
      return Optional.empty();
    }
    var prevEra = new Era(current.era().value() - 1);
    var record = table.record(prevEra);
    if (record.isPresent() && record.get().establishedBy().compareTo(slot) <= 0) {
      return Optional.of(record.get().era());
    }
    return Optional.empty();
  }

  public Ballot current() {
    return current;
  }

  public Ballot retained() {
    return retained;
  }

  public Status status() {
    return status;
  }

  public Slot accepted() {
    return accepted;
  }

  public Slot committed() {
    return committed;
  }

  public Slot applied() {
    return applied;
  }

  public Slot checkpoint() {
    return checkpoint;
  }

  public long revision() {
    return revision;
  }

  public EraTable config() {
    return config;
  }

  public Optional<Fault> fault() {
    return fault;
  }

  public ProgressSnapshot toSnapshot() {
    return new ProgressSnapshot(
        current.era().value(),
        current.view().value(),
        retained.era().value(),
        retained.view().value(),
        status.toWord(),
        fault.isPresent(),
        accepted.value(),
        committed.value(),
        applied.value(),
        checkpoint.value(),
        revision);
  }

  private void refuseIfFaulted() throws ProgressException {
    if (fault.isPresent()) {
      throw new ProgressException(new ProgressError.AlreadyFaulted(fault.get()));
    }
  }

  public Progress withAccepted(Slot nextAccepted) throws ProgressException {
    refuseIfFaulted();
    if (nextAccepted.compareTo(this.accepted) < 0) {
      throw new ProgressException(new ProgressError.FrontierRegress());
    }
    var next = new Progress(current, retained, status, nextAccepted, committed, applied, checkpoint, revision + 1, config, fault);
    next.check();
    return next;
  }

  public Progress withCommitted(Slot nextCommitted) throws ProgressException {
    refuseIfFaulted();
    if (nextCommitted.compareTo(this.committed) < 0) {
      throw new ProgressException(new ProgressError.FrontierRegress());
    }
    var next = new Progress(current, retained, status, accepted, nextCommitted, applied, checkpoint, revision + 1, config, fault);
    next.check();
    return next;
  }

  public Progress withApplied(Slot nextApplied) throws ProgressException {
    refuseIfFaulted();
    if (nextApplied.compareTo(this.applied) < 0) {
      throw new ProgressException(new ProgressError.FrontierRegress());
    }
    var next = new Progress(current, retained, status, accepted, committed, nextApplied, checkpoint, revision + 1, config, fault);
    next.check();
    return next;
  }

  public Progress withCheckpoint(Slot nextCheckpoint) throws ProgressException {
    refuseIfFaulted();
    if (nextCheckpoint.compareTo(this.checkpoint) < 0) {
      throw new ProgressException(new ProgressError.FrontierRegress());
    }
    var next = new Progress(current, retained, status, accepted, committed, applied, nextCheckpoint, revision + 1, config, fault);
    next.check();
    return next;
  }

  public Progress withViewChange(Ballot target) throws ProgressException {
    refuseIfFaulted();
    if (!current.isLegalSuccessor(target)) {
      throw new ProgressException(new ProgressError.ViewSuccessor());
    }
    var next = new Progress(target, retained, Status.VIEW_CHANGE, accepted, committed, applied, checkpoint, revision + 1, config, fault);
    next.check();
    return next;
  }

  public Progress withViewInstalled(Ballot view, Slot nextAccepted) throws ProgressException {
    refuseIfFaulted();
    if (!view.equals(current) && !current.isLegalSuccessor(view)) {
      throw new ProgressException(new ProgressError.ViewSuccessor());
    }
    var next = new Progress(view, view, Status.NORMAL, nextAccepted, committed, applied, checkpoint, revision + 1, config, fault);
    next.check();
    return next;
  }

  public Progress withStatus(Status nextStatus) throws ProgressException {
    refuseIfFaulted();
    var next = new Progress(current, retained, nextStatus, accepted, committed, applied, checkpoint, revision + 1, config, fault);
    next.check();
    return next;
  }

  public Progress withConfig(EraTable nextConfig) throws ProgressException {
    refuseIfFaulted();
    if (nextConfig.current().era().compareTo(config.current().era()) < 0) {
      throw new ProgressException(new ProgressError.ConfigRegress());
    }
    var next = new Progress(current, retained, status, accepted, committed, applied, checkpoint, revision + 1, nextConfig, fault);
    next.check();
    return next;
  }

  public Progress withFault(Fault nextFault) throws ProgressException {
    refuseIfFaulted();
    var next = new Progress(current, retained, status, accepted, committed, applied, checkpoint, revision + 1, config, Optional.of(nextFault));
    next.check();
    return next;
  }

  public Progress withRevision(long nextRevision) {
    return new Progress(current, retained, status, accepted, committed, applied, checkpoint, nextRevision, config, fault);
  }
}
