// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.timeout;

import java.util.Objects;

/// The Sorry policy matcher: the exhaustive opinion for a state and a timeout.
public final class TimeoutMatcher {

    public static final String UNFLUSHED_RUNBOOK =
            "a node stopping but not flushed presents a stall the operator may resolve by freeing disk space and retrying the flush, or by a hard kill, and which of those is wanted is a policy we do not comprehend and will never decide";

    private TimeoutMatcher() {}

    public static Opinion matcher(State state, Timeout timeout) {
        Objects.requireNonNull(state, "state cannot be null");
        Objects.requireNonNull(timeout, "timeout cannot be null");

        if (state == State.STOPPING_NOT_FLUSHED) {
            return new Opinion.Sorry(UNFLUSHED_RUNBOOK);
        }

        return switch (state) {
            case STOPPING_NOT_FLUSHED -> new Opinion.Sorry(UNFLUSHED_RUNBOOK);
            case WITNESS, UNKNOWN, BOOTED, CRASHED, STOPPING -> new Opinion.DoNothing();
            case IN_THE_CLUSTER -> switch (timeout) {
                case CLUSTER -> new Opinion.Retransmit();
                case STEADY -> new Opinion.Heartbeat();
                case WITNESS, UNKNOWN, BOOTED, CRASHED, STOPPING, STOPPING_NOT_FLUSHED -> new Opinion.DoNothing();
            };
            case STEADY -> switch (timeout) {
                case CLUSTER -> new Opinion.Retransmit();
                case STEADY -> new Opinion.StartViewChange();
                case WITNESS, UNKNOWN, BOOTED, CRASHED, STOPPING, STOPPING_NOT_FLUSHED -> new Opinion.DoNothing();
            };
        };
    }
}
