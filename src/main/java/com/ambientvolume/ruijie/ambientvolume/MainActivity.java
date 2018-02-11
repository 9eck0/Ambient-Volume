package com.ambientvolume.ruijie.ambientvolume;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity
{
    protected Button StartButton;
    protected Button StopButton;
    protected Button SamplePointsSetButton;
    protected CheckBox RingtoneVolumeCheckbox;
    protected CheckBox MediaVolumeCheckbox;
    protected EditText SamplePointsEditText;
    protected SeekBar LowestVolumeSeekBar;
    protected SeekBar MaxVolumeSeekBar;
    protected TextView SetSamplePointsTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LowestVolumeSeekBar = (SeekBar) findViewById(R.id.lowestVolumeSeekBar);
        LowestVolumeSeekBar.setOnSeekBarChangeListener(new LowestVolumeSeekbar_ProgressChanged());
        LowestVolumeStep = LowestVolumeSeekBar.getProgress();

        MaxVolumeSeekBar = (SeekBar) findViewById(R.id.maxVolumeSeekBar);
        MaxVolumeSeekBar.setOnSeekBarChangeListener(new MaxVolumeSeekbar_ProgressChanged());
        MaxVolumeStep = MaxVolumeSeekBar.getProgress();

        RingtoneVolumeCheckbox = (CheckBox) findViewById(R.id.ringtoneVolumeCheckBox);
        MediaVolumeCheckbox = (CheckBox) findViewById(R.id.mediaVolumeCheckBox);

        StartButton = (Button) findViewById(R.id.start_button);
        StartButton.setOnClickListener(new StartButton_Click());

        StopButton = (Button) findViewById(R.id.stop_button);
        StopButton.setOnClickListener(new StopButton_Click());

        SamplePointsEditText = (EditText) findViewById(R.id.samplePointsEditText);

        SamplePointsSetButton = (Button) findViewById(R.id.SamplePointsSetButton);
        SamplePointsSetButton.setOnClickListener(new SamplePointsSetButton_Click());

        SetSamplePointsTextView = (TextView) findViewById(R.id.setSamplePointsTextView);

        //SamplePointsEditText.setText(Integer.toString(stepsPerMinute));
        UIupdateStepsPerMinute(stepsPerMinute);

        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
    }

    // === Listeners ===

    private class StartButton_Click implements View.OnClickListener
    {
        public void onClick(View view)
        {
            System.out.println("Starting measuring ambient volume...");
            StartAmbientDecibelsTracking();
        }
    }

    private class StopButton_Click implements View.OnClickListener
    {
        public void onClick(View view)
        {
            System.out.println("Stopping measurement...");
            requestRecorderStop = true;
        }
    }

    private class SamplePointsSetButton_Click implements View.OnClickListener
    {
        public void onClick(View view)
        {
            int steps = 120;

            try {
                steps = Integer.parseInt(SamplePointsEditText.getText().toString());
            } catch (Exception e) {System.err.println("An error occurred while parsing integer from text: " + e.getMessage());}

            if (steps < 1)
            {
                steps = 1;
            }

            stepsPerMinute = steps;
            UIupdateStepsPerMinute(steps);
            RestartRecorder();
        }
    }

    private class LowestVolumeSeekbar_ProgressChanged implements SeekBar.OnSeekBarChangeListener
    {
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }

        public void onStartTrackingTouch(SeekBar seekBar) { }

        public void onStopTrackingTouch(SeekBar seekBar)
        {
            LowestVolumeStep = (int) Math.ceil((((double)seekBar.getProgress())/10)*audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            //LowestVolumeStep = (int) Math.ceil(seekBar.getProgress());
        }
    }

    private class MaxVolumeSeekbar_ProgressChanged implements SeekBar.OnSeekBarChangeListener
    {
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }

        public void onStartTrackingTouch(SeekBar seekBar) { }

        public void onStopTrackingTouch(SeekBar seekBar)
        {
            MaxVolumeStep = (int) Math.ceil((((double)seekBar.getProgress())/10)*audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            System.out.println("Set max volume step at: " + MaxVolumeStep);
        }
    }

    // === Methods ===

    public void UIupdateStepsPerMinute(int stepsPerMinute)
    {
        SetSamplePointsTextView.setText("Sample points/minute: " + Integer.toString(stepsPerMinute));
        SamplePointsEditText.setText(Integer.toString(stepsPerMinute));
    }


    AudioManager audioManager;
    protected int LowestVolumeStep;
    protected int MaxVolumeStep;

    public void setVolume(int streamType, int volume)
    {
        try
        {
            audioManager.setStreamVolume(streamType, volume, 0);
        }
        catch (Exception e)
        {
            System.err.println("An error occurred while setting volume: " + e.getLocalizedMessage());
            System.err.println(e.getStackTrace());
        }
    }

    // === Amplitude recording ===

    MediaRecorder recorder;
    File tempRecordingFile;
    Timer recorderTimer;
    boolean isRecording = false;
    boolean requestRecorderStop = false;
    int stepsPerMinute = 120;

    private class RecorderTask extends TimerTask {
        private MediaRecorder recorder;

        private RecorderTask(MediaRecorder recorder) {
            this.recorder = recorder;
        }

        public void run()
        {
            if (requestRecorderStop)
            {
                disposeRecorder();
            }

            int amplitude = recorder.getMaxAmplitude();
            // getMaxAmplitude() returns
            System.out.println("Measured amplitude: " + amplitude);

            try {
                AddSamplePoint(amplitude);
            }
            catch (Exception e)
            {
                System.err.println("An error occurred while adding sample point: " + e.getMessage());
                System.err.println(e.getStackTrace());
            }
        }
    }

    public void StartAmbientDecibelsTracking()
    {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
        {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    0);
        }

        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);

        try
        {
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
            tempRecordingFile = File.createTempFile("tempRecording", ".mp3");
            recorder.setOutputFile(tempRecordingFile.getPath());
            recorder.prepare();
            recorder.start();
        }
        catch(Exception e)
        {
            System.err.println("An error occurred while preparing recorder: " + e.getMessage());
            System.err.println(e.getStackTrace());
        }
        if (recorderTimer == null)
        {
            recorderTimer = new Timer();
        }

        // Sample max amplitude at every fixed interval. Default: 500ms
        recorderTimer.scheduleAtFixedRate(new RecorderTask(recorder), 0, convertSamplePerMinuteToMilliseconds(stepsPerMinute));
        isRecording = true;
    }

    protected void disposeRecorder()
    {
        recorder.stop();

        // Dispose recorderTimer
        recorderTimer.cancel();
        recorderTimer = null;

        recorder.reset();
        recorder.release();
        requestRecorderStop = false;
        isRecording = false;
    }

    protected void RestartRecorder()
    {
        if (isRecording)
        {
            disposeRecorder();
            StartAmbientDecibelsTracking();
        }
    }

    private int convertSamplePerMinuteToMilliseconds(int samplePerMinute)
    {
        return (int) Math.ceil((1/(samplePerMinute/60)) * 1000);
    }

    // === Logic ===

    private int[] SamplePoints = new int[5];
    private int _currentIndex = 0;

    protected void AddSamplePoint(int samplePoint)
    {
        if (SamplePoints[4] != 0)
        {
            if (RingtoneVolumeCheckbox.isChecked())
            {
                changeVolume(AudioManager.STREAM_RING, GetFloorSamplePoint());
            }
            if (MediaVolumeCheckbox.isChecked())
            {
                changeVolume(AudioManager.STREAM_MUSIC, GetFloorSamplePoint());
            }
            SamplePoints = new int[5];
            _currentIndex = 0;
        }

        SamplePoints[_currentIndex] = samplePoint;
        _currentIndex++;
    }

    protected int GetFloorSamplePoint()
    {
        int floorValue = 32000;
        for (int i = 0; i<SamplePoints.length; i++)
        {
            if (SamplePoints[i] < floorValue && SamplePoints[i] != 0)
            {
                floorValue = SamplePoints[i];
            }
        }
        System.out.println("Floor value for current loop is: " + floorValue);
        return floorValue;
    }

    protected void changeVolume(int StreamType, double floorSamplePoint)
    {
        try
        {
            int maxSteps = audioManager.getStreamMaxVolume(StreamType);
            //int maxSteps = MaxVolumeStep;
            int index = (int) Math.ceil((floorSamplePoint/(MaxVolumeStep*3200))*maxSteps);
            if (index > MaxVolumeStep)
            {
                index = MaxVolumeStep;
            }
            if (index < LowestVolumeStep)
            {
                index = LowestVolumeStep;
            }
            setVolume(StreamType, index);
        }
        catch (Exception e)
        {
            System.err.println("An error occurred while changing volume: " + e.getMessage());
            System.err.println(e.getStackTrace());
        }
    }
}
