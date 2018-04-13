package de.haw_hamburg.bachelorprojekt_rc.rccar;
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
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.VideoView;


public class ControlSliderActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener, Button.OnTouchListener, MessageReceivedListener {

    // Drive (SeekBar)
    SeekBar seekBarDrive;
    TextView textViewCurrentDrive;
    float positionDrive;

    // Steering (SeekBar)
    SeekBar seekBarSteering;
    TextView textViewCurrentSteering;

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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control_slider);

        // Drive(SeekBar)
        textViewCurrentDrive = (TextView)findViewById(R.id.textViewCurrentDrive);
        seekBarDrive = (SeekBar)findViewById(R.id.seekBarDrive);
        seekBarDrive.setOnSeekBarChangeListener(this);

        // Steering (SeekBar)
        textViewCurrentSteering = (TextView)findViewById(R.id.textViewCurrentSterring);
        seekBarSteering = (SeekBar)findViewById(R.id.seekBarSteering);
        seekBarSteering.setOnSeekBarChangeListener(this);

        // Buttons (Light and Horn)
        imageButtonHornSlider = (ImageButton) findViewById(R.id.imageButtonHornSlider);
        imageButtonHornSlider.setOnTouchListener(this);
        imageButtonLightSlider = (ImageButton) findViewById(R.id.imageButtonLightSlider);
        imageButtonLightSlider.setOnTouchListener(this);

        // CheckBoxs (Limitation)
        checkBoxLimitationSlider = (CheckBox) findViewById(R.id.checkBoxLimitationSlider);

        // VideoStream
        cameraStream = (VideoView)findViewById(R.id.cameraView);

        // Camera visible?
        if (!getIntent().getExtras().getBoolean("cameraIsChecked")) {
            cameraStream.setVisibility(View.GONE);
        }

        // Send data output
        textViewSendSlider = (TextView) findViewById(R.id.textViewSendSlider);
    }

    @Override
    public void onStart() {
        super.onStart();

        // reset information
        positionDrive = 127;
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
        send();

        // play Camera stream
        if (getIntent().getExtras().getBoolean("cameraIsChecked")) {
            playStream(ipAdr);
            cameraStream.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        cameraStream.stopPlayback();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Disconnect from server and stop servos
        while(client.isConnected()) {
            int result = sendByteInstruction(new byte[]{(byte) 0x7F, (byte) 0x7F, (byte) 0x01});
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Camera visible?
        if (!getIntent().getExtras().getBoolean("cameraIsChecked")) {
            cameraStream.setVisibility(View.GONE);
        }
    }


    // Send information
    public void send() {
        data = new byte[3];
        data[0] = (byte) positionDrive;
        data[1] = (byte) seekBarSteering.getProgress();
        data[2] = (byte) (128 * lightIsActive + 64 * hornIsActive);

        // Output
        String output = String.format("Information: data[0]: 0x%x", data[0]) + String.format(" - data[1]: 0x%x", data[1]) + String.format(" - data[2]: 0x%x", data[2]);
        textViewSendSlider.setText(output);

        // send data to server
        sendByteInstruction(data);
    }

    // play camera stream
    private void playStream(String ip){
        String address = "http://"+ip+":8090";
        Uri UriSrc = Uri.parse(address);
        if(UriSrc == null)
            Toast.makeText(ControlSliderActivity.this, "UriSrc == null", Toast.LENGTH_LONG).show();
        else{
            cameraStream.setVideoURI(UriSrc);
            cameraStream.start();

            Toast.makeText(ControlSliderActivity.this, "Connect: "+ ip, Toast.LENGTH_SHORT).show();
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

        // Sending data
        send();
        return false;
    }


    // Methods for Seekbars (Drive + Steering)
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        switch (seekBar.getId()){
            case R.id.seekBarDrive:
                // set current Drive
                // Limitation of Drive
                if (checkBoxLimitationSlider.isChecked()) {
                    positionDrive = seekBarDrive.getProgress() * 61 / 256 + (127 - 30);

                    // change textView of Drive
                    textViewCurrentDrive.setText(Float.toString(positionDrive));
                } else {
                    positionDrive = seekBarDrive.getProgress();

                    // change textView of Drive
                    textViewCurrentDrive.setText(Float.toString(positionDrive));
                }
                break;
            case  R.id.seekBarSteering:
                // set current Steering
                textViewCurrentSteering.setText(Integer.toString(progress));
                break;
            default:
                break;
        }

        // Send data
        send();
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        switch (seekBar.getId()){
            case R.id.seekBarDrive:
                // reset progress
                seekBarDrive.setProgress(127);
                break;
            case  R.id.seekBarSteering:
                // reset progress
                seekBarSteering.setProgress(127);
                break;
            default:
                break;
        }

        // send data
        for (int i=0;i<15;i++){
            send();
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

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
