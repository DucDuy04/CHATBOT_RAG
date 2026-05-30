# Runbook — Lệnh vận hành

Operational commands cho CHATBOT_RAG. PowerShell examples trên Windows; Linux/macOS thay `.\mvnw.cmd` → `./mvnw`.

---

## Prerequisites

- Docker Desktop running
- Java 21 (`JAVA_HOME`)
- `.env` tại repo root với `GROQ_API_KEY`, `NOMIC_API_KEY`

---

## Start stack

```powershell
cd E:\chatbot-rag-workspace\CHATBOT_RAG
docker compose config -q
docker compose up -d mysql qdrant
docker compose up --build -d backend
docker compose ps
docker compose logs backend --tail=200
```

Optional frontend:

```powershell
docker compose up -d frontend
```

URLs:

| Service | URL |
|---------|-----|
| Backend | http://localhost:8080 |
| Frontend | http://localhost:5173 |
| Qdrant REST | http://localhost:6333 |
| MySQL | localhost:3306 (db: `ragchatbot`, user/pass: `root`) |

---

## Check logs

```powershell
docker compose logs backend --tail=200
docker compose logs mysql --tail=50
docker compose logs qdrant --tail=50
```

Follow live:

```powershell
docker compose logs -f backend
```

---

## Check Nomic key prefix (container)

Verify key được inject — **chỉ xem prefix, không log full key:**

```powershell
docker compose exec backend sh -lc 'echo ${NOMIC_API_KEY:0:8}'
```

Local PowerShell:

```powershell
$env:NOMIC_API_KEY.Substring(0, [Math]::Min(8, $env:NOMIC_API_KEY.Length))
```

---

## Run backend locally (dev profile)

MySQL + Qdrant phải đang chạy:

```powershell
cd Backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:GROQ_API_KEY='your-key'
$env:NOMIC_API_KEY='your-key'
.\mvnw.cmd spring-boot:run
```

Profile docker (inside container): `SPRING_PROFILES_ACTIVE=docker` — set bởi compose.

---

## Run tests

```powershell
cd Backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd clean test
```

Expected: **120 tests**, 0 failures, 0 errors.

Compile only:

```powershell
.\mvnw.cmd -DskipTests compile
```

---

## Frontend (local dev)

```powershell
cd Frontend
npm install
npm run dev
```

Build + widget:

```powershell
npm run build
npm run lint
npm run build:widget
```

Output: `dist-widget/chatbot-widget.iife.js` → synced to `public/dist-widget/`.

Local widget test: http://localhost:5173/widget?widgetKey=YOUR_UUID

---

## DB cleanup verification (historical feedback artifacts)

Optional — verify legacy feedback schema removed or still present on old DB:

```sql
SHOW TABLES LIKE 'chat_feedbacks';
SHOW COLUMNS FROM settings_profiles LIKE 'notify_new_feedback';
```

**Expected on cleaned DB:** no rows. Backend 28A+ does not require these; safe to drop after backup if still present.

---

## Health checks

```powershell
# Backend up
curl http://localhost:8080/actuator/health 2>$null
# Qdrant
curl http://localhost:6333/collections/documents 2>$null
```

---

## Restart single service

```powershell
docker compose restart backend
docker compose up -d --build backend
```

---

## Cleanup warning — NEVER for routine ops

**Không chạy** trừ khi reset destructive có chủ đích:

```text
docker compose down -v          # xóa MySQL + Qdrant volumes
drop Qdrant collection          # mất toàn bộ vectors
truncate tables                 # mất metadata
DELETE without document_id filter on Qdrant
```

**Cleanup đúng cách:**

- Document: `DELETE /api/documents/{id}` → soft-delete + Qdrant purge by `document_id`
- Chatbot: `DELETE /api/chatbots/{id}` → cascade documents first

---

## Troubleshooting quick refs

| Symptom | Check |
|---------|-------|
| Backend won't start | `docker compose logs backend`; MySQL healthcheck |
| Embedding fail | NOMIC key prefix; network from container |
| Qdrant mismatch | Compare `chunkCount` vs Qdrant points for `document_id` |
| Mojibake in answers | `QdrantPayloadUnicodeTest`; verify REST path not gRPC |
| Upload stuck PROCESSING | Backend logs; file size ≤ 50MB |

Chi tiết: [`agent/03-backend.md`](03-backend.md), [`agent/06-operations.md`](06-operations.md)

API smoke tests: [`docs/api/API_SMOKE_TESTS_20260530.md`](../docs/api/API_SMOKE_TESTS_20260530.md)
