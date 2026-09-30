// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.replica;

import com.github.trex_paxos.core.effects.Effect;

import java.util.List;
import java.util.Objects;

/// What publish did with an accepted transition.
public sealed interface PublishOutcome {

    long revision();
    List<Effect> effects();

    record Published(long revision, List<Effect> effects) implements PublishOutcome {
        public Published {
            Objects.requireNonNull(effects, "effects cannot be null");
            effects = List.copyOf(effects);
        }
    }

    record Parked(long revision, List<Effect> effects) implements PublishOutcome {
        public Parked {
            Objects.requireNonNull(effects, "effects cannot be null");
            effects = List.copyOf(effects);
        }
    }
}
