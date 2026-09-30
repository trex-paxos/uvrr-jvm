// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0
package com.github.trex_paxos.core.configuration;

import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.ids.View;
import com.github.trex_paxos.core.wire.Malformed;
import com.github.trex_paxos.core.wire.Pack;
import com.github.trex_paxos.core.wire.PackWriter;
import com.github.trex_paxos.core.wire.UnpackCursor;
import com.github.trex_paxos.core.wire.UnpackError;
import com.github.trex_paxos.core.wire.UnpackException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// The reconfiguration operation alphabet (§8.7.2).
public sealed interface SystemOperation extends Pack {
  int discriminant();

  record Void() implements SystemOperation {
    @Override
    public int discriminant() {
      return 1;
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

  record Init(List<NodeId> order) implements SystemOperation {
    public Init {
      Objects.requireNonNull(order, "order");
      order = List.copyOf(order);
    }

    @Override
    public int discriminant() {
      return 2;
    }

    @Override
    public int packedLen() {
      return 1 + 4 + 4 * order.size();
    }

    @Override
    public void pack(PackWriter w) {
      w.u8(discriminant());
      w.u32(order.size());
      for (NodeId node : order) {
        w.u32(node.value());
      }
    }
  }

  record Increment(NodeId node) implements SystemOperation {
    public Increment {
      Objects.requireNonNull(node, "node");
    }

    @Override
    public int discriminant() {
      return 3;
    }

    @Override
    public int packedLen() {
      return 1 + 4;
    }

    @Override
    public void pack(PackWriter w) {
      w.u8(discriminant());
      w.u32(node.value());
    }
  }

  record Decrement(NodeId node) implements SystemOperation {
    public Decrement {
      Objects.requireNonNull(node, "node");
    }

    @Override
    public int discriminant() {
      return 4;
    }

    @Override
    public int packedLen() {
      return 1 + 4;
    }

    @Override
    public void pack(PackWriter w) {
      w.u8(discriminant());
      w.u32(node.value());
    }
  }

  record DoubleOp() implements SystemOperation {
    @Override
    public int discriminant() {
      return 5;
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

  record HalveOp() implements SystemOperation {
    @Override
    public int discriminant() {
      return 6;
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

  record Join(NodeId node, long position) implements SystemOperation {
    public Join {
      Objects.requireNonNull(node, "node");
      if (position < 0 || position > 0xFFFF_FFFFL) {
        throw new IllegalArgumentException("position must fit in 32 unsigned bits: " + position);
      }
    }

    @Override
    public int discriminant() {
      return 7;
    }

    @Override
    public int packedLen() {
      return 1 + 4 + 4;
    }

    @Override
    public void pack(PackWriter w) {
      w.u8(discriminant());
      w.u32(node.value());
      w.u32(position);
    }
  }

  record Leave(NodeId node) implements SystemOperation {
    public Leave {
      Objects.requireNonNull(node, "node");
    }

    @Override
    public int discriminant() {
      return 8;
    }

    @Override
    public int packedLen() {
      return 1 + 4;
    }

    @Override
    public void pack(PackWriter w) {
      w.u8(discriminant());
      w.u32(node.value());
    }
  }

  record Batch(List<SystemOperation> ops) implements SystemOperation {
    public Batch {
      Objects.requireNonNull(ops, "ops");
      ops = List.copyOf(ops);
    }

    @Override
    public int discriminant() {
      return 9;
    }

    @Override
    public int packedLen() {
      int sum = 1 + 4;
      for (SystemOperation op : ops) {
        sum += op.packedLen();
      }
      return sum;
    }

    @Override
    public void pack(PackWriter w) {
      w.u8(discriminant());
      w.u32(ops.size());
      for (SystemOperation op : ops) {
        op.pack(w);
      }
    }
  }

  record Nominate(View from, long offset) implements SystemOperation {
    public Nominate {
      Objects.requireNonNull(from, "from");
      if (offset <= 0 || offset > 0xFFFF_FFFFL) {
        throw new IllegalArgumentException("offset must be positive and fit in 32 unsigned bits: " + offset);
      }
    }

    @Override
    public int discriminant() {
      return 10;
    }

    @Override
    public int packedLen() {
      return 1 + 4 + 4;
    }

    @Override
    public void pack(PackWriter w) {
      w.u8(discriminant());
      w.u32(from.value());
      w.u32(offset);
    }
  }

  static SystemOperation unpack(UnpackCursor c) throws UnpackException {
    int disc = c.u8();
    return switch (disc) {
      case 1 -> new Void();
      case 2 -> {
        long count = c.u32();
        if (count > Integer.MAX_VALUE) {
          throw new UnpackException(new UnpackError.MalformedError(new Malformed.LengthPrefixOverflow()));
        }
        var order = new ArrayList<NodeId>((int) count);
        for (int i = 0; i < count; i++) {
          order.add(new NodeId(c.u32()));
        }
        yield new Init(order);
      }
      case 3 -> new Increment(new NodeId(c.u32()));
      case 4 -> new Decrement(new NodeId(c.u32()));
      case 5 -> new DoubleOp();
      case 6 -> new HalveOp();
      case 7 -> new Join(new NodeId(c.u32()), c.u32());
      case 8 -> new Leave(new NodeId(c.u32()));
      case 9 -> {
        long count = c.u32();
        if (count > Integer.MAX_VALUE) {
          throw new UnpackException(new UnpackError.MalformedError(new Malformed.LengthPrefixOverflow()));
        }
        var ops = new ArrayList<SystemOperation>((int) count);
        for (int i = 0; i < count; i++) {
          ops.add(SystemOperation.unpack(c));
        }
        yield new Batch(ops);
      }
      case 10 -> new Nominate(new View(c.u32()), c.u32());
      default -> throw new UnpackException(new UnpackError.MalformedError(new Malformed.OutOfDomain()));
    };
  }
}
