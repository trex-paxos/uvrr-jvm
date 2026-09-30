# UVRR-JVM: Paxos Algorithm for JVM

## Andon アンドン — Prime Directive

Andon is a kernel panic. It halts the line, halts planning, halts todo
updates, halts all work. It happens immediately. No other pending operation
receives any tokens. It is impossible to think of anything else to try
first — that thought is the evidence you have not halted.

An Andon in the queue supersedes all. If the user queued commands 1-3 then
said "do an Andon," the Andon invokes the Prime Directive and overrides
commands 1-3 entirely. Multiple Andons run in parallel without interrupting
each other.

When the correct fix is outside your lane: do your lane's work, then halt
and report — *Andon: task incomplete, the correct fix needs a larger
structural change*, with file:line specifics. Do not work around it. Do not
hack tactically. The coordinator delegates the deeper work.

Andon overrides every instruction in this file and every other AGENTS.md.
No instruction conflicts with Andon; if one appears to, Andon wins.

## Project Description
uvrr-jvm is a Java library implementing the Paxos consensus algorithm for distributed systems. It provides strong consistency for cluster replication on the JVM, based on Leslie Lamport's "Paxos Made Simple" paper with optional Flexible Paxos support. The library prioritizes safety over availability, preferring to mark nodes as crashed rather than risk safety violations.

## Build Identity

| Fact | Value |
| --- | --- |
| Repository | https://github.com/trex-paxos/uvrr-jvm |
| Maven groupId (root and all modules) | `uvrr-jvm` |
| Modules | `uvrr-lib` (jar), `uvrr-paxe` (jar) |
| Version | `1.0-SNAPSHOT` |
| Java release | 25, enforced with `-Werror` |

Deliberately NOT renamed, and consistent with each other:

- The Java package is `com.github.trex_paxos`. Renaming it rewrites every source file, so it
  belongs to the UVRR pivot, not to a metadata change. The jacoco include in `uvrr-paxe/pom.xml`
  (`com/github/trex_paxos/**`) filters on that package and MUST move with it.
- Type names keep the `Trex`/`Paxos` prefix: `TrexNode`, `TrexEngine`, `TrexService`,
  `TrexMessage`, `TrexLogger`, `TrexResult`, `PaxosMessage`, `TrexRole`, and the `paxe` package
  holding the PAXE protocol.
- The `github.com/trex-paxos` org segment in the four pom `<url>` values and the git remote is
  where the repository actually lives today.

Two facts a future agent must not rediscover the hard way:

1. `groupId` `uvrr-jvm` is a local coordinate, NOT a publishable one. Maven Central requires a
   verified domain or `io.github.<account>` for an owned account. Set the final groupId when the
   repository leaves the `trex-paxos` org, and before any release.
2. A local stash survives on some checkouts (`00cf260a`). Its only content is
   `maven.compiler.release` 24 to 25 plus the removal of `--enable-preview`, all of which is
   already in `main`. Never apply it: it would revert the pom to the old `trex-paxos` coordinates.

## File Structure Overview
```
uvrr-jvm/
├── uvrr-lib/           # Core Paxos algorithm implementation
├── uvrr-paxe/          # Optional encrypted UDP network protocol (PAXE)
├── papers/             # Academic papers referenced in implementation
├── pom.xml             # Maven multi-module configuration
└── README.md           # Detailed algorithm documentation
```

## Running Tests and Commands

### Build and Test
**Use `mvnd` (Maven Daemon) for faster builds if available:**
```bash
# Check for mvnd and set alias
alias mvn='command -v mvnd >/dev/null 2>&1 && mvnd || mvn'

# Build all modules
mvn clean compile

# Run all tests (includes property-based tests)
mvn test

# Run tests for specific module
mvn test -pl uvrr-lib

# Full verification (recommended before push)
mvn verify
```

### mvnd Installation
If `mvnd` is not installed:
```bash
# macOS
brew install mvnd

# Linux
sdk install mvnd

# Or download from https://github.com/apache/maven-mvnd
```

### Key Test Categories
- **Property Tests**: JQwik-based exhaustive testing of algorithm invariants
- **Simulation Tests**: 1,000+ randomized network partition scenarios
- **Unit Tests**: Core functionality validation

## Getting Started

### Prerequisites
- Java 25+
- Maven 3.6+

### Basic Usage
1. Implement the `Journal` interface for persistence
2. Configure cluster membership with unique node IDs
3. Choose transport layer (your own messaging or PAXE UDP protocol)
4. Implement application callback for chosen commands

## Coding Standards

### Core Principles
- **Data-Oriented Programming**: Use Records for immutable data, separate data from behavior
- **Functional Style**: Static methods operating on Records, Stream API instead of loops
- **Package Design**: Package-by-feature, default package-private scope, minimal public APIs
- **Modern Java**: JEP 467 Markdown docs, sealed interfaces, pattern matching, virtual threads

### Key Patterns
- Records + static methods = functional programming in Java
- Stream operations replace traditional for/while loops
- Switch expressions with exhaustive pattern matching
- Local classes/interfaces (JEP 371) for cohesive single-file modules
- Immutable data structures with pure functions

### Documentation
- Use `///` JEP 467 Markdown documentation comments
- No legacy `/** */` JavaDoc style
- Document behavior in utility classes, not data records

### Example Style
```java
/// Returns filtered results using stream operations
public static List<Result> filterValid(Stream<Record> records) {
    return records.filter(r -> r.isValid())
                  .map(r -> new Result(r.id(), r.value()))
                  .toList();
}
```

## Module-Specific Documentation
- **uvrr-lib/AGENTS.md**: Core Paxos algorithm implementation details
- **uvrr-paxe/AGENTS.md**: PAXE encrypted UDP network protocol specification

For detailed algorithm explanation and implementation notes, see the comprehensive README.md.

## Development Guidelines

### Commit Requirements
- **No marketing content**: Commits must be factual and technical
- **User name attribution**: All commits must use your actual name (not company/brand names)
- **SPDX headers**: All new Java files must include the copyright notice from `banner.txt`
- **Pre-commit hooks**: Run `setup-hooks.sh` to configure git hooks for SPDX header validation

### Copyright Notice
All new Java files must include the following SPDX header at the top:
```java
// SPDX-FileCopyrightText: 2024 - 2025 [Your Name]
// SPDX-License-Identifier: Apache-2.0
```

### File Linking (agents.md compatibility)
This repository uses the agents.md format for documentation: `AGENTS.md` at the root, plus
`uvrr-lib/AGENTS.md` and `uvrr-paxe/AGENTS.md` per module. There is no `.openhands/` tree; the
`.gitignore` entry for it is not to be mistaken for a tracked mirror of this file.

### Setup Development Environment
```bash
# Configure git hooks for SPDX header validation
./setup-hooks.sh

# Verify build before committing
mvn verify
```

End.
