package com.example.gamehub.games.sudoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class SudokuGridView extends View {
    public interface OnBoardChangedListener {
        void onCellSelected(int row, int col, boolean editable);

        void onBoardChanged(int[][] board);
    }

    private final Paint cellFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fixedTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint editableTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint noteTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedCellBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Purple circle paints for selected cell
    private final Paint selectedCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedCircleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    
    // Highlight for cells with the same number
    private final Paint sameValueHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    
    // Highlight for row and column of selected cell
    private final Paint crossHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF rect = new RectF();

    private int[][] initialBoard = new int[9][9];
    private int[][] currentBoard = new int[9][9];
    private final int[][] notes = new int[9][9]; // Bitmask for numbers 1-9
    
    private int selectedRow = -1;
    private int selectedCol = -1;
    private float cellSize;
    private OnBoardChangedListener onBoardChangedListener;
    private boolean notesMode = false;

    public SudokuGridView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        cellFillPaint.setColor(Color.WHITE);
        cellFillPaint.setStyle(Paint.Style.FILL);
        
        // Darker and thicker border for individual cells
        cellBorderPaint.setColor(Color.parseColor("#B0C4D6"));
        cellBorderPaint.setStyle(Paint.Style.STROKE);
        cellBorderPaint.setStrokeWidth(2.0f);
        
        fixedTextPaint.setColor(Color.parseColor("#1F2A37"));
        fixedTextPaint.setTextAlign(Paint.Align.CENTER);
        fixedTextPaint.setTextSize(36f);
        
        editableTextPaint.setColor(Color.parseColor("#3E7DD9"));
        editableTextPaint.setTextAlign(Paint.Align.CENTER);
        editableTextPaint.setTextSize(36f);

        noteTextPaint.setColor(Color.parseColor("#5E7083")); // Grey for notes
        noteTextPaint.setTextAlign(Paint.Align.CENTER);
        noteTextPaint.setTextSize(12f);
        
        // Bold brand blue for 3x3 dividers
        linePaint.setColor(Color.parseColor("#4A90E2"));
        linePaint.setStrokeWidth(4.5f);

        // Light purple tint for selected cell background
        selectedPaint.setColor(Color.parseColor("#D4D0FF"));
        selectedPaint.setStyle(Paint.Style.FILL);

        // Bold purple border for selected cell
        selectedCellBorderPaint.setColor(Color.parseColor("#5B4FCF"));
        selectedCellBorderPaint.setStyle(Paint.Style.STROKE);
        selectedCellBorderPaint.setStrokeWidth(4.0f);

        // Purple filled circle for selected cell with a number
        selectedCirclePaint.setColor(Color.parseColor("#5B4FCF"));
        selectedCirclePaint.setStyle(Paint.Style.FILL);

        // White text on top of the purple circle
        selectedCircleTextPaint.setColor(Color.WHITE);
        selectedCircleTextPaint.setTextAlign(Paint.Align.CENTER);
        selectedCircleTextPaint.setTextSize(36f);

        // Deep purple highlight for matching numbers
        sameValueHighlightPaint.setColor(Color.parseColor("#B9B0FF"));
        sameValueHighlightPaint.setStyle(Paint.Style.FILL);
        
        // Soft purple for row/column highlighting
        crossHighlightPaint.setColor(Color.parseColor("#EDE9FF"));
        crossHighlightPaint.setStyle(Paint.Style.FILL);

        setWillNotDraw(false);
    }

    public void setBoard(int[][] initialBoard, int[][] currentBoard) {
        this.initialBoard = SudokuLogic.copyMatrix(initialBoard);
        this.currentBoard = SudokuLogic.copyMatrix(currentBoard);
        // Clear notes
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                notes[i][j] = 0;
            }
        }
        selectedRow = -1;
        selectedCol = -1;
        invalidate();
    }

    public void setOnBoardChangedListener(OnBoardChangedListener listener) {
        this.onBoardChangedListener = listener;
    }

    public int[][] getCurrentBoard() {
        return SudokuLogic.copyMatrix(currentBoard);
    }

    public void setNotesMode(boolean enabled) {
        this.notesMode = enabled;
    }

    public int getSelectedRow() {
        return selectedRow;
    }

    public int getSelectedCol() {
        return selectedCol;
    }

    public void setSelectedValue(int value) {
        if (selectedRow < 0 || selectedCol < 0 || initialBoard[selectedRow][selectedCol] != 0) {
            return;
        }

        if (notesMode && value != 0) {
            // Toggle note
            int bit = 1 << (value - 1);
            if ((notes[selectedRow][selectedCol] & bit) != 0) {
                notes[selectedRow][selectedCol] &= ~bit;
            } else {
                notes[selectedRow][selectedCol] |= bit;
                // If we set a note, clear the main value
                currentBoard[selectedRow][selectedCol] = 0;
            }
        } else {
            // Set final value
            currentBoard[selectedRow][selectedCol] = value;
            // Clear notes if a final value is set
            if (value != 0) {
                notes[selectedRow][selectedCol] = 0;
            }
        }
        
        invalidate();
        if (onBoardChangedListener != null) {
            onBoardChangedListener.onBoardChanged(getCurrentBoard());
        }
    }

    public void setCellValue(int row, int col, int value) {
        if (row < 0 || row >= 9 || col < 0 || col >= 9) return;
        currentBoard[row][col] = value;
        if (value != 0) {
            notes[row][col] = 0;
        }
        invalidate();
        if (onBoardChangedListener != null) {
            onBoardChangedListener.onBoardChanged(getCurrentBoard());
        }
    }

    public void clearSelectedCell() {
        if (selectedRow >= 0 && selectedCol >= 0 && initialBoard[selectedRow][selectedCol] == 0) {
            currentBoard[selectedRow][selectedCol] = 0;
            notes[selectedRow][selectedCol] = 0;
            invalidate();
            if (onBoardChangedListener != null) {
                onBoardChangedListener.onBoardChanged(getCurrentBoard());
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int measuredWidth = View.MeasureSpec.getSize(widthMeasureSpec);
        int measuredHeight = View.MeasureSpec.getSize(heightMeasureSpec);
        int size = measuredHeight == 0 ? measuredWidth : Math.min(measuredWidth, measuredHeight);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        cellSize = getWidth() / 9f;
        float textSize = cellSize * 0.52f;
        float noteSize = cellSize / 3f;
        float cornerRadius = 0f; // Changed from cellSize * 0.28f to 0f for square borders
        float inset = cellSize * 0.06f;
        
        fixedTextPaint.setTextSize(textSize);
        editableTextPaint.setTextSize(textSize);
        selectedCircleTextPaint.setTextSize(textSize);
        noteTextPaint.setTextSize(cellSize * 0.25f);
        
        float textYOffset = (fixedTextPaint.descent() + fixedTextPaint.ascent()) / 2f;
        float noteYOffset = (noteTextPaint.descent() + noteTextPaint.ascent()) / 2f;

        int selectedValue = (selectedRow >= 0 && selectedCol >= 0) ? currentBoard[selectedRow][selectedCol] : 0;

        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                float left = col * cellSize;
                float top = row * cellSize;
                rect.set(left + inset, top + inset, left + cellSize - inset, top + cellSize - inset);

                boolean isSelected = (row == selectedRow && col == selectedCol);
                boolean isInSameRowOrCol = (selectedRow != -1 && (row == selectedRow || col == selectedCol));
                int value = currentBoard[row][col];
                boolean isSameValue = (selectedValue != 0 && value == selectedValue && !isSelected);

                if (isSelected && value != 0) {
                    // Draw purple filled square for selected cell with a number
                    canvas.drawRect(rect, selectedCirclePaint);
                    // Also draw the bold border for selection clarity
                    canvas.drawRect(rect, selectedCellBorderPaint);
                    
                    canvas.drawText(
                            String.valueOf(value),
                            left + cellSize / 2f,
                            top + cellSize / 2f - textYOffset,
                            selectedCircleTextPaint
                    );
                } else {
                    // Normal cell background or highlights
                    if (isSelected) {
                        canvas.drawRect(rect, selectedPaint);
                    } else if (isSameValue) {
                        canvas.drawRect(rect, sameValueHighlightPaint);
                    } else if (isInSameRowOrCol) {
                        canvas.drawRect(rect, crossHighlightPaint);
                    } else {
                        canvas.drawRect(rect, cellFillPaint);
                    }
                    
                    if (isSelected) {
                        canvas.drawRect(rect, selectedCellBorderPaint);
                    } else {
                        canvas.drawRect(rect, cellBorderPaint);
                    }

                    if (value != 0) {
                        Paint textPaint = initialBoard[row][col] != 0 ? fixedTextPaint : editableTextPaint;
                        canvas.drawText(
                                String.valueOf(value),
                                left + cellSize / 2f,
                                top + cellSize / 2f - textYOffset,
                                textPaint
                        );
                    } else if (notes[row][col] != 0) {
                        // Draw notes
                        for (int n = 0; n < 9; n++) {
                            if ((notes[row][col] & (1 << n)) != 0) {
                                float noteX = left + (n % 3) * noteSize + noteSize / 2f;
                                float noteY = top + (n / 3) * noteSize + noteSize / 2f - noteYOffset;
                                canvas.drawText(String.valueOf(n + 1), noteX, noteY, noteTextPaint);
                            }
                        }
                    }
                }
            }
        }

        // Draw bold 3x3 box dividers
        for (int i = 0; i <= 9; i++) {
            if (i % 3 != 0 || i == 0) {
                continue;
            }
            canvas.drawLine(i * cellSize, 0, i * cellSize, getHeight(), linePaint);
            canvas.drawLine(0, i * cellSize, getWidth(), i * cellSize, linePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(event);
        }
        int row = Math.min(8, (int) (event.getY() / cellSize));
        int col = Math.min(8, (int) (event.getX() / cellSize));
        boolean editable = initialBoard[row][col] == 0;
        
        selectedRow = row;
        selectedCol = col;
        invalidate();
        if (onBoardChangedListener != null) {
            onBoardChangedListener.onCellSelected(row, col, editable);
        }
        return true;
    }
}
