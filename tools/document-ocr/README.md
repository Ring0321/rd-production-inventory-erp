# REUSE-3: bounded local document OCR

Baseline: `c0ead420488e94c557e104712ad406d0bc394a01`. This increment adds only the isolated OCR service, its backend proxy, and the embeddable reader. It does not create official documents, call GLM/cloud OCR, query business data, or change stock/pickup/quality workflows. Shared route/navigation/configuration and the Maven reactor integration gate belong to the parent task.

## Files and integration

- `tools/document-ocr/document_ocr/`: stdlib HTTP server, disposable Paddle worker, file limits, normalization and conservative field parser.
- `tools/document-ocr/requirements.txt`: exact direct package pins. `requirements.lock.txt` is the generated tested transitive environment when installation completes.
- `tools/document-ocr/models.lock.json`: official model repository commits and content hashes; no runtime auto-download.
- `backend/rd-erp-inventory/src/main/java/com/ruiding/erp/controller/admin/documentocr/RdDocumentOcrController.java`.
- `backend/rd-erp-inventory/src/main/java/com/ruiding/erp/service/documentocr/`: access guard, proxy and safe error type.
- `backend/rd-erp-inventory/src/test/java/com/ruiding/erp/service/documentocr/`: isolated permission, method-security, upload, response and SSRF/error tests.
- `frontend/src/views/rd/assist/ocr/LocalDocumentReader.vue`: default import, no required props/emits. Embed with `<LocalDocumentReader />` in the authenticated parent view. It independently observes the existing user store and `onAuthContextChange`/`getAuthContextRevision`.
- `frontend/src/api/rd/documentocr/index.ts`: uses the original authenticated axios wrapper. No browser request targets the Python origin.

Parent-owned enabling: set the server property `rd.document-ocr.enabled=true` only after the local service has been validated. Default is disabled. The trusted server environment `RD_OCR_BASE_URL` defaults to `http://127.0.0.1:48110`; only literal HTTP `127.0.0.1` and an unprivileged explicit port are accepted. No hostname/DNS, URL path, userinfo, query, fragment, proxy, redirect or request-supplied override is accepted. Keep this process on the SAME host/network namespace as the backend; do not bind it to LAN or expose it through a public ingress.

The parent must preserve original method-security/filter authentication and permit the normal multipart upload through its existing size configuration (at least 10 MiB file, slightly more for multipart overhead). This increment does not alter shared security/YAML/poms/SQL/routes. A reverse proxy/container should bound multipart requests to 11 MiB, upload duration and request concurrency before Spring's multipart parser; the controller independently rejects bytes above 10 MiB after parsing. Do not enable request/response-body logging at a gateway for these routes.

## Backend contract

Both endpoints require the existing authenticated actor, NOT `rd_forklift_driver`, AND either `rd:pickup-slip:create` OR `rd:quality-inspection:create`. The explicit driver exclusion wins even when a driver has broad permissions. The permission API is the original `PermissionCommonApi`, with no `skipPermissionCheck` bypass. No new database tables or application permissions are introduced.

- `GET /rd/document-ocr/status`: ordinary `CommonResult` success `{code:0,data:{available,state,device,maxBytes,maxPages,timeoutSeconds,concurrency}}`. States include `DISABLED`, `UNAVAILABLE`, `MODELS_OR_RUNTIME_MISSING`, `LOCAL_FILES_READY`, `INFERENCE_FAILED`. Status never imports/loads a model or performs inference/network model checks. `LOCAL_FILES_READY` describes local dependency presence and verified files, not an accuracy or hardware certification; model failures make the service unavailable until the operator repairs/restarts this owned service.
- `POST /rd/document-ocr/recognize`: multipart with exactly one part `file`; no text parts or query parameters. Original filename/MIME are not trusted or forwarded. It proxies raw bytes to the fixed internal `/recognize` endpoint with `application/octet-stream` and never forwards browser auth/cookies/headers.
- Errors: safe `CommonResult.error` with HTTP 400/413/415/422/429/503/504 and fixed `OCR_*` code in `msg`, without upstream error text, paths, file contents or stack traces. Inference cannot be presented as success on unavailable/missing models, timeout, malformed files or malformed service responses.

Successful recognition returns:

```json
{
  "schemaVersion": 1,
  "engine": "PaddleOCR",
  "model": "PP-OCRv5_mobile",
  "device": "cpu",
  "pages": 1,
  "rawLines": [{"id":"p1-l1","page":1,"text":"订单号：000042","confidence":0.98}],
  "candidates": {
    "customer": {"state":"UNKNOWN","candidates":[]},
    "order": {"state":"CANDIDATE","candidates":[{"value":"000042","sourceLineIds":["p1-l1"],"evidence":"订单号：000042","confidence":0.98}]},
    "model": {"state":"UNKNOWN","candidates":[]},
    "bagCount": {"state":"UNKNOWN","candidates":[]},
    "weightKg": {"state":"UNKNOWN","candidates":[]}
  }
}
```

All candidate values remain strings, including leading-zero/long IDs. Confidence is optional and only copied from actual model line scores; candidate confidence is the minimum source-line score, NOT a calibrated probability that the field is correct. Missing units/labels/ambiguous numbers remain unknown. Only explicitly labelled kg/公斤/千克/t/吨 weights are normalized to kg with decimal arithmetic. Multiple competing candidates remain visible; the draft is only prefilled for a unique candidate. Human edits do not overwrite evidence. Export is `LOCAL_OCR_DRAFT` JSON with `officialDocumentCreated:false` and explicit unknowns, never an official create API.

## Bounds and privacy

- Byte magic: PNG/JPEG/PDF only, 1 byte to 10 MiB. Decode errors, animated images, empty/broken/encrypted unsupported PDFs are rejected. Up to 3 PDF pages, 5000 px per side, 12 MP per page/image, 24 MP total. PDF rasterization at 1.5 px/point checks every page before rendering the first page.
- One model job at a time, no queued inference. HTTP listener allows at most 4 active request threads; body read has a 10-second absolute deadline. Inference has a 60-second wall limit including process startup, decoding and model loading; child is killed and joined before the slot is released. Backend has a 2-second connect limit and a 70-second total inference exchange deadline, bounded 1 MiB response subscriber, no proxy and no redirects.
- At most 600 normalized lines, 500 characters per line, 30 candidates per field, two source lines per candidate. Backend independently verifies schema/string IDs/evidence/confidence/output limits.
- Listener is hardcoded loopback. No CORS headers; browser `Origin`/`Sec-Fetch-*` are refused, no preflight, and inference requires a non-simple MIME type. No URL/path/command OCR input exists. Worker passes only decoded local arrays to the official API and blocks Python sockets; the pinned PaddleX's eager hoster checks fail before connecting, and explicit local model directories prevent automatic downloads. Native-library egress should additionally be denied by the deployment firewall. Installed SDK dependencies are not cloud features and are never given credentials or document text.
- No uploaded documents are persisted by this service. In-memory data dies with the worker; native/Python stdout/stderr are suppressed. Backend access logging annotations disable request and response bodies. Model/cache directories and synthetic test artifacts are under ignored `.runtime/` only. The launcher passes only a minimal OS environment, isolates home/caches, and opens no visible window.
- PNG/JPEG previews use only inspected local magic/dimensions and blob URLs, not remote URLs or HTML. PDFs are represented by their local file metadata, not embedded in an active PDF viewer. All blob URLs are revoked on clear/auth change/unmount. Draft/results/files are not stored in browser persistence. Auth revision/scope changes abort and invalidate outstanding requests and clear old data; results arriving late cannot reappear.

## Runtime and lifecycle (PowerShell)

Run from this checkout's `tools/document-ocr/`. Commands intentionally start with `rtk`. Do not install into the business runtime.

```powershell
rtk proxy C:/Users/Ring/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe --version
rtk proxy C:/Users/Ring/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe -m venv .venv
rtk proxy .venv/Scripts/python.exe -m pip install -r requirements.lock.txt
rtk proxy .venv/Scripts/python.exe setup_models.py
rtk proxy .venv/Scripts/python.exe service_control.py start --port 48110
rtk proxy .venv/Scripts/python.exe service_control.py status
rtk proxy .venv/Scripts/python.exe service_control.py stop
```

Before a tested lock exists, use the direct pins in `requirements.txt` and then `capture_environment.py`. Bundled Python was observed as **3.12.14**. System Python 3.13 was not used. Never rerun `-m venv` over a running service. `setup_models.py` is an explicit operator-only network download of fixed public model files and does not accept a source URL. Do not call it from status or an upload request.

`service_control.py` starts only this test service with `CREATE_NO_WINDOW`, refuses an occupied port, records PID/start-time/executable/cwd, and stops only that verified process and its children. It does not restart or stop any existing application service. `--port` may select another unused high loopback port; update the trusted backend environment accordingly. Foreground alternative: `rtk proxy .venv/Scripts/python.exe -m document_ocr.server --port 48110`. The test service should be stopped at handoff; see `VERIFICATION.md` for actual final state.

## Verification commands

```powershell
rtk proxy .venv/Scripts/python.exe -m unittest discover -s tests -v
rtk proxy node --test ../../frontend/src/views/rd/assist/ocr/session.test.mjs
rtk proxy .venv/Scripts/python.exe tests/synthetic_document.py
rtk proxy .venv/Scripts/python.exe service_control.py start
rtk proxy .venv/Scripts/python.exe tests/test_real_inference.py --real -v
rtk proxy .venv/Scripts/python.exe service_control.py stop
```

The opt-in real test uses the actual Paddle model on a printed Chinese synthetic pickup/weight document, via the loopback HTTP service. Ordinary tests deliberately use fakes for boundary/failure/concurrency checks; skipped real inference is NOT a model PASS. The fixture font is the local Windows Microsoft YaHei, not redistributed. This does not establish handwritten/dirty-bag accuracy, real customer document performance, target-factory hardware readiness, or field acceptance.

`verify_java.py --classpath-report <existing baseline test XML> --launcher <existing junit-platform-launcher-1.8.2.jar>` reads only that report's classpath, compiles only this increment with Java `--release 17` into `.runtime/java-classes`, and runs the 13 scoped non-model JUnit tests without Maven/DB. Add `--real` with the owned service running to include the 14th test, which sends the synthetic PNG through the actual Java proxy and validates the real model response. On this machine JDK 21 requires the standalone launcher-only `net.bytebuddy.experimental=true` for the existing old Mockito/Byte Buddy. This is supplemental evidence; parent must run the normal reactor gate on its chosen supported JDK.

`rtk proxy node verify_frontend.mjs <parent-checkout>/frontend` uses existing dependencies read-only, a temporary Vite server and a clean headless Edge profile. The actual component/API adapter is exercised with a synthetic auth/backend fixture (NOT real Spring auth or model evidence). It type-checks scoped TypeScript and checks desktop/mobile, multipart upload, manual draft/JSON export, auth clearing, stale responses and object-URL cleanup. No application session or credential is loaded. Artifacts are `.runtime/ui-*.png` and `.runtime/ui-verification.json`. Server/browser are closed by the test.

## Source and license provenance

Pinned compatibility baseline, not a claim to use the newest release:

| Component | Pin / upstream | License |
| --- | --- | --- |
| PaddleOCR | [v3.3.2 source/API](https://github.com/PaddlePaddle/PaddleOCR/blob/v3.3.2/paddleocr/_pipelines/ocr.py) | [Apache-2.0](https://github.com/PaddlePaddle/PaddleOCR/blob/v3.3.2/LICENSE) |
| PaddlePaddle CPU | 3.2.2, official PyPI Windows cp312 wheel | [Apache-2.0](https://github.com/PaddlePaddle/Paddle/blob/v3.2.2/LICENSE) |
| PaddleX OCR | 3.3.2 | [Apache-2.0](https://github.com/PaddlePaddle/PaddleX/blob/v3.3.2/LICENSE) |
| Detector | [PaddlePaddle/PP-OCRv5_mobile_det](https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det), commit `0d63e78e2b680928f6b1747d76a08db6e645efb7` | Model card explicitly declares Apache-2.0 |
| Recognizer | [PaddlePaddle/PP-OCRv5_mobile_rec](https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_rec), commit `682f20538d8c086cb2128e5cfac775e6c4904e85` | Model card explicitly declares Apache-2.0 |
| pypdfium2 | 5.14.0 | [Apache-2.0 OR BSD-3-Clause](https://github.com/pypdfium2-team/pypdfium2); native PDFium and bundled third parties have their own notices |
| Pillow / NumPy | 12.3.0 / 2.2.6 | MIT-CMU / BSD-3-Clause with bundled-component notices; retain wheel notices |

Official v3.3.2 and the live upstream source were inspected on 2026-10-15. Implementation uses the v3 constructor's `text_detection_model_dir`, `text_recognition_model_dir`, `device='cpu'`, disabled optional orientation/unwarping modules, and `predict(numpy.ndarray)` results `rec_texts`/`rec_scores`, not the deprecated v2 `.ocr()` tuple API. Model yml/json/weights are hash checked against the tracked lock, not inferred from a repository license. Downloaded model README files are retained alongside them. Weight SHA-256 values: detector `afa1820cb16c1fd0dad589d0f8b389139061c1ef6d68019685fd07be997dda5b`, recognizer `2460da90875937c94db97eba74ae3d9e5d4c4c57c42f1f41531c09a26bcc771a`.

No weights, venv, Windows font, external Java jars or frontend dependencies are added to Git. A generated `THIRD_PARTY.json` inventories the tested Python distribution metadata; it is not legal certification or a substitute for retaining all installed wheel/PDFium/native-library LICENSE/NOTICE files when packaging. Do not label the whole transitive runtime Apache-2.0. Parent release packaging and target-hardware/field gates remain open.

Windows Unicode checkout note: the pinned native Paddle loader cannot open the Chinese absolute model path. The worker uses Python's Unicode-aware `chdir()` into the fixed model root, then gives Paddle only ASCII relative model-directory names. This changes only the disposable child's cwd and does not copy models outside this module, create drive mappings, change source data, or weaken path validation. The real model tests include this workaround.
