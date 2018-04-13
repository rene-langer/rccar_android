package de.haw_hamburg.bachelorprojekt_rc.rccar;

import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.util.Timer;
import java.util.TimerTask;

import io.github.controlwear.virtual.joystick.android.JoystickView;


public class ControlJoystickActivity extends AppCompatActivity implements JoystickView.OnMoveListener, ImageButton.OnTouchListener, MessageReceivedListener {

    JoystickView joystick;
    double acceleration = 127.0;
    double steering = 127.0;

    // Buttons (Light and Horn)
    ImageButton imageButtonHornSlider;
    ImageButton imageButtonLightSlider;

    // CheckBoxes (Limitation)
    CheckBox checkBoxLimitationSlider;

    // VideoView (Camera Stream)
    VideoView cameraStream;

    // Send data
    private SocketClient client = null;
    private boolean sendingData = false;
    private byte[] dataToSend;
    TextView textViewSendSlider;
    byte[] data;
    int hornIsActive;
    int lightIsActive;
    final static String ipAdr = "192.168.5.1";

    Timer sendTimer;


    @Override
    public void onMove(int angle, int strength) {

        if(strength < 20){
            acceleration = 127;
        }
        else{
            if(checkBoxLimitationSlider.isChecked()){
                acceleration = 127 + 25 * strength/100 * Math.signum(Math.sin(Math.toRadians(angle)));
            }
            else{
                acceleration = 127 + 127 * strength/100 * Math.signum(Math.sin(Math.toRadians(angle)));
            }
        }




        steering = 127 + 127 * strength/100 * Math.cos(Math.toRadians(angle));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control_joystick);

        joystick = (JoystickView) findViewById(R.id.joystick);
        joystick.setOnMoveListener(this, 20);

        // Buttons (Light and Horn)
        imageButtonHornSlider = (ImageButton) findViewById(R.id.imageButtonHornSlider);
        imageButtonHornSlider.setOnTouchListener(this);
        imageButtonLightSlider = (ImageButton) findViewById(R.id.imageButtonLightSlider);
        imageButtonLightSlider.setOnTouchListener(this);

        // CheckBoxs (Limitation)
        checkBoxLimitationSlider = (CheckBox) findViewById(R.id.checkBoxLimitationSlider);

//        // VideoStream
//        cameraStream = (VideoView)findViewById(R.id.cameraView);

        // Send data output
        textViewSendSlider = (TextView) findViewById(R.id.textViewSendSlider);

        // automatic sending
        sendTimer = new Timer();
    }

    @Override
    public void onStart() {
        super.onStart();

        // reset information
        acceleration = 127.0;
        steering = 127.0;
        hornIsActive = 0;
        lightIsActive = 0;
        checkBoxLimitationSlider.setChecked(true);

        // Connect to server
        if(client == null || !client.isConnected()) {
            client = new SocketClient(this);
            client.Connect(ipAdr, 9999);
        }
        else {
            Log.e("Connect", "Already connected to server");
        }

        // send init
        sendTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                send();
            }
        }, 20, 20);

        // play Camera stream
        //playStream(ipAdr);
    }

    @Override
    protected void onPause() {
        super.onPause();

        sendTimer.cancel();
        // Disconnect from server and stop servos
        while(client.isConnected()) {
            int result = sendByteInstruction(new byte[]{(byte) 0x7F, (byte) 0x7F, (byte) 0x01});
        }
    }

    // play camera stream
    private void playStream(String ip){
        String address = "http://"+ip+":8090";
        Uri UriSrc = Uri.parse(address);
        if(UriSrc == null)
            Toast.makeText(ControlJoystickActivity.this, "UriSrc == null", Toast.LENGTH_LONG).show();
        else{
            cameraStream.setVideoURI(UriSrc);
            cameraStream.start();

            Toast.makeText(ControlJoystickActivity.this, "Connect: "+ ip, Toast.LENGTH_SHORT).show();
        }
    }


    // Method for Button
    @Override
    public boolean onTouch(View v, MotionEvent event) {

        switch(v.getId()) {
            case R.id.imageButtonHornSlider:     // ImageButton Horn
                boolean performClick = v.performClick();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:   // pressed
                        hornIsActive = 1;
                        imageButtonHornSlider.setImageResource(R.mipmap.signal_horn_on);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: // released
                        hornIsActive = 0;
                        imageButtonHornSlider.setImageResource(R.mipmap.signal_horn_off);
                        break;
                }
                break;

            case R.id.imageButtonLightSlider:   // ImageButton Light
                // Light on
                if (lightIsActive == 0 && (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)) {
                    lightIsActive = 1;
                    imageButtonLightSlider.setImageResource(R.mipmap.light_bulb_on);
                    break;

                // Light off
                } else if (lightIsActive == 1 && (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)) {
                    lightIsActive = 0;
                    imageButtonLightSlider.setImageResource(R.mipmap.light_bulb_off);
                    break;
                }
        }

        return false;
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
    // Send information
    public void send() {
        data = new byte[3];
        data[0] = (byte) acceleration;
        data[1] = (byte) steering;
        data[2] = (byte) (128 * lightIsActive + 64 * hornIsActive);

        // Output
        String output = String.format("Information: data[0]: 0x%x", data[0]) +
                        String.format(" - data[1]: 0x%x", data[1]) +
                        String.format(" - data[2]: 0x%x", data[2]);
        //textViewSendSlider.setText(output);

        // send data to server
        sendByteInstruction(data);
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
