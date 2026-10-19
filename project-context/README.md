# OpenCell project context

This folder is a portable Markdown snapshot of the project's written context, prepared on 2026-10-19 from application revision `84300e4`. Start with `PROJECT_STATUS.md`; the copied plans include aspirations, historical claims, and old environment notes, not just completed work.

## Included documents

The source path is shown in parentheses so the copies can be compared with the live project documentation:

1. [`PROJECT_STATUS.md`](PROJECT_STATUS.md) — current implementation and verification status.
2. [`ROOT_README.md`](ROOT_README.md) — product overview, status, build, testing, and distribution notes from `README.md`.
3. [`CLI_README.md`](CLI_README.md) — CLI, Python client, MCP, and speech-server usage from `cli/README.md`.
4. [`SETUP.md`](SETUP.md) — phone, computer, AI, pairing, and troubleshooting instructions from `docs/SETUP.md`.
5. [`AI_ASSISTANT_PLAN.md`](AI_ASSISTANT_PLAN.md) — phased assistant implementation plan from `docs/AI_ASSISTANT_PLAN.md`.
6. [`WEBSITE_README.md`](WEBSITE_README.md) — GitHub Pages deployment notes from `website/README.md`.
7. [`V2_IMPLEMENTATION_PLAN.md`](V2_IMPLEMENTATION_PLAN.md) — original v2 implementation plan from `docs/superpowers/plans/2026-10-11-opencell-v2-implementation.md`.
8. [`V2_DESIGN_SPEC.md`](V2_DESIGN_SPEC.md) — original v2 design and architecture specification from `docs/superpowers/specs/2026-10-11-opencell-v2-design.md`.
9. [`ARCHITECTURE.md`](ARCHITECTURE.md) — current code map, data model, request paths, and security boundaries.
10. [`VERIFICATION.md`](VERIFICATION.md) — reproducible check commands, actual results, emulator evidence, and outstanding checks.

All seven pre-existing tracked Markdown documents are included. The source files remain in their original locations. This folder intentionally uses descriptive, flat filenames so it can be copied or handed to another contributor without needing the rest of the repository's directory structure to find the written context. Relative documentation links in the README copies point to the local copies; other source content is preserved.

## How to use this context

1. Read `PROJECT_STATUS.md` for the current implementation, limitations, decisions, and next steps.
2. Read `ARCHITECTURE.md` to locate the code behind each feature.
3. Read `VERIFICATION.md` to see what was actually tested and how to repeat it.
4. Read the copied setup and CLI guides for user workflows.
5. Treat the copied design and phase plans as historical intent, checking their claims against the status and source code.

Commands and code paths in these documents are relative to the repository root unless another working directory is specified. The context Markdown can be read separately, but running the app still requires the source repository and its dependencies.

## Scope and safety

- This snapshot contains project documentation only.
- No API keys, tokens, passwords, device pairing codes, private logs, or local machine credentials are included.
- Source code and generated build artifacts are not copied here; the original repository remains the source of truth for implementation.
- When documentation changes, update the source document first and refresh the corresponding snapshot copy.
- The original plans mention proposed GPL licensing, QR pairing, LAN access, and on-device LLM support. The repository currently declares Apache-2.0, implements code-based pairing, binds the gateway to loopback, and defers the on-device LLM. Do not use the older plan as a guarantee of those features.
