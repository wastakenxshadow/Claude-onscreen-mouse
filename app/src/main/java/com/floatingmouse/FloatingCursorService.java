package com.floatingmouse;

import android.app.Service;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingCursorService extends Service {

    private WindowManager wm;

    // Full-screen non-touchable cursor drawing layer
    private CursorView cursorView;
    private WindowManager.LayoutParams cursorParams;

    // Touchpad box — bottom-right corner
    private LinearLayout touchpadLayout;
    private WindowManager.LayoutParams touchpadParams;

    // Small END DRAG button shown during drag mode
    private Button endDragBtn;
    private WindowManager.LayoutParams endDragParams;

    private float cursorX, cursorY;
    private Handler handler = new Handler();

    private boolean clickModeActive = false;
    private boolean dragModeActive  = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        cursorX = dm.widthPixels  / 2f;
        cursorY = dm.heightPixels / 2f;

        buildCursorOverlay();
        buildTouchpadOverlay();
        buildEndDragButton();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (cursorView     != null) wm.removeView(cursorView);
        if (touchpadLayout != null) wm.removeView(touchpadLayout);
        if (endDragBtn     != null) wm.removeView(endDragBtn);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── Cursor overlay ────────────────────────────────────────────────────────

    private void buildCursorOverlay() {
        cursorView = new CursorView();
        cursorParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        wm.addView(cursorView, cursorParams);
    }

    // ── Touchpad overlay ──────────────────────────────────────────────────────

    private void buildTouchpadOverlay() {
        touchpadLayout = new LinearLayout(this);
        touchpadLayout.setOrientation(LinearLayout.VERTICAL);
        touchpadLayout.setBackgroundColor(0xCC111111);
        touchpadLayout.setPadding(8, 8, 8, 8);

        TextView label = new TextView(this);
        label.setText("TOUCHPAD");
        label.setTextColor(Color.LTGRAY);
        label.setTextSize(9f);
        label.setGravity(Gravity.CENTER);
        touchpadLayout.addView(label);

        // Drag area — finger movement moves cursor
        View dragArea = new View(this);
        dragArea.setBackgroundColor(0xFF222222);
        dragArea.setLayoutParams(new LinearLayout.LayoutParams(180, 180));

        GestureDetector gd = new GestureDetector(this,
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                        float distX, float distY) {
                    DisplayMetrics dm = new DisplayMetrics();
                    wm.getDefaultDisplay().getMetrics(dm);
                    cursorX = clamp(cursorX - distX * 2.5f, 0, dm.widthPixels);
                    cursorY = clamp(cursorY - distY * 2.5f, 0, dm.heightPixels);
                    cursorView.invalidate();
                    return true;
                }
            });

        dragArea.setOnTouchListener((v, event) -> {
            gd.onTouchEvent(event);
            return true;
        });

        touchpadLayout.addView(dragArea);

        // Button row: CLICK | DRAG
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowLp.topMargin = 6;
        btnRow.setLayoutParams(rowLp);

        Button clickBtn = new Button(this);
        clickBtn.setText("CLICK");
        clickBtn.setTextSize(10f);
        LinearLayout.LayoutParams clkLp =
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        clkLp.rightMargin = 4;
        clickBtn.setLayoutParams(clkLp);
        clickBtn.setOnClickListener(v -> startClickMode());
        btnRow.addView(clickBtn);

        Button dragBtn = new Button(this);
        dragBtn.setText("DRAG");
        dragBtn.setTextSize(10f);
        dragBtn.setLayoutParams(
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        dragBtn.setOnClickListener(v -> startDragMode());
        btnRow.addView(dragBtn);

        touchpadLayout.addView(btnRow);

        touchpadParams = new WindowManager.LayoutParams(
            220, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        touchpadParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        touchpadParams.x = 10;
        touchpadParams.y = 10;

        wm.addView(touchpadLayout, touchpadParams);
    }

    // ── END DRAG button ───────────────────────────────────────────────────────

    private void buildEndDragButton() {
        endDragBtn = new Button(this);
        endDragBtn.setText("END DRAG");
        endDragBtn.setTextSize(11f);
        endDragBtn.setBackgroundColor(0xDDDD3333);
        endDragBtn.setTextColor(Color.WHITE);
        endDragBtn.setOnClickListener(v -> endDragMode());

        endDragParams = new WindowManager.LayoutParams(
            1, 1,   // starts invisible (1x1)
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        );
        endDragParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        endDragParams.y = 40;

        wm.addView(endDragBtn, endDragParams);
    }

    // ── Click mode ────────────────────────────────────────────────────────────

    private void startClickMode() {
        if (clickModeActive || dragModeActive) return;
        clickModeActive = true;

        cursorView.setCountdown(3);
        handler.postDelayed(() -> cursorView.setCountdown(2), 700);
        handler.postDelayed(() -> cursorView.setCountdown(1), 1400);
        handler.postDelayed(() -> {
            cursorView.setCountdown(0);
            setTouchpadPassthrough(true);
        }, 2000);

        handler.postDelayed(() -> {
            setTouchpadPassthrough(false);
            clickModeActive = false;
        }, 4000);
    }

    // ── Drag mode ─────────────────────────────────────────────────────────────

    private void startDragMode() {
        if (dragModeActive || clickModeActive) return;
        dragModeActive = true;
        cursorView.setDragging(true);

        // Hide touchpad entirely, open screen for real drag
        setTouchpadPassthrough(true);
        touchpadLayout.setAlpha(0f);

        // Show END DRAG button
        endDragParams.width  = WindowManager.LayoutParams.WRAP_CONTENT;
        endDragParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        endDragParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        wm.updateViewLayout(endDragBtn, endDragParams);
    }

    private void endDragMode() {
        if (!dragModeActive) return;
        dragModeActive = false;
        cursorView.setDragging(false);

        // Restore touchpad
        setTouchpadPassthrough(false);
        touchpadLayout.setAlpha(1f);

        // Hide END DRAG button
        endDragParams.width  = 1;
        endDragParams.height = 1;
        endDragParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        wm.updateViewLayout(endDragBtn, endDragParams);
    }

    // ── Shared helper ─────────────────────────────────────────────────────────

    private void setTouchpadPassthrough(boolean on) {
        if (on) {
            touchpadParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            touchpadParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            touchpadLayout.setAlpha(1f);
        }
        wm.updateViewLayout(touchpadLayout, touchpadParams);
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    // ── Cursor drawing ────────────────────────────────────────────────────────

    class CursorView extends View {
        private Paint fill    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint text    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int     countdown = -1;
        private boolean dragging  = false;

        CursorView() {
            super(FloatingCursorService.this);
            outline.setStyle(Paint.Style.STROKE);
            outline.setColor(Color.BLACK);
            outline.setStrokeWidth(2f);
            text.setColor(Color.YELLOW);
            text.setTextSize(48f);
            text.setFakeBoldText(true);
        }

        void setCountdown(int n)    { countdown = n; invalidate(); }
        void setDragging(boolean d) { dragging  = d; invalidate(); }

        @Override
        protected void onDraw(Canvas canvas) {
            float x = cursorX, y = cursorY;

            Path arrow = new Path();
            arrow.moveTo(x,      y);
            arrow.lineTo(x + 22, y + 32);
            arrow.lineTo(x + 9,  y + 26);
            arrow.lineTo(x + 14, y + 48);
            arrow.lineTo(x + 6,  y + 50);
            arrow.lineTo(x + 1,  y + 28);
            arrow.lineTo(x - 9,  y + 37);
            arrow.close();

            // Shadow
            fill.setColor(0x66000000);
            canvas.save();
            canvas.translate(3, 3);
            canvas.drawPath(arrow, fill);
            canvas.restore();

            // Colour by state
            if (dragging)          fill.setColor(0xFFFF8800); // orange = dragging
            else if (countdown > 0) fill.setColor(Color.YELLOW); // yellow = about to click
            else                   fill.setColor(Color.WHITE);   // white = idle

            canvas.drawPath(arrow, fill);
            canvas.drawPath(arrow, outline);

            if (countdown > 0) {
                canvas.drawText(String.valueOf(countdown), x + 28, y - 10, text);
            }

            if (dragging) {
                text.setTextSize(26f);
                text.setColor(0xFFFF8800);
                canvas.drawText("DRAGGING", x - 14, y + 68, text);
                text.setTextSize(48f);
                text.setColor(Color.YELLOW);
            }
        }
    }
}
