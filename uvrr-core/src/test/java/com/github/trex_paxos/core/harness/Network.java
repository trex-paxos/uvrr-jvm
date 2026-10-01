// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.harness;

import com.github.trex_paxos.core.ids.Era;
import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.ids.Slot;
import com.github.trex_paxos.core.message.Message;
import com.github.trex_paxos.core.wire.Tag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// The harness's synthetic network. Not a real transport: a FIFO queue, a held
/// list for partition-crossing datagrams, and the counters that make a
/// delivery account for itself.
///
/// The era authorizing each copy rides on the envelope separately, exactly as
/// `Effect.Send` carries it, so overlap-mode routing is observable here rather
/// than something the receiver must decode (W1).
final class Network {

    /// One datagram in flight.
    record Envelope(NodeId from, NodeId to, Era era, Message message) {
        Envelope {
            Objects.requireNonNull(from, "from cannot be null");
            Objects.requireNonNull(to, "to cannot be null");
            Objects.requireNonNull(era, "era cannot be null");
            Objects.requireNonNull(message, "message cannot be null");
        }
    }

    private final Deque<Envelope> queue = new ArrayDeque<>();
    private final List<Envelope> held = new ArrayList<>();
    private Partition partition;
    private long undeliverable;
    private long dropped;

    /// Which two sides of the cluster a partition separates. A node named by
    /// neither side is unreachable by the partition's terms and its traffic
    /// flows; the sides name the split, not the survivors.
    record Partition(List<NodeId> a, List<NodeId> b) {
        Partition {
            a = List.copyOf(a);
            b = List.copyOf(b);
        }

        boolean crosses(NodeId from, NodeId to) {
            return (a.contains(from) && b.contains(to)) || (b.contains(from) && a.contains(to));
        }
    }

    /// Enqueues a datagram, holding it when it crosses the partition. Returns
    /// whether it was held, which is what the trace line reports.
    boolean route(Envelope envelope) {
        if (partition != null && partition.crosses(envelope.from(), envelope.to())) {
            held.add(envelope);
            return true;
        }
        queue.addLast(envelope);
        return false;
    }

    Optional<Envelope> pollNext() {
        return Optional.ofNullable(queue.pollFirst());
    }

    /// Removes the oldest queued datagram satisfying `predicate`.
    Optional<Envelope> pollMatching(java.util.function.Predicate<Envelope> predicate) {
        for (var it = queue.iterator(); it.hasNext(); ) {
            var envelope = it.next();
            if (predicate.test(envelope)) {
                it.remove();
                return Optional.of(envelope);
            }
        }
        return Optional.empty();
    }

    /// Lifts the partition and requeues the held datagrams, in the order they
    /// were held. Every held datagram is requeued, not only those that now
    /// flow: with the partition gone there is nothing left to hold.
    int heal() {
        partition = null;
        int released = held.size();
        for (var envelope : held) {
            queue.addLast(envelope);
        }
        held.clear();
        return released;
    }

    void partition(List<NodeId> a, List<NodeId> b) {
        partition = new Partition(a, b);
    }

    List<Envelope> held() {
        return List.copyOf(held);
    }

    List<Envelope> queued() {
        return List.copyOf(queue);
    }

    int queuedLen() {
        return queue.size();
    }

    int heldLen() {
        return held.size();
    }

    void countUndeliverable() {
        undeliverable++;
    }

    long undeliverableCount() {
        return undeliverable;
    }

    /// Drops every held datagram, counting them.
    int dropHeld() {
        int count = held.size();
        dropped += count;
        held.clear();
        return count;
    }

    /// Drops every datagram queued for `id`, counting them.
    int dropQueuedFor(NodeId id) {
        int count = 0;
        for (var it = queue.iterator(); it.hasNext(); ) {
            if (it.next().to().equals(id)) {
                it.remove();
                count++;
            }
        }
        dropped += count;
        return count;
    }

    long droppedCount() {
        return dropped;
    }

    static java.util.function.Predicate<Envelope> addressedTo(NodeId id) {
        return envelope -> envelope.to().equals(id);
    }

    static java.util.function.Predicate<Envelope> addressedTo(NodeId id, Tag tag, Slot slot) {
        return envelope -> envelope.to().equals(id)
                && envelope.message().header().tag() == tag
                && envelope.message().header().slot().equals(slot);
    }
}
