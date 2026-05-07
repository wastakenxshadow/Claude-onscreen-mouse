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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.GestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingCursorService extends Service {

    private WindowManager wm;

    // Cursor overlay — full screen, non-touchable, just draws the arrow
    private CursorView cursorView;
    private WindowManager.LayoutParams cursorParams;

    // Touchpad overlay — small box in bottom-right corner
    private LinearLayout touchpadLayout;
    private WindowManager.LayoutParams touchpadParams;

    // Countdown label shown on cursor during click mode
    private TextView countdownLabel;
    private WindowManager.LayoutParams countdownParams;

    private float cursorX, cursorY;
    private float lastX, lastY;
    private static final float SENSITIVITY = 2.5f;

    private Handler handler = new Handler();
    private boolean clickModeActive = false;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);

        // Start cursor in centre of screen
        cursorX = dm.widthPixels  / 2f;
        cursorY = dm.heightPixels / 2f;

        buildCursorOverlay();
        buildTouchpadOverlay();
    }

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

        // Label
        TextView label = new TextView(this);
        label.setText("TOUCHPAD");
        label.setTextColor(Color.LTGRAY);
        label.setTextSize(9f);
        label.setGravity(Gravity.CENTER);
        touchpadLayout.addView(label);

        // Drag area
        View dragArea = new View(this);
        dragArea.setBackgroundColor(0xFF222222);
        LinearLayout.LayoutParams dragLp = new LinearLayout.LayoutParams(180, 180);
        dragArea.setLayoutParams(dragLp);

        GestureDetector gd = new GestureDetector(this,
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                        float distX, float distY) {
                    DisplayMetrics dm = new DisplayMetrics();
                    wm.getDefaultDisplay().getMetrics(dm);
                    cursorX = clamp(cursorX - distX * SENSITIVITY, 0, dm.widthPixels);
                    cursorY = clamp(cursorY - distY * SENSITIVITY, 0, dm.heightPixels);
                    cursorView.invalidate();
                    return true;
                }
            });

        dragArea.setOnTouchListener((v, event) -> {
            gd.onTouchEvent(event);
            return true;
        });

        touchpadLayout.addView(dragArea);

        // Click button
        Button clickBtn = new Button(this);
        clickBtn.setText("CLICK");
        clickBtn.setTextSize(11f);
        clickBtn.setOnClickListener(v -> startClickMode());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnLp.topMargin = 6;
        clickBtn.setLayoutParams(btnLp);
        touchpadLayout.addView(clickBtn);

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

    // ── Click mode ────────────────────────────────────────────────────────────

    private void startClickMode() {
        if (clickModeActive) return;
        clickModeActive = true;

        // Show countdown on cursor
        cursorView.setCountdown(3);
        handler.postDelayed(() -> cursorView.setCountdown(2), 700);
        handler.postDelayed(() -> cursorView.setCountdown(1), 1400);
        handler.postDelayed(() -> {
            cursorView.setCountdown(0);
            // Hide touchpad so user can tap through
            touchpadParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            wm.updateViewLayout(touchpadLayout, touchpadParams);
            touchpadLayout.setAlpha(0.1f);
        }, 2000);

        // Restore after 4 seconds
        handler.postDelayed(() -> {
            touchpadParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            wm.updateViewLayout(touchpadLayout, touchpadParams);
            touchpadLayout.setAlpha(1f);
            clickModeActive = false;
        }, 4000);
    }

    // ── Cursor view ───────────────────────────────────────────────────────────

    class CursorView extends View {
        private Paint fillPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint outPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint textPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int countdown    = -1;

        CursorView() {
            super(FloatingCursorService.this);
            outPaint.setStyle(Paint.Style.STROKE);
            outPaint.setColor(Color.BLACK);
            outPaint.setStrokeWidth(2f);

            textPaint.setColor(Color.YELLOW);
            textPaint.setTextSize(48f);
            textPaint.setFakeBoldText(true);
        }

        void setCountdown(int n) {
            countdown = n;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float x = cursorX;
            float y = cursorY;

            // Arrow shape
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
            fillPaint.setColor(0x66000000);
            canvas.save();
            canvas.translate(3, 3);
            canvas.drawPath(arrow, fillPaint);
            canvas.restore();

            // Fill — yellow during countdown, white normally
            fillPaint.setColor(countdown > 0 ? Color.YELLOW : Color.WHITE);
            canvas.drawPath(arrow, fillPaint);
            canvas.drawPath(arrow, outPaint);

            // Countdown number
            if (countdown > 0) {
                canvas.drawText(String.valueOf(countdown), x + 28, y - 10, textPaint);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (cursorView    != null) wm.removeView(cursorView);
        if (touchpadLayout != null) wm.removeView(touchpadLayout);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
