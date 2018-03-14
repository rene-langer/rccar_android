package de.haw_hamburg.bachelorprojekt_rc.rccar;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;


public class ControlSemiMotionActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener, SensorEventListener, CompoundButton.OnCheckedChangeListener, Button.OnTouchListener, MessageReceivedListener {

    // Drive (SeekBar)
    TextView textViewCurrentDriveSemiMotion;
    SeekBar seekBarDrive;

    // Steering (Gyro)
    TextView textViewCurrentSteeringSemiMotion;
    SensorManager sensorManager;
    Sensor sensor;
    float positionSteering;
    float positionSteeringOffset;
    boolean steeringIsStarted;
    boolean steeringIsStartedFirst;

    // Buttons (Light and Horn)
    Button buttonHornSemiMotion;
    ToggleButton toggleButtonLightSemiMotion;

    // Send data
    private SocketClient client = null;
    private boolean sendingData = false;
    private byte[] dataToSend;
    TextView textViewSendSemiMotion;
    byte[] data;
    int hornIsActive;
    int lightIsActive;


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

        // Buttons (Light and Horn)
        buttonHornSemiMotion = (Button) findViewById(R.id.buttonHornSemiMotion);
        buttonHornSemiMotion.setOnTouchListener(this);
        ToggleButton toggleButtonLightSemiMotion = (ToggleButton) findViewById(R.id.toggleButtonLightSemiMotion);
        toggleButtonLightSemiMotion.setOnCheckedChangeListener(this);

        // reset information
        steeringIsStarted = false;
        steeringIsStartedFirst = true;
        positionSteeringOffset = 0;
        hornIsActive = 0;
        lightIsActive = 0;

        // Send data output
        textViewSendSemiMotion = (TextView) findViewById(R.id.textViewSendSemiMotion);
    }

    @Override
    public void onResume() {
        super.onResume();
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

        // Connect to server
        if(client == null || !client.isConnected()) {
            client = new SocketClient(this);
            client.Connect("192.168.4.1", 9999);
        }
        else {
            Log.e("Connect", "Already connected to server");
        }
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
        data[0] = (byte) seekBarDrive.getProgress();
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
        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:   // pressed
                hornIsActive = 1;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: // released
                hornIsActive = 0;
                break;
        }

        // Send data
        send();
        return false;
    }


    // Method for ToggleButton
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked)  // enabled
            lightIsActive = 1;
        else    // disabled
            lightIsActive = 0;

        // Send data
        send();
    }


    // Methods for Steering (Gyro)
    @Override
    public void onSensorChanged(SensorEvent event) {

        // set Offset when seekBar is touched first
        if (steeringIsStarted && steeringIsStartedFirst) {
            positionSteeringOffset = event.values[1];
            steeringIsStartedFirst = false;
        }

        // get sensor information and calculate output
        positionSteering = (event.values[1] - positionSteeringOffset);

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
        // set current Drive
        textViewCurrentDriveSemiMotion.setText(Integer.toString(progress));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        steeringIsStarted = true;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // reset progress
        seekBarDrive.setProgress(127);

        // send data
        send();
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
