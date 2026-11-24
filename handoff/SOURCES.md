# Source inventory and reproducibility

These are the repositories actually used or investigated in the accessible conversation summary. This is not a claim that every earlier external reference or entire chat transcript is available. Archives are source snapshots, not tested builds; inspect each project's own licences and submodules/LFS requirements.

| Project | Reference | Relationship |
|---|---|---|
| [MLMVPN Android](https://github.com/mzworkplace/mlmvpn_android/tree/fdb07a5d0a99ca6347bd3716b0f0783341bac16a) | `fdb07a5d0a99ca6347bd3716b0f0783341bac16a` | Base repository, Psiphon AAR/config/server entries and native tun2socks source reviewed. `git archive` snapshot in Drive contains Git LFS pointer files, not necessarily LFS payloads. |
| [Psiphon Tunnel Core](https://github.com/Psiphon-Labs/psiphon-tunnel-core/tree/cd31fecf7b0e0b4c0fadfcc75e04a805d1a802c6) | v2.0.39 / `cd31fecf7b0e0b4c0fadfcc75e04a805d1a802c6` | Used by Android AAR; Windows ConsoleClient interface reviewed. GPL-3.0-or-later. Source also included in the 0.2 Drive source ZIP. |
| [Lantern](https://github.com/getlantern/lantern/tree/5fafbcaa9e8d07539becd1cc2ee2d70543236608) | `5fafbcaa9e8d07539becd1cc2ee2d70543236608` | Snapshot obtained for this handoff; exact revision consulted earlier is not known. Official implementation uses Flutter/Go; it is not integrated into TriGate WIP. GPL-3.0; check all notices/dependencies. |
| [Firefox VPN client](https://github.com/armanlife/firefox-vpn-client/tree/405f412f) | `405f412f` | Reviewed for an earlier issue; no claim that it was incorporated in this WIP. |
| [Proton WebClients](https://github.com/ProtonMail/WebClients) | revision not preserved in available summary | Previous ProtonGate source reference, exact revision / incorporated files unknown. |

Additional reference material: https://github.com/getlantern/lantern/blob/main/README-dev.md , https://support.torproject.org/tor-browser/circumvention/getting-bridges/ .

## Drive archive checksums

Archives and handoff are in the public handoff folder linked from README-FA.md. For the split MLMVPN archive, download both parts and concatenate in numeric order, e.g. `cat mlmvpn-android-fdb07a5-git-archive.tar.gz.part00 mlmvpn-android-fdb07a5-git-archive.tar.gz.part01 > mlmvpn.tar.gz`; then verify the combined checksum before extracting.

- `mlmvpn-android-fdb07a5-git-archive.tar.gz.part00`: SHA-256 `60b2d6a62b7a7037f2544e916146b10ae444ac909de5e8f6e3f5185ec33b79bf`
- `.part01`: SHA-256 `f3cc87874c89a834bd25e9e96391fd2df801d570fe9ae3196b73aefed062399f`
- Joined archive: SHA-256 `df7c3cc82f6a800cd89b7490ed7311495d0a458e2f7db17dfdaa9deeb906ebe9`
- `psiphon-v2.0.39-source.tar.gz`: SHA-256 `a3ae4dbaa0f05a8c273b9dc22562b15d761fc5fe80b9b9f217f574dd8aa2d7e6`. This commit URL archive can differ bytewise from the v2.0.39 tag URL archive included in the 0.2 source ZIP; both reference the same commit.
- `lantern-source-5fafbcaa.tar.gz`: SHA-256 `bbe7f697f4c814ebe6eff9868e2824c85ec3dc409c73275314307facfd4dc0c3`

## Git checkout build dependency

The standalone source ZIP on Drive contains its Psiphon AAR. GitHub handoff intentionally does not add a duplicate 43 MB binary; the base repo already has it. From repository root:

```sh
mkdir -p trigate-new/android/app/libs
cp app/libs/psiphontunnel-2.0.39-ns.aar trigate-new/android/app/libs/psiphontunnel-2.0.39.aar
sha256sum trigate-new/android/app/libs/psiphontunnel-2.0.39.aar
# Expected: a0d57701c1afc3b8b5dd17686445e4474bcc69eb000221406e83bd15546d0220
cd trigate-new/android && gradle :app:assembleDebug
```

Install JDK 17, Android SDK API 34, Android Gradle Plugin / Gradle as described in `trigate-new/ANDROID-PROXY-DEV.md`. Git LFS may be needed for base-repository native files. This Gradle recipe was not executed in the development build environment; do not describe it as verified.

The Psiphon and Lantern source archives are also present in `handoff/upstream/` in this Git branch. The MLMVPN base source is the repository itself; its split archive is in Drive.
