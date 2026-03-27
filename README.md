# Companion repo for Automating API Busywork in Scala

See https://github.com/yoohaemin/decrel for decrel.

This repository contains the demo code and slides for the Scalar 2026 talk
`Automating API Busywork in Scala`.

The talk has two threads:

- deriving API error schemas directly from Scala types using union types and
  `transparent trait`
- representing backend selection trees as data with `decrel`, then compiling
  them into batched fetch programs

The examples use a small dragon-rental domain, ZIO, Caliban, H2, and
`decrel-zquery-next`.

## Repository layout

### Talk material

- `slides.adoc`: the Reveal.js slide deck source
- `build-slides.sh`: builds `slides.adoc` into `dist/slides.html`
- `highlight-entry.js`: highlight.js entry used by the slide build
- `dist/`: generated slide output and bundled frontend assets

### Scala source files

- `project.scala`: Scala CLI build file with compiler options and dependencies
- `domain.scala`: domain model and relation declarations used by both demos
- `errors.scala`: part 1 of the talk
  - defines `Rejection` and the demo error ADT
  - contains `ErrorSchemaDemo`, which shows precise error accumulation
  - contains `GraphQLApiDemo`, which derives a GraphQL union schema from the
    inferred result type
  - contains `MatchExample`, which shows exhaustive boundary handling for a REST
    style API
- `repo.scala`: storage layer and decrel proof wiring
  - defines repository interfaces and H2-backed implementations
  - seeds the in-memory database with demo data
  - records fetch logs so batching and deduplication are visible
  - defines `SelectionTreeProofs`, the `decrel` datasource implementations for
    each relation
- `selection.scala`: part 2 of the talk
  - defines `SelectionTreeDemo`
  - shows selection-tree expressions such as
    `Rental.fetch >>: Rental.dragon <>: Dragon.breed`
  - includes examples for reduced trees, deduplication, and batched roots
- `main.scala`: runnable entry point that prints both demos and the generated
  GraphQL schema
- `slides.test.scala`: tests used to keep the talk artifacts honest
  - updates the checked-in GraphQL schema golden file
  - verifies a multi-root selection tree hits the DB only twice

### Generated / checked-in artifacts

- `golden/graphql-schema.graphql`: generated GraphQL schema for the error-schema
  demo
- `package.json` / `package-lock.json`: slide-build dependencies

## How the code maps to the talk

### Part 1: error schemas

Start with `errors.scala`.

`ErrorSchemaDemo.rentDragon` returns a ZIO effect whose error channel is inferred
as a union of the concrete rejection types used by the business logic. That
inferred type is then:

- rendered into a GraphQL union in `GraphQLApiDemo`
- pattern-matched exhaustively in `MatchExample`

The golden file in `golden/graphql-schema.graphql` is produced from this demo.

### Part 2: selection trees

Start with `selection.scala` and `repo.scala`.

`domain.scala` defines relations like `Rental.dragon`, `Dragon.breed`, and
`Dragon.schedule`. `SelectionTreeProofs` in `repo.scala` teaches `decrel` how to
execute those relations efficiently against the H2-backed repository.

`SelectionTreeDemo` then expresses tree shapes directly as values and runs them
with `.startingFrom(...)` or `.expand(...)`.

## Build and run

Install slide dependencies:

```bash
npm ci --no-fund --fetch-timeout=300000 --fetch-retries=5
```

Build the slides:

```bash
./build-slides.sh
```

Serve the repo root and open the generated slides:

```bash
python3 -m http.server 50000
```

Then visit:

```text
http://127.0.0.1:50000/dist/slides.html
```

Compile the Scala code:

```bash
scala-cli compile .
```

Run the tests:

```bash
scala-cli test .
```

Run the demo app:

```bash
scala-cli run .
```

## Verified in this repository

These commands were previously verified here:

```bash
npm ci --no-fund --fetch-timeout=300000 --fetch-retries=5
./build-slides.sh
python3 -m http.server 50000
curl -I http://127.0.0.1:50000/dist/slides.html
scala-cli compile .
scala-cli test .
```
