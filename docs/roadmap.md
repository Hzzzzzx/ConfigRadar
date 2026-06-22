# Roadmap

## Phase 1: Core Static Coverage

Build the useful skeleton:

- core inventory model
- raw finding model
- uncertain finding model
- default YAML output
- Java/Spring direct scan
- generic placeholder scan
- basic inventory diff
- simple rule template

## Phase 2: Profiling and Rule Packs

Add project and ecosystem learning:

- project profiling
- generated rule candidates
- agent-assisted rule review
- detector/rule pack loading
- stale/unused rule suggestions

## Phase 3: Artifact Scan

Scan built outputs:

- `target/classes`
- `build/classes`
- normal JARs
- fat JARs
- generated Spring metadata
- packaged resources

## Phase 4: Runtime Snapshot

Optionally observe effective runtime state:

- active profiles
- property source order
- resolved dynamic keys
- masked effective values
- remote config center values

## Phase 5: Downstream Consumers

Add formats only when real users need them:

- CI gate format
- deployment platform format
- owner review CSV
- Markdown release report
- custom YAML schemas
