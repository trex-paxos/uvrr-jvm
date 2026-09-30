// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.replica;

import com.github.trex_paxos.core.effects.Effect;
import com.github.trex_paxos.core.effects.PersistenceIntent;
import com.github.trex_paxos.core.ids.Fault;
import com.github.trex_paxos.core.invariant.InputKind;
import com.github.trex_paxos.core.observe.Diagnostic;
import com.github.trex_paxos.core.progress.Progress;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// The output of plan: everything publish needs, and nothing released.
public record PlannedTransition(
        long base,
        Progress candidate,
        JournalMutation journal,
        PersistenceIntent intent,
        List<Effect> effects,
        InputKind kind,
        boolean completion,
        Bookkeeping bookkeeping,
        Diagnostic diagnostic,
        Optional<Fault> fault
) {
    public PlannedTransition {
        Objects.requireNonNull(candidate, "candidate cannot be null");
        Objects.requireNonNull(journal, "journal cannot be null");
        Objects.requireNonNull(intent, "intent cannot be null");
        Objects.requireNonNull(effects, "effects cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
        Objects.requireNonNull(bookkeeping, "bookkeeping cannot be null");
        Objects.requireNonNull(diagnostic, "diagnostic cannot be null");
        Objects.requireNonNull(fault, "fault cannot be null");
        effects = List.copyOf(effects);
    }

    /// Test hook: replaces candidate for gate testing.
    public PlannedTransition substituteCandidateForGateTesting(Progress newCandidate) {
        return new PlannedTransition(
                base, newCandidate, journal, intent, effects, kind, completion, bookkeeping, diagnostic, fault
        );
    }

    public PlannedTransition withDiagnostic(Diagnostic newDiagnostic) {
        return new PlannedTransition(
                base, candidate, journal, intent, effects, kind, completion, bookkeeping, newDiagnostic, fault
        );
    }

    public PlannedTransition withFault(Fault declaredFault) {
        return new PlannedTransition(
                base, candidate, journal, intent, effects, kind, completion, bookkeeping, diagnostic, Optional.of(declaredFault)
        );
    }

    public PlannedTransition withBookkeeping(Bookkeeping newBookkeeping) {
        return new PlannedTransition(
                base, candidate, journal, intent, effects, kind, completion, newBookkeeping, diagnostic, fault
        );
    }

    public PlannedTransition withEffect(Effect effect) {
        var newEffects = new java.util.ArrayList<>(effects);
        newEffects.add(effect);
        return new PlannedTransition(
                base, candidate, journal, intent, newEffects, kind, completion, bookkeeping, diagnostic, fault
        );
    }
}
