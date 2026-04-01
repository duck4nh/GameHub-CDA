package com.example.gamehub.games.sudoku;

import com.example.gamehub.data.local.entities.SudokuBoard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SudokuGeneratorTest {
    @Test
    public void generateEasyBoardCreatesValidPuzzleAndSolution() {
        SudokuBoard board = SudokuGenerator.generate(1, "easy");

        assertNotNull(board);
        assertEquals("easy", board.level);
        assertEquals(81, board.initialMatrix.length());
        assertEquals(81, board.solutionMatrix.length());

        int[][] puzzle = SudokuLogic.parseMatrix(board.initialMatrix);
        int[][] solution = SudokuLogic.parseMatrix(board.solutionMatrix);

        assertValidSolution(solution);

        int clueCount = 0;
        int blankCount = 0;
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                if (puzzle[row][col] == 0) {
                    blankCount++;
                    continue;
                }
                clueCount++;
                assertEquals(solution[row][col], puzzle[row][col]);
            }
        }

        assertTrue("Puzzle should contain blanks", blankCount > 0);
        assertTrue("Easy puzzle should keep at least 40 clues", clueCount >= 40);
    }

    private void assertValidSolution(int[][] board) {
        for (int index = 0; index < 9; index++) {
            assertTrue(isValidGroup(readRow(board, index)));
            assertTrue(isValidGroup(readColumn(board, index)));
        }

        for (int row = 0; row < 9; row += 3) {
            for (int col = 0; col < 9; col += 3) {
                assertTrue(isValidGroup(readBox(board, row, col)));
            }
        }
    }

    private int[] readRow(int[][] board, int row) {
        int[] values = new int[9];
        System.arraycopy(board[row], 0, values, 0, 9);
        return values;
    }

    private int[] readColumn(int[][] board, int col) {
        int[] values = new int[9];
        for (int row = 0; row < 9; row++) {
            values[row] = board[row][col];
        }
        return values;
    }

    private int[] readBox(int[][] board, int startRow, int startCol) {
        int[] values = new int[9];
        int index = 0;
        for (int row = startRow; row < startRow + 3; row++) {
            for (int col = startCol; col < startCol + 3; col++) {
                values[index++] = board[row][col];
            }
        }
        return values;
    }

    private boolean isValidGroup(int[] values) {
        boolean[] seen = new boolean[10];
        for (int value : values) {
            if (value < 1 || value > 9 || seen[value]) {
                return false;
            }
            seen[value] = true;
        }
        return true;
    }
}
