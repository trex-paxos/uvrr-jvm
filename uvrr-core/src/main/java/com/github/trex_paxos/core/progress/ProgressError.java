// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.progress;

import com.github.trex_paxos.core.ids.Fault;
import java.util.Objects;

/// Why a progress transition or construction was refused.
public sealed interface ProgressError {
  record FrontierChain() implements ProgressError {}
  record FrontierRegress() implements ProgressError {}
  record StatusViewRelation() implements ProgressError {}
  record ViewSuccessor() implements ProgressError {}
  record AlreadyFaulted(Fault fault) implements ProgressError {
    public AlreadyFaulted {
      Objects.requireNonNull(fault, "fault");
    }
  }
  record EraSlotDiscipline() implements ProgressError {}
  record ConfigRegress() implements ProgressError {}
  record RevisionExhausted() implements ProgressError {}
}
