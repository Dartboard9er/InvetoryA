# Implementation Plan: Resilient & Optimized Item Intel Research

The user reported intermittent timeouts when using the "Research Intel" feature on added items. Researching deep specs across 3–5 consumer forums, manuals, and repair guides produces high token volume that currently exceeds the 25-second HTTP read timeout, occasionally causing socket timeouts or failed queries when specific links are slow to locate.

Based on user feedback:
- **Preserve comprehensive output structure**: Keep the rich forum sources, common issues, pro tips, manual links, and maintenance tasks that worked well.
- **Prompt optimization for non-stalling**: Direct the model to immediately fill what is verified without stalling if niche URLs or deep threads are unindexed.
- **Graceful completion without timeout errors**: If real-time forum querying times out or encounters delays, seamlessly populate available information accompanied by an intellectual synthesis message rather than returning an error.

---

## User Review Required

> [!NOTE]
> - **Timeout expansion**: We will increase the OkHttpClient read/write timeouts from 25s to 60s for research operations.
> - **Graceful degradation guarantee**: If the live network query times out or returns incomplete data, the system will synthesize standard engineering baseline intel (maintenance, safety, known category issues) and present an intellectual synthesis summary, ensuring the user never experiences a broken timeout dialog.

---

## Proposed Changes

### AI & Prompting Layer

#### [GeminiPromptAndParser.kt](file:///app/src/main/java/com/example/ai/gemini/GeminiPromptAndParser.kt)
- **Optimize `buildResearchPrompt`**:
  - Add explicit non-stalling instructions: instruct the model to synthesize verified manufacturer data and community knowledge rapidly. If specific niche forum threads or direct manual PDFs are unavailable, synthesize known product line/brand engineering knowledge without stalling or looping.
  - Add `intellectualInsight` field to the requested JSON schema: an erudite, thoughtful synthesis summary (e.g., explaining the technical architecture, design philosophy, and community consensus for this equipment).
- **Update `ItemResearchResult` & `parseItemResearchJson`**:
  - Add `intellectualInsight: String?` property.
  - Ensure parser safely extracts all fields, falling back gracefully to empty lists and non-null values if the model returned partial JSON.

#### [GeminiService.kt](file:///app/src/main/java/com/example/ai/gemini/GeminiService.kt)
- **Dedicated Network Timeout Configuration**:
  - Create a research-optimized HTTP client with a 60-second read timeout (`client.newBuilder().readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()`).
- **Resilient Fallback & Partial Filler**:
  - In `researchItemIntel`, implement a multi-stage execution strategy:
    1. Primary attempt with Gemini 3.5 Flash (60s timeout).
    2. Fallback attempt with fast model if primary times out or fails.
    3. Intelligent Offline/Baseline Synthesis Fallback: If both network calls encounter a timeout or connection drop, dynamically construct an intellectual `ItemResearchResult` derived from the item's brand, model, and category. It fills maintenance intervals, safety notes, common category quirks, and includes an intellectual note:
       *"Synthesized core engineering profile and preventative maintenance baseline for ${brand ?: category} equipment. Live forum index timed out, preserving verified offline technical knowledge."*
    4. Ensure `researchItemIntel` returns `Result.success(result)` instead of failing, fulfilling the user's requirement: never show a timeout error.

---

### Data & Repository Layer

#### [InventoryRepository.kt](file:///app/src/main/java/com/example/data/repository/InventoryRepository.kt)
- **Update `applyResearchToItem`**:
  - Persist `intellectualInsight` into `ItemIntelligenceProfileEntity` (or maintenance summary/notes) and record it as an `AiMemoryEntity` for conversational assistant grounding.
  - Update `ItemEntity.aiSummary` to highlight the intellectual insight note.

---

### UI Layer

#### [ItemDetailScreen.kt](file:///app/src/main/java/com/example/ui/screens/ItemDetailScreen.kt)
- **Enhance Research Dialog & Intel Card**:
  - Update dialog copy: when research completes, display the intellectual insight note prominently in an elegant, styled card.
  - Handle partial/baseline synthesis gracefully: show an informative badge (e.g. "🧠 Intellectual Synthesis") with the insight explanation, avoiding any scary red timeout dialogs.
  - Add progress feedback indicating live forum and manual indexing status.

---

## Verification Plan

### Automated Tests
- Run existing Robolectric test suite to verify no regressions:
  ```bash
  gradle :app:testDebugUnitTest
  ```
- Add unit/Robolectric test cases in `com.example.homeai.ai.GeminiResearchTest`:
  - Test `parseItemResearchJson` handles partial and complete JSON with `intellectualInsight`.
  - Test `researchItemIntel` graceful fallback when network call throws `SocketTimeoutException`.
  - Verify `applyResearchToItem` correctly stores intellectual notes, maintenance tasks, and intelligence profile.

### Manual / Build Verification
- Run `compile_applet` to ensure pristine compilation and resource validation.
- Verify simulated research call completes within seconds or yields an intellectual synthesis without errors.
