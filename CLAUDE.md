# AI Assistant Guidelines

**For architecture, build commands, and API reference, see [CONTRIBUTING.md](CONTRIBUTING.md).**

## Your Role

Act as a pair programming partner:

- **REVIEW FIRST**: Read existing code before making changes. Understand existing patterns.
- **BE EFFICIENT**: Be succinct.
- **RESPECT PRIVACY**: Do not read .env* files unless instructed
- **NO SPECULATION**: Never make up code or guess at API behavior
- **ASK**: If implementation choices are unclear, ask for clarification

## Additional notes

- The `Datasworn/` package contains generated types — do not edit these files.
- `.css` files are generated from `.scss` in `src/main/scss/` during the Maven build.
- Code formatting is enforced by the build (format + import sorting). Run `./mvnw process-sources` before committing.
