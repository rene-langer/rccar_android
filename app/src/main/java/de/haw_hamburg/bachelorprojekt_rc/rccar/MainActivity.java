package de.haw_hamburg.bachelorprojekt_rc.rccar;


import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    Button buttonConnect, buttonMotion, buttonSemiMotion, buttonSlider;
    TextView textViewConnected;
    CheckBox checkBoxCamera;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonConnect = (Button)findViewById(R.id.buttonConnect);
        buttonConnect.setOnClickListener(this);
        buttonMotion = (Button)findViewById(R.id.buttonControlMotion);
        buttonMotion.setOnClickListener(this);
        buttonSemiMotion = (Button)findViewById(R.id.buttonControlSemiMotion);
        buttonSemiMotion.setOnClickListener(this);
        buttonSlider = (Button)findViewById(R.id.buttonControlSlider);
        buttonSlider.setOnClickListener(this);

        // Camera information
        checkBoxCamera = (CheckBox) findViewById(R.id.checkBoxCamera);

        // Connection information
        textViewConnected = (TextView)findViewById(R.id.textViewConnected);
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();

        if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
            if (wifiInfo.getSSID().equals("\"RCCar\""))
                textViewConnected.setText("Status: Connected to " + wifiInfo.getSSID() + "(" + dhcpInfo.ipAddress + ")" + "!");
            else
                textViewConnected.setText("Status: Not connected!");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // update Connection information
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();

        if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
            if (wifiInfo.getSSID().equals("\"RCCar\""))
                textViewConnected.setText("Status: Connected to " + wifiInfo.getSSID() + "(" + dhcpInfo.ipAddress + ")" + "!");
            else
                textViewConnected.setText("Status: Not connected!");
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.buttonConnect:
                // go to WIFI Settings
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                break;
            case R.id.buttonControlMotion:
                // go to ControlMotionActivity
                Intent intentControlMotion = new Intent(this, ControlMotionActivity.class);
                intentControlMotion.putExtra("cameraIsChecked", checkBoxCamera.isChecked());
                startActivity(intentControlMotion);
                break;
            case R.id.buttonControlSlider:
                // go to ControlSliderActivity
                Intent intentControlSlider = new Intent(this, ControlSliderActivity.class);
                intentControlSlider.putExtra("cameraIsChecked", checkBoxCamera.isChecked());
                startActivity(intentControlSlider);
                break;
            case R.id.buttonControlSemiMotion:
                // go to ControlSemiMotionActivity
                Intent intentControlSemiMotion = new Intent(this, ControlSemiMotionActivity.class);
                intentControlSemiMotion.putExtra("cameraIsChecked", checkBoxCamera.isChecked());
                startActivity(intentControlSemiMotion);
                break;
        }
    }
}
