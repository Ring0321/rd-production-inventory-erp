# REUSE-3 handoff and evidence

Date: 2026-10-15, Asia/Shanghai. Isolated checkout:
`D:/芯珩科技/货位统计明细表/rd-production-inventory-erp-worktrees/reuse-ocr`.
Baseline/HEAD remains `c0ead420488e94c557e104712ad406d0bc394a01` (detached).

## Result and parent contract

The local OCR increment is implemented and recoverable as NEW scoped files in this checkout. No commits/pushes, subdelegation, business DB/runtime/credential access, existing service restarts, or changes to baseline files were performed. Only the owned test OCR process and ephemeral isolated UI test servers/browsers were started/stopped. `.venv/` and `.runtime/` are ignored; source hashes are in `SOURCE_MANIFEST.sha256`.

Entry points:

- `frontend/src/views/rd/assist/ocr/LocalDocumentReader.vue`, default import, `<LocalDocumentReader />`, no required props/emits. Original user store plus auth-context revision events clear the module. No new shared frontend dependency.
- `frontend/src/api/rd/documentocr/index.ts`, original axios wrapper, authenticated backend only.
- `backend/rd-erp-inventory/src/main/java/com/ruiding/erp/controller/admin/documentocr/RdDocumentOcrController.java`.
- `backend/rd-erp-inventory/src/main/java/com/ruiding/erp/service/documentocr/RdDocumentOcrService.java` and `RdDocumentOcrAccess.java`.
- `tools/document-ocr/document_ocr/server.py`, fixed loopback service, default 48110.

Routes: `POST /rd/document-ocr/recognize` with exactly one multipart part `file`; `GET /rd/document-ocr/status`. Original admin API prefix/baseURL remains owned by the host app. Both routes require authenticated original pickup-create OR quality-create permission, and explicitly deny `rd_forklift_driver`, even with broad grants. Byte magic and size are independently checked in Java and Python. POST accepts no URL/path/command/extra field or query parameter. Results use the original `CommonResult` envelope and return only bounded normalized lines plus candidate/evidence records. See `README.md` for exact schema, limits, error/status codes, model provenance and license links.

Parent enables `rd.document-ocr.enabled=true` only after installing/verifying the owned service. Server-only `RD_OCR_BASE_URL` defaults to `http://127.0.0.1:48110`, validates literal loopback/port, and cannot be controlled by a browser request. Browser has no direct service path or CORS. No original document creation/write API is called; JSON exports are explicitly drafts.

## Final observed checks

| Check | Actual result | Boundary of evidence |
| --- | --- | --- |
| Bundled Python | 3.12.14 | System 3.13 not used |
| Isolated installation | PASS, 87 exact package pins captured, `pip check` clean | No installation into business runtime |
| Python boundary/parser/process tests | 16 passed; 2 real-model tests skipped in ordinary discovery | Fakes used for controlled HTTP/error/concurrency cases; actual spawned child kill/reap also tested |
| Opt-in real CPU model tests | 2 passed, actual PNG/JPEG/PDF inference | Real PaddleOCR/Paddle models, not mocks |
| Java scoped tests with `--real` | 14 passed, 0 skipped/failed | Includes actual method-security interceptor and real Java-to-Python-model proxy; no whole application or DB |
| Frontend pure session tests | 5 passed | Auth invalidation, strings/unknowns, role/permission guard, magic/dimensions |
| Scoped TypeScript | PASS, 0 diagnostics | Existing read-only parent dependencies with explicitly synthetic auth/API adapters |
| Isolated browser UI | PASS, desktop 1440x1000 and mobile 390x844 | Actual component, synthetic backend/auth; latest captured real-model payload used; not Spring-authenticated E2E |
| UI behavior | Upload, manual edit, JSON download, scope/auth clear, late response ignore, blob revocation, no direct 48110 request | No application credentials/session loaded |
| Visual inspection | Desktop/mobile screenshots inspected, no horizontal overflow or overlapping text | Harness uses a neutral fixed-size stand-in for the host's globally registered icons |
| Source ownership | Only new allowed files; no tracked baseline diff | Audited by `final_audit.py` and source manifest |
| Final lifecycle | Owned OCR service STOPPED; state PID file absent; 48110 not listening | UI test servers/browsers also closed |

Final pinned direct runtime: PaddleOCR 3.3.2, PaddlePaddle CPU 3.2.2 (Windows cp312), PaddleX OCR 3.3.2, NumPy 2.2.6, Pillow 12.3.0, pypdfium2 5.14.0. `requirements.lock.txt` contains all 87 exact tested transitive pins; `THIRD_PARTY.json` captures installed license metadata. Pillow/PDFium were refreshed to their verified current PyPI pins before the final unit/real-model/Java proxy reruns.

Final actual model run on the non-sensitive printed Chinese pickup/weight fixture:

| Format | Pages | Normalized lines | Candidate fields | Elapsed seconds |
| --- | ---: | ---: | ---: | ---: |
| PNG | 1 | 7 | 5 | 14.594 |
| JPEG | 1 | 7 | 5 | 11.203 |
| PDF | 1 | 7 | 5 | 10.234 |

These are observed single runs including disposable-process/model startup, not performance guarantees or an accuracy benchmark. Leading-zero order string, model, customer, bag count and explicit-unit weight candidates were checked. Source line text/confidence remain model output; the real recognizer produced the traditional `凈重` label, which now has an explicit tested alias without digit guessing or evidence rewriting. Tests cover both `凈重` and `淨重`.

## Resolved runtime issues

1. Initial real inference failed at native model load on the Chinese absolute checkout path. No success was claimed then. Python's Unicode-aware `chdir()` into the fixed model root plus ASCII relative model-directory names fixed the Windows native file opening. No drive mapping or copy outside the allowed module was used.
2. Running the first full venv suite exposed a difference in old PDFium context-manager support that the bundled newer runtime did not expose. Document lifetime now uses explicit `try/finally` close; final pinned PDFium 5.14.0 passes malformed/4-page/oversized-page tests, 3-page decode, and real PDF inference.
3. Standalone Java verification uses `javac --release 17`. This host's installed JDK 21 needs the test-launcher-only `net.bytebuddy.experimental=true` for the baseline's old Mockito/Byte Buddy. No pom/dependency or production JVM setting was changed. The original reactor gate is still the parent's responsibility.

## Reproduce (from tools/document-ocr)

```powershell
rtk proxy .venv/Scripts/python.exe -m unittest discover -s tests -v
rtk proxy node --test ../../frontend/src/views/rd/assist/ocr/session.test.mjs
rtk proxy .venv/Scripts/python.exe service_control.py start --port 48110
rtk proxy .venv/Scripts/python.exe tests/test_real_inference.py --real -v
rtk proxy C:/Users/Ring/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe verify_java.py --classpath-report D:/芯珩科技/货位统计明细表/rd-production-inventory-erp-worktrees/warehouse-pda-next-phase/backend/rd-erp-inventory/target/surefire-reports/TEST-com.ruiding.erp.service.quality.RdQualityInspectionControllerTest.xml --launcher C:/Users/Ring/.m2/repository/org/junit/platform/junit-platform-launcher/1.8.2/junit-platform-launcher-1.8.2.jar --real
rtk proxy node verify_frontend.mjs D:/芯珩科技/货位统计明细表/rd-production-inventory-erp-worktrees/warehouse-pda-next-phase/frontend
rtk proxy .venv/Scripts/python.exe service_control.py stop
rtk proxy .venv/Scripts/python.exe final_audit.py
```

For a fresh checkout, the exact venv/model setup commands are in `README.md`. Models are explicit fixed official downloads only; ordinary startup/status does not download them. The helper checks owned process executable/cwd/PID creation time before stopping it, and opens no visible window. `diagnose_runtime.py` is operator-only and runs just the synthetic fixture; it prints no recognized document text. The HTTP service never exposes its internal diagnostic metadata.

Ignored local evidence (all synthetic; source and setup instructions remain tracked/recoverable):

- `.runtime/models/manifest.json`: all fetched artifact SHA-256 hashes, byte counts and fixed revision URLs; compared with tracked `models.lock.json`.
- `.runtime/real-inference.json`: real PNG result and elapsed duration, synthetic only.
- `.runtime/real-model-formats.json`: real JPEG/PDF matrix without raw document text.
- `.runtime/java-tests.log`: final 14/14 Java check and baseline test-launcher warnings, no document payload.
- `.runtime/ui-verification.json`, `.runtime/ui-desktop.png`, `.runtime/ui-mobile.png`: isolated synthetic browser results.

Model weight provenance: detector commit `0d63e78e2b680928f6b1747d76a08db6e645efb7`, weights SHA-256 `afa1820cb16c1fd0dad589d0f8b389139061c1ef6d68019685fd07be997dda5b`; recognizer commit `682f20538d8c086cb2128e5cfac775e6c4904e85`, weights SHA-256 `2460da90875937c94db97eba74ae3d9e5d4c4c57c42f1f41531c09a26bcc771a`. Official model cards declare Apache-2.0; model json/yml hashes and model-card Git blobs are pinned separately. Paddle code licenses were verified independently. The Python/native transitive runtime is NOT all Apache-2.0; preserve all wheel/native licenses and notices when packaging.

## Remaining parent gates (not local failures)

- Embed the component and original permission-gated navigation; enable the backend property and retain authenticated method security.
- Confirm shared Spring multipart/reverse-proxy limits before enabling: 10 MiB file acceptance, bounded multipart overhead/upload time/concurrency. This increment checks the file after Spring parses multipart; it cannot change shared parser settings under the exclusive-write constraint.
- Run the normal Maven reactor/frontend production gates and full authenticated browser -> original Spring backend -> owned service workflow, with the parent's intended identity/role wiring. Our real Java proxy check is not that full app gate.
- Validate deployment low privilege, native-parser sandbox/OS egress policy, timeout capacity and current license/native-wheel redistribution notices. Disposable child/pixel/time limits are not an OS security sandbox.
- Target warehouse CPU/RAM/throughput, real permitted document samples, handwritten/dirty-bag performance, PDA/physical equipment and field acceptance remain untested. No such accuracy/readiness claim is made.

No remaining installation/model/hardware blocker on this local workstation. Shared integration and target-hardware/field acceptance are pending by ownership, not marked PASS here.
