# ConfigRadar Plan

This is the organized entry point. Keep raw discussion details in [design-draft.md](design-draft.md).

## Product Frame

ConfigRadar is an extensible configuration inventory and change analysis tool for Java/Spring applications.

First solve the common static case well:

- scan source and resources without starting the application
- produce a stable configuration inventory
- compare inventories for release review
- expose uncertain/dynamic config instead of guessing
- let projects evolve rules over time

It should not promise 100% discovery. The goal is high-confidence coverage plus an extension loop that lets teams gradually approach their own 100%.

## Documents

- [Use Cases](use-cases.md)
- [Architecture](architecture.md)
- [Coverage](coverage.md)
- [Rules and Profiling](rules-and-profiling.md)
- [Technical Decisions](technical-decisions.md)
- [Implementation Flow](implementation-flow.md)
- [Roadmap](roadmap.md)
- [Raw Design Draft](design-draft.md)

## Current Direction

Phase 1 should focus on:

- Java core configuration reads
- Spring/Spring Boot static configuration sources
- generic placeholder detection
- default YAML inventory output
- inventory diff
- uncertain/dynamic finding summaries
- simple project rules

Later phases can add:

- project profiling and generated rules
- detector/rule packs
- artifact/JAR scan
- runtime snapshot
- downstream consumers

## Parking Lot

Discuss later:

- exact technical stack
- OpenRewrite vs JavaParser vs tree-sitter split
- YAML/properties/XML parser choices
- schema versioning policy
- CLI shape
- first test fixture project
- skill packaging
- pack loading format
- whether `profile` belongs in the first implementation milestone
