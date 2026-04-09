# Wallet Repo Guidance

- Use GPT-5.4 by default for multi-repo or protocol-sensitive wallet work.
- Treat `project-docs/docs/EIDAS_ARF_Implementation_Brief.md` and `project-docs/docs/AI_Working_Agreement.md` as mandatory constraints.
- This repo owns the Android wallet implementation used as the local reference client.
- Keep wallet changes focused on interoperability, local trust, or explicitly approved wallet workstreams.
- When wallet behaviour, trust material, or local integration behaviour changes, update `project-docs` in the same task.
- Default Git flow in this workspace is local `wip/<stream>` commits promoted directly with `git push origin HEAD:main`; do not publish remote `wip/<stream>` branches unless explicitly requested.

## Local Checks

- `./gradlew test`
- `./gradlew buildAndInstallDemoDebug`
- `./gradlew buildAndInstallDevDebug`

## Sensitive Areas

- Do not casually modify network trust behaviour, wallet core protocol handling, or preregistered issuer/verifier configuration.
- Keep local certificate handling aligned with the shared local runtime trust model documented in `project-docs`.