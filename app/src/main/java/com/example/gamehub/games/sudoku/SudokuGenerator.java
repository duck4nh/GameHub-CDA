package com.example.gamehub.games.sudoku;

import com.example.gamehub.data.local.entities.SudokuBoard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class SudokuGenerator {
    private static final int SIZE = 9;
    private static final int MAX_ATTEMPTS = 6;
    private static final int SOLUTION_LIMIT = 2;

    private SudokuGenerator() {
    }

    public static SudokuBoard generate(int boardId, String level) {
        String normalizedLevel = normalizeLevel(level);
        int targetClues = getTargetClues(normalizedLevel);
        Random random = new Random();

        SudokuBoard bestBoard = null;
        int bestClueCount = Integer.MAX_VALUE;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int[][] solution = new int[SIZE][SIZE];
            if (!fillBoard(solution, random)) {
                continue;
            }

            int[][] puzzle = SudokuLogic.copyMatrix(solution);
            int clueCount = carvePuzzle(puzzle, targetClues, random);
            SudokuBoard candidate = new SudokuBoard(
                    boardId,
                    normalizedLevel,
                    SudokuLogic.serializeMatrix(puzzle),
                    SudokuLogic.serializeMatrix(solution)
            );

            if (bestBoard == null || clueCount < bestClueCount) {
                bestBoard = candidate;
                bestClueCount = clueCount;
            }

            if (clueCount <= targetClues) {
                return candidate;
            }
        }

        if (bestBoard != null) {
            return bestBoard;
        }

        int[][] fallbackSolution = new int[SIZE][SIZE];
        fillBoard(fallbackSolution, new Random());
        return new SudokuBoard(
                boardId,
                normalizedLevel,
                SudokuLogic.serializeMatrix(fallbackSolution),
                SudokuLogic.serializeMatrix(fallbackSolution)
        );
    }

    private static int carvePuzzle(int[][] puzzle, int targetClues, Random random) {
        List<Integer> positions = buildShuffledPositions(random);
        int clueCount = SIZE * SIZE;

        for (int position : positions) {
            if (clueCount <= targetClues) {
                break;
            }

            int row = position / SIZE;
            int col = position % SIZE;
            int savedValue = puzzle[row][col];
            puzzle[row][col] = 0;

            if (countSolutions(SudokuLogic.copyMatrix(puzzle), SOLUTION_LIMIT) != 1) {
                puzzle[row][col] = savedValue;
                continue;
            }

            clueCount--;
        }

        return clueCount;
    }

    private static boolean fillBoard(int[][] board, Random random) {
        int[] cell = findMostConstrainedEmptyCell(board);
        if (cell == null) {
            return true;
        }

        List<Integer> numbers = buildShuffledNumbers(random);
        int row = cell[0];
        int col = cell[1];
        for (int value : numbers) {
            if (!isValidPlacement(board, row, col, value)) {
                continue;
            }
            board[row][col] = value;
            if (fillBoard(board, random)) {
                return true;
            }
            board[row][col] = 0;
        }
        return false;
    }

    private static int countSolutions(int[][] board, int limit) {
        int[] cell = findMostConstrainedEmptyCell(board);
        if (cell == null) {
            return 1;
        }

        int row = cell[0];
        int col = cell[1];
        int solutions = 0;
        for (int value = 1; value <= SIZE; value++) {
            if (!isValidPlacement(board, row, col, value)) {
                continue;
            }
            board[row][col] = value;
            solutions += countSolutions(board, limit - solutions);
            if (solutions >= limit) {
                board[row][col] = 0;
                return solutions;
            }
            board[row][col] = 0;
        }
        return solutions;
    }

    private static int[] findMostConstrainedEmptyCell(int[][] board) {
        int[] bestCell = null;
        int bestCandidateCount = Integer.MAX_VALUE;

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (board[row][col] != 0) {
                    continue;
                }

                int candidateCount = countCandidates(board, row, col);
                if (candidateCount < bestCandidateCount) {
                    bestCandidateCount = candidateCount;
                    bestCell = new int[]{row, col};
                    if (candidateCount <= 1) {
                        return bestCell;
                    }
                }
            }
        }

        return bestCell;
    }

    private static int countCandidates(int[][] board, int row, int col) {
        int count = 0;
        for (int value = 1; value <= SIZE; value++) {
            if (isValidPlacement(board, row, col, value)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isValidPlacement(int[][] board, int row, int col, int value) {
        for (int index = 0; index < SIZE; index++) {
            if (board[row][index] == value || board[index][col] == value) {
                return false;
            }
        }

        int boxRow = (row / 3) * 3;
        int boxCol = (col / 3) * 3;
        for (int r = boxRow; r < boxRow + 3; r++) {
            for (int c = boxCol; c < boxCol + 3; c++) {
                if (board[r][c] == value) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<Integer> buildShuffledNumbers(Random random) {
        List<Integer> numbers = new ArrayList<>(SIZE);
        for (int value = 1; value <= SIZE; value++) {
            numbers.add(value);
        }
        Collections.shuffle(numbers, random);
        return numbers;
    }

    private static List<Integer> buildShuffledPositions(Random random) {
        List<Integer> positions = new ArrayList<>(SIZE * SIZE);
        for (int position = 0; position < SIZE * SIZE; position++) {
            positions.add(position);
        }
        Collections.shuffle(positions, random);
        return positions;
    }

    private static int getTargetClues(String level) {
        if ("medium".equals(level)) {
            return 34;
        }
        if ("hard".equals(level)) {
            return 30;
        }
        if ("expert".equals(level)) {
            return 26;
        }
        return 40;
    }

    private static String normalizeLevel(String level) {
        if ("medium".equals(level) || "hard".equals(level) || "expert".equals(level)) {
            return level;
        }
        return "easy";
    }
}
