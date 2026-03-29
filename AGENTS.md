# AGENTS.md

## Project Overview
- Project: `GameHub`
- Platform: Android
- Current repo shape: single `app` module, Java source, XML layouts, `Activity` + `Fragment` UI, Room database scaffold, Firebase Auth/Firestore dependencies present, WorkManager available, no Compose.
- Product shape: game + social app with intended hybrid offline-first behavior.

## Scoped Ownership: Leaderboard + Statistics/History + Community Chat Only
- In scope:
- `Leaderboard`
- `Statistics & History`
- `Community Chat / Discussion Room`
- Firestore integration for these features
- Room/SQLite integration for `Local_History`
- SharedPreferences/session usage required by these features
- Minimal sync logic for `Local_History -> Game_Records -> Users.total_score`

## Out Of Scope
- Authentication/register/login redesign beyond minimal session reading
- Profile editing
- Friends feature implementation outside minimal integration context
- Game gameplay modules
- Broad architecture refactors
- Unrelated UI redesign
- Invented backend contracts or schema changes not supported by code or the documented brief

## Architecture Guardrails
- Treat the existing repo as the technical source of truth for package layout, Java/XML UI approach, Room integration style, activity entry points, worker usage, and shared utilities.
- Prefer minimal diffs over rewrites.
- Do not replace the current XML/View stack with Compose.
- Do not casually refactor teammate-owned modules.
- If a required integration is missing, add the smallest viable implementation that preserves the current architecture.
- If documentation and code disagree, inspect the code first, report the mismatch, and choose the safest path.

## Feature Sources Of Truth

### Leaderboard
- Firestore collections:
- `Users`
- `Game_Records`
- Rules:
- all-time leaderboard reads from `Users.total_score`
- weekly leaderboard reads from `Game_Records`
- weekly mode filters records from the start of the current week
- weekly ranking groups by `uid` client-side and sums `score`
- UI in this scope must not rely on hardcoded mock leaderboard data once real data is available

### Statistics & History
- Local table:
- `Local_History`
- Rules:
- statistics/history prioritize `Local_History`
- newest history first
- statistics use conservative aggregates already supported by Room/DAO
- screens must remain usable offline
- UI in this scope must not rely on hardcoded mock history/statistics data once real data is available

### Community Chat
- Firestore collection:
- `Chat_Messages`
- Rules:
- messages read ordered by `timestamp` ascending
- current user messages render as self
- sending a message uses current session `uid` and cached/current nickname
- snapshot listener is preferred when Firebase is active
- UI in this scope must not rely on hardcoded mock chat messages once real data is available

## Firestore Collections For This Scope
- `Users`
- `Game_Records`
- `Chat_Messages`

## Local Table For This Scope
- `Local_History`

## SharedPreferences Keys For This Scope
- `current_uid`
- `cache_nickname`
- `is_dark_mode`
- `leaderboard_filter`
- `LAST_SYNC_TIME`

## Offline-First Rules To Preserve
- Game results are written locally first into `Local_History` with `is_synced = 0`.
- `Local_History` acts as both local history and sync queue for this scope.
- When network is available, unsynced local records may be pushed to `Game_Records`.
- `Users.total_score` may be updated only once per successfully synced record.
- Only after successful push should local records be marked synced.
- `LAST_SYNC_TIME` should be updated after successful sync work.
- Avoid duplicate sync and duplicate score accumulation.

## Anti-Hardcode Rule
- Once the data layer for this scope is available, leaderboard, statistics/history, and chat UI must not keep using hardcoded/demo/mock data sources.

## Anti-Hallucination Rules
- Never invent a Firestore collection or field outside this scope unless the existing codebase clearly already uses it.
- Never invent a local table/column beyond `Local_History` for this scope unless it already exists in the repo.
- Never invent unsupported backend behavior for chat rooms, leaderboard aggregation, or sync semantics.
- If sync or session behavior is ambiguous, inspect surrounding code and report uncertainty rather than guessing.

## Build, Lint, And Test Expectations
- Run the most relevant Gradle build/checks after scoped changes.
- Report pre-existing failures separately from failures introduced by the change.
- Be explicit if Firebase behavior cannot be fully validated because runtime credentials/session/backend state are outside the local repo.

## Done Checklist
- Only leaderboard, statistics/history, chat, and the required local/sync/session paths were modified.
- Hardcoded/mock leaderboard data was removed from these screens.
- Hardcoded/mock chat data was removed from these screens.
- Statistics/history read from `Local_History`.
- Leaderboard reads from Firestore-backed `Users` and `Game_Records`.
- Chat reads/writes `Chat_Messages`.
- SharedPreferences keys are actually used where needed.
- Sync from `Local_History` to Firestore is implemented as far as the repo/backend allows.
- Changed files and remaining risks are reported clearly.
