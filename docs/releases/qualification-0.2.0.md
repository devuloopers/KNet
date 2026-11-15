# Desktop and Companion 0.2.0 release qualification

Status: **COMPLETED — both stable releases are public and verified.**

Both product tags identify `0131e742ea197a55bc9a0137937c360455f23134`. Product versions are 0.2.0; Android and Apple build
numbers are 2000. Existing published releases and tags remain unchanged.

## Hosted gates

| Workflow | Evidence |
| --- | --- |
| Complete CI | [Run 43748271759](https://github.com/devuloopers/KNet/actions/runs/43748271759) |
| Desktop packages and draft | [Run 43748319603](https://github.com/devuloopers/KNet/actions/runs/43748319603) |
| Companion packages and draft | [Run 43748319567](https://github.com/devuloopers/KNet/actions/runs/43748319567) |

CI passed architecture, protocol/editor, Network Conditions/Protected Traffic, sustained I/O/memory,
desktop runtime tests on Apple Silicon/Intel/Windows/Linux, Android qualification and Kotlin iOS plus
the unsigned Debug Simulator app. Desktop release qualification and all five native packaging jobs passed.
The Companion APK passed release qualification/signing. Kotlin iOS qualification and the unsigned Release
Simulator application passed before draft staging. All three workflows succeeded on the same source commit.

## Findings repaired during preparation

| Finding | Repair and verification |
| --- | --- |
| Companion Home action moved when localized runtime copy wrapped | Reserve measured heading/footnote heights; 375px/420px and larger-text assertions plus all 48 local shared UI cases passed. Android hosted qualification also passed. |
| SQLite 2.7.x JVM bundle omitted Intel macOS JNI | Pin complete SQLite 2.6.2 with Room 2.8.4; all five desktop native resources and current-host execution are checked. Eight native/migration cases and hosted Intel desktop tests passed. |
| Timer assertion depended on host scheduling | Freeze Netty's embedded ticker before handler installation and advance that clock; idle/progress/hold assertions remain intact. |
| macOS 100-client fixture truncated responses | Origin diagnostics exposed kernel mbuf exhaustion. Bound endpoint socket buffers to 64 KiB while retaining 100 simultaneous 10 MiB responses and existing memory/time bounds. Ten consecutive runs delivered all 1,000 exact responses; hosted desktop tests passed. |
| Background editor layout shared native fonts with rendering | Give each worker job an independent resolver/cacheless measurer, preserving density and layout direction; exercise rapid width replacement, larger text and disposal. All 100 editor and 214 Traffic cases passed locally; combined Linux x64 run passed 224 Traffic/editor JVM cases. |
| Failed socket registration threw again during cleanup | Dispose unregistered candidates through Netty transport cleanup. New regression reproduced the failure before repair; complete 185-case proxy suite passed afterward. |
| Checksum manifest included its own changing contents | Exclude the manifest, verify generated package entries before upload, and compare staged digests independently. |

Hosted failures retain complete assertion causes, test reports and native-crash logs. The full editor suite
is an explicit protocol/release gate. Qualification remained enabled throughout the retry cycle.

## Download verification

The Desktop release contains 13 installers/portable archives plus `SHA256SUMS`: macOS arm64/x64 DMG/ZIP,
Windows x64 EXE/MSI/ZIP and Linux arm64/x64 DEB/RPM/TAR.GZ. Every package entry matches GitHub's upload
digest. The downloaded manifest's digest and signed provenance verify against the exact tag, source
commit, release workflow and GitHub-hosted runner policy.

The downloaded Companion APK passed signature verification, package/version checks and ZIP integrity.

- Application ID: `com.devuloopers.knet.companion`
- Version name/code: `0.2.0` / `2000`; previous code was `1005`.
- SHA-256: `d000a26013fa9096fa00da7e51dfd558b8c7a61557623ec2b91ad9730c02c385`
- Signing certificate SHA-256: `e6eee701f6e10ea926357780085a4dbe44880afb30e1dbd626aacd47ce709884` (same as Companion 0.1.5).
- Native ABIs retained: arm64-v8a, armeabi-v7a, x86, x86_64.

The Companion manifest and signed provenance also verify against its exact release tag, source commit,
workflow and hosted runner policy. Its staged/public APK digest matches the locally verified APK above.
Both public release pages returned HTTP 200. Anonymous HTTP HEAD checks passed for all 16 public asset
URLs. Both publicly downloaded checksum manifests match the verified copies.

## Publication record

- [Desktop 0.2.0](https://github.com/devuloopers/KNet/releases/tag/desktop-v0.2.0)
- [Companion 0.2.0](https://github.com/devuloopers/KNet/releases/tag/companion-v0.2.0)

Companion was published at `2026-11-15T19:20:26Z`, followed by Desktop at
`2026-11-15T19:20:32Z`. GitHub's Latest endpoint identifies `desktop-v0.2.0`.
Both release records are public, stable, and retain the verified package digests and source commit.
Published tags remain immutable.

iOS qualification covers source and unsigned Simulator builds; this release distributes the signed
Android APK. New physical-device validation is outside this release cycle's evidence.
