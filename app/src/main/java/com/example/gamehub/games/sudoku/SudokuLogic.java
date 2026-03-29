package com.example.gamehub.games.sudoku;

public final class SudokuLogic {
    private SudokuLogic() {
    }

    public static int[][] parseMatrix(String matrix) {
        int[][] board = new int[9][9];
        if (matrix == null) {
            return board;
        }
        String normalized = matrix.replace(",", "").replace(" ", "").trim();
        int limit = Math.min(normalized.length(), 81);
        for (int i = 0; i < limit; i++) {
            char value = normalized.charAt(i);
            if (Character.isDigit(value)) {
                board[i / 9][i % 9] = Character.getNumericValue(value);
            }
        }
        return board;
    }

    public static String serializeMatrix(int[][] matrix) {
        StringBuilder builder = new StringBuilder(81);
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                builder.append(matrix[row][col]);
            }
        }
        return builder.toString();
    }

    public static int[][] copyMatrix(int[][] source) {
        int[][] copy = new int[9][9];
        for (int row = 0; row < 9; row++) {
            System.arraycopy(source[row], 0, copy[row], 0, 9);
        }
        return copy;
    }

    public static boolean isBoardFull(int[][] board) {
        for (int[] row : board) {
            for (int value : row) {
                if (value == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isSolved(int[][] current, int[][] solution) {
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                if (current[row][col] != solution[row][col]) {
                    return false;
                }
            }
        }
        return true;
    }

    public static int countCorrectCells(int[][] current, int[][] solution) {
        int count = 0;
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                if (current[row][col] != 0 && current[row][col] == solution[row][col]) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int countIncorrectFilledCells(int[][] current, int[][] solution) {
        int count = 0;
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                if (current[row][col] != 0 && current[row][col] != solution[row][col]) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int countCorrectOccurrences(int[][] current, int[][] solution, int value) {
        int count = 0;
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                if (current[row][col] == value && current[row][col] == solution[row][col]) {
                    count++;
                }
            }
        }
        return count;
    }
}
