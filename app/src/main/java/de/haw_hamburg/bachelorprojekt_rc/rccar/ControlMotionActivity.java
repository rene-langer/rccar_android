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
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;


public class ControlMotionActivity extends AppCompatActivity implements SensorEventListener, CompoundButton.OnCheckedChangeListener, Button.OnTouchListener, MessageReceivedListener {

    // Drive (Gyro)
    TextView textViewCurrentDriveMotion;
    float positionDriveMotion;

    // Steering (Gyro)
    TextView textViewCurrentSteeringMotion;
    float positionSteeringMotion;

    // Drive + Steering (Gyro)
    SensorManager sensorManager;
    Sensor sensor;

    // Buttons (Light and Horn)
    Button buttonHornMotion;
    ToggleButton toggleButtonLightMotion;

    // Send data
    private SocketClient client = null;
    private boolean sendingData = false;
    private byte[] dataToSend;
    TextView textViewSendMotion;
    byte[] data;
    int hornIsActive;
    int lightIsActive;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control_motion);

        // Drive (Gyro)
        textViewCurrentDriveMotion = (TextView) findViewById(R.id.textViewCurrentDriveMotion);

        // Steering (Gyro)
        textViewCurrentSteeringMotion = (TextView) findViewById(R.id.textViewCurrentSteeringMotion);

        // Drive + Steering (Gyro)
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            // accelerometer available
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            // no accelerometer available --> go to ControlSliderActivity
            Toast.makeText(this, "No sensor detected!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(ControlMotionActivity.this, ControlSliderActivity.class);
            startActivity(intent);
        }

        // Buttons (Light and Horn)
        buttonHornMotion = (Button) findViewById(R.id.buttonHornMotion);
        buttonHornMotion.setOnTouchListener(this);
        ToggleButton toggleButtonLightMotion = (ToggleButton) findViewById(R.id.toggleButtonLightMotion);
        toggleButtonLightMotion.setOnCheckedChangeListener(this);

        // Reset information
        hornIsActive = 0;
        lightIsActive = 0;

        // Send data output
        textViewSendMotion = (TextView) findViewById(R.id.textViewSendMotion);
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
        int result = sendByteInstruction(new byte[]{(byte)0x7F, (byte)0x7F, (byte)0x01});

    }


    // Send information
    public void send() {
        data = new byte[3];
        data[0] = (byte) positionDriveMotion;
        data[1] = (byte) positionSteeringMotion;
        data[2] = (byte) (128 * lightIsActive + 64 * hornIsActive);

        // Output
        String output = String.format("Information: data[0]: 0x%x", data[0]) + String.format(" - data[1]: 0x%x", data[1]) + String.format(" - data[2]: 0x%x", data[2]);
        textViewSendMotion.setText(output);

        // send data to server
        sendByteInstruction(data);
    }


    // Method for Button
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        boolean performClick = v.performClick();
        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:   // pressed
                hornIsActive = 1;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: // released
                hornIsActive = 0;
                break;
        }

        // Sending data
        send();
        return false;
    }


    // Method for ToggleButton
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked)  // enabled
            lightIsActive = 1;
        else    // disabled
            lightIsActive = 0;

        // Sending data
        send();
    }


    // Methods for Steering (Gyro)
    @Override
    public void onSensorChanged(SensorEvent event) {

        // get sensor information and calculate steering
        positionSteeringMotion = event.values[1];

        if (positionSteeringMotion < -5)
            positionSteeringMotion = 0;
        else if (positionSteeringMotion > 5)
            positionSteeringMotion = 255;
        else
            positionSteeringMotion = Math.round((positionSteeringMotion + 5) * 256 / 10);

        // get sensor information and calculate drive
        positionDriveMotion = -event.values[0];

        if (positionDriveMotion < -7)
            positionDriveMotion = 0;
        else if (positionDriveMotion > 3)
            positionDriveMotion = 255;
        else
            positionDriveMotion = Math.round((positionDriveMotion + 7) * 256 / 10);

        // Calculate correct steering
        //output = (input - input_start)*output_range / input_range + output_start;

        // set current Steering
        textViewCurrentSteeringMotion.setText(String.format("%s", Float.toString(positionSteeringMotion)));

        // set current drive
        textViewCurrentDriveMotion.setText(String.format("%s", Float.toString(positionDriveMotion)));

        // send data
        send();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

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
