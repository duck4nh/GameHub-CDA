package com.example.gamehub.data.local;

import com.example.gamehub.data.local.entities.MemoryLevel;
import com.example.gamehub.data.local.entities.SudokuBoard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DatabaseSeeder {
    private static final int MEMORY_LEVEL_COUNT = 30;

    private DatabaseSeeder() {
    }

    public static void seedIfNeeded(AppDatabase database) {
        syncMemoryLevels(database);
        if (database.sudokuDao().getCount() == 0) {
            database.sudokuDao().insertAll(buildSudokuBoards());
        }
    }

    private static void syncMemoryLevels(AppDatabase database) {
        List<MemoryLevel> targetLevels = buildMemoryLevels();
        List<MemoryLevel> existingLevels = database.memoryDao().getAllLevels();
        if (matchesTarget(existingLevels, targetLevels)) {
            return;
        }

        Map<Integer, Long> bestTimeByLevelId = new HashMap<>();
        int highestUnlockedLevelId = 1;
        for (MemoryLevel existingLevel : existingLevels) {
            bestTimeByLevelId.put(existingLevel.levelId, existingLevel.bestTimeMs);
            if (existingLevel.isUnlocked) {
                highestUnlockedLevelId = Math.max(highestUnlockedLevelId, existingLevel.levelId);
            }
        }

        int unlockedLimit = Math.min(MEMORY_LEVEL_COUNT, Math.max(1, highestUnlockedLevelId));
        for (MemoryLevel targetLevel : targetLevels) {
            targetLevel.isUnlocked = targetLevel.levelId <= unlockedLimit;
            Long bestTime = bestTimeByLevelId.get(targetLevel.levelId);
            if (bestTime != null) {
                targetLevel.bestTimeMs = bestTime;
            }
        }

        database.memoryDao().clearAll();
        database.memoryDao().insertAll(targetLevels);
    }

    private static boolean matchesTarget(List<MemoryLevel> existingLevels, List<MemoryLevel> targetLevels) {
        if (existingLevels.size() != targetLevels.size()) {
            return false;
        }
        for (int index = 0; index < targetLevels.size(); index++) {
            MemoryLevel existing = existingLevels.get(index);
            MemoryLevel target = targetLevels.get(index);
            if (existing.levelId != target.levelId
                    || existing.rowCount != target.rowCount
                    || existing.columnCount != target.columnCount
                    || existing.timeLimitSec != target.timeLimitSec) {
                return false;
            }
        }
        return true;
    }

    private static List<MemoryLevel> buildMemoryLevels() {
        Map<Integer, LevelSpec> specsByPairCount = new LinkedHashMap<>();
        for (int rowCount = 3; rowCount <= 40; rowCount++) {
            for (int columnCount = 4; columnCount <= 6; columnCount++) {
                if ((rowCount * columnCount) % 2 != 0) {
                    continue;
                }
                int pairCount = (rowCount * columnCount) / 2;
                if (pairCount < 6) {
                    continue;
                }
                LevelSpec current = new LevelSpec(rowCount, columnCount, pairCount);
                LevelSpec existing = specsByPairCount.get(pairCount);
                if (existing == null || current.balanceScore() < existing.balanceScore()) {
                    specsByPairCount.put(pairCount, current);
                }
            }
        }

        List<LevelSpec> sortedSpecs = new ArrayList<>(specsByPairCount.values());
        sortedSpecs.sort(Comparator.comparingInt(spec -> spec.pairCount));

        List<MemoryLevel> items = new ArrayList<>();
        int levelId = 1;
        for (LevelSpec spec : sortedSpecs) {
            items.add(new MemoryLevel(
                    levelId,
                    spec.rowCount,
                    spec.columnCount,
                    35L + spec.pairCount * 5L,
                    0L,
                    levelId == 1
            ));
            levelId++;
            if (levelId > MEMORY_LEVEL_COUNT) {
                break;
            }
        }
        return items;
    }

    private static List<SudokuBoard> buildSudokuBoards() {
        List<SudokuBoard> items = new ArrayList<>();
        items.add(new SudokuBoard(
                1,
                "easy",
                "530070000600195000098000060800060003400803001700020006060000280000419005000080079",
                "534678912672195348198342567859761423426853791713924856961537284287419635345286179"
        ));
        items.add(new SudokuBoard(
                2,
                "medium",
                "003020600900305001001806400008102900700000008006708200002609500800203009005010300",
                "483921657967345821251876493548132976729564138136798245372689514814253769695417382"
        ));
        items.add(new SudokuBoard(
                3,
                "hard",
                "000000907000420180000705026100904000050000040000507009920108000034059000507000000",
                "462831957795426183381795426173984265659312748248567319926178534834259671517643892"
        ));
        items.add(new SudokuBoard(
                4,
                "expert",
                "000900002050123400000000000030050600000308000001020090000000000006751040800004000",
                "314986752659123487287475931438259671972318564561627398145862379396751248823594116"
        ));
        return items;
    }

    private static class LevelSpec {
        final int rowCount;
        final int columnCount;
        final int pairCount;

        LevelSpec(int rowCount, int columnCount, int pairCount) {
            this.rowCount = rowCount;
            this.columnCount = columnCount;
            this.pairCount = pairCount;
        }

        int balanceScore() {
            return Math.abs(rowCount - columnCount);
        }
    }
}
