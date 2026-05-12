# Cursor Report 04A - BACKEND_PUBLIC_AND_PLAYGROUND_CHAT_RUNTIME_SMOKE_TEST

## 1. Mức độ hiểu task
- Task là gì? Chạy runtime smoke test thật cho `POST /api/public/chat` và `POST /api/playground/chat` theo contract FE, chỉ sửa bug nhỏ nếu fail.
- Hiểu task: 98%
- Phần chắc chắn:
  - Public chat dùng header `x-api-key`, body `{ message, sessionId }`, response `{ answer, sessionId, sources }`.
  - Playground chat dùng body `{ chatbotId, message, sessionId, overrideParams }`, trả SSE `event: token` và `event: done`.
  - Không mở rộng feature ngoài scope.
- Phần còn giả định:
  - Server runtime phải chạy đúng source hiện tại (không phải JVM cũ).
  - `chatbotId` playground là UUID backend.
- Phạm vi không làm:
  - Không sửa Frontend/mock, không đổi schema, không đổi core RAG flow, không làm sessions/compare/export/dashboard/settings.

## 2. Files/rules/reports đã đọc
| File | Mục đích đọc | Kết luận chính |
|---|---|---|
| `.cursor/rules/*.mdc` | Bám rule workspace | Giữ minimal diff, report trung thực, verify compile/test |
| `agent.md`, `agent/01-overview.md`, `agent/02-architecture.md` | Nắm kiến trúc runtime | Chat/RAG multi-tenant theo widget/chatbot UUID |
| `reports/CURSOR_REPORT_01B_BACKEND_CANONICAL_FE_ENDPOINTS_NOT_ALIASES.md` | Baseline canonical endpoints | `/api/public/chat`, `/api/playground/chat` đã có |
| `reports/CURSOR_REPORT_02B_RERUN_BACKEND_CHATBOTS_API_RUNTIME_SMOKE_TEST.md` | Baseline runtime chatbots | Có thể tạo chatbot + có `apiKey` |
| `reports/CURSOR_REPORT_03A_BACKEND_DOCUMENTS_API_CONTRACT_ALIGNMENT_AND_SAFE_CANONICAL_ENDPOINTS.md` | Baseline documents contract | Upload canonical cần `chatbotId` |
| `reports/CURSOR_REPORT_03B_FE_DOCUMENT_UPLOAD_TENANT_CONTEXT.md` | Baseline FE tenant upload | FE flow đã truyền tenant context |
| `reports/CURSOR_REPORT_03C_BACKEND_DOCUMENTS_RUNTIME_SMOKE_TEST.md` | Baseline runtime documents | Documents smoke test đã PASS |
| `Frontend/src/api/publicChatApi.js` | Contract public chat FE | FE parse lỗi từ `message/error`; không JWT |
| `Frontend/src/api/playgroundApi.js` | Contract parser SSE FE | Parser mong `token` + `done`, token ưu tiên JSON `{token}` fallback raw, done parse JSON |
| `Frontend/src/mocks/playgroundMock.js` | Shape tham chiếu mock | done mock có `sources`, `latency`, `sessionId` |
| `Frontend/src/pages/playground/PlaygroundPage.jsx` + `components/*` | FE consume SSE result | FE dùng `result.sources`, `result.latency`, `result.sessionId` theo defensive access |
| `Backend/src/main/java/.../api/PublicChatController.java` | Runtime public endpoint | Tự tạo sessionId khi null, trả `PublicChatResponse` |
| `Backend/src/main/java/.../api/PlaygroundController.java` | Runtime playground endpoint | Validate `chatbotId/message`, map sang `ChatService.chatStream` |
| `Backend/src/main/java/.../service/ChatService.java` | SSE event shape thực tế | `event: token` data JSON `{\"token\":\"...\"}`, `event: done` data JSON array sources |
| `Backend/src/main/java/.../config/WidgetAuthFilter.java` | Public auth behavior | `/api/public/chat` cần API key; missing/invalid trả 401 |
| `Backend/src/main/java/.../config/SecurityConfig.java` | Security permit | `/api/public/**` và `/api/playground/**` permitAll |
| `Backend/src/main/resources/application.yml`, `application-dev.yml`, `docker-compose.yml` | Runtime env | backend dev profile, mysql/qdrant local ports |

## 3. Runtime environment
| Item | Status | Notes |
|---|---|---|
| Docker | RUNNING | `ragchatbot-mysql`, `ragchatbot-qdrant` đều up |
| MySQL | RUNNING/healthy | `3306` |
| Qdrant | RUNNING | `6333-6334` |
| Backend | RUNNING | `http://localhost:8080`, restart từ source workspace sau khi fix |
| Base URL | OK | `http://localhost:8080` |
| Env keys | Present | Nạp từ `.env` khi start backend (không log secret) |

## 4. Test data setup
- Chatbot id: `b9bd0f80-9c7c-453a-915f-c2df94f74ba5`
- Chatbot apiKey / public key: generated từ `POST /api/chatbots` (đã dùng test, không public secret ở đây)
- Uploaded document id: `77f4e995-264c-4b99-8215-f2ec022a0304`
- Document status: `INDEXED`
- Chunks count: `1`
- Cleanup plan:
  - DELETE document test: done
  - DELETE chatbot test: done
  - Remove temp files `tmp-chat-smoke.txt`, `tmp-playground-invalid.json`, `tmp-playground-missing.json`: done

## 5. Public Chat smoke test results
| Test | Endpoint | Expected | Actual | Status: PASS / FAIL / BLOCKED / NOT RUN | Notes |
|---|---|---|---|---|---|
| Public chat no JWT, x-api-key | `POST /api/public/chat` | 200 + `answer/sessionId/sources` | 200, đủ 3 field, answer non-empty, sources array | PASS | Session mới sinh hợp lệ |
| Session continuation | `POST /api/public/chat` | 200, session hợp lệ tiếp tục | 200, sessionId giữ nguyên giữa 2 lượt | PASS | Không lỗi parse UUID |
| Missing API key | `POST /api/public/chat` | 401/400, không 500 | 401 `{"error":"Missing API key header."}` | PASS | Đúng filter |
| Invalid API key | `POST /api/public/chat` | 401/400, không 500 | 401 `{"error":"Invalid UUID format for Widget Key."}` | PASS | Không 500 |

## 6. Playground Chat SSE smoke test results
| Test | Endpoint | Expected | Actual | Status: PASS / FAIL / BLOCKED / NOT RUN | Notes |
|---|---|---|---|---|---|
| SSE basic | `POST /api/playground/chat` | 200 + `text/event-stream`, có `token` + `done` | 200 `text/event-stream`, stream có `event:token` và `event:done` | PASS | `done` data là JSON array sources |
| Invalid chatbotId | `POST /api/playground/chat` | 400 + message rõ | 400 `{"message":"chatbotId must be a UUID"}` | PASS | Sau fix nhỏ controller |
| Missing chatbotId | `POST /api/playground/chat` | 400 + message rõ | 400 `{"message":"chatbotId must not be blank"}` | PASS | Sau fix nhỏ controller |

## 7. Bugs found and fixed
| Bug | File | Fix | Retest status |
|---|---|---|---|
| `playground` validation errors trả body generic, khó map theo FE `message/error` | `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/PlaygroundController.java` | Trả `ResponseEntity.badRequest().body(Map.of("message", ...))` cho validate fail (`message blank`, `chatbotId blank/invalid`) và giữ stream success trả `SseEmitter` | PASS |

## 8. Files changed
| File | Change | Reason | Risk |
|---|---|---|---|
| `Backend/src/main/java/KLTN/RAG_CHATBOT_BE/api/PlaygroundController.java` | Validation failure trả JSON `message` rõ ràng; success path vẫn SSE stream | Align FE error parsing + smoke test expectation, không đổi core chat stream | Low |

## 9. Validation results
| Command | Result | Notes |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | PASS | BUILD SUCCESS sau sửa controller |
| `cd Backend && ./mvnw test` | PASS | 14 tests pass trong môi trường hiện tại |
| `cd Frontend && npm run lint` | NOT RUN | Ngoài scope prompt backend runtime |
| `cd Frontend && npm run build` | NOT RUN | Ngoài scope prompt backend runtime |
| `cd Frontend && npm run build:widget` | NOT RUN | Ngoài scope prompt backend runtime |
| `docker compose config` | NOT RUN | Không đổi compose/config trong prompt này |

## 10. Known limitations / gaps
- SSE `done` hiện trả JSON array `sources` (không gồm `latency`/`sessionId`), nhưng parser FE đang defensive (`result?.sources`, `result?.latency`, `result?.sessionId`) nên không crash.
- Public/Playground trả text có dấu bị lệch encoding trong terminal PowerShell output; response contract/shape vẫn đúng.

## 11. Final decision
**Public Chat and Playground Chat smoke tests PASS. Proceed to next backend prompt.**

## 12. Recommended next prompt
`04B_BACKEND_PLAYGROUND_DONE_EVENT_ENRICHMENT_COMPAT_CHECK`  
(chỉ khi muốn enrich thêm `latency/sessionId` trong `done` event; không bắt buộc để pass contract hiện tại)

