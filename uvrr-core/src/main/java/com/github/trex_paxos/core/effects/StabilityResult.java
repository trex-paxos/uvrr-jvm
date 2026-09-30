// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.effects;

import java.util.Arrays;
import java.util.Objects;

/// The host's report on a PersistenceIntent (decision S3).
public sealed interface StabilityResult {

    /// The intent reached the declared stability level.
    record Stable(byte[] receipt) implements StabilityResult {
        public Stable {
            Objects.requireNonNull(receipt, "receipt cannot be null");
            receipt = receipt.clone();
        }

        @Override
        public byte[] receipt() {
            return receipt.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Stable stable = (Stable) o;
            return Arrays.equals(receipt, stable.receipt);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(receipt);
        }
    }

    /// The intent determinately did not complete. NOT a fault: the candidate
    /// is discarded, the previously published state stays visible and
    /// observable, and the node continues (S3).
    record Failed(byte[] reason) implements StabilityResult {
        public Failed {
            Objects.requireNonNull(reason, "reason cannot be null");
            reason = reason.clone();
        }

        @Override
        public byte[] reason() {
            return reason.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Failed failed = (Failed) o;
            return Arrays.equals(reason, failed.reason);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(reason);
        }
    }

    /// The host cannot say whether the intent completed. Sticky-faults the
    /// node (§5 invariant 5): the process must not guess whether the
    /// transition committed.
    record Indeterminate(byte[] reason) implements StabilityResult {
        public Indeterminate {
            Objects.requireNonNull(reason, "reason cannot be null");
            reason = reason.clone();
        }

        @Override
        public byte[] reason() {
            return reason.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Indeterminate that = (Indeterminate) o;
            return Arrays.equals(reason, that.reason);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(reason);
        }
    }
}
