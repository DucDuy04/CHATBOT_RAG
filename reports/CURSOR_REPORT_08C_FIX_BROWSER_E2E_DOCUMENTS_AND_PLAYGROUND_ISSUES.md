# Cursor Report 08C - FIX_BROWSER_E2E_DOCUMENTS_AND_PLAYGROUND_ISSUES

## 1. Muc do hieu task
- Task la gi? Sua bug integration browser cho 2 khu vuc `Documents` va `Playground` theo danh sach FAIL, khong mo rong feature.
- Hieu task: 96%
- Phan chac chan:
  - Documents: refresh list sau upload, drawer status/chunks, assign/retry error message.
  - Playground: token stream UI, session click restore history, retrieval panel empty-state.
  - Khong sua compare logic runtime override.
- Phan con gia dinh:
  - Khong the thao tac browser truc tiep trong moi truong agent, nen manual retest UI duoc xac nhan bang code-path + build/lint pass.
- Pham vi khong lam:
  - Public widget/chat, Dashboard, Analytics, Settings, Chatbots, backend schema/RAG core, compare override engine.

## 2. Files/rules/reports da doc
| File | Muc dich doc | Ket luan chinh |
|---|---|---|
| `.cursor/rules/00-core-working-rule.mdc` | Scope/minimal diff | Chi sua dung diem fail |
| `.cursor/rules/10-backend-rag-rule.mdc` | Doi chieu contract BE | Xac nhan shape `/api/documents/*`, `/api/playground/*` |
| `.cursor/rules/20-frontend-widget-rule.mdc` | FE SSE/state checklist | Tap trung parser SSE, state streaming |
| `.cursor/rules/30-deploy-env-ops-rule.mdc` | Runtime discipline | Khong doi env/deploy scope |
| `.cursor/rules/40-db-vector-rule.mdc` | Out-of-scope DB/vector | Khong doi schema/vector behavior |
| `.cursor/rules/90-report-verification-rule.mdc` | Report/verification format | Ghi trung thuc PASS/NOT RUN |
| `reports/CURSOR_REPORT_08A_FULLSTACK_REAL_API_INTEGRATION_QA_AND_MINOR_FIXES.md` | Baseline fullstack | Real API da duoc noi, can xu ly bug UI integration |
| `reports/CURSOR_REPORT_08B_BROWSER_MANUAL_E2E_QA_REAL_API_AND_MINOR_FIXES.md` | Danh sach fail browser | Chot cac FAIL can fix trong 08C |
| `reports/CURSOR_REPORT_03B_FE_DOCUMENT_UPLOAD_TENANT_CONTEXT.md` | Upload tenant context | Upload dung `chatbotId` |
| `reports/CURSOR_REPORT_03C_BACKEND_DOCUMENTS_RUNTIME_SMOKE_TEST.md` | Contract Documents runtime | Backend tra 400 message ro cho assign/retry unsafe |
| `reports/CURSOR_REPORT_04A_BACKEND_PUBLIC_AND_PLAYGROUND_CHAT_RUNTIME_SMOKE_TEST.md` | SSE event shape | `event: token`, `event: done` |
| `reports/CURSOR_REPORT_04B_BACKEND_PLAYGROUND_SESSIONS_COMPARE_EXPORT_CANONICAL_API.md` | Sessions/export contract | Session item co the co `messages`; export tra JSON |
| `Frontend/src/api/documentsApi.js` | API FE documents | Confirm endpoint va response fields |
| `Frontend/src/api/playgroundApi.js` | SSE parser/export FE | Phat hien parser done/token can robust hon |
| `Frontend/src/pages/documents/DocumentsPage.jsx` | Orchestration documents | Phat hien catch message chua lay `response.data.message` day du |
| `Frontend/src/pages/documents/components/*` | UI actions/drawer | Chunks button bi disable theo status, drawer chi goi chunks |
| `Frontend/src/pages/playground/PlaygroundPage.jsx` | Orchestration stream/session | Click session dang phu thuoc export blob nen khong restore history |
| `Frontend/src/pages/playground/components/*` | Session/retrieval/export UI | Empty-state retrieval va export button behavior |
| `Backend/src/main/java/.../DocumentController.java` | Doi chieu API errors | 400/404 tra `message` ro rang |
| `Backend/src/main/java/.../DocumentService.java` | Doi chieu status/chunks map | `status/chunkCount/chunks` shape hop le |
| `Backend/src/main/java/.../PlaygroundController.java` | Doi chieu sessions/export | Endpoint sessions/export dung nhu FE can |
| `Backend/src/main/java/.../PlaygroundService.java` | Doi chieu restore messages | Session list/export deu co messages |
| `Backend/src/main/java/.../ChatService.java` | Doi chieu done event | `done` dang tra JSON array sources |

## 3. User reported failures
| Area | Failure | Reproduced? | Root cause | Fix status |
|---|---|---|---|---|
| Documents | List refresh sau upload | Co (qua code-path) | Sau refresh khong thong bao ro khi filter hien tai khong match doc moi | FIXED |
| Documents | Status/chunks drawer fail | Co (qua code-path) | Drawer chi goi chunks, khong goi status; chunks button bi khoa khi khong INDEXED | FIXED |
| Documents | Assign/retry unsafe khong bao loi ro | Co | Catch UI khong uu tien `response.data.message`, assign flow khong toast/catch day du | FIXED |
| Playground | Token stream hien thi fail | Co | Parser SSE chua robust (`\r`, multi-line data, trailing buffer), done payload array chua normalize | FIXED |
| Playground | Click session khong restore history | Co | `exportSession` real mode tra Blob, `handleSessionSelect` doc `result.messages` nen rong | FIXED |
| Playground | Sources/retrieval panel gay nham | Co nhe | Khong crash, nhung empty-state chua ro nghia khi khong co sources | FIXED (UX text nho, khong redesign) |

## 4. Root cause details
- Documents list refresh:
  - Co goi refresh sau upload, nhung khong co thong bao ro rang khi upload chatbot A trong luc filter dang chatbot/status khac.
  - Dẫn den cam giac "khong refresh" du request list da chay.
- Documents chunks/status drawer:
  - `ChunkDrawer` truoc day chi goi `GET /chunks`, khong goi `GET /status`.
  - Nut `Chunks` bi disable neu document khong `INDEXED`, lam user khong mo duoc drawer de xem trang thai.
- Documents assign/retry error handling:
  - UI toast/catch uu tien `err.message`, bo sot backend `response.data.message`.
  - `handleAssign` khong catch de show message ro + rethrow cho modal.
- Playground token stream:
  - Parser chia event theo `\n\n` nhung khong flush buffer cuoi.
  - Parser done chua normalize truong hop backend tra mang sources.
  - Parser data chua robust voi `\r` va multi-line `data:`.
- Playground session restore:
  - `playgroundApi.exportSession` luon `responseType: blob`; `PlaygroundPage` ky vong object `{messages}`.
  - Vi vay click session set selected nhung khong co message duoc restore.
- Sources/retrieval panel:
  - Khong co crash, nhung empty-state text chua ro "khong co sources cho cau tra loi nay".

## 5. Files changed
| File | Change | Reason | Risk |
|---|---|---|---|
| `Frontend/src/api/playgroundApi.js` | Hardening SSE parser, normalize `done`, them option `exportSession(..., { asBlob })` | Fix token stream + session restore path | Low |
| `Frontend/src/pages/playground/PlaygroundPage.jsx` | Restore session uu tien `session.messages`, fallback export JSON; xu ly loi xoa session | Fix click session khong hien history | Low |
| `Frontend/src/pages/playground/components/ExportSessionButton.jsx` | Goi export voi `asBlob: true` | Giu hanh vi download file | Low |
| `Frontend/src/pages/playground/components/RetrievalPanel.jsx` | Empty-state ro nghia hon | Giam confusion khi khong co sources | Low |
| `Frontend/src/pages/documents/DocumentsPage.jsx` | Normalize API error message; toast ro khi filter khong match sau upload; assign/retry/delete error handling | Fix feedback loi + refresh perception | Low |
| `Frontend/src/pages/documents/components/DocumentsTable.jsx` | Bo khoa nut `Chunks` theo status | Cho phep mo drawer xem status/chunks | Low |
| `Frontend/src/pages/documents/components/ChunkDrawer.jsx` | Goi ca `/status` + `/chunks`; empty/error handling ro hon | Fix drawer behavior theo contract | Low |

## 6. Detailed changes
### `Frontend/src/api/playgroundApi.js`
- Da sua:
  - Tach parser `processEventBlock` de parse event robust hon.
  - Xu ly `\r`, multi-line `data:`.
  - Normalize `done` payload array => `{ sources: [...] }`.
  - Flush trailing event block khi stream ket thuc.
  - `exportSession` ho tro `asBlob` de dung cho 2 luong: restore JSON va download Blob.
- Vi sao:
  - Dung shape backend thuc te (`done` dang la array sources), dam bao token append realtime.
- Behavior sau sua:
  - Streaming token append lien tuc, done khong lam mat sources.
  - Session restore co the lay JSON thay vi blob.

### `Frontend/src/pages/playground/PlaygroundPage.jsx`
- Da sua:
  - Them `normalizeRestoredMessages`.
  - Click session uu tien `session.messages` tu list; neu khong co moi goi export JSON.
  - Catch restore show toast va refresh sessions neu co loi.
- Vi sao:
  - Tranh phu thuoc blob trong luong restore.
- Behavior sau sua:
  - Click session hien lai lich su chat dung role/content/sources.

### `Frontend/src/pages/playground/components/ExportSessionButton.jsx`
- Da sua:
  - Export button goi `playgroundApi.exportSession(sessionId, { asBlob: true })`.
- Vi sao:
  - Tach biet ro giua luong restore (JSON) va luong download (blob).
- Behavior sau sua:
  - Export/download giu nguyen.

### `Frontend/src/pages/playground/components/RetrievalPanel.jsx`
- Da sua:
  - Empty-state text thanh thong diep ro rang "No sources for this answer".
- Vi sao:
  - User confusion, khong can redesign panel.
- Behavior sau sua:
  - Khong co source van hien trang thai ro, khong crash.

### `Frontend/src/pages/documents/DocumentsPage.jsx`
- Da sua:
  - Them helper `getApiErrorMessage` uu tien backend message.
  - `handleUpload` await refresh list va thong bao ro neu filter hien tai khong match doc vua upload.
  - `handleAssign` them catch + toast + throw de modal hien error.
  - Retry/Delete cung show backend message ro hon.
- Vi sao:
  - Fix silent fail va nham lan "upload thanh cong nhung list khong thay".
- Behavior sau sua:
  - Assign/retry unsafe hien message backend ro, loading/modal state khong ket.

### `Frontend/src/pages/documents/components/DocumentsTable.jsx`
- Da sua:
  - Nut `Chunks` chi disable khi action dang busy, khong disable theo `status !== INDEXED`.
- Vi sao:
  - De user co the mo drawer xem status/chunks/empty state.
- Behavior sau sua:
  - Drawer mo duoc voi ca document processing/failed.

### `Frontend/src/pages/documents/components/ChunkDrawer.jsx`
- Da sua:
  - Mo drawer se goi dong thoi `GET /status` va `GET /chunks`.
  - Hien summary status/chunk/progress.
  - Error message uu tien backend `response.data.message`.
- Vi sao:
  - Dung yeu cau status/chunks drawer va tranh crash/silent.
- Behavior sau sua:
  - Empty chunks hien empty-state; 400/404 hien loi ro.

## 7. Validation results
| Command | Result | Notes |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | Prompt 08C chi sua Frontend |
| `cd Backend && ./mvnw test` | NOT RUN | Prompt 08C chi sua Frontend |
| `cd Frontend && npm run lint` | PASS | Hoan tat sau khi sua |
| `cd Frontend && npm run build` | PASS | Vite build thanh cong |

## 8. Manual retest results
| Area | Flow | Expected | Actual | Status |
|---|---|---|---|---|
| Documents | Upload txt sau khi chon chatbot | Upload 200 + list refresh | Da sua code-path refresh + thong bao mismatch filter | PARTIAL (code-level) |
| Documents | All Statuses / Indexed | Hien dung theo filter | Da sua thong bao ro neu khong match filter | PARTIAL (code-level) |
| Documents | Open status/chunks drawer | Goi `/status` + `/chunks`, khong crash | Da implement Promise.all + empty/error state | PARTIAL (code-level) |
| Documents | Assign unsafe / retry non-failed | Hien message 400 ro, khong silent | Da parse backend message va toast/modal flow | PARTIAL (code-level) |
| Documents | Delete | Van hoat dong | Khong doi logic delete, chi doi message parser | PARTIAL (code-level) |
| Playground | Send message stream | Token append trong stream, done van dung | Parser SSE duoc harden + flush trailing buffer | PARTIAL (code-level) |
| Playground | Sources panel | Khong crash, empty state ro | Da doi empty-state text | PARTIAL (code-level) |
| Playground | Sessions list/click restore | Click session hien lai history | Da restore tu `session.messages` hoac export JSON | PARTIAL (code-level) |
| Playground | Export/delete session | Khong regress | Export button van dung blob, delete logic khong doi | PARTIAL (code-level) |
| Playground | Compare mode | Khong regress | Khong sua compare logic | PARTIAL (code-level) |

## 9. Known limitations / gaps
- Chua chay browser thao tac truc tiep trong moi truong agent, nen ket qua manual retest la code-level verification + lint/build.
- Compare override van la limitation hien tai: backend nhan/echo config nhung chua apply runtime override sau.
- Public widget/chat chua test trong prompt nay (theo scope).
- Co the co case retrieval khong tra source cho mot so cau hoi, panel se hien empty-state (khong coi la crash).

## 10. Final decision
Some issues remain. Follow-up bugfix prompt required.

(Ly do: can 1 vong browser manual retest tren may nguoi dung de dong PASS cuoi cung cho C6/C7 sau khi da fix code-path.)

## 11. Recommended next prompt
08D_PUBLIC_WIDGET_AND_PUBLIC_CHAT_BROWSER_VERIFICATION

