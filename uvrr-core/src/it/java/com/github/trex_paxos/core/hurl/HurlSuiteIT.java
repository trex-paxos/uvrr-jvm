// SPDX-FileCopyrightText: 2024 - 2026 Simon Massey
// SPDX-License-Identifier: Apache-2.0

package com.github.trex_paxos.core.hurl;

import jdk.incubator.java.util.json.Json;
import jdk.incubator.java.util.json.JsonArray;
import jdk.incubator.java.util.json.JsonNumber;
import jdk.incubator.java.util.json.JsonObject;
import jdk.incubator.java.util.json.JsonString;

import com.github.trex_paxos.core.harness.Harness;
import com.github.trex_paxos.core.ids.CrashCounter;
import com.github.trex_paxos.core.ids.NodeId;
import com.github.trex_paxos.core.ids.SystemId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// The `compat` profile's own wiring, asserted before any conformance logic
/// depends on it. If this fails, nothing downstream is worth reading: the source
/// root, the JSON dependency and the submodule pin are all load-bearing, and all
/// three are silent failures if they are absent.
class HurlSuiteIT {

    private static Path specDir() {
        return Path.of(System.getProperty("uvrr.spec.dir", "../spec-uvrr-core"));
    }

    private static NodeId identity(int system) {
        return new NodeId(new SystemId(system), new CrashCounter(1));
    }

    @Test
    @DisplayName("src/it/java compiles and the json.util.json backport is on the classpath")
    void theJsonBackportIsPresent() {
        var parsed = (JsonObject) Json.parse("{\"type\":\"read\",\"msg_id\":7,\"key\":3}");
        assertEquals("read", parsed.get("type").asString());
        assertEquals(7L, parsed.get("msg_id").asLong());
        assertTrue(parsed.tryGet("absent").isEmpty(), "an absent member is an empty Optional");
    }

    @Test
    @DisplayName("an integration test can reach the src/test harness")
    void theHarnessIsReachable() {
        // The harness lives in src/test/java; src/it/java is added as another test
        // source root, so both compile to target/test-classes and see each other.
        var harness = Harness.provision(3);
        harness.tickAll();
        assertEquals(3, harness.roster().size());
        assertEquals(
                java.util.List.of(
                        com.github.trex_paxos.core.progress.Status.NORMAL,
                        com.github.trex_paxos.core.progress.Status.NORMAL,
                        com.github.trex_paxos.core.progress.Status.NORMAL),
                harness.roster().stream()
                        .map(id -> harness.status(id).orElseThrow())
                        .toList(),
                "a provisioned cluster settles, which is what every corpus case assumes");

        // The status spelling the renderer must produce, recorded so the trap is
        // visible here rather than discovered as 73 corpus failures. Neither accessor
        // on Status yields it: `name()` is the screaming-case constant "NORMAL" and
        // `statusName()` is lowercase "normal", while the corpus states "Normal" in
        // every case. The renderer has to own the capitalisation itself.
        assertEquals("NORMAL", com.github.trex_paxos.core.progress.Status.NORMAL.name());
        assertEquals("normal", com.github.trex_paxos.core.progress.Status.NORMAL.statusName());
    }

    @Test
    @DisplayName("the Rust reference submodule is checked out with its Hurl suite")
    void theSubmoduleIsPresent() throws java.io.IOException {
        var spec = specDir();
        assertTrue(Files.isDirectory(spec),
                "the Rust reference submodule is missing at " + spec
                        + ": run git submodule update --init --recursive");

        var hurlSuite = spec.resolve("tests/hurl");
        assertTrue(Files.isDirectory(hurlSuite), "the Hurl suite is missing at " + hurlSuite);

        var files = Files.list(hurlSuite).toList();
        assertTrue(files.size() >= 11,
                "the suite is one file per family plus the transport contract, found " + files.size());

        var corpus = spec.resolve("tests/compliance/corpus");
        assertTrue(Files.isDirectory(corpus), "the corpus is missing at " + corpus);
    }

    @Test
    @DisplayName("json objects built from a linked map serialise in a stated order")
    void jsonSerialisationIsDeterministic() {
        // Map.of is unordered and its iteration order is not specified, so a
        // deterministic wire rendering must use a LinkedHashMap. This is the
        // trap that would otherwise make an output byte comparison flaky.
        var ordered = new java.util.LinkedHashMap<String, jdk.incubator.java.util.json.JsonValue>();
        ordered.put("src", JsonString.of("n1"));
        ordered.put("dest", JsonString.of("c1"));
        ordered.put("body", JsonObject.of(new java.util.LinkedHashMap<>(
                java.util.Map.of("type", JsonString.of("read_ok")))));
        assertEquals(
                "{\"src\":\"n1\",\"dest\":\"c1\",\"body\":{\"type\":\"read_ok\"}}",
                JsonObject.of(ordered).toString(),
                "the rendering is insertion-ordered, so two hosts agree byte for byte");
    }

    @Test
    @DisplayName("arrays round-trip through the backport")
    void jsonArraysRoundTrip() {
        var array = JsonArray.of(java.util.List.of(JsonNumber.of(1), JsonString.of("x")));
        assertEquals(2, array.asList().size());
        assertEquals(1L, array.get(0).asLong());
        assertEquals("x", array.get(1).asString());
        assertEquals("[1,\"x\"]", array.toString());
    }
}
