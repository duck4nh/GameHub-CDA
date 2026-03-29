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
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedCellBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Purple circle paints for selected cell
    private final Paint selectedCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedCircleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    
    // Highlight for cells with the same number
    private final Paint sameValueHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF rect = new RectF();

    private int[][] initialBoard = new int[9][9];
    private int[][] currentBoard = new int[9][9];
    private int selectedRow = -1;
    private int selectedCol = -1;
    private float cellSize;
    private OnBoardChangedListener onBoardChangedListener;

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
        
        // Bold brand blue for 3x3 dividers
        linePaint.setColor(Color.parseColor("#4A90E2"));
        linePaint.setStrokeWidth(4.5f);

        // Very light purple tint for selected but empty cells
        selectedPaint.setColor(Color.parseColor("#F2F0FF"));
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

        // Darker purple highlight for matching numbers
        sameValueHighlightPaint.setColor(Color.parseColor("#D1C9FF"));
        sameValueHighlightPaint.setStyle(Paint.Style.FILL);

        setWillNotDraw(false);
    }

    public void setBoard(int[][] initialBoard, int[][] currentBoard) {
        this.initialBoard = SudokuLogic.copyMatrix(initialBoard);
        this.currentBoard = SudokuLogic.copyMatrix(currentBoard);
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

    public void setSelectedValue(int value) {
        if (selectedRow < 0 || selectedCol < 0 || initialBoard[selectedRow][selectedCol] != 0) {
            return;
        }
        currentBoard[selectedRow][selectedCol] = value;
        invalidate();
        if (onBoardChangedListener != null) {
            onBoardChangedListener.onBoardChanged(getCurrentBoard());
        }
    }

    public void setCellValue(int row, int col, int value) {
        if (row < 0 || row >= 9 || col < 0 || col >= 9) return;
        currentBoard[row][col] = value;
        invalidate();
        if (onBoardChangedListener != null) {
            onBoardChangedListener.onBoardChanged(getCurrentBoard());
        }
    }

    public void clearSelectedCell() {
        setSelectedValue(0);
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
        float cornerRadius = cellSize * 0.28f;
        float inset = cellSize * 0.06f;
        fixedTextPaint.setTextSize(textSize);
        editableTextPaint.setTextSize(textSize);
        selectedCircleTextPaint.setTextSize(textSize);
        float textYOffset = (fixedTextPaint.descent() + fixedTextPaint.ascent()) / 2f;

        int selectedValue = (selectedRow >= 0 && selectedCol >= 0) ? currentBoard[selectedRow][selectedCol] : 0;

        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                float left = col * cellSize;
                float top = row * cellSize;
                rect.set(left + inset, top + inset, left + cellSize - inset, top + cellSize - inset);

                boolean isSelected = (row == selectedRow && col == selectedCol);
                int value = currentBoard[row][col];
                boolean isSameValue = (selectedValue != 0 && value == selectedValue && !isSelected);

                if (isSelected && value != 0) {
                    // Draw purple filled circle for selected cell with a number
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, selectedCirclePaint);
                    // Also draw the bold border for selection clarity
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, selectedCellBorderPaint);
                    
                    // White text on top
                    canvas.drawText(
                            String.valueOf(value),
                            left + cellSize / 2f,
                            top + cellSize / 2f - textYOffset,
                            selectedCircleTextPaint
                    );
                } else {
                    // Normal cell background or highlights
                    if (isSelected) {
                        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, selectedPaint);
                    } else if (isSameValue) {
                        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, sameValueHighlightPaint);
                    } else {
                        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellFillPaint);
                    }
                    
                    // Draw the appropriate border
                    if (isSelected) {
                        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, selectedCellBorderPaint);
                    } else {
                        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellBorderPaint);
                    }

                    if (value != 0) {
                        Paint textPaint = initialBoard[row][col] != 0 ? fixedTextPaint : editableTextPaint;
                        canvas.drawText(
                                String.valueOf(value),
                                left + cellSize / 2f,
                                top + cellSize / 2f - textYOffset,
                                textPaint
                        );
                    }
                }
            }
        }

        // Draw bold 3x3 box dividers
        for (int i = 0; i <= 9; i++) {
            if (i % 3 != 0 || i == 0) {
                continue;
            }
            // Use the pre-configured linePaint
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
        
        if (editable && row == selectedRow && col == selectedCol && currentBoard[row][col] != 0) {
            currentBoard[row][col] = 0;
            if (onBoardChangedListener != null) {
                onBoardChangedListener.onBoardChanged(getCurrentBoard());
            }
        }
        selectedRow = row;
        selectedCol = col;
        invalidate();
        if (onBoardChangedListener != null) {
            onBoardChangedListener.onCellSelected(row, col, editable);
        }
        return true;
    }
}
