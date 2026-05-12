# CURSOR REPORT 10 - SETTINGS PAGE

## 1. Muc do hieu task

- Hieu task: 100%
- Chac chan: implement route `/settings` gom 3 khu vuc Profile, API Keys, Danger Zone theo checklist.
- Phan con gia dinh: profile payload co field notifications duoc backend chap nhan (mock chap nhan qua `Object.assign`).
- Thieu du kien: contract backend chi tiet cho notifications fields va reveal raw key ngoai `plainTextKey` sau generate.

## 2. Tom tat yeu cau

- Replace placeholder `SettingsPage.jsx`.
- Profile:
  - avatar initials, name/email/language
  - notification toggles
  - GET + PUT profile
- API Keys:
  - list keys (masked), reveal button client-side
  - generate key (show plain key once)
  - delete key (ConfirmDeleteModal)
  - GET + POST + DELETE api-keys
- Danger Zone:
  - Delete account button + confirm modal
  - khong goi API vi logic TBD

## 3. Hien trang truoc khi sua

- `SettingsPage.jsx` chi placeholder.
- `settingsApi.js` da co san cac function profile/api-keys va team.
- `settingsMock.js` da co profile + apiKeys dataset.
- Chua co settings components rieng cho profile, api keys, danger zone.

## 4. Nguyen nhan goc xac nhan tu source

- Route `/settings` chua co implementation UI/flow va chua wiring API section-level.

## 5. Chien luoc sua da chon

- Tao 4 components local trong `src/pages/settings/components/`:
  - `ProfileSettingsSection`
  - `ApiKeysSection`
  - `ApiKeyRow`
  - `DangerZoneSection`
- `SettingsPage` chi lam shell + `useLayout().setPageTitle("Settings")`.
- Tach fetch/loading/error theo section de mot section fail khong crash ca page.
- Khong dung team endpoints du API co san.

## 6. Danh sach file da doc

- `.cursor/rules/00-core-working-rule.mdc`: source-first, minimal diff.
- `.cursor/rules/10-backend-rag-rule.mdc`: backend out-of-scope.
- `.cursor/rules/20-frontend-widget-rule.mdc`: frontend constraints + lint/build.
- `.cursor/rules/30-deploy-env-ops-rule.mdc`: no deploy scope.
- `.cursor/rules/40-db-vector-rule.mdc`: no db/vector scope.
- `.cursor/rules/90-report-verification-rule.mdc`: report + final output format.
- `reports/CURSOR_REPORT_00_PROJECT_AUDIT.md`: roadmap context.
- `reports/CURSOR_REPORT_01_GLOBAL_SHARED.md`: shared components/layout baseline.
- `reports/CURSOR_REPORT_01C_LAYOUT_CONTEXT.md`: `useLayout` pattern.
- `reports/CURSOR_REPORT_02_API_LAYER.md`: settings API contracts.
- `reports/CURSOR_REPORT_09C_ANALYTICS_FEEDBACK_TAB.md`: latest baseline, avoid scope overlap.
- `Frontend/src/pages/settings/SettingsPage.jsx`: placeholder source.
- `Frontend/src/api/settingsApi.js`: settings endpoints and payload/response shape.
- `Frontend/src/mocks/settingsMock.js`: profile + api key mock fields.
- `Frontend/src/components/common/ConfirmDeleteModal.jsx`: reusable confirm modal.
- `Frontend/src/components/common/SkeletonLoader.jsx`: loading UI.
- `Frontend/src/components/common/EmptyState.jsx`: empty UI.
- `Frontend/src/components/common/useToast.js`: success/error/warning toast.
- `Frontend/src/contexts/LayoutContext.jsx`: page title integration.

## 7. Danh sach file da sua

- `Frontend/src/pages/settings/SettingsPage.jsx`
  - Muc dich: page shell settings + page title.
  - Layer: ui
- `Frontend/src/pages/settings/components/ProfileSettingsSection.jsx`
  - Muc dich: profile form + notifications + save.
  - Layer: ui
- `Frontend/src/pages/settings/components/ApiKeysSection.jsx`
  - Muc dich: load/list/generate/delete api keys + shown-once plain key.
  - Layer: ui
- `Frontend/src/pages/settings/components/ApiKeyRow.jsx`
  - Muc dich: row render masked/reveal/status/metadata/delete action.
  - Layer: ui
- `Frontend/src/pages/settings/components/DangerZoneSection.jsx`
  - Muc dich: delete account confirm flow (no API call).
  - Layer: ui

## 8. Diff thay doi cua tung file

### `Frontend/src/pages/settings/SettingsPage.jsx`

- Cu: placeholder text.
- Moi: set title + render 3 sections.

```diff
- <p>Settings — placeholder...</p>
+ const { setPageTitle } = useLayout();
+ useEffect(() => setPageTitle("Settings"), [setPageTitle]);
+ <ProfileSettingsSection />
+ <ApiKeysSection />
+ <DangerZoneSection />
```

### `Frontend/src/pages/settings/components/ProfileSettingsSection.jsx`

- Cu: chua co.
- Moi:
  - GET profile on mount.
  - name/email/language fields.
  - avatar initials from name fallback email.
  - 3 notification toggles.
  - PUT profile on save.
  - loading + inline error + retry + toast.

```diff
+ const data = await settingsApi.getProfile()
+ await settingsApi.updateProfile({
+   name, email, language,
+   notifications: { embeddingFailed, dailySummary, newFeedback }
+ })
```

### `Frontend/src/pages/settings/components/ApiKeysSection.jsx`

- Cu: chua co.
- Moi:
  - GET keys on mount.
  - generate key via POST with optional name.
  - show plain text key once in memory state (`latestPlainTextKey`).
  - delete key via ConfirmDeleteModal + DELETE endpoint.
  - loading/error/empty states.

```diff
+ const keys = await settingsApi.getApiKeys()
+ const res = await settingsApi.generateApiKey({ name })
+ setLatestPlainTextKey(res?.plainTextKey || null)
+ await settingsApi.deleteApiKey(deleteTarget.id)
```

### `Frontend/src/pages/settings/components/ApiKeyRow.jsx`

- Cu: chua co.
- Moi:
  - masked display default.
  - reveal toggle client-side only.
  - fallback masking if raw provided but no `maskedKey`.
  - status + createdAt/lastUsedAt render.

```diff
+ const masked = item?.maskedKey || fallbackMask(raw)
+ {revealed ? revealedValue : masked}
+ <button>{revealed ? "Hide" : "Reveal"}</button>
```

### `Frontend/src/pages/settings/components/DangerZoneSection.jsx`

- Cu: chua co.
- Moi:
  - destructive section UI.
  - confirm modal for delete account.
  - no API call, only toast warning "not implemented yet".

```diff
+ <button onClick={() => setOpen(true)}>Delete account</button>
+ onConfirm => toast.warning("Delete account is not implemented yet.")
```

## 9. Anh huong sau sua

- Behavior thay doi:
  - `/settings` da hoat dong that voi 3 sections.
  - Profile save cap nhat qua `PUT /api/settings/profile`.
  - API Keys list/generate/delete chay duong API checklist.
  - plain key chi hien thi trong state hien tai sau generate.
- Behavior giu nguyen:
  - khong su dung team endpoints.
  - khong anh huong Usage/Sessions/Feedback, Playground, Dashboard, Chatbots, Documents.
- Dieu kien bat:
  - Reveal chi toggle client-side; neu khong co raw key thi van khong hien raw.
  - Delete account chi la placeholder flow, khong call API.
- Fallback:
  - moi section co loading/error rieng, khong crash toan page.
- Tai nguyen:
  - tang nhe bundle frontend; khong tac dong MySQL/Qdrant/backend schema.

## 10. Edge cases da xem xet

- Profile fetch fail: inline error + retry + toast.
- API keys fetch fail: inline error + retry + toast.
- Empty keys list: EmptyState.
- Generate key dang xu ly: disable button.
- Delete key dang xu ly: modal loading.
- Reveal khi khong co raw key: chi hien masked + warning text.
- Name/email empty khi save profile: inline form error.
- Plain text key khong luu localStorage/sessionStorage.

## 11. Ket qua kiem tra

| Command | Ket qua | Ghi chu |
|---|---|---|
| `cd Backend && ./mvnw -DskipTests compile` | NOT RUN | Khong sua backend |
| `cd Backend && ./mvnw test` | NOT RUN | Khong sua backend |
| `cd Frontend && npm run lint` | PASS | 0 loi/0 warning sau cleanup |
| `cd Frontend && npm run build` | PASS | build pass, warning chunk-size >500k van ton tai |
| `cd Frontend && npm run build:widget` | NOT RUN | Ngoai scope |
| `docker compose config` | NOT RUN | Khong sua infra |

## 12. Rui ro con lai

- Contract backend real cho `notifications` trong profile chua xac nhan chinh thuc.
- API keys list khong tra raw key (dung theo bao mat), reveal co the chi hien masked key trong real mode.
- Delete account endpoint chua co, Danger Zone chi la UX placeholder.

## 13. De xuat tiep theo

- Chot contract profile notifications voi backend (neu can luu persistent fields nay).
- Neu product can: bo sung flow copy key vao clipboard va warning UX chi tiet hon.
- Khi backend co endpoint delete account, noi logic tu Danger Zone modal.
