// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.lifecycle;

import java.util.Optional;

/// The host's durable mechanics for the boot gate.
public interface LifecycleStore {

    /// The quorum read: the working set of copies as the store observed them.
    Optional<SuperblockCopies> readCopies() throws Exception;

    /// The forced write of a decided rewrite: every copy, durably.
    void commit(SuperblockCopies copies) throws Exception;

    /// The drain: the host forces its WALs and grids to stable storage.
    void drain() throws Exception;
}
