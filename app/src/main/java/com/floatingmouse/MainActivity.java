package com.floatingmouse;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startBtn = findViewById(R.id.startBtn);
        Button stopBtn  = findViewById(R.id.stopBtn);
        TextView info   = findViewById(R.id.info);

        info.setText(
            "HOW TO USE:\n\n" +
            "1. Press START CURSOR\n" +
            "2. Open Dolphin Browser\n" +
            "3. Drag inside the small dark box (bottom-right) to move cursor\n" +
            "4. Tap CLICK button inside the box once to enter click mode\n" +
            "5. You have 2 seconds — tap the screen where the cursor is\n" +
            "6. Flash receives the click\n\n" +
            "Press STOP CURSOR to remove the overlay."
        );

        startBtn.setOnClickListener(v ->
            startService(new Intent(this, FloatingCursorService.class))
        );

        stopBtn.setOnClickListener(v ->
            stopService(new Intent(this, FloatingCursorService.class))
        );
    }
}
