# Contributing to rāyik

Thanks for helping tune rāyik pixel by pixel. The rules below keep a
small-scope project shippable — please follow them.

## Ground rules

1. **Know the strategy.** Unofficial YouTube Music client, small scope,
   beauty bar — see the README overview and the Definition of Done below.
   (Maintainers keep extra strategy notes in a local, untracked `AGENTS.md`.)
2. **Keep changes focused.** One concern per PR; preserve user-facing behavior
   unless the PR is explicitly about changing it.
3. **Prefer existing patterns.** Search the repo (`rg`) before renaming
   identifiers or introducing new dependencies. Reuse shared components,
   dimens, shapes, and string resources — never near-duplicates.
4. **Never commit** keystores, passwords, tokens, API keys, or credentials.
   Signing happens via CI secrets only.

## Workflow

```bash
git checkout -b <short-name>          # branch off master
# ... make focused changes ...
git diff --check                      # required before every commit
./gradlew :app:testDebugUnitTest      # unit tests must stay green
git commit -m "<concise message>"     # match repo style, explain why
```

Open the PR against `master`. CI runs unit tests plus
`assemble*Release` and bundles on every PR — all must pass.

## Definition of Done (per feature)

From the project bar, every feature needs:

- Degraded-streaming state + retry (loading / unavailable / retry, never blank)
- Rotation + tablet + dark/light pass
- R8 release install test
- Telemetry added (logcat under `RayikPlayer` counts)
- `git diff --check` + `lint` clean

Report exactly which checks passed, failed, or could not run in the PR.

## Bug reports

Playback issues are network-dependent, so include:

- Track + what you tapped, expected vs actual behavior
- The exact on-screen error (it carries the HTTP status, e.g. `HTTP 403`)
- `adb logcat -s RayikPlayer` lines around the failure
- Network type (Wi-Fi / carrier) and theme — both matter for diagnosis

## Upstream breakage

YouTube changes break unofficial clients without warning. If search or
playback dies globally:

1. Confirm with a second network (bot-checks are often IP-scoped).
2. Capture the `RayikPlayer` log + the failing endpoint shape.
3. File it with the `upstream` label before attempting extractor surgery —
   small, verified fixes beat rewrites.

## Code of conduct

Be kind and direct. See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
