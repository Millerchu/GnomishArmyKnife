# Knowledge Base Public Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the knowledge base into a public published library with user submissions and admin review, and surface published highlights as rotating copy on the login page.

**Architecture:** Extend `gak_knowledge_entry` with publication and review metadata, split service queries by published/my-submissions/pending-review views, and keep the login-page highlight feed reading only published entries. On the frontend, separate public content, user submissions, and admin review state so the existing page does not keep one overloaded state object.

**Tech Stack:** Spring Boot, MyBatis-Plus, Vue 3, Vite, Node test runner, Maven

---

### Task 1: Add Failing Back-End Review Workflow Tests

**Files:**
- Modify: `gak-modules/gak-knowledge-base/src/test/java/com/gak/knowledgebase/service/KnowledgeBaseServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Add tests for:
- normal user create -> `PENDING`
- default page view -> only `PUBLISHED`
- normal user cannot read another user's pending entry
- admin pending-review view returns pending entries
- admin publish moves an entry into highlight results

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -pl gak-modules/gak-knowledge-base -Dtest=KnowledgeBaseServiceTest test`
Expected: FAIL because status/view/review methods do not exist yet

- [ ] **Step 3: Write minimal implementation**

Implement only the fields, DTOs, and service methods needed to satisfy the tests.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -pl gak-modules/gak-knowledge-base -Dtest=KnowledgeBaseServiceTest test`
Expected: PASS

### Task 2: Implement Knowledge Entry Review Model

**Files:**
- Modify: `gak-start/src/main/resources/schema.sql`
- Modify: `gak-modules/gak-knowledge-base/src/main/java/com/gak/knowledgebase/domain/KnowledgeEntry.java`
- Modify: `gak-modules/gak-knowledge-base/src/main/java/com/gak/knowledgebase/dto/KnowledgeEntryQueryRequest.java`
- Create: `gak-modules/gak-knowledge-base/src/main/java/com/gak/knowledgebase/dto/ReviewKnowledgeEntryRequest.java`
- Create: `gak-modules/gak-knowledge-base/src/main/java/com/gak/knowledgebase/enums/KnowledgeEntryStatus.java`
- Modify: `gak-modules/gak-knowledge-base/src/main/java/com/gak/knowledgebase/vo/KnowledgeEntryVO.java`

- [ ] **Step 1: Extend schema and seed data**

Add columns:
- `status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED'`
- `reviewed_by BIGINT`
- `reviewed_at TIMESTAMP`
- `review_remark VARCHAR(200)`

Backfill historical seeded rows as `PUBLISHED`.

- [ ] **Step 2: Extend Java model and request/response objects**

Expose:
- `status`
- `ownerUserId`
- `reviewedBy`
- `reviewedAt`
- `reviewRemark`

Add `view` to `KnowledgeEntryQueryRequest`.

- [ ] **Step 3: Compile the module**

Run: `./mvnw -pl gak-modules/gak-knowledge-base -DskipTests compile`
Expected: BUILD SUCCESS

### Task 3: Implement Service and Controller Review Flow

**Files:**
- Modify: `gak-modules/gak-knowledge-base/src/main/java/com/gak/knowledgebase/service/KnowledgeBaseService.java`
- Modify: `gak-modules/gak-knowledge-base/src/main/java/com/gak/knowledgebase/controller/KnowledgeBaseController.java`

- [ ] **Step 1: Add published/my-submissions/pending-review query paths**

Default list path returns only `PUBLISHED`.
`my-submissions` returns current user's entries.
`pending-review` is admin-only.

- [ ] **Step 2: Add role-based mutation rules**

Normal user:
- create => `PENDING`
- update only own `PENDING/REJECTED`, then reset to `PENDING`
- delete only own unpublished entries

Admin:
- create published
- update any
- delete any

- [ ] **Step 3: Add review endpoints**

Controller endpoints:
- `PUT /knowledge-base/entries/{id}/publish`
- `PUT /knowledge-base/entries/{id}/reject`

Both take `ReviewKnowledgeEntryRequest`.

- [ ] **Step 4: Verify back-end tests**

Run: `./mvnw -pl gak-modules/gak-knowledge-base -Dtest=KnowledgeBaseServiceTest test`
Expected: PASS

### Task 4: Add Failing Front-End View-State Tests

**Files:**
- Create: `GnomishArmyKnife-Web/src/utils/knowledgeBaseAccess.js`
- Create: `GnomishArmyKnife-Web/src/utils/__tests__/knowledgeBaseAccess.test.js`
- Create: `GnomishArmyKnife-Web/src/utils/__tests__/loginKnowledgeTicker.test.js`

- [ ] **Step 1: Write the failing tests**

Cover:
- list view labels resolve correctly by role and view
- published highlight ticker can rotate through entries safely
- empty or failed highlight payload hides ticker

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test`
Expected: FAIL because helper modules do not exist yet

- [ ] **Step 3: Implement minimal helpers**

Create small pure helpers only for state parsing and ticker rotation.

- [ ] **Step 4: Re-run tests**

Run: `npm test`
Expected: PASS

### Task 5: Refactor Knowledge Base Front-End for Public/Submission/Review Views

**Files:**
- Modify: `GnomishArmyKnife-Web/src/api/knowledgeBase.js`
- Modify: `GnomishArmyKnife-Web/src/views/KnowledgeBase.vue`

- [ ] **Step 1: Extend API layer**

Add:
- `listKnowledgeEntries({ view, ... })`
- `publishKnowledgeEntry(id, data)`
- `rejectKnowledgeEntry(id, data)`

- [ ] **Step 2: Split page state**

Separate:
- public list state
- my submissions state
- pending review state
- edit form state
- detail state

- [ ] **Step 3: Update page copy and actions**

Change:
- public list heading
- submit entry button copy
- status chips
- admin review actions
- rejection reason display

- [ ] **Step 4: Build and verify**

Run: `npm run build`
Expected: PASS

### Task 6: Add Login-Page Published Knowledge Ticker

**Files:**
- Modify: `GnomishArmyKnife-Web/src/views/Login.vue`
- Modify: `GnomishArmyKnife-Web/src/api/knowledgeBase.js`

- [ ] **Step 1: Load published highlights on login page**

Request a small highlight set.
Failure should be silent.

- [ ] **Step 2: Render left-brand rotating copy**

Add:
- section label `今日经验`
- title
- one-line summary
- manual switch button
- auto-rotation timer

- [ ] **Step 3: Verify login build path**

Run: `npm run build`
Expected: PASS

### Task 7: Final Verification

**Files:**
- Modify: none

- [ ] **Step 1: Run front-end tests**

Run: `npm test`
Expected: PASS

- [ ] **Step 2: Run front-end production build**

Run: `npm run build`
Expected: PASS

- [ ] **Step 3: Run back-end knowledge-base tests**

Run: `./mvnw -pl gak-modules/gak-knowledge-base -Dtest=KnowledgeBaseServiceTest test`
Expected: PASS

- [ ] **Step 4: Run start module compile**

Run: `./mvnw -pl gak-start -DskipTests compile`
Expected: BUILD SUCCESS
