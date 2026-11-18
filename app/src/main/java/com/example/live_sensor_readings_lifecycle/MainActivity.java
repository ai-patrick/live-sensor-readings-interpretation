package com.example.live_sensor_readings_lifecycle;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "LiveSensorLab06";
    private static final float EMA_ALPHA = 0.3f;
    private static final int PROGRESS_MAX = 10000; // 10k lux max for progress bar

    private SensorManager sensorManager;
    private Sensor lightSensor;
    private SensorViewModel viewModel;

    // UI Elements
    private TextView tvSensorName, tvSensorUnit, tvSensorStatus, tvDataSource;
    private TextView tvRawValue, tvSmoothedValue, tvTimestamp, tvListenerState, tvLifecyclePhase;
    private TextView tvAnalysis, tvTechnicalNotes;
    private TextView tvObs1Raw, tvObs1Smooth, tvObs1Time;
    private TextView tvObs2Raw, tvObs2Smooth, tvObs2Time;
    private TextView tvObs3Raw, tvObs3Smooth, tvObs3Time;
    private Button btnRegister, btnUnregister;
    private Button btnRecordObs1, btnRecordObs2, btnRecordObs3;
    private Button btnGenerateAnalysis;
    private android.widget.ProgressBar progressLight;

    private boolean isListenerRegistered = false;
    private float smoothedValue = 0f;
    private boolean hasFirstReading = false;

    // Observation storage
    private static class Observation {
        float raw;
        float smoothed;
        String timestamp;
    }

    private Observation obs1 = null, obs2 = null, obs3 = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        updateLifecyclePhase("onCreate");

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(SensorViewModel.class);

        // Initialize SensorManager
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        initViews();
        setupClickListeners();
        checkSensorAvailability();
        restoreState(savedInstanceState);
    }

    private void initViews() {
        tvSensorName = findViewById(R.id.tvSensorName);
        tvSensorUnit = findViewById(R.id.tvSensorUnit);
        tvSensorStatus = findViewById(R.id.tvSensorStatus);
        tvDataSource = findViewById(R.id.tvDataSource);
        tvRawValue = findViewById(R.id.tvRawValue);
        tvSmoothedValue = findViewById(R.id.tvSmoothedValue);
        tvTimestamp = findViewById(R.id.tvTimestamp);
        tvListenerState = findViewById(R.id.tvListenerState);
        tvLifecyclePhase = findViewById(R.id.tvLifecyclePhase);
        tvAnalysis = findViewById(R.id.tvAnalysis);
        tvTechnicalNotes = findViewById(R.id.tvTechnicalNotes);

        tvObs1Raw = findViewById(R.id.tvObs1Raw);
        tvObs1Smooth = findViewById(R.id.tvObs1Smooth);
        tvObs1Time = findViewById(R.id.tvObs1Time);
        tvObs2Raw = findViewById(R.id.tvObs2Raw);
        tvObs2Smooth = findViewById(R.id.tvObs2Smooth);
        tvObs2Time = findViewById(R.id.tvObs2Time);
        tvObs3Raw = findViewById(R.id.tvObs3Raw);
        tvObs3Smooth = findViewById(R.id.tvObs3Smooth);
        tvObs3Time = findViewById(R.id.tvObs3Time);

        btnRegister = findViewById(R.id.btnRegister);
        btnUnregister = findViewById(R.id.btnUnregister);
        btnRecordObs1 = findViewById(R.id.btnRecordObs1);
        btnRecordObs2 = findViewById(R.id.btnRecordObs2);
        btnRecordObs3 = findViewById(R.id.btnRecordObs3);
        btnGenerateAnalysis = findViewById(R.id.btnGenerateAnalysis);
        progressLight = findViewById(R.id.progressLight);
    }

    private void setupClickListeners() {
        btnRegister.setOnClickListener(v -> registerListener());
        btnUnregister.setOnClickListener(v -> unregisterListener());

        btnRecordObs1.setOnClickListener(v -> recordObservation(1));
        btnRecordObs2.setOnClickListener(v -> recordObservation(2));
        btnRecordObs3.setOnClickListener(v -> recordObservation(3));

        btnGenerateAnalysis.setOnClickListener(v -> generateAnalysis());
    }

    private void checkSensorAvailability() {
        if (lightSensor == null) {
            tvSensorStatus.setText("Unavailable — No light sensor on device");
            tvSensorStatus.setTextColor(0xFFB00020);
            tvDataSource.setText("N/A");
            tvDataSource.setTextColor(0xFFB00020);
            btnRegister.setEnabled(false);
            btnUnregister.setEnabled(false);
            btnRecordObs1.setEnabled(false);
            btnRecordObs2.setEnabled(false);
            btnRecordObs3.setEnabled(false);
            Toast.makeText(this, "Light sensor not available on this device", Toast.LENGTH_LONG).show();
            Log.w(TAG, "Light sensor not available");
        } else {
            tvSensorStatus.setText("Available");
            tvSensorStatus.setTextColor(0xFF174d43);
            tvSensorName.setText(lightSensor.getName() + " (v" + lightSensor.getVersion() + ")");
            tvSensorUnit.setText("lux");
            tvDataSource.setText("Measured");
            tvDataSource.setTextColor(0xFF174d43);
            Log.i(TAG, "Light sensor found: " + lightSensor.getName() + ", vendor: " + lightSensor.getVendor() +
                    ", maxRange: " + lightSensor.getMaximumRange() + ", resolution: " + lightSensor.getResolution() +
                    ", power: " + lightSensor.getPower() + "mA");
        }
    }

    private void registerListener() {
        if (lightSensor == null) {
            Toast.makeText(this, "No light sensor available", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isListenerRegistered) {
            Toast.makeText(this, "Listener already registered", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean success = sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        if (success) {
            isListenerRegistered = true;
            updateListenerStateUI();
            Log.i(TAG, "Listener registered with SENSOR_DELAY_NORMAL");
            Toast.makeText(this, "Listener registered (SENSOR_DELAY_NORMAL ~200ms)", Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "Failed to register listener");
            Toast.makeText(this, "Failed to register listener", Toast.LENGTH_SHORT).show();
        }
    }

    private void unregisterListener() {
        if (!isListenerRegistered) {
            Toast.makeText(this, "Listener not registered", Toast.LENGTH_SHORT).show();
            return;
        }

        sensorManager.unregisterListener(this);
        isListenerRegistered = false;
        updateListenerStateUI();
        Log.i(TAG, "Listener unregistered");
        Toast.makeText(this, "Listener unregistered", Toast.LENGTH_SHORT).show();
    }

    private void updateListenerStateUI() {
        if (isListenerRegistered) {
            tvListenerState.setText("Registered (SENSOR_DELAY_NORMAL)");
            tvListenerState.setTextColor(0xFF174d43);
            btnRegister.setEnabled(false);
            btnUnregister.setEnabled(true);
        } else {
            tvListenerState.setText("Unregistered");
            tvListenerState.setTextColor(0xFFB00020);
            btnRegister.setEnabled(lightSensor != null);
            btnUnregister.setEnabled(false);
        }
    }

    private void updateLifecyclePhase(String phase) {
        tvLifecyclePhase.setText(phase);
        Log.d(TAG, "Lifecycle: " + phase);
    }

    private void recordObservation(int obsNumber) {
        if (!hasFirstReading) {
            Toast.makeText(this, "No sensor reading yet. Wait for live data.", Toast.LENGTH_SHORT).show();
            return;
        }

        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        Observation obs = new Observation();
        obs.raw = Float.parseFloat(tvRawValue.getText().toString().replace(" lux", ""));
        obs.smoothed = smoothedValue;
        obs.timestamp = timestamp;

        switch (obsNumber) {
            case 1:
                obs1 = obs;
                tvObs1Raw.setText(String.format(Locale.getDefault(), "%.1f", obs.raw));
                tvObs1Smooth.setText(String.format(Locale.getDefault(), "%.1f", obs.smoothed));
                tvObs1Time.setText(timestamp);
                break;
            case 2:
                obs2 = obs;
                tvObs2Raw.setText(String.format(Locale.getDefault(), "%.1f", obs.raw));
                tvObs2Smooth.setText(String.format(Locale.getDefault(), "%.1f", obs.smoothed));
                tvObs2Time.setText(timestamp);
                break;
            case 3:
                obs3 = obs;
                tvObs3Raw.setText(String.format(Locale.getDefault(), "%.1f", obs.raw));
                tvObs3Smooth.setText(String.format(Locale.getDefault(), "%.1f", obs.smoothed));
                tvObs3Time.setText(timestamp);
                break;
        }

        Toast.makeText(this, "Observation " + obsNumber + " recorded", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Obs " + obsNumber + ": raw=" + obs.raw + ", smooth=" + obs.smoothed + " @ " + timestamp);
    }

    private void generateAnalysis() {
        StringBuilder analysis = new StringBuilder();

        if (obs1 == null || obs2 == null || obs3 == null) {
            analysis.append("⚠ Complete all 3 observations first.\n\n");
        } else {
            analysis.append("✅ Three controlled conditions recorded:\n\n");

            analysis.append(String.format(Locale.getDefault(),
                    "1. Covered:       raw=%.1f lux, smooth=%.1f lux @ %s\n",
                    obs1.raw, obs1.smoothed, obs1.timestamp));
            analysis.append(String.format(Locale.getDefault(),
                    "2. Indoor Light:  raw=%.1f lux, smooth=%.1f lux @ %s\n",
                    obs2.raw, obs2.smoothed, obs2.timestamp));
            analysis.append(String.format(Locale.getDefault(),
                    "3. Flashlight:    raw=%.1f lux, smooth=%.1f lux @ %s\n\n",
                    obs3.raw, obs3.smoothed, obs3.timestamp));

            // Pattern analysis
            analysis.append("📊 Pattern Interpretation:\n");
            if (obs1.raw < obs2.raw && obs2.raw < obs3.raw) {
                analysis.append("• Clear monotonic increase: Covered < Indoor < Flashlight ✓\n");
                analysis.append("• This matches expected physics: light sensor responds to photon flux\n");
            } else {
                analysis.append("• Non-monotonic pattern — check for confounding factors:\n");
                analysis.append("  - Sensor angle/orientation changed between readings\n");
                analysis.append("  - Ambient light fluctuations (clouds, auto-brightness)\n");
                analysis.append("  - Sensor warm-up drift (first readings after register)\n");
            }

            // Confounding factors
            analysis.append("\n⚠ Confounding Factors (no calibration claimed):\n");
            analysis.append("• Factory calibration unknown — absolute lux values are approximate\n");
            analysis.append("• Spectral response varies: LED vs incandescent vs sunlight\n");
            analysis.append("• Cosine response error at oblique angles\n");
            analysis.append("• Temperature drift not compensated\n");
            analysis.append("• Device casing may attenuate light differently per angle\n");

            // Sampling & timing
            analysis.append("\n⏱ Sampling & Timing:\n");
            analysis.append("• Requested: SENSOR_DELAY_NORMAL (~200ms, ~5 Hz)\n");
            analysis.append("• Actual rate: OS-dependent, not real-time guaranteed\n");
            analysis.append("• Events batched in background; timestamps = event receipt, not capture\n");
            analysis.append("• No hardware timestamp — cannot measure exact inter-sample interval\n");

            // Battery
            analysis.append("\n🔋 Battery Cost:\n");
            analysis.append("• Continuous NORMAL listening: ~1-3%/hour (vendor-dependent)\n");
            analysis.append("• UI update + EMA compute: negligible additional cost\n");
            analysis.append("• Best practice: unregister in onPause (implemented)\n");

            // Smoothing
            analysis.append("\n📈 Smoothing (EMA α=0.3):\n");
            analysis.append("• Rule: S_t = α·raw_t + (1-α)·S_{t-1}\n");
            analysis.append("• Reduces jitter from quantization & transient shadows\n");
            analysis.append("• Trade-off: lag ~ 1/α samples (~3 samples) behind step changes\n");
            analysis.append("• Raw vs Smoothed comparison shows noise suppression vs responsiveness\n");
        }

        analysis.append("\n📱 Lifecycle Behavior:\n");
        analysis.append("• onResume → registerListener() (visible lifecycle)\n");
        analysis.append("• onPause  → unregisterListener() (prevents background drain)\n");
        analysis.append("• Rotation → ViewModel retains smoothed value & observations\n");
        analysis.append("• Duplicate registration prevented by isListenerRegistered flag\n");

        tvAnalysis.setText(analysis.toString());
    }

    // ===== SensorEventListener =====
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float raw = event.values[0];

            // EMA smoothing
            if (!hasFirstReading) {
                smoothedValue = raw;
                hasFirstReading = true;
            } else {
                smoothedValue = EMA_ALPHA * raw + (1 - EMA_ALPHA) * smoothedValue;
            }

            // Update UI on main thread
            runOnUiThread(() -> {
                tvRawValue.setText(String.format(Locale.getDefault(), "%.1f lux", raw));
                tvSmoothedValue.setText(String.format(Locale.getDefault(), "%.1f lux", smoothedValue));
                tvTimestamp.setText(new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date()));

                // Progress bar (clamped to max)
                int progress = Math.min((int) raw, PROGRESS_MAX);
                progressLight.setProgress(progress);
            });

            // Save to ViewModel for rotation survival
            viewModel.setLastRaw(raw);
            viewModel.setLastSmoothed(smoothedValue);
            viewModel.setHasFirstReading(hasFirstReading);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        String accuracyStr;
        switch (accuracy) {
            case SensorManager.SENSOR_STATUS_UNRELIABLE: accuracyStr = "UNRELIABLE"; break;
            case SensorManager.SENSOR_STATUS_ACCURACY_LOW: accuracyStr = "LOW"; break;
            case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM: accuracyStr = "MEDIUM"; break;
            case SensorManager.SENSOR_STATUS_ACCURACY_HIGH: accuracyStr = "HIGH"; break;
            default: accuracyStr = "UNKNOWN(" + accuracy + ")";
        }
        Log.w(TAG, "Sensor accuracy changed: " + accuracyStr);
        runOnUiThread(() -> {
            if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                tvDataSource.setText("Unreliable");
                tvDataSource.setTextColor(0xFFFF8F00);
            } else {
                tvDataSource.setText("Measured");
                tvDataSource.setTextColor(0xFF174d43);
            }
        });
    }

    // ===== Lifecycle =====
    @Override
    protected void onStart() {
        super.onStart();
        updateLifecyclePhase("onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLifecyclePhase("onResume (visible) — auto-register");
        // Visible lifecycle: register when UI becomes visible
        if (lightSensor != null && !isListenerRegistered) {
            registerListener();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateLifecyclePhase("onPause (hidden) — auto-unregister");
        // Visible lifecycle: unregister when UI no longer visible
        // Prevents background battery drain and duplicate listeners
        if (isListenerRegistered) {
            unregisterListener();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        updateLifecyclePhase("onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        updateLifecyclePhase("onDestroy");
        // Ensure cleanup
        if (isListenerRegistered) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("hasFirstReading", hasFirstReading);
        outState.putFloat("smoothedValue", smoothedValue);
        if (obs1 != null) {
            outState.putFloat("obs1_raw", obs1.raw);
            outState.putFloat("obs1_smooth", obs1.smoothed);
            outState.putString("obs1_time", obs1.timestamp);
        }
        if (obs2 != null) {
            outState.putFloat("obs2_raw", obs2.raw);
            outState.putFloat("obs2_smooth", obs2.smoothed);
            outState.putString("obs2_time", obs2.timestamp);
        }
        if (obs3 != null) {
            outState.putFloat("obs3_raw", obs3.raw);
            outState.putFloat("obs3_smooth", obs3.smoothed);
            outState.putString("obs3_time", obs3.timestamp);
        }
    }

    private void restoreState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            hasFirstReading = savedInstanceState.getBoolean("hasFirstReading", false);
            smoothedValue = savedInstanceState.getFloat("smoothedValue", 0f);

            if (hasFirstReading) {
                float raw = viewModel.getLastRaw();
                tvRawValue.setText(String.format(Locale.getDefault(), "%.1f lux", raw));
                tvSmoothedValue.setText(String.format(Locale.getDefault(), "%.1f lux", smoothedValue));
            }

            // Restore observations
            if (savedInstanceState.containsKey("obs1_raw")) {
                obs1 = new Observation();
                obs1.raw = savedInstanceState.getFloat("obs1_raw");
                obs1.smoothed = savedInstanceState.getFloat("obs1_smooth");
                obs1.timestamp = savedInstanceState.getString("obs1_time");
                tvObs1Raw.setText(String.format(Locale.getDefault(), "%.1f", obs1.raw));
                tvObs1Smooth.setText(String.format(Locale.getDefault(), "%.1f", obs1.smoothed));
                tvObs1Time.setText(obs1.timestamp);
            }
            if (savedInstanceState.containsKey("obs2_raw")) {
                obs2 = new Observation();
                obs2.raw = savedInstanceState.getFloat("obs2_raw");
                obs2.smoothed = savedInstanceState.getFloat("obs2_smooth");
                obs2.timestamp = savedInstanceState.getString("obs2_time");
                tvObs2Raw.setText(String.format(Locale.getDefault(), "%.1f", obs2.raw));
                tvObs2Smooth.setText(String.format(Locale.getDefault(), "%.1f", obs2.smoothed));
                tvObs2Time.setText(obs2.timestamp);
            }
            if (savedInstanceState.containsKey("obs3_raw")) {
                obs3 = new Observation();
                obs3.raw = savedInstanceState.getFloat("obs3_raw");
                obs3.smoothed = savedInstanceState.getFloat("obs3_smooth");
                obs3.timestamp = savedInstanceState.getString("obs3_time");
                tvObs3Raw.setText(String.format(Locale.getDefault(), "%.1f", obs3.raw));
                tvObs3Smooth.setText(String.format(Locale.getDefault(), "%.1f", obs3.smoothed));
                tvObs3Time.setText(obs3.timestamp);
            }
        }
    }

    // ===== ViewModel for rotation survival =====
    public static class SensorViewModel extends ViewModel {
        private float lastRaw = 0f;
        private float lastSmoothed = 0f;
        private boolean hasFirstReading = false;

        public float getLastRaw() { return lastRaw; }
        public void setLastRaw(float v) { lastRaw = v; }
        public float getLastSmoothed() { return lastSmoothed; }
        public void setLastSmoothed(float v) { lastSmoothed = v; }
        public boolean getHasFirstReading() { return hasFirstReading; }
        public void setHasFirstReading(boolean v) { hasFirstReading = v; }
    }
}