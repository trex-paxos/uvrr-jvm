// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.harness;

import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.journal.LogEntry;
import com.github.trex_paxos.core.lifecycle.Lifecycle;
import com.github.trex_paxos.core.lifecycle.LifecycleStore;
import com.github.trex_paxos.core.lifecycle.Marker;
import com.github.trex_paxos.core.lifecycle.SuperblockCopies;
import com.github.trex_paxos.core.lifecycle.CopyState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// The harness's `LifecycleStore` (`docs/uvrr-io-obligations.md`, the boot-gate
/// chapter §6): four plain marker files in a temporary directory, one per copy,
/// plus a WAL the drain appends to and forces.
///
/// Every read, write and drain is recorded on the operation log in order, so a
/// script can assert the boot gate's fixed write schedules against it. The
/// operation log outlives the session that wrote it: a halt consumes its
/// session, and the schedule that halt wrote stays assertable.
///
/// The format stamp rules: a file without `v1` predates the durable identity
/// pair and is refused, never migrated in place.
final class TmpGate implements LifecycleStore {

    static final String BLANK = "v1\n0\n-\n";
    private static final int COPIES = 4;

    private final Path dir;
    private final List<String> ops;

    private TmpGate(Path dir, List<String> ops) {
        this.dir = Objects.requireNonNull(dir, "dir cannot be null");
        this.ops = Objects.requireNonNull(ops, "ops cannot be null");
    }

    /// Opens the gate over `dir`, creating the four blank copies when the
    /// directory holds none: the first life has no durable identity yet.
    static TmpGate open(Path dir, List<String> ops) throws IOException {
        Files.createDirectories(dir);
        var gate = new TmpGate(dir, ops);
        for (var index = 0; index < COPIES; index++) {
            var path = gate.file(index);
            if (!Files.exists(path)) {
                Files.writeString(path, BLANK, StandardCharsets.UTF_8);
            }
        }
        return gate;
    }

    Path dir() {
        return dir;
    }

    /// The operation log in order: `read`, `commit:<marker>@<identity>`, `drain`.
    List<String> ops() {
        return List.copyOf(ops);
    }

    private Path file(int index) {
        return dir.resolve("gate" + index);
    }

    private CopyState readOne(int index) throws IOException {
        var text = Files.readString(file(index), StandardCharsets.UTF_8);
        var lines = List.of(text.split("\n", -1));
        if (lines.isEmpty() || !lines.getFirst().strip().equals("v1")) {
            throw new IOException("gate" + index + " predates the durable identity pair: " + text);
        }
        var identity = new NodeId(Long.parseLong(lines.get(1).strip()));
        var markerWord = lines.size() > 2 ? lines.get(2).strip() : "-";
        // A blank copy carries no marker. The identity is zero, which is what
        // makes the whole directory read as "no durable identity yet", so the
        // marker word is irrelevant; JOINING is the placeholder that keeps
        // `CopyState` total.
        var marker = markerOf(markerWord, index);
        return new CopyState(identity, marker);
    }

    private static Marker markerOf(String word, int index) throws IOException {
        return switch (word) {
            case "-" -> Marker.JOINING;
            case "Stopping" -> Marker.STOPPING;
            case "Stopped" -> Marker.STOPPED;
            case "Restarting" -> Marker.RESTARTING;
            case "Joining" -> Marker.JOINING;
            default -> throw new IOException("gate" + index + " names an unknown marker: " + word);
        };
    }

    private static String markerName(Marker marker) {
        return switch (marker) {
            case STOPPING -> "Stopping";
            case STOPPED -> "Stopped";
            case RESTARTING -> "Restarting";
            case JOINING -> "Joining";
        };
    }

    @Override
    public Optional<SuperblockCopies> readCopies() {
        ops.add("read");
        try {
            var copies = new ArrayList<CopyState>(COPIES);
            for (var index = 0; index < COPIES; index++) {
                copies.add(readOne(index));
            }
            var allBlank = copies.stream().allMatch(copy -> copy.identity().value() == 0);
            if (allBlank) {
                return Optional.empty();
            }
            return Optional.of(new SuperblockCopies(copies));
        } catch (IOException e) {
            throw new IllegalStateException("the gate read failed", e);
        }
    }

    @Override
    public void commit(SuperblockCopies copies) {
        try {
            for (var index = 0; index < COPIES; index++) {
                var copy = copies.copies().get(index);
                Files.writeString(
                        file(index),
                        "v1\n" + copy.identity().value() + "\n" + markerName(copy.marker()) + "\n",
                        StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("the gate commit failed", e);
        }
        var head = copies.copies().getFirst();
        ops.add("commit:" + markerName(head.marker()) + "@" + head.identity().value());
    }

    @Override
    public void drain() {
        try {
            Files.writeString(
                    dir.resolve("wal"),
                    "drain\n",
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("the gate drain failed", e);
        }
        ops.add("drain");
    }

    /// The marker schedule as the compliance grammar states it: each round the
    /// gate wrote, as `Marker@system:counter`, interleaved with the `drain`
    /// the halt schedule forces between its rounds.
    static List<String> schedule(List<String> ops) {
        var schedule = new ArrayList<String>();
        for (var op : ops) {
            if (op.equals("drain")) {
                schedule.add("drain");
            } else if (op.startsWith("commit:")) {
                schedule.add(op.substring("commit:".length()));
            }
        }
        return schedule;
    }

    /// Boots over this gate, the first step of every lifecycle transition.
    static Lifecycle.BootOutcome boot(LifecycleStore store) {
        try {
            return Lifecycle.boot(store);
        } catch (Exception e) {
            throw new IllegalStateException("the boot failed", e);
        }
    }
}
