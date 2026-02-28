# Quarkus Ironsworn

An AI-powered solo [Ironsworn](https://www.ironswornrpg.com/) RPG web application,
built as a conference demo for progressive disclosure of LLM/RAG concepts.

Built with **Quarkus**, **LangChain4j**, **Ollama** (local LLM), and **Neo4j** (vector embeddings).

---

## What This Demonstrates

This application shows four levels of LLM integration, each building on the last:

### Level 1 — Plain Chat (`/chat`)

A direct call to the LLM with a minimal system prompt. Ask it anything.
The model responds based on its training data alone — no game context, no persona.

**Key source:**

- [`ChatAssistant.chat()`](src/main/java/dev/ebullient/ironsworn/chat/ChatAssistant.java) — the `@RegisterAiService` interface with an inline system prompt
- [`ChatResource`](src/main/java/dev/ebullient/ironsworn/api/ChatResource.java) — REST endpoint at `/api/chat`

### Level 2 — System Prompt (`/rules`)

The same code pattern, but the system prompt establishes an Ironsworn expert persona.
The prompt text alone produces dramatically different responses — same model, same API call.

**Key source:**

- [`ChatAssistant.rules()`](src/main/java/dev/ebullient/ironsworn/chat/ChatAssistant.java) — same interface, different system prompt
- [`ChatRulesResource`](src/main/java/dev/ebullient/ironsworn/api/ChatRulesResource.java) — REST endpoint at `/api/rules`

### Level 3 — Full Gameplay (`/play`)

A complete solo RPG experience. The LLM acts as narrator using:

- A rich system prompt loaded from an external file
- A structured user prompt template with character context and recent journal entries
- Short-term chat memory (last N exchanges) for narrative continuity
- Structured JSON output (guardrails) to extract narrative, NPCs, and location

**Key source:**

- [`PlayWebSocket`](src/main/java/dev/ebullient/ironsworn/PlayWebSocket.java) — WebSocket at `/ws/play/{campaignId}`, the central message router for all gameplay
- Character creation
    - [`CreationAssistant`](src/main/java/dev/ebullient/ironsworn/chat/CreationAssistant.java) - guided character creation with stat-driven world-building
- Narration
    - [`PlayAssistant`](src/main/java/dev/ebullient/ironsworn/chat/PlayAssistant.java) — AI service for narration, move results, and oracle inspiration
    - [`PlayResponseGuardrail`](src/main/java/dev/ebullient/ironsworn/chat/PlayResponseGuardrail.java) — validates structured JSON output
- Oracle inspiration ([`OracleService`](src/main/java/dev/ebullient/ironsworn/chat/OracleService.java) orchestrates both paths, toggled by `ironsworn.oracle.use-tool-calling`)
    - **Tool calling** — the LLM autonomously calls [`OracleTool.rollOracle()`](src/main/java/dev/ebullient/ironsworn/chat/OracleTool.java) to pick and roll an oracle table itself via [`InspireToolAssistant`](src/main/java/dev/ebullient/ironsworn/chat/InspireToolAssistant.java)
    - **Without tool calling** — [`InspireOracleSelector`](src/main/java/dev/ebullient/ironsworn/chat/InspireOracleSelector.java) asks the LLM which oracle to roll (structured JSON output with [`InspireOracleChoiceGuardrail`](src/main/java/dev/ebullient/ironsworn/chat/InspireOracleChoiceGuardrail.java)), the server rolls it, then [`PlayAssistant.inspire()`](src/main/java/dev/ebullient/ironsworn/chat/PlayAssistant.java) narrates the result
- Prompt templates: [`play-narrate-system.txt`](src/main/resources/prompts/play-narrate-system.txt), [`play-narrateMoveResult-system.txt`](src/main/resources/prompts/play-narrateMoveResult-system.txt), [`play-inspire-system.txt`](src/main/resources/prompts/play-inspire-system.txt)
- [`IronswornMechanics`](src/main/java/dev/ebullient/ironsworn/IronswornMechanics.java) — server-side dice rolling, oracle lookups, progress tracking
- [`GameJournal`](src/main/java/dev/ebullient/ironsworn/GameJournal.java) — markdown-based campaign persistence

### Level 4 — Campaign Q&A with RAG (`/campaign/{id}`)

Ask questions about your campaign's history. The LLM answers using:

- The same character context and recent journal as Level 3
- **Retrieval-Augmented Generation**: relevant past journal entries are retrieved
  from Neo4j vector embeddings and injected into the prompt
- The model can answer questions about events from much earlier in the campaign
  that would not fit in the context window

> The Campaign Q&A is accessible from the play index once a campaign exists.

**Key source:**

- [`StoryMemoryIndexer`](src/main/java/dev/ebullient/ironsworn/memory/StoryMemoryIndexer.java) — incremental embedding pipeline: parses journal exchanges, hashes for change detection, embeds narrative text into Neo4j
- [`StoryMemoryService`](src/main/java/dev/ebullient/ironsworn/memory/StoryMemoryService.java) — retrieval side: embeds the query, searches Neo4j by campaign, formats relevant excerpts as context
- [`CampaignAssistant`](src/main/java/dev/ebullient/ironsworn/chat/CampaignAssistant.java) — AI service that answers questions using character context, journal, and retrieved memory
- [`CampaignResource`](src/main/java/dev/ebullient/ironsworn/api/CampaignResource.java) — REST endpoint that assembles context and calls the assistant
- Prompt templates: [`campaign-answer-system.txt`](src/main/resources/prompts/campaign-answer-system.txt), [`campaign-answer-user.txt`](src/main/resources/prompts/campaign-answer-user.txt)

---

## Application Components

| Path | Description |
|------|-------------|
| `/` | Landing page — links to all sections |
| `/chat` | Level 1: Plain chat with the LLM |
| `/rules` | Level 2: Ironsworn rules & setting assistant |
| `/play` | Campaign list; create new campaigns |
| `/play/{id}` | Level 3: Full Ironsworn gameplay |
| `/campaign/{id}` | Level 4: Campaign Q&A with RAG |
| `/reference` | Rules reference — moves, oracles, atlas |

---

## Running the Demo

### Prerequisites

**Ollama** (local LLM):

```shell
ollama serve
ollama pull llama3.2
ollama pull nomic-embed-text
```

**Neo4j** (vector store for story memory):

```shell
docker compose up -d neo4j
```

### Start the application

```shell
./mvnw quarkus:dev
```

Open [http://localhost:8082](http://localhost:8082).

---

## Project Structure

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for architecture details, code formatting rules,
and contributor guidelines.
