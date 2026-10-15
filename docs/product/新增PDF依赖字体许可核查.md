# Font-Specific Package Notes

Observed installed snapshot: 2026-10-15. Read-only inspection; no package, lockfile,
dependency license, or application source was edited. This note does not clear
the entire dependency tree or replace the parent's release-notice audit.

## pdfme Built-In Roboto

`@pdfme/common` 6.2.5 declares MIT, but `getDefaultFont()` returns a bundled
129,584-byte `Roboto-Regular` font. Parsed with the same fontkit 2.0.4 used by
`@pdfme/generator`, its actual name records say:

- Copyright: `Copyright 2011 Google Inc. All Rights Reserved.`
- Version: `Version 2.137; 2017`.
- Font license URL: `http://www.apache.org/licenses/LICENSE-2.0`.

Evidence: installed `frontend/node_modules/@pdfme/common/dist/index.js`, exported
`getDefaultFont()`, source-mapped `src/constants.ts`, and parsed embedded font
name table. It is inline font data, not a separately listed `.ttf` file.

Release-note implication: do not label this bundled font MIT or reuse the Noto
OFL notice for it. Preserve the actual Roboto copyright and applicable Apache-2.0
license/required notices if redistributed. A separate local CJK font option does
not itself prove the default Roboto bytes were tree-shaken out of the final app.
The parent owns final bundle/notice verification. This increment did not add or
copy any Roboto binary or invent an upstream NOTICE file.

## bwip-js Inconsolata

`@pdfme/schemas` 6.2.5 depends on `bwip-js` 4.11.4. That package declares MIT,
but its `fonts/Inconsolata.otf` explicitly identifies the SIL Open Font License.
The parsed font copyright is:

`Created by Raph Levien using his own tools and FontForge. Copyright 2006 Raph Levien. Released under the SIL Open Font License, http://scripts.sil.org/OFL.`

Evidence: installed
`frontend/node_modules/.pnpm/bwip-js@4.11.4/node_modules/bwip-js/fonts/Inconsolata.otf`,
font name table, `LICENSE.INCONSOLATA`, and `README.md` custom-font example.

`LICENSE.INCONSOLATA` contains the full OFL body but its copyright header uses
unfilled template placeholders such as `<dates>` and `<Copyright Holder>`.
Those placeholders are not actual attribution. Preserve the real font metadata
copyright together with the applicable full OFL when distributing this optional
font. Do not replace its font license with the package MIT license. If modified,
review the actual upstream RFN terms. No modification/copy of it was made here.

## bwip-js OCR A/B: Unresolved Font Attribution Check

The installed package also contains `fonts/OCRA7.ttf` and `fonts/OCRB7.ttf`.
Their parsed copyright fields identify:

- OCRA: `Matthew Skala (2011-12); based on code by Richard B. Wales (1988-89) and Tor Lillqvist`.
- OCRB: `Matthew Skala (2011); based on code by Norbert Schwarz (1986, 2011)`.

Their name tables did not provide a license description or license URL, and the
checked package notice inventory supplied no separate OCR font license.
`src/fontlib.js` identifies OCR-B as the default barcode font. Package MIT alone
is not evidence resolving every upstream font contribution. Parent release
review should establish the applicable original OCR font terms and preserve the
right attributions if these bytes are distributed. This is an evidence gap, not
a claim that they are unlicensed, public domain, GPL, or covered by Noto's OFL.
No unsupported license/provenance has been supplied for these fonts.

## Bounded Inventory

Installed `@pdfme/common`, `@pdfme/generator`, `@pdfme/schemas`, `@pdfme/ui`,
`@pdfme/pdf-lib`, `@pdfme/converter` 6.2.5 and fontkit 2.0.4 declare MIT. The four
direct pdfme packages and fontkit had no standalone license file in the checked
published package trees; the parent should retain/recover their official MIT
notices as part of the broader audit. This observation does not change their
declared licenses.

Konva 10.7.1 and vue-konva 4.0.1 declare MIT; vis-timeline 8.5.4 declares
`(Apache-2.0 OR MIT)`. Their checked own package trees contained no TTF, OTF, WOFF,
or WOFF2 files. That is only a bounded file inventory, not a guarantee about all
inline data, optional assets or transitive dependencies. Versions may change as
the parent continues independent integration.
