// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.wire;

import java.util.Optional;

/// Protocol datagram discriminants (§11, §13.1, W1).
public enum Tag {
  PREPARE(2, "prepare"),
  PREPARE_OK(3, "prepare_ok"),
  COMMIT(4, "commit"),
  START_VIEW_CHANGE(5, "start_view_change"),
  DO_VIEW_CHANGE(6, "do_view_change"),
  START_VIEW(7, "start_view"),
  PLANNED_VIEW_CHANGE(8, "planned_view_change"),
  GET_STATE(9, "get_state"),
  NEW_STATE(10, "new_state"),
  REINCARNATION(13, "reincarnation"),
  FUSE(14, "fuse"),
  FUSE_OK(15, "fuse_ok"),
  COMMIT_BATCH(16, "commit_batch"),
  GOSSIP_REQUEST(17, "gossip_request");

  private final int discriminant;
  private final String wireName;

  Tag(int discriminant, String wireName) {
    this.discriminant = discriminant;
    this.wireName = wireName;
  }

  public long asU32() {
    return discriminant & 0xFFFF_FFFFL;
  }

  public String wireName() {
    return wireName;
  }

  public static Optional<Tag> fromU32(long value) {
    if (value == 2) return Optional.of(PREPARE);
    if (value == 3) return Optional.of(PREPARE_OK);
    if (value == 4) return Optional.of(COMMIT);
    if (value == 5) return Optional.of(START_VIEW_CHANGE);
    if (value == 6) return Optional.of(DO_VIEW_CHANGE);
    if (value == 7) return Optional.of(START_VIEW);
    if (value == 8) return Optional.of(PLANNED_VIEW_CHANGE);
    if (value == 9) return Optional.of(GET_STATE);
    if (value == 10) return Optional.of(NEW_STATE);
    if (value == 13) return Optional.of(REINCARNATION);
    if (value == 14) return Optional.of(FUSE);
    if (value == 15) return Optional.of(FUSE_OK);
    if (value == 16) return Optional.of(COMMIT_BATCH);
    if (value == 17) return Optional.of(GOSSIP_REQUEST);
    return Optional.empty();
  }
}
