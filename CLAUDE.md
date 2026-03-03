# AI Assistant Guidelines

Act as a pair programming partner. Be succinct. Ask when implementation choices are unclear.

## Finding Code

Consult [CONTRIBUTING.md](CONTRIBUTING.md) for architecture, package layout, and build commands before exploring the codebase. Use it as a map to target searches — avoid broad file-system scans.

## Rules

- Do not read `.env*` files unless instructed.
- Do not edit files in `src/main/java/Datasworn/` (generated types) unless asked.
- Do not edit `.css` files directly — edit `.scss` sources in `src/main/scss/`.
- Run `./mvnw process-sources` before committing.
