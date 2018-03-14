package de.haw_hamburg.bachelorprojekt_rc.rccar;
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
import android.widget.ToggleButton;


public class ControlSliderActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener, Button.OnTouchListener, MessageReceivedListener {

    // Drive (SeekBar)
    SeekBar seekBarDrive;
    TextView textViewCurrentDrive;

    // Steering (SeekBar)
    SeekBar seekkBarSteering;
    TextView textViewCurrentSteering;

    // Buttons (Light and Horn)
    Button buttonHornSlider;
    ToggleButton toggleButtonLightSlider;

    // Send data
    private SocketClient client = null;
    private boolean sendingData = false;
    private byte[] dataToSend;
    TextView textViewSendSlider;
    byte[] data;
    int hornIsActive;
    int lightIsActive;


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
        seekkBarSteering = (SeekBar)findViewById(R.id.seekBarSteering);
        seekkBarSteering.setOnSeekBarChangeListener(this);

        // Buttons (Light and Horn)
        buttonHornSlider = (Button) findViewById(R.id.buttonHornSlider);
        buttonHornSlider.setOnTouchListener(this);
        ToggleButton toggleButtonLightSlider = (ToggleButton) findViewById(R.id.toggleButtonLightSlider);
        toggleButtonLightSlider.setOnCheckedChangeListener(this);

        // Reset information
        hornIsActive = 0;
        lightIsActive = 0;

        // Send data output
        textViewSendSlider = (TextView) findViewById(R.id.textViewSendSlider);
    }

    @Override
    public void onResume() {
        super.onResume();

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
    protected void onPause() {
        super.onPause();

        // Disconnect from server and stop servos
        int result = sendByteInstruction(new byte[]{(byte)0x7F, (byte)0x7F, (byte)0x01});
    }


    // Send information
    public void send() {
        data = new byte[3];
        data[0] = (byte) seekBarDrive.getProgress();
        data[1] = (byte) seekkBarSteering.getProgress();
        data[2] = (byte) (128 * lightIsActive + 64 * hornIsActive);

        // Output
        String output = String.format("Information: data[0]: 0x%x", data[0]) + String.format(" - data[1]: 0x%x", data[1]) + String.format(" - data[2]: 0x%x", data[2]);
        textViewSendSlider.setText(output);

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


    // Methods for Seekbars (Drive + Steering)
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        switch (seekBar.getId()){
            case R.id.seekBarDrive:
                // set current Drive
                textViewCurrentDrive.setText(Integer.toString(progress));
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
                seekkBarSteering.setProgress(127);
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
