package de.haw_hamburg.bachelorprojekt_rc.rccar;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;


public class ControlMotionActivity extends AppCompatActivity implements SensorEventListener, Button.OnTouchListener, MessageReceivedListener {

    // Drive (Gyro)
    TextView textViewCurrentDriveMotion;
    float positionDrive;
    float positionDriveOffset;

    // Steering (Gyro)
    TextView textViewCurrentSteeringMotion;
    float positionSteering;
    float positionSteeringOffset;

    // Drive + Steering (Gyro)
    SensorManager sensorManager;
    Sensor sensor;

    // Buttons (Light, Horn and Calibration)
    ImageButton imageButtonHornMotion;
    ImageButton imageButtonLightMotion;
    ImageButton imageButtonCalibrationMotion;

    // CheckBoxes (Change Axis and Limitation)
    CheckBox checkBoxChangeAxisMotion;
    CheckBox checkBoxLimitationMotion;

    // VideoView (Camera Stream)
    VideoView cameraStream;
    MediaController mediaController;

    // Send data
    private SocketClient client = null;
    private boolean sendingData = false;
    private byte[] dataToSend;
    TextView textViewSendMotion;
    byte[] data;
    int hornIsActive;
    int lightIsActive;
    boolean calibrationIsActive;
    final static String ipAdr = "192.168.5.1";

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
        if (sensorManager != null) {
            // accelerometer available
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            // no accelerometer available --> go to ControlSliderActivity
            Toast.makeText(this, "No sensor detected!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(ControlMotionActivity.this, ControlSliderActivity.class);
            startActivity(intent);
        }

        // Buttons (Light, Horn and Calibration)
        imageButtonHornMotion = (ImageButton) findViewById(R.id.imageButtonHornMotion);
        imageButtonHornMotion.setOnTouchListener(this);
        imageButtonLightMotion = (ImageButton) findViewById(R.id.imageButtonLightMotion);
        imageButtonLightMotion.setOnTouchListener(this);
        imageButtonCalibrationMotion = (ImageButton) findViewById(R.id.imageButtonCalibrationMotion);
        imageButtonCalibrationMotion.setOnTouchListener(this);

        // CheckBoxes (Change Axis and Limitation)
        checkBoxChangeAxisMotion = (CheckBox) findViewById(R.id.checkBoxChangeAxisMotion);
        checkBoxLimitationMotion = (CheckBox) findViewById(R.id.checkBoxLimitationMotion);

        // VideoStream
        cameraStream = (VideoView)findViewById(R.id.cameraView);

        // Send data output
        textViewSendMotion = (TextView) findViewById(R.id.textViewSendMotion);
    }

    @Override
    public void onStart() {
        super.onStart();

        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

        // reset information
        positionSteering = 127;
        positionSteeringOffset = 0;
        positionDrive = 127;
        positionDriveOffset = 0;
        hornIsActive = 0;
        lightIsActive = 0;
        calibrationIsActive = false;
        checkBoxChangeAxisMotion.setChecked(false);
        checkBoxLimitationMotion.setChecked(true);

        // Connect to server
        if(client == null || !client.isConnected()) {
            client = new SocketClient(this);
            client.Connect(ipAdr, 9999);
        }
        else {
            Log.e("Connect", "Already connected to server");
        }

        // send init
        send();

        // play Camera stream
        playStream(ipAdr);
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
        data[0] = (byte) positionDrive;
        data[1] = (byte) positionSteering;
        data[2] = (byte) (128 * lightIsActive + 64 * hornIsActive);

        // Output
        String output = String.format("Information: data[0]: 0x%x", data[0]) + String.format(" - data[1]: 0x%x", data[1]) + String.format(" - data[2]: 0x%x", data[2]);
        textViewSendMotion.setText(output);

        // send data to server
        sendByteInstruction(data);
    }

    // play camera stream
    private void playStream(String ip){
        String address = "http://"+ip+":8090";
        Uri UriSrc = Uri.parse(address);
        if(UriSrc == null)
            Toast.makeText(ControlMotionActivity.this, "UriSrc == null", Toast.LENGTH_LONG).show();
        else{
            cameraStream.setVideoURI(UriSrc);
            mediaController = new MediaController(this);
            cameraStream.setMediaController(mediaController);
            cameraStream.start();

            Toast.makeText(ControlMotionActivity.this, "Connect: "+ ip, Toast.LENGTH_SHORT).show();
        }
    }

    // Method for Button
    @Override
    public boolean onTouch(View v, MotionEvent event) {

        switch(v.getId()) {
            case R.id.imageButtonHornMotion:     // ImageButton Horn
                boolean performClick = v.performClick();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:   // pressed
                        hornIsActive = 1;
                        imageButtonHornMotion.setImageResource(R.mipmap.signal_horn_on);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: // released
                        hornIsActive = 0;
                        imageButtonHornMotion.setImageResource(R.mipmap.signal_horn_off);
                        break;
                }
                break;

            case R.id.imageButtonCalibrationMotion:  // ImageButton Calibration
                calibrationIsActive = true;
                imageButtonCalibrationMotion.setImageResource(R.mipmap.calibration_on);

                // timer after clicked calibration button
                new CountDownTimer(400, 400) {
                    public void onFinish() {
                        imageButtonCalibrationMotion.setImageResource(R.mipmap.calibration_off);
                    }

                    public void onTick(long millisUntilFinished) {
                        // millisUntilFinished    The amount of time until finished.
                    }
                }.start();

                break;

            case R.id.imageButtonLightMotion:   // ImageButton Light
                // Light on
                if (lightIsActive == 0 && (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)) {
                    lightIsActive = 1;
                    imageButtonLightMotion.setImageResource(R.mipmap.light_bulb_on);
                    break;

                // Light off
                } else if (lightIsActive == 1 && (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)) {
                    lightIsActive = 0;
                    imageButtonLightMotion.setImageResource(R.mipmap.light_bulb_off);
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
        if (checkBoxChangeAxisMotion.isChecked()) {
            // modified
            axis0 = event.values[1];
            axis1 = event.values[0];
        } else {
            // standard
            axis0 = event.values[0];
            axis1 = event.values[1];
        }

        // Calibration
        if (calibrationIsActive) {
            positionSteeringOffset = axis1;
            positionDriveOffset = axis0;

            // reset
            calibrationIsActive = false;
        }

        // get sensor information and calculate steering
        positionSteering = axis1 - positionSteeringOffset;

        if (positionSteering < -5)
            positionSteering = 0;
        else if (positionSteering > 5)
            positionSteering = 255;
        else
            positionSteering = Math.round((positionSteering + 5) * 256 / 10);


        // get sensor information and calculate drive
        positionDrive = -axis0 + positionDriveOffset;

        if (positionDrive < -5)
            positionDrive = 0;
        else if (positionDrive > 5)
            positionDrive = 255;
        else
            positionDrive = Math.round((positionDrive + 5) * 256 / 10);

        // Limitation of Drive
        if (checkBoxLimitationMotion.isChecked()) {
            positionDrive = Math.round(positionDrive * 61 / 256 + (127 - 30));
        }

        // Calculate formula
        // output = (input - input_start)*output_range / input_range + output_start;



        // set current Steering
        textViewCurrentSteeringMotion.setText(String.format("%s", Float.toString(positionSteering)));

        // set current drive
        textViewCurrentDriveMotion.setText(String.format("%s", Float.toString(positionDrive)));

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
