# AGENTS.md

## Project Overview
- Project: `GameHub`
- Platform: Android
- Current repo shape: single `app` module, Java source, XML layouts, `Activity` + `Fragment` UI, Room database scaffold, placeholder remote/sync layers, no Compose.
- Product shape: game + social app with intended hybrid offline-first behavior.

## Scoped Ownership: Chu Duc Anh + Hoang Viet Anh Only
- In scope for Chu Duc Anh:
- `Quiz Game`
- `Memory Game`
- local Room/SQLite question and level data
- gameplay flow
- win/lose result handling
- end-game animation behavior
- local history writes for Quiz and Memory
- In scope for Hoang Viet Anh:
- `Sudoku` data management
- `Sudoku` gameplay and board rendering
- completion checking
- save/resume in-progress Sudoku state
- Sudoku statistics
- Sudoku local history writes
- minimal Sudoku sync integration only if supported by the existing repo structure

## Out Of Scope
- Authentication, register, login
- Profile ownership outside minimal navigation consistency
- Friends, leaderboard, chat, and statistics features owned by other teammates
- Broad architecture refactors
- Unrelated UI redesign
- Invented backend contracts or schema changes not supported by code or the documented brief

## Architecture Guardrails
- Treat the existing repo as the technical source of truth for package layout, Java/XML UI approach, Room integration style, activity entry points, and shared utilities.
- Prefer minimal diffs over rewrites.
- Do not replace the current XML/View stack with Compose.
- Do not casually refactor teammate-owned modules to fit the game scope.
- If a required game integration is missing, add the smallest viable implementation that preserves the current architecture.
- If documentation and code disagree, inspect the code first, report the mismatch, and choose the safest path.

## Feature Sources Of Truth

### Quiz
- Source of truth:
- Local Room/SQLite data from `Quiz_Questions`
- Existing repo package `games/quiz`
- Required fields:
- `id`
- `category`
- `question`
- `link_image`
- `opt_a`
- `opt_b`
- `opt_c`
- `opt_d`
- `correct_ans`
- `difficulty`
- Rules:
- normal gameplay must be offline-capable
- questions load from local DB, not network
- result flow must determine win/lose clearly
- finished session writes into `Local_History`

### Memory
- Source of truth:
- Local Room/SQLite data from `Memory_Levels`
- Existing repo package `games/memory`
- Required fields:
- `level_id`
- `grid_size`
- `time_limit`
- `best_time`
- `is_unlocked`
- Rules:
- grid-based card matching gameplay
- time-limit-aware flow only if supported by level data
- level progression/unlock only if consistent with existing code/schema
- finished session writes into `Local_History`

### Sudoku
- Source of truth:
- Local board/state/stat data in repo and documented schema
- Existing repo package `games/sudoku`
- Required board fields:
- `Sudoku_Boards`
- `Sudoku_Game_State`
- `Sudoku_Stats`
- Rules:
- render 9x9 board
- support player interaction and solved-state checking
- persist/resume in-progress state when supported by repo/database
- finished session writes `game_name = "Sudoku"` into `Local_History`
- support sync hook only if current repo architecture actually provides it

## Key Data Structures And Preference Keys
- Local tables:
- `Quiz_Questions`
- `Memory_Levels`
- `Sudoku_Boards`
- `Sudoku_Game_State`
- `Sudoku_Stats`
- `Local_History`
- Cloud/shared collections:
- `Users`
- `Game_Records`
- SharedPreferences keys to use only if present or required:
- `LAST_SYNC_TIME`
- `IS_SOUND_ON`
- `IS_ANIMATION_ON`
- `LAST_SUDOKU_LEVEL`
- `IS_HINT_ENABLED`

## Offline-First Rules To Preserve
- Game results are written locally first.
- `Local_History` acts as both local history and sync queue.
- Normal Quiz/Memory/Sudoku play should remain offline-capable.
- Unsynced records may later be pushed to cloud through existing sync structure, but do not double-write score or duplicate sync behavior.
- Do not make game content depend on network when local DB already supports it.

## Schema Caution
- The brief warns that Sudoku matrix naming may be inverted between `solution_matrix` and `initial_matrix`.
- Never assume naming semantics without checking repo implementation first.
- If the repo and brief use opposite meanings, preserve repo consistency and report the mismatch explicitly.

## Anti-Hallucination Rules
- Never invent a field, collection, table, route, repository, score formula, or business rule unless it is already in code or explicitly stated by the project brief.
- If a feature is still a stub in the repo, state that and build only the minimal missing implementation needed for the scoped game features.
- Treat existing unstaged or staged user changes as authoritative context; do not revert them unless explicitly asked.

## Build, Lint, And Test Expectations
- Run the most relevant Gradle build/checks after scoped changes.
- Report pre-existing failures separately from failures introduced by the change.
- Be explicit if sync/Firebase behavior cannot be fully validated because the repo lacks working backend wiring.

## Done Checklist
- Only Quiz, Memory, Sudoku, and their required local data/state/history paths were modified.
- Quiz gameplay loads from local DB and writes local history.
- Memory gameplay loads level data from local DB and writes local history.
- Sudoku gameplay, board state, resume/stat path, and local history path are implemented within repo constraints.
- Minimal sync hook was added only where existing architecture supports it.
- No unrelated teammate feature was broadly rewritten.
- Changed files and remaining risks are reported clearly.
