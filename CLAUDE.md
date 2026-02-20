# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

An AI-powered solo Ironsworn RPG web application built with Quarkus. Players create characters and play through campaigns with an LLM acting as collaborative narrator. Uses Ollama (local LLM) for chat, Neo4j for embeddings, and markdown files as the campaign journal/persistence layer.

## Build & Run Commands

```shell
./mvnw quarkus:dev          # Dev mode with live reload (http://localhost:8080)
./mvnw package              # Build
./mvnw test                 # Run unit tests
./mvnw test -Dtest=GameJournalTest  # Run a single test class
./mvnw test -Dtest=GameJournalTest#testMethodName  # Run a single test method
```

**Prerequisites**: Ollama running locally with `llama3.2` and `nomic-embed-text` models. Neo4j instance for embeddings.

## Code Formatting

The build enforces formatting via `formatter-maven-plugin` (Eclipse style from `src/ide-config/eclipse-format.xml`) and `impsort-maven-plugin` (import ordering: `java., javax., jakarta., org., com.`; removes unused imports). Both run automatically during build. Skip with `-DskipFormat`.

## Architecture

### Web Layer (Renarde MVC + REST + WebSocket)
- **`web/`** — Renarde controllers serving Qute templates for server-rendered pages:
  - `Index` → `/` landing page
  - `Chat` → `/chat` and `/rules` simple chat interfaces
  - `Play` → `/play` campaign list and `/play/{campaignId}` game interface
  - `Reference` → `/reference/moves` and `/reference/oracles` rules reference
- **`api/`** — REST endpoints (`ChatResource`, `ChatRulesResource`, `MovesResource`, `OraclesResource`, `PlayResource`)
- **`PlayWebSocket`** — Primary gameplay WebSocket at `/ws/play/{campaignId}`. Handles the full play loop: character creation flow, narrative input, move results, oracle rolls, progress tracking. Message types: `creation_chat`, `finalize_creation`, `narrative`, `move_result`, `oracle`, `oracle_manual`, `progress_mark`, `character_update`.

### AI/Chat Layer (Quarkus LangChain4j)
- **`chat/ChatAssistant`** — Stateless assistant for general chat and rules Q&A
- **`chat/PlayAssistant`** — Stateful (memory per campaign) narrator; `narrate()` for free-form play, `narrateMoveResult()` for mechanical outcomes
- **`chat/CreationAssistant`** — Guided character creation through backstory conversation; suggests a vow after 2-3 exchanges
- **Response types + Guardrails** — Each assistant has a paired response record and guardrail (e.g., `PlayResponse`/`PlayResponseGuardrail`) for structured JSON output validation

### Game Engine
- **`IronswornMechanics`** — Dice rolling, oracle table lookups, move outcome text retrieval, progress tracking
- **`DataswornService`** — Loads Ironsworn rules data from `src/main/resources/rules/` YAML files (Datasworn format) at startup
- **`GameJournal`** — Markdown file-based persistence. Campaign journals live in configurable `ironsworn.journal.dir` (`target/dev-ironsworn` in dev). Parses/updates character sheets, vows, and journal entries in-place within the markdown
- **`JournalParser`** — Static utilities for extracting player input, detecting narration needs, counting exchanges from journal markdown

### Datasworn Types
The `Datasworn/` package (root-level, not under `dev.ebullient`) contains **generated Java types** from the Datasworn schema — moves, oracles, assets, NPCs, etc. Do not manually edit these files.

### Frontend
- **Templates**: Qute templates in `src/main/resources/templates/` (organized by controller: `Chat/`, `Play/`, `Reference/`, `Index/`)
- **SCSS**: `src/main/scss/` compiled to CSS via `sass-cli-maven-plugin` during `generate-resources` phase
- **JS**: `src/main/resources/META-INF/resources/` — `chat-interface.js`, `play-interface.js`, `theme.js`

### Journal Format
Campaign data is stored as markdown files. Character stats, meters, and vows are in a structured header section parsed via regex. The `## Journal` section contains interleaved narrative text and blockquoted (`>`) mechanical results. Player inputs are formatted as `*Player: text*`.
