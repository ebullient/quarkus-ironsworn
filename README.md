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

### Level 2 — System Prompt (`/rules`)

The same code pattern, but the system prompt establishes an Ironsworn expert persona.
The prompt text alone produces dramatically different responses — same model, same API call.

### Level 3 — Full Gameplay (`/play`)

A complete solo RPG experience. The LLM acts as narrator using:

- A rich system prompt loaded from an external file
- A structured user prompt template with character context and recent journal entries
- Short-term chat memory (last N exchanges) for narrative continuity
- Structured JSON output (guardrails) to extract narrative, NPCs, and location

### Level 4 — Campaign Q&A with RAG (`/campaign/{id}`)

Ask questions about your campaign's history. The LLM answers using:

- The same character context and recent journal as Level 3
- **Retrieval-Augmented Generation**: relevant past journal entries are retrieved
  from Neo4j vector embeddings and injected into the prompt
- The model can answer questions about events from much earlier in the campaign
  that would not fit in the context window

> The Campaign Q&A is accessible from the play index once a campaign exists.

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
