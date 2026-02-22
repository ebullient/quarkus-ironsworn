# Contributing

## Project Overview

An AI-powered solo Ironsworn RPG web application built with Quarkus. Players create characters and play through campaigns with an LLM acting as collaborative narrator. Uses Ollama (local LLM) for chat, Neo4j for embeddings, and markdown files as the campaign journal/persistence layer.

## Build & Run Commands

```shell
./mvnw quarkus:dev          # Dev mode with live reload (http://localhost:8080)
./mvnw package              # Build
./mvnw test                 # Run unit tests
./mvnw test -Dtest=TestClassName  # Run a single test class
./mvnw test -Dtest=TestClassName#testMethodName  # Run a single test method
```

**Prerequisites**: Ollama running locally with `llama3.2` and `nomic-embed-text` models. Neo4j instance for embeddings.

## Code Formatting

The build enforces formatting via `formatter-maven-plugin` (Eclipse style from `src/ide-config/eclipse-format.xml`) and `impsort-maven-plugin` (import ordering: `java., javax., jakarta., org., com.`; removes unused imports). Both run automatically during build. Skip with `-DskipFormat`.

## Architecture

### Web Layer (Renarde MVC + REST + WebSocket)

- **`web/`** — Renarde controllers serving Qute templates for server-rendered pages
    - `/` landing page, `/chat` and `/rules` chat interfaces, `/play/{campaignId}` game interface, `/reference/moves` and `/reference/oracles` rules reference
- **`api/`** — REST endpoints for chat, moves, oracles, and gameplay
- Primary gameplay WebSocket at `/ws/play/{campaignId}` — handles the full play loop: character creation, narrative input, move results, oracle rolls, progress tracking. Message types: `creation_chat`, `finalize_creation`, `narrative`, `move_result`, `oracle`, `oracle_manual`, `progress_mark`, `character_update`.

### AI/Chat Layer (Quarkus LangChain4j)

- **`chat/`** — LLM-backed assistants for general chat/rules Q&A, campaign narration (stateful per campaign), and guided character creation
- Each assistant has a paired response record and guardrail for structured JSON output validation

### Game Engine

- Dice rolling, oracle table lookups, move outcome text retrieval, progress tracking
- Rules data loaded from `src/main/resources/rules/` YAML files (Datasworn format) at startup
- Markdown file-based persistence — campaign journals live in configurable `ironsworn.journal.dir` (`target/dev-ironsworn` in dev). Character sheets, vows, and journal entries are parsed/updated in-place within the markdown

### Datasworn Types

The `Datasworn/` package (root-level, not under `dev.ebullient`) contains **generated Java types** from the Datasworn schema — moves, oracles, assets, NPCs, etc. Do not manually edit these files.

### Frontend

- **Templates**: Qute templates in `src/main/resources/templates/` (organized by controller)
- **SCSS**: `src/main/scss/` compiled to CSS via `sass-cli-maven-plugin` during `generate-resources` phase
- **JS**: `src/main/resources/META-INF/resources/`

### Journal Format

Campaign data is stored as markdown files. Character stats, meters, and vows are in a structured header section parsed via regex. The `## Journal` section contains interleaved narrative text and blockquoted (`>`) mechanical results. Player inputs are formatted as `*Player: text*`.
