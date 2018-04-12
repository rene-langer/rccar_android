package de.haw_hamburg.bachelorprojekt_rc.rccar;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


public class ControlSemiMotionActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener, SensorEventListener, Button.OnTouchListener, MessageReceivedListener {

    // Drive (SeekBar)
    TextView textViewCurrentDriveSemiMotion;
    SeekBar seekBarDrive;
    float positionDrive;

    // Steering (Gyro)
    TextView textViewCurrentSteeringSemiMotion;
    SensorManager sensorManager;
    Sensor sensor;
    float positionSteering;
    float positionSteeringOffset;

    // Buttons (Light, Horn and Calibration)
    ImageButton imageButtonHornSemiMotion;
    ImageButton imageButtonLightSemiMotion;
    ImageButton imageButtonCalibrationSemiMotion;

    // CheckBoxes (Change Axis and Limitation)
    CheckBox checkBoxChangeAxisSemiMotion;
    CheckBox checkBoxLimitationSemiMotion;

    // Send data
    private SocketClient client = null;
    private boolean sendingData = false;
    private byte[] dataToSend;
    TextView textViewSendSemiMotion;
    byte[] data;
    int hornIsActive;
    int lightIsActive;
    boolean calibrationIsActive;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control_semi_motion);

        // Drive (SeedkBar)
        textViewCurrentDriveSemiMotion = (TextView)findViewById(R.id.textViewCurrentDriveSemiMotion);
        seekBarDrive = (SeekBar)findViewById(R.id.seekBarDrive);
        seekBarDrive.setOnSeekBarChangeListener(this);

        // Steering (Gyro)
        textViewCurrentSteeringSemiMotion = (TextView) findViewById(R.id.textViewCurrentSteeringSemiMotion);

        // Drive + Steering (Gyro)
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            // accelerometer available
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            // no accelerometer available --> go to ControlSliderActivity
            Toast.makeText(this, "No sensor detected!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(ControlSemiMotionActivity.this, ControlSliderActivity.class);
            startActivity(intent);
        }

        // Buttons (Light, Horn and Calibration)
        imageButtonHornSemiMotion = (ImageButton) findViewById(R.id.imageButtonHornSemiMotion);
        imageButtonHornSemiMotion.setOnTouchListener(this);
        imageButtonLightSemiMotion = (ImageButton) findViewById(R.id.imageButtonLightSemiMotion);
        imageButtonLightSemiMotion.setOnTouchListener(this);
        imageButtonCalibrationSemiMotion = (ImageButton) findViewById(R.id.imageButtonCalibrationSemiMotion);
        imageButtonCalibrationSemiMotion.setOnTouchListener(this);

        // CheckBoxs (Change Axis and Limitation)
        checkBoxChangeAxisSemiMotion = (CheckBox) findViewById(R.id.checkBoxChangeAxisSemiMotion);
        checkBoxLimitationSemiMotion = (CheckBox) findViewById(R.id.checkBoxLimitationSemiMotion);

        // Send data output
        textViewSendSemiMotion = (TextView) findViewById(R.id.textViewSendSemiMotion);
    }

    @Override
    public void onStart() {
        super.onStart();

        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

        // reset information
        positionSteering = 127;
        positionSteeringOffset = 0;
        positionDrive = 127;
        hornIsActive = 0;
        lightIsActive = 0;
        calibrationIsActive = false;
        checkBoxChangeAxisSemiMotion.setChecked(false);
        checkBoxLimitationSemiMotion.setChecked(true);

        // Connect to server
        if(client == null || !client.isConnected()) {
            client = new SocketClient(this);
            client.Connect("192.168.4.1", 9999);
        }
        else {
            Log.e("Connect", "Already connected to server");
        }

        // send init
        send();
    }

    @Override
    public void onStop() {
        super.onStop();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);

        // Disconnect from server and stop servos
        while(client.isConnected()) {
            int result = sendByteInstruction(new byte[]{(byte) 0x7F, (byte) 0x7F, (byte) 0x01});
        }
    }


    // Send information
    public void send() {
        data = new byte[3];
        data[0] = (byte) positionDrive;
        data[1] = (byte) positionSteering;
        data[2] = (byte) (128 * lightIsActive + 64 * hornIsActive);

        // Output
        String output = String.format("Information: data[0]: 0x%x", data[0]) + String.format(" - data[1]: 0x%x", data[1]) + String.format(" - data[2]: 0x%x", data[2]);
        textViewSendSemiMotion.setText(output);

        // send data to server
        sendByteInstruction(data);
    }


    // Method for Button
    @Override
    public boolean onTouch(View v, MotionEvent event) {

        switch(v.getId()) {
            case R.id.imageButtonHornSemiMotion:     // ImageButton Horn
                boolean performClick = v.performClick();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:   // pressed
                        hornIsActive = 1;
                        imageButtonHornSemiMotion.setImageResource(R.mipmap.signal_horn_on);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: // released
                        hornIsActive = 0;
                        imageButtonHornSemiMotion.setImageResource(R.mipmap.signal_horn_off);
                        break;
                }
                break;

            case R.id.imageButtonCalibrationSemiMotion:  // ImageButton Calibration
                calibrationIsActive = true;
                imageButtonCalibrationSemiMotion.setImageResource(R.mipmap.calibration_on);

                // timer after clicked calibration button
                new CountDownTimer(400, 400) {
                    public void onFinish() {
                        imageButtonCalibrationSemiMotion.setImageResource(R.mipmap.calibration_off);
                    }

                    public void onTick(long millisUntilFinished) {
                        // millisUntilFinished    The amount of time until finished.
                    }
                }.start();

                break;

            case R.id.imageButtonLightSemiMotion:   // ImageButton Light
                // Light on
                if (lightIsActive == 0 && (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)) {
                    lightIsActive = 1;
                    imageButtonLightSemiMotion.setImageResource(R.mipmap.light_bulb_on);
                    break;

                // Light off
                } else if (lightIsActive == 1 && (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)) {
                    lightIsActive = 0;
                    imageButtonLightSemiMotion.setImageResource(R.mipmap.light_bulb_off);
                    break;
                }
        }

        // Sending data
        send();
        return false;
    }


    // Methods for Steering (Gyro)
    @Override
    public void onSensorChanged(SensorEvent event) {

        //Change axis
        float axis0, axis1;
        if (checkBoxChangeAxisSemiMotion.isChecked()) {
            // modified
            axis1 = event.values[0];
        } else {
            // standard
            axis1 = event.values[1];
        }

        // Calibration of Steering
        if (calibrationIsActive) {
            positionSteeringOffset = axis1;

            // reset
            calibrationIsActive = false;
        }

        // get sensor information and calculate output
        positionSteering = (axis1 - positionSteeringOffset);


        if (positionSteering < -5)
            positionSteering = 0;
        else if (positionSteering > 5)
            positionSteering = 255;
        else
            positionSteering = Math.round((positionSteering  + 5) * 256 / 10);

        // Calculate correct steering
        //output = (input - input_start)*output_range / input_range + output_start;

        // set current Steering
        textViewCurrentSteeringSemiMotion.setText(Float.toString(positionSteering));

        // send data
        send();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    // Methods for Drive (SeekBar)
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

        // Limitation of Drive
        if (checkBoxLimitationSemiMotion.isChecked()) {
            positionDrive = seekBarDrive.getProgress() * 61 / 256 + (127 - 30);

            // change textView of Drive
            textViewCurrentDriveSemiMotion.setText(Float.toString(positionDrive));

        } else {
            positionDrive = seekBarDrive.getProgress();

            // change textView of Drive
            textViewCurrentDriveSemiMotion.setText(Float.toString(positionDrive));
        }

        // set current Drive
        //textViewCurrentDriveSemiMotion.setText(Integer.toString(progress));

        // Send data
        send();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // reset progress
        seekBarDrive.setProgress(127);

        // send data
        for (int i=0;i<15;i++){
            send();
        }
    }


    // Methods for sending
    private int sendByteInstruction(byte[] data) {
        if(!sendingData && client.isConnected()) {
            sendingData = true;
            dataToSend = data;
            client.WriteData(new byte[]{TCPCommands.SERVER_REQUEST});
            return 1;
        }
        else {
            return -1;
        }
    }

    @Override
    public void OnByteReceived(byte data){
        if(data == TCPCommands.SERVER_READY){
            // send the 3-byte dataset if server accepted connection
            client.WriteData(dataToSend);
        }
        if(data == TCPCommands.SERVER_FINISHED){
            sendingData = false;
        }
        if(data == TCPCommands.SERVER_CONNECTION_CLOSED){
            sendingData = false;
            client.Disconnect();
        }
    }

    @Override
    public void OnConnectionError(int type) {
        if(type == 1){
            Log.e("ConnectionError", "Could not connect to server");
        }
    }

    @Override
    public void OnConnectSuccess() {
        Log.i("ConnectionSuccess", "Connected to server");
    }
}
