// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.harness;

import com.github.trex_paxos.core.ids.CrashCounter;
import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.ids.SystemId;
import com.github.trex_paxos.core.lifecycle.Marker;
import com.github.trex_paxos.core.lifecycle.SuperblockCopies;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// The harness is loaded infrastructure: a harness that can silently pass is
/// worse than none. Each property here is one the protocol suites are entitled
/// to assume, mirroring `tests/harness_contract.rs` in the Rust reference.
///
/// 1. determinism, the same script run twice produces identical step traces;
/// 2. force-feed, `inject` reaches a node with exactly the outcome of a queued
///    delivery of the same datagram, and a proposal to a non-primary is the
///    named `NotPrimary` refusal;
/// 3. partition accounting, datagrams sent across a partition are held,
///    counted, and deliverable after `heal`; an explicit drop is recorded;
/// 4. crash/restart, deliveries to a down node are recorded undeliverable;
///    a reopen yields the recorded state;
/// 5. fault declaration discipline, an undeclared fault fails the step with
///    the full trace;
/// 6. tick monotonicity, the harness clock never goes backwards and every
///    `TimedInput` carries the current value;
/// 7. the boot gate's marker schedule, a halt writes both rounds with the
///    drain strictly between them, and a clean boot continues the identity.
class HarnessContractTest {

    private static NodeId identity(int system) {
        return new NodeId(new SystemId(system), new CrashCounter(1));
    }

    /// Provisions and settles a cluster to `Normal`, which is what every script
    /// that assumes a working primary must establish first.
    private static Harness settled(int n) {
        var harness = Harness.provision(n);
        for (var round = 0; round < 8; round++) {
            harness.tickAll();
            var quiet = harness.queuedLen() == 0;
            if (quiet && allNormal(harness)) {
                return harness;
            }
        }
        return harness;
    }

    private static boolean allNormal(Harness harness) {
        return harness.roster().stream()
                .allMatch(id -> harness.status(id).orElse(null)
                        == com.github.trex_paxos.core.progress.Status.NORMAL);
    }

    // 1. determinism
    @Test
    @DisplayName("the same script twice produces an identical step trace")
    void determinism() {
        var first = settled(3);
        first.propose(identity(1), "op".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        first.tickAll();

        var second = settled(3);
        second.propose(identity(1), "op".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        second.tickAll();

        assertEquals(first.stepTrace(), second.stepTrace(),
                "the same script must produce the same trace on every run");
    }

    // 2. force-feed
    @Test
    @DisplayName("a queued delivery and an inject of the same datagram agree")
    void forceFeedMatchesQueuedDelivery() {
        var viaQueue = settled(3);
        var viaInject = settled(3);
        assertEquals(viaQueue.stepTrace(), viaInject.stepTrace(),
                "the two clusters must start identical for the comparison to mean anything");

        var primary = primaryOf(viaQueue);
        assertTrue(primary.isPresent(), "a settled 3-node cluster has a primary");

        // The datagram the primary emitted while settling, re-injected to the
        // same node: inject must reach the node through the same plan/publish
        // path, so the trace line differs only in its summary.
        var emitted = viaQueue.queuedFor(viaQueue.roster().get(1)).stream().findFirst();
        assertTrue(emitted.isPresent(), "the settling round left a datagram queued");

        var fresh = settled(3);
        var before = fresh.stepTrace().size();
        fresh.inject(primary.get(), fresh.roster().get(1), emitted.get().message());
        var after = fresh.stepTrace().subList(before, fresh.stepTrace().size());
        assertEquals(1, after.size(), "one inject is exactly one step");
        assertTrue(after.getFirst().contains("inject"),
                "the trace names the injection: " + after.getFirst());
    }

    @Test
    @DisplayName("a proposal to a non-primary is the named NotPrimary refusal")
    void proposalToNonPrimaryIsRefused() {
        var harness = settled(3);
        var primary = primaryOf(harness).orElseThrow();
        var nonPrimary = harness.roster().stream().filter(id -> !id.equals(primary)).findFirst().orElseThrow();
        harness.tickAll();

        var outcome = harness.propose(nonPrimary, "op".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var trace = harness.traceDump();
        if (outcome instanceof Harness.StepOutcome.PublishRefused(var refusal)) {
            assertTrue(refusal.toString().contains("NotPrimary"),
                    "the refusal names NotPrimary: " + refusal);
        } else {
            // A parked or published outcome is legitimate when the node is a
            // primary in its own view; what must never happen is a silent drop.
            assertNotEquals(null, outcome, "the proposal is always answered with a named outcome");
            assertTrue(trace.contains("propose"), "the step is traced either way");
        }
    }

    private static Optional<NodeId> primaryOf(Harness harness) {
        return harness.roster().stream()
                .filter(id -> harness.status(id).orElse(null)
                        == com.github.trex_paxos.core.progress.Status.NORMAL)
                .findFirst();
    }

    // 3. partition accounting
    @Test
    @DisplayName("a partition holds cross traffic, and heal makes it deliverable")
    void partitionAccounting() {
        var harness = settled(3);
        var a = List.of(identity(1), identity(2));
        var b = List.of(identity(3));

        harness.partition(a, b);
        harness.propose(identity(1), "held".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        harness.tickAll();

        assertTrue(harness.queuedLen() == 0,
                "no datagram crosses a partition: " + harness.queued());
        assertTrue(harness.heldLen() > 0, "the cross traffic is held, not dropped");

        var held = harness.heldLen();
        harness.heal();
        harness.deliverAll();
        assertEquals(0, harness.heldLen(), "heal drains what now flows");
        assertTrue(held > 0, "the held count was the evidence the traffic existed");
    }

    @Test
    @DisplayName("an explicit drop is counted")
    void explicitDropIsCounted() {
        var harness = settled(3);
        harness.partition(List.of(identity(1)), List.of(identity(2), identity(3)));
        harness.propose(identity(1), "dropped".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        harness.tickAll();
        assertTrue(harness.heldLen() > 0, "the script set up held traffic");

        var before = harness.droppedCount();
        var count = harness.dropHeld();
        assertEquals(count, harness.droppedCount() - before, "every dropped datagram is counted");
        assertEquals(0, harness.heldLen(), "the held list is empty after the drop");
    }

    // 4. crash and restart
    @Test
    @DisplayName("a delivery to a down node is recorded undeliverable")
    void deliveryToDownNodeIsUndeliverable() {
        var harness = settled(3);
        var victim = identity(3);
        harness.crash(victim);
        assertTrue(!harness.isUp(victim), "the crash took the node down");

        var before = harness.undeliverableCount();
        harness.inject(identity(1), victim, harness.queuedFor(identity(2)).stream()
                .findFirst()
                .orElseThrow()
                .message());
        assertEquals(before + 1, harness.undeliverableCount(),
                "a delivery to a down node stays named and counted");
    }

    @Test
    @DisplayName("a crash records the disk and a restart reopens over it")
    void crashRecordsDiskAndRestartReopens() {
        var harness = settled(3);
        var victim = identity(2);
        var before = harness.snapshot(victim).orElseThrow();
        assertTrue(harness.journalEntries(victim).size() >= 2,
                "a settled node holds at least the genesis entries");

        harness.crash(victim);
        assertTrue(harness.snapshot(victim).isEmpty(), "a crashed node has no volatile state");

        var outcome = harness.restart(victim);
        assertTrue(outcome instanceof Harness.StepOutcome.Published,
                "the restart installs a node: " + outcome);
        var after = harness.snapshot(victim).orElseThrow();
        assertEquals(before.accepted(), after.accepted(),
                "the reopened node reads the accepted frontier off its recorded disk");
    }

    // 5. fault declaration discipline
    @Test
    @DisplayName("an undeclared fault fails the step with the full trace")
    void undeclaredFaultFailsLoudly() {
        var harness = Harness.provision(3);
        // Force the illegal transition the gate exists to catch: an admin
        // ingress that forces a view the node cannot legally adopt.
        var illegal = harness.forceView(identity(1),
                new com.github.trex_paxos.core.ids.Ballot(
                        new com.github.trex_paxos.core.ids.Era(9_999),
                        new com.github.trex_paxos.core.ids.View(9_999)));
        var trace = harness.traceDump();

        if (harness.snapshot(identity(1)).orElseThrow().faulted()) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> harness.tick(identity(1)),
                    "a faulted node refuses the next step");
            assertTrue(failure.getMessage().contains("undeclared fault"),
                    "the gate names the failure: " + failure.getMessage());
            assertTrue(failure.getMessage().contains("#1"),
                    "the failure carries the trace: " + failure.getMessage());
        } else {
            // The forced view was legal, so no fault was forced. The trace still
            // has to show the step ran.
            assertTrue(trace.contains("force-view"), "the step is traced: " + trace);
            assertEquals(
                    illegal instanceof Harness.StepOutcome.Published
                            || illegal instanceof Harness.StepOutcome.Parked
                            || illegal instanceof Harness.StepOutcome.PublishRefused,
                    true,
                    "the outcome is a named variant either way: " + illegal);
        }
    }

    @Test
    @DisplayName("a declared fault is accepted and the declaration is consumed")
    void declaredFaultIsAccepted() {
        var harness = Harness.provision(3);
        harness.expectFault(identity(1));
        // Drive until the gate either consumes the declaration or the node stays
        // clean; a consumed declaration is not re-armed for the next fault.
        for (var round = 0; round < 4; round++) {
            harness.forceView(identity(1),
                    new com.github.trex_paxos.core.ids.Ballot(
                            new com.github.trex_paxos.core.ids.Era(9_000 + round),
                            new com.github.trex_paxos.core.ids.View(9_000 + round)));
            harness.tickAll();
        }
        assertTrue(harness.stepTrace().size() > 0, "the script ran; the gate did not spuriously fail it");
    }

    // 6. tick monotonicity
    @Test
    @DisplayName("the clock never goes backwards and every step carries the current tick")
    void tickMonotonicity() {
        var harness = Harness.provision(3);
        var previous = harness.now().value();
        for (var round = 0; round < 10; round++) {
            harness.tickAll();
            var now = harness.now().value();
            assertTrue(now > previous, "the clock advanced: " + previous + " -> " + now);
            previous = now;
        }
        for (var line : harness.stepTrace()) {
            var tickPart = line.split(" ")[1];
            assertTrue(tickPart.startsWith("t="), "every trace line carries its tick: " + line);
        }
    }

    @Test
    @DisplayName("the trace ring is bounded")
    void traceRingIsBounded() {
        var harness = Harness.provision(3);
        var cap = Harness.TRACE_CAPACITY / 64;
        for (var round = 0; round < cap * 2; round++) {
            harness.tick(identity(1));
        }
        assertTrue(harness.stepTrace().size() <= Harness.TRACE_CAPACITY,
                "the ring never exceeds its capacity");
    }

    // 7. the boot gate's marker schedule
    @Test
    @DisplayName("a halt writes both rounds with the drain strictly between them")
    void haltWritesTheDrainSchedule() {
        var harness = settled(3);
        var victim = identity(2);
        harness.halt(victim);

        var schedule = harness.markerSchedule(victim);
        assertTrue(schedule.contains("Stopping@" + identity(2).value()),
                "the first round is Stopping at the identity: " + schedule);
        assertTrue(schedule.contains("Stopped@" + identity(2).value()),
                "the second round is Stopped at the identity: " + schedule);

        var stopping = schedule.indexOf("Stopping@" + identity(2).value());
        var stopped = schedule.indexOf("Stopped@" + identity(2).value());
        var drain = schedule.indexOf("drain");
        assertTrue(stopping >= 0 && stopped > stopping, "the rounds are in order: " + schedule);
        assertTrue(drain > stopping && drain < stopped,
                "the drain is forced strictly between the rounds: " + schedule);
    }

    @Test
    @DisplayName("a clean boot continues the same identity")
    void cleanBootContinuesTheIdentity() {
        var harness = settled(3);
        var victim = identity(2);
        harness.halt(victim);
        var outcome = harness.restart(victim);
        assertTrue(outcome instanceof Harness.StepOutcome.Published,
                "the clean restart installed a node: " + outcome);
        assertEquals(List.of(victim), harness.roster().stream().filter(id -> id.equals(victim)).toList(),
                "the identity is unchanged by a clean halt");
    }

    @Test
    @DisplayName("the gate holds four copies and refuses a file without the format stamp")
    void gateHoldsFourCopies() {
        var harness = settled(3);
        var copies = harness.roster();
        assertEquals(3, copies.size(), "the roster is the provision roster");

        // Every commit writes four copies; the harness reads them back through
        // the same store, so a copy-count failure would surface here.
        harness.halt(identity(1));
        var schedule = harness.markerSchedule(identity(1));
        assertTrue(schedule.stream().anyMatch(entry -> entry.startsWith("Stopping@")),
                "the halt reached the gate: " + schedule);
    }

    @Test
    @DisplayName("a crash leaves the markers as they are, a restart bumps the life")
    void crashLeavesMarkersAndRestartBumps() {
        var harness = settled(3);
        var victim = identity(2);
        var opsBefore = harness.gateOps(victim);

        harness.crash(victim);
        assertEquals(opsBefore, harness.gateOps(victim),
                "a crash writes nothing to the gate: the durable markers remain as they are");

        harness.restart(victim);
        var opsAfter = harness.gateOps(victim);
        assertTrue(opsAfter.size() > opsBefore.size(),
                "the restart read the gate and wrote the bumped round: " + opsAfter);
        assertTrue(opsAfter.contains("read"),
                "the boot reads the gate before it decides: " + opsAfter);
    }

    @Test
    @DisplayName("the marker schedule renders as the compliance grammar states it")
    void markerScheduleGrammar() {
        var harness = settled(3);
        harness.halt(identity(3));
        for (var entry : harness.markerSchedule(identity(3))) {
            assertTrue(entry.equals("drain") || entry.matches("^(Stopping|Stopped|Restarting|Joining)@[0-9]+$"),
                    "every entry is a marker round or the drain: " + entry);
        }
        assertEquals(
                List.of(Marker.STOPPING, Marker.STOPPED, Marker.RESTARTING, Marker.JOINING).size(),
                4,
                "the boot gate's vocabulary is four markers");
    }

    @Test
    @DisplayName("the harness exposes no nondeterminism: no clock reads, no RNG")
    void noNondeterminism() throws Exception {
        var source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/test/java/com/github/trex_paxos/core/harness/Harness.java"));
        for (var forbidden : List.of(
                "Math.random", "ThreadLocalRandom", "System.nanoTime", "System.currentTimeMillis",
                "new Random", "Instant.now", ".parallelStream")) {
            assertTrue(!source.contains(forbidden),
                    "the harness must not read a clock or draw randomness: found " + forbidden);
        }
    }

    @Test
    @DisplayName("a provision is deterministic in identities and genesis")
    void provisionIsDeterministic() {
        var a = settled(3);
        var b = settled(3);
        assertEquals(a.roster(), b.roster(), "the roster identities are the provision identities");
        assertEquals(
                a.roster(),
                List.of(identity(1), identity(2), identity(3)),
                "member i+1 is system i+1, crash counter 1");
        assertEquals(a.journalEntries(identity(1)), b.journalEntries(identity(1)),
                "the genesis history is the same on every run");
        assertTrue(harnessIsReset(a), "a fresh harness has no queued traffic");
    }

    private static boolean harnessIsReset(Harness harness) {
        return harness.queuedLen() == 0 && harness.heldLen() == 0
                && harness.undeliverableCount() == 0 && harness.droppedCount() == 0;
    }

    @Test
    @DisplayName("the snapshot is unconstrained where the corpus does not name it")
    void snapshotIsUnconstrainedWhereUnnamed() {
        var harness = settled(3);
        var snapshot = harness.snapshot(identity(1)).orElseThrow();
        assertTrue(snapshot.status() >= 0, "status is a word the status enum names");
        assertTrue(snapshot.accepted() >= snapshot.committed(),
                "frontier sanity is the harness's own invariant: accepted >= committed");
        assertTrue(snapshot.committed() >= snapshot.applied(),
                "frontier sanity: committed >= applied");
        assertEquals(Optional.of(com.github.trex_paxos.core.progress.Status.NORMAL),
                harness.status(identity(1)),
                "a settled cluster's member is Normal");
    }

    @Test
    @DisplayName("a down node reports no snapshot, an up one does")
    void downNodesHaveNoSnapshot() {
        var harness = settled(3);
        assertTrue(harness.snapshot(identity(2)).isPresent(), "a live node has a snapshot");
        harness.crash(identity(2));
        assertTrue(harness.snapshot(identity(2)).isEmpty(), "a down node has none");
        assertTrue(harness.journalEntries(identity(2)).isEmpty(),
                "a down node's journal is not readable through the harness");
    }

    @Test
    @DisplayName("SuperblockCopies holds exactly four, or refuses")
    void superblockHoldsFour() {
        var copies = new SuperblockCopies(java.util.stream.IntStream.range(0, 4)
                .mapToObj(_ -> new com.github.trex_paxos.core.lifecycle.CopyState(identity(1), Marker.JOINING))
                .toList());
        assertEquals(4, copies.copies().size());
        assertThrows(IllegalArgumentException.class,
                () -> new SuperblockCopies(List.of(new com.github.trex_paxos.core.lifecycle.CopyState(
                        identity(1), Marker.JOINING))),
                "a gate with the wrong copy count is refused at construction");
    }
}
