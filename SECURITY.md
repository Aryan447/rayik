# Security policy

## Supported versions

rāyik is pre-1.0. Only the latest `master` and the newest `v*` release are
supported with security fixes. Preview (`*preview*`) tags are explicitly
unsupported — update to a stable release.

| Version        | Supported |
| -------------- | --------- |
| latest stable  | ✅        |
| older releases | ❌        |
| preview tags   | ❌        |

## Reporting a vulnerability

**Do not open a public issue for vulnerabilities.** Report privately via
GitHub *Security → Report a vulnerability* on this repo, or a private
message to [@Aryan447](https://github.com/Aryan447). Include:

- App version, Android version, device
- Steps to reproduce and impact assessment
- Logs (`adb logcat -s RayikPlayer`) with tokens redacted

Expect an acknowledgement within a week. Fixes ship as a patch release;
credit is given unless you ask otherwise.

## Scope notes (please read)

- **Upstream playback breakage is not a vulnerability.** YouTube changes
  break unofficial clients regularly — file those as normal `upstream` bugs.
- **Out of scope:** decompiling/repackaging reports, Play Store absence
  (intentional — we distribute via GitHub Releases, F-Droid planned),
  missing certificate pinning on public metadata endpoints.
- **In scope:** credential or session-cookie leakage, insecure storage of
  auth material, RCE via downloaded content, dependency vulnerabilities
  with a working exploit against this app.

## Handling secrets

Never put keystores, API keys, tokens, or session cookies in issues, PRs,
or logs. Signing and keys live in CI secrets only.
