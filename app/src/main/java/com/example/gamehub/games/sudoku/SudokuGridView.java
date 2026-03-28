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
        cellBorderPaint.setColor(Color.parseColor("#D9E3EC"));
        cellBorderPaint.setStyle(Paint.Style.STROKE);
        cellBorderPaint.setStrokeWidth(1.5f);
        fixedTextPaint.setColor(Color.parseColor("#1F2A37"));
        fixedTextPaint.setTextAlign(Paint.Align.CENTER);
        fixedTextPaint.setTextSize(36f);
        editableTextPaint.setColor(Color.parseColor("#3E7DD9"));
        editableTextPaint.setTextAlign(Paint.Align.CENTER);
        editableTextPaint.setTextSize(36f);
        linePaint.setColor(Color.parseColor("#63A4EE"));
        selectedPaint.setColor(Color.parseColor("#EEF6FF"));
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
        float textYOffset = (fixedTextPaint.descent() + fixedTextPaint.ascent()) / 2f;

        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                float left = col * cellSize;
                float top = row * cellSize;
                rect.set(left + inset, top + inset, left + cellSize - inset, top + cellSize - inset);
                if (row == selectedRow && col == selectedCol) {
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, selectedPaint);
                } else {
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellFillPaint);
                }
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellBorderPaint);

                int value = currentBoard[row][col];
                if (value != 0) {
                    Paint textPaint = initialBoard[row][col] != 0 ? fixedTextPaint : editableTextPaint;
                    canvas.drawText(String.valueOf(value), left + cellSize / 2f, top + cellSize / 2f - textYOffset, textPaint);
                }
            }
        }

        for (int i = 0; i <= 9; i++) {
            if (i % 3 != 0 || i == 0) {
                continue;
            }
            linePaint.setStrokeWidth(2.5f);
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
