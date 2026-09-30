// SPDX-FileCopyrightText: 2024 - 2025 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.message;

import com.github.trex_paxos.core.configuration.SystemOperation;
import com.github.trex_paxos.core.ids.Ballot;
import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.journal.LogEntry;
import com.github.trex_paxos.core.wire.Pack;
import com.github.trex_paxos.core.wire.PackWriter;
import com.github.trex_paxos.core.wire.Tag;
import com.github.trex_paxos.core.wire.UnpackCursor;
import com.github.trex_paxos.core.wire.UnpackError;
import com.github.trex_paxos.core.wire.UnpackException;
import com.github.trex_paxos.core.wire.Malformed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// The protocol body alphabet: [VR-2012] §4/§5, plus era evidence (§8.7.7–§8.7.8).
public sealed interface Body extends Pack {

    /// The tag this body is the payload of.
    Tag tag();

    default int discriminant() {
        return (int) tag().asU32();
    }

    /// Prepare proposal of entry at its slot (§6).
    record Prepare(LogEntry entry, Slot committed) implements Body {
        public Prepare {
            Objects.requireNonNull(entry, "entry cannot be null");
            Objects.requireNonNull(committed, "committed cannot be null");
        }

        @Override
        public Tag tag() {
            return Tag.PREPARE;
        }

        @Override
        public int packedLen() {
            return 1 + entry.packedLen() + committed.packedLen();
        }

        @Override
        public void pack(PackWriter w) {
            w.u8(discriminant());
            entry.pack(w);
            committed.pack(w);
        }
    }

    /// Backup's acceptance of a Prepare (§6).
    record PrepareOk() implements Body {
        @Override
        public Tag tag() {
            return Tag.PREPARE_OK;
        }

        @Override
        public int packedLen() {
            return 1;
        }

        @Override
        public void pack(PackWriter w) {
            w.u8(discriminant());
        }
    }

    /// Commit frontier advance carrying no new operation (§6, §13.3).
    record Commit(Slot committed) implements Body {
        public Commit {
            Objects.requireNonNull(committed, "committed cannot be null");
        }

        @Override
        public Tag tag() {
            return Tag.COMMIT;
        }

        @Override
        public int packedLen() {
            return 1 + committed.packedLen();
        }

        @Override
        public void pack(PackWriter w) {
            w.u8(discriminant());
            committed.pack(w);
        }
    }

    /// Ordinary view-change fence (§9.1).
    record StartViewChange() implements Body {
        @Override
        public Tag tag() {
            return Tag.START_VIEW_CHANGE;
        }

        @Override
        public int packedLen() {
            return 1;
        }

        @Override
        public void pack(PackWriter w) {
            w.u8(discriminant());
        }
    }

    /// Replica's state evidence for the designated new primary (§9.1).
    record DoViewChange(
            Ballot retained,
            Slot accepted,
            Slot committed,
            List<LogEntry> suffix,
            EvidenceKind evidence,
            EraProof eraProof
    ) implements Body {
        public DoViewChange {
            Objects.requireNonNull(retained, "retained cannot be null");
            Objects.requireNonNull(accepted, "accepted cannot be null");
            Objects.requireNonNull(committed, "committed cannot be null");
            Objects.requireNonNull(suffix, "suffix cannot be null");
            Objects.requireNonNull(evidence, "evidence cannot be null");
            Objects.requireNonNull(eraProof, "eraProof cannot be null");
            suffix = List.copyOf(suffix);
        }

        @Override
        public Tag tag() {
            return Tag.DO_VIEW_CHANGE;
        }

        @Override
        public int packedLen() {
            return 1 + retained.packedLen()
                    + accepted.packedLen()
                    + committed.packedLen()
                    + entriesPackedLen(suffix)
                    + evidence.packedLen()
                    + eraProof.packedLen();
        }

        @Override
        public void pack(PackWriter w) {
            w.u8(discriminant());
            retained.pack(w);
            accepted.pack(w);
            committed.pack(w);
            packEntries(suffix, w);
            evidence.pack(w);
            eraProof.pack(w);
        }
    }

    /// The new primary installing the selected history (§9.1, §13.1).
    record StartView(
            List<LogEntry> suffix,
            Slot accepted,
            Slot committed,
            EraProof eraProof
    ) implements Body {
        public StartView {
            Objects.requireNonNull(suffix, "suffix cannot be null");
            Objects.requireNonNull(accepted, "accepted cannot be null");
            Objects.requireNonNull(committed, "committed cannot be null");
            Objects.requireNonNull(eraProof, "eraProof cannot be null");
            suffix = List.copyOf(suffix);
        }

        @Override
        public Tag tag() {
            return Tag.START_VIEW;
        }

        @Override
        public int packedLen() {
            return 1 + entriesPackedLen(suffix)
                    + accepted.packedLen()
                    + committed.packedLen()
                    + eraProof.packedLen();
        }

        @Override
        public void pack(PackWriter w) {
            w.u8(discriminant());
            packEntries(suffix, w);
            accepted.pack(w);
            committed.pack(w);
            eraProof.pack(w);
        }
    }

    /// Solicitation of planned view-change evidence during overlap mode (§8.7.7 step 4).
    record PlannedViewChange() implements Body {
        @Override
        public Tag tag() {
            return Tag.PLANNED_VIEW_CHANGE;
        }

        @Override
        public int packedLen() {
            return 1;
        }

        @Override
        public void pack(PackWriter w) {
            w.u8(discriminant());
        }
    }

    /// Request for history range the requester lacks (§4, §13.1 step 5).
    record GetState(Slot from) implements Body {
        public GetState {
            Objects.requireNonNull(from, "from cannot be null");
        }

        @Override
        public Tag tag() {
            return Tag.GET_STATE;
        }

        @Override
        public int packedLen() {
            return 1 + from.packedLen();
        }

        @Override
        public void pack(PackWriter w) {
            w.u8(discriminant());
            from.pack(w);
        }
    }

    /// Chunk of history stream answering GetState (§4, §13.1 step 5).
    record NewState(
            List<LogEntry> entries,
            Slot through,
            Slot committed,
            boolean more
    ) implements Body {
        public NewState {
            Objects.requireNonNull(entries, "entries cannot be null");
            Objects.requireNonNull(through, "through cannot be null");
            Objects.requireNonNull(committed, "committed cannot be null");
            entries = List.copyOf(entries);
        }

        @Override
        public Tag tag() {
            return Tag.NEW_STATE;
        }

        @Override
        public int packedLen() {
            return 1 + entriesPackedLen(entries) + through.packedLen() + committed.packedLen() + 1;
        }

        @Override
        public void pack(PackWriter w) {
            w.u8(discriminant());
            packEntries(entries, w);
            through.pack(w);
            committed.pack(w);
            w.bool(more);
        }
    }

    /// Reincarnation announcement (`docs/uvrr-protocols.md`, reincarnation chapter §4).
    record Reincarnation(
            NodeId old,
            NodeId newId,
            Slot committed,
            Slot prepared
    ) implements Body {
        public Reincarnation {
            Objects.requireNonNull(old, "old cannot be null");
            Objects.requireNonNull(newId, "newId cannot be null");
            Objects.requireNonNull(committed, "committed cannot be null");
            Objects.requireNonNull(prepared, "prepared cannot be null");
        }

        @Override
        public Tag tag() {
            return Tag.REINCARNATION;
        }

        @Override
        public int packedLen() {
            return 1 + old.packedLen() + newId.packedLen() + committed.packedLen() + prepared.packedLen();
        }

        @Override
        public void pack(PackWriter w) {
            w.u8(discriminant());
            old.pack(w);
            newId.pack(w);
            committed.pack(w);
            prepared.pack(w);
        }
    }

    /// Packed Phase2s of one reconfiguration schedule (`docs/uvrr-fuse.md` §1).
    record Fuse(List<SystemOperation> ops) implements Body {
        public Fuse {
            Objects.requireNonNull(ops, "ops cannot be null");
            ops = List.copyOf(ops);
        }

        @Override
        public Tag tag() {
            return Tag.FUSE;
        }

        @Override
        public int packedLen() {
            return 1 + countedPackedLen(ops);
        }

        @Override
        public void pack(PackWriter w) {
            w.u8(discriminant());
            packCounted(ops, w);
        }
    }

    /// Acceptor's acknowledgement of a Fuse (`docs/uvrr-fuse.md` §3).
    record FuseOk(List<Slot> acks) implements Body {
        public FuseOk {
            Objects.requireNonNull(acks, "acks cannot be null");
            acks = List.copyOf(acks);
        }

        @Override
        public Tag tag() {
            return Tag.FUSE_OK;
        }

        @Override
        public int packedLen() {
            return 1 + countedPackedLen(acks);
        }

        @Override
        public void pack(PackWriter w) {
            w.u8(discriminant());
            packCounted(acks, w);
        }
    }

    /// One committed frontier per packed slot, in batch order (`docs/uvrr-fuse.md` §4).
    record CommitBatch(List<Slot> committed) implements Body {
        public CommitBatch {
            Objects.requireNonNull(committed, "committed cannot be null");
            committed = List.copyOf(committed);
        }

        @Override
        public Tag tag() {
            return Tag.COMMIT_BATCH;
        }

        @Override
        public int packedLen() {
            return 1 + countedPackedLen(committed);
        }

        @Override
        public void pack(PackWriter w) {
            w.u8(discriminant());
            packCounted(committed, w);
        }
    }

    /// Rejoin gossip request (`docs/uvrr-protocols.md`, rejoin chapter §2–§3).
    record GossipRequest(Slot prepared, Slot committed) implements Body {
        public GossipRequest {
            Objects.requireNonNull(prepared, "prepared cannot be null");
            Objects.requireNonNull(committed, "committed cannot be null");
        }

        @Override
        public Tag tag() {
            return Tag.GOSSIP_REQUEST;
        }

        @Override
        public int packedLen() {
            return 1 + prepared.packedLen() + committed.packedLen();
        }

        @Override
        public void pack(PackWriter w) {
            w.u8(discriminant());
            prepared.pack(w);
            committed.pack(w);
        }
    }

    static int entriesPackedLen(List<LogEntry> entries) {
        int sum = 4;
        for (LogEntry e : entries) {
            sum += e.packedLen();
        }
        return sum;
    }

    static void packEntries(List<LogEntry> entries, PackWriter w) {
        w.u32(entries.size());
        for (LogEntry e : entries) {
            e.pack(w);
        }
    }

    static List<LogEntry> unpackEntries(UnpackCursor c) throws UnpackException {
        long count = c.u32();
        List<LogEntry> entries = new ArrayList<>();
        for (long i = 0; i < count; i++) {
            entries.add(LogEntry.unpack(c));
        }
        return Collections.unmodifiableList(entries);
    }

    static <T extends Pack> int countedPackedLen(List<T> items) {
        int sum = 4;
        for (T item : items) {
            sum += item.packedLen();
        }
        return sum;
    }

    static <T extends Pack> void packCounted(List<T> items, PackWriter w) {
        w.u32(items.size());
        for (T item : items) {
            item.pack(w);
        }
    }

    static Body unpack(UnpackCursor c) throws UnpackException {
        int disc = c.u8();
        Tag tag = switch (disc) {
            case 2 -> Tag.PREPARE;
            case 3 -> Tag.PREPARE_OK;
            case 4 -> Tag.COMMIT;
            case 5 -> Tag.START_VIEW_CHANGE;
            case 6 -> Tag.DO_VIEW_CHANGE;
            case 7 -> Tag.START_VIEW;
            case 8 -> Tag.PLANNED_VIEW_CHANGE;
            case 9 -> Tag.GET_STATE;
            case 10 -> Tag.NEW_STATE;
            case 13 -> Tag.REINCARNATION;
            case 14 -> Tag.FUSE;
            case 15 -> Tag.FUSE_OK;
            case 16 -> Tag.COMMIT_BATCH;
            case 17 -> Tag.GOSSIP_REQUEST;
            default -> throw new UnpackException(new UnpackError.MalformedError(new Malformed.OutOfDomain()));
        };

        return switch (tag) {
            case PREPARE -> new Prepare(LogEntry.unpack(c), Slot.unpack(c));
            case PREPARE_OK -> new PrepareOk();
            case COMMIT -> new Commit(Slot.unpack(c));
            case START_VIEW_CHANGE -> new StartViewChange();
            case DO_VIEW_CHANGE -> new DoViewChange(
                    Ballot.unpack(c),
                    Slot.unpack(c),
                    Slot.unpack(c),
                    unpackEntries(c),
                    EvidenceKind.unpack(c),
                    EraProof.unpack(c)
            );
            case START_VIEW -> new StartView(
                    unpackEntries(c),
                    Slot.unpack(c),
                    Slot.unpack(c),
                    EraProof.unpack(c)
            );
            case PLANNED_VIEW_CHANGE -> new PlannedViewChange();
            case GET_STATE -> new GetState(Slot.unpack(c));
            case NEW_STATE -> new NewState(
                    unpackEntries(c),
                    Slot.unpack(c),
                    Slot.unpack(c),
                    c.bool()
            );
            case REINCARNATION -> new Reincarnation(
                    NodeId.unpack(c),
                    NodeId.unpack(c),
                    Slot.unpack(c),
                    Slot.unpack(c)
            );
            case FUSE -> {
                long count = c.u32();
                if (count == 0) throw new UnpackException(new UnpackError.MalformedError(new Malformed.OutOfDomain()));
                List<SystemOperation> ops = new ArrayList<>();
                for (long i = 0; i < count; i++) {
                    ops.add(SystemOperation.unpack(c));
                }
                yield new Fuse(ops);
            }
            case FUSE_OK -> {
                long count = c.u32();
                if (count == 0) throw new UnpackException(new UnpackError.MalformedError(new Malformed.OutOfDomain()));
                List<Slot> acks = new ArrayList<>();
                for (long i = 0; i < count; i++) {
                    acks.add(Slot.unpack(c));
                }
                yield new FuseOk(acks);
            }
            case COMMIT_BATCH -> {
                long count = c.u32();
                if (count == 0) throw new UnpackException(new UnpackError.MalformedError(new Malformed.OutOfDomain()));
                List<Slot> committed = new ArrayList<>();
                for (long i = 0; i < count; i++) {
                    committed.add(Slot.unpack(c));
                }
                yield new CommitBatch(committed);
            }
            case GOSSIP_REQUEST -> new GossipRequest(Slot.unpack(c), Slot.unpack(c));
        };
    }
}
