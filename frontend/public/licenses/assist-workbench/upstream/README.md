# REUSE-6 Upstream Notice Recovery

This is a bounded source-evidence increment, not a legal certification or
commercial-use clearance. `PROVENANCE.json` contains the full commit/blob pins,
source URLs, installed metadata hashes, version-link qualifications, and font
evidence. The parent MANIFEST and its metadataOnly values are intentionally
unchanged.

## Original Files

All eight files below are complete, unchanged upstream Git blobs, decoded from
official GitHub API base64. Byte lengths, Git blob SHA-1, SHA-256, and saved bytes
were checked. No copyright, newline, encoding, license body, or template was
rewritten. No font binary was copied.

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `pdfme-6.2.5/LICENSE.md` | 1064 | `d43121abfdaebee1e0d24ce91805811627df2cd27419cc86a13fae6a4e5d9069` |
| `air-datepicker-3.6.0/LICENSE.md` | 1079 | `a397e2a7f45f596cced15a89e88185248da9f895febcb87e393ebd14933568ff` |
| `ant-design-icons-svg-4.6.0/LICENSE` | 1099 | `5d367fb0a07340571542eb4ee8eb1add62d37b71bad6786a57c2e9a86bf76c70` |
| `css-styled-1.0.8/LICENSE` | 1064 | `77f98221f8531e87aa227c0a8d63c17c903dd4d36daf24bdc48296b09e06a25e` |
| `egjs-component-3.0.5/README.md` | 4641 | `4ba5e5b536b5e55ed46aa62177fd6663104ef9bd6f0d5b942bde38c48e340313` |
| `is-mobile-5.0.0/README.md` | 2475 | `1f1f35b6a103d7b010f51a6d7d61e3e47dbc71db2c69cbb7c0ffd40c641b04e2` |
| `roboto-2.137/LICENSE.txt` | 11358 | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` |
| `roboto-2.137/COPYRIGHT.txt` | 47 | `48133474d1a2371d22b7a2b10c92431998e0cb7cf121895003e17902fc346031` |

## Software Version Links

- **pdfme:** tag `6.2.5`, commit
  `4c48a60cd0f33d67b81c07b0ac1c0bf61ac6a796`, root `LICENSE.md`, copyright
  `Copyright (c) 2020 HandDot`. This monorepo notice covers the checked
  `@pdfme/common`, `ui`, `generator`, `schemas`, and `converter` 6.2.5 packages.
  The source package.json files say `0.0.0`: the pinned publish workflow derives
  VERSION from the Git tag and stamps all workspaces before publication. The
  workflow and all five source package metadata blobs were inspected. This is
  release-tag association, not an assertion of an unavailable npm gitHead.
- **air-datepicker:** exact tag `v3.6.0`, commit
  `7031158c6c89b8c95b229748d913aa82639e74e8`; source package name/version/license
  match. Copyright is Timofey Marochkin.
- **@ant-design/icons-svg:** pinned commit
  `7f2516ac91226d2b41f93b35cb5197c8d94f7189`; source package metadata says 4.6.0.
  No matching version tag or installed npm gitHead was found. This is a
  same-version source notice, not a proven npm publication commit. Original
  copyright: `Copyright (c) 2018-present Ant UED, https://xtech.antfin.com/`.
- **css-styled:** pinned commit
  `51fb10e66da6c8e2ca3c121b614a54e527617f08`; source metadata says 1.0.8. The same
  tag/gitHead limitation applies. Copyright: `Copyright (c) 2019 Daybrush`.
- **@egjs/component and is-mobile:** tags `3.0.5` and `v5.0.0`, commits
  `231c6f945714222a3bda1b85545eabe6eb677804` and
  `2708dd8f07d91f354a46b8dff3053abb6b840067`. Their README License sections
  contain full original MIT copyright, permission and disclaimer text. These
  exact README bytes are ALREADY present in the parent's retained notices. The
  upstream copies confirm provenance; they do not count as newly recovered
  missing license bodies. Original copyrights are NAVER Corp. (2015) and Julian
  Gruber (2013), respectively. Markdown entities and fences remain unchanged.

No installed metadata for the 15 metadataOnly entries supplied a gitHead. No
existing notice, including the separate `@pdfme/pdf-lib` copyright/license, was
replaced. Four new standalone software license files relate to eight versions:
six release-tag-associated versions and two same-version-commit-only versions.

## Specific Remaining Package Gaps

| Installed Package | Pinned Source Inspected | Unrecovered Evidence |
| --- | --- | --- |
| `fontkit@2.0.4` | `v2.0.4` -> `fbf3b9ef21eebd219eb73e666faed573af0fba09` | Complete source tree has no project LICENSE/NOTICE/COPYING/COPYRIGHT; README says MIT only. Test-font licenses do not license fontkit itself. Original full copyright/permission notice not recovered. |
| `brotli@1.3.3` | `v1.3.3` -> `e36051345f6d27a56e5f39ea70d2f789b8574046` | Same standalone-notice absence; README says MIT only. Full original project notice not recovered. Installed metadata's devongovett URL redirects to foliojs. |
| `dfa@1.2.0` | `v1.2.0` -> `3f5e603e5082caedabefefdf53e8a6ca3111e966` | Same standalone-notice absence; README says MIT only. Full original project notice not recovered. Installed metadata's devongovett URL redirects to foliojs. |
| `keycon@1.4.0` | `keycon@1.4.0` -> `e7343fbcf6a7c21c01458ab760896d2c6c8a4112` | No root/core notice. Only the sibling `packages/vue2-keycon/LICENSE` exists; it was not substituted for the core package. |
| `@cfcs/core@0.0.6` | current commit `c70cd72d6378d162c2550bd98f9605d458ce719c` | Available tags and bounded root/core metadata history did not identify 0.0.6. Current source package is 0.1.0; its LICENSE/NOTICE were not misrepresented as 0.0.6 evidence. |

The fontkit upstream missing-file report is
<https://github.com/foliojs/fontkit/issues/255>. No notice was manufactured from
an unrelated Devon Govett project, sibling wrapper, or unmerged proposal.

## Fonts

**Roboto:** the two original files come from `google/fonts`, commit
`724bf98e9f5cb98a1d3d5044f45a2e286b817401` ("roboto: v2.137 added"), directory
`apache/roboto`. `COPYRIGHT.txt` is exactly
`Copyright 2011 Google Inc. All Rights Reserved.` with no added newline. The
Apache-2.0 license was copied in full, including its unchanged standard appendix;
the appendix's sample placeholders are not substituted for the real copyright.

The installed pdfme runtime contains a 129584-byte inline Roboto-Regular font:
SHA-256 `7277cfb805def6410f317129b8e1f78bdd47d1a4e24c233077d06e88a36e57ae`,
version `Version 2.137; 2017`, the same real copyright, and name-table license URL
`http://www.apache.org/licenses/LICENSE-2.0`. Its installed source-map constants
are byte-identical to the pinned pdfme source constants blob
`803023e4f803a7d077db94620dfa49aad27c9bca`; the font data string was also checked
in the installed runtime JS. The historical Google Fonts TTF is 171676 bytes,
so exact binary identity with that upstream TTF is NOT established. No upstream
NOTICE exists in that pinned font directory, and no NOTICE was invented. Final
application bundle/font inclusion remains the parent's check.

**Inconsolata:** the parent's full `bwip-js4.11.4-LICENSE.INCONSOLATA` is retained
and unchanged. Its unfilled copyright placeholders are not real attribution.
The actual installed font copyright was re-read and recorded in PROVENANCE:
`Created by Raph Levien using his own tools and FontForge. Copyright 2006 Raph Levien. Released under the SIL Open Font License, http://scripts.sil.org/OFL.`

**OCR-A/B:** the installed `OCRA7.ttf` and `OCRB7.ttf` bytes and name records are
recorded in PROVENANCE. They identify Matthew Skala and predecessor authors but
provide no license description or URL. The bounded official GitHub search did
not recover original terms bound to these binaries. A separate OCR-Bitcoin
derivative is not evidence that these fonts use its MIT license. No OCR license
was copied; no public-domain, GPL, OFL, MIT, or commercial-clearance claim is
made. Original OCR font terms and attribution remain a parent release gap.

## Verification And Boundary

The original parent MANIFEST SHA-256 remains
`fe84e33d66aad9cbf361cf339236fa23f3a85ba09d911af686a888baea504606`.
All 250 original package-notice files still match their manifest hashes. The
installed metadata checked for this increment is unchanged. App integration,
manifest edits, final distribution decisions, and remaining release gates were
not performed here. No dependencies, application source, DB, credentials,
services, Git index, commits, or remotes were changed. No subdelegation occurred.
