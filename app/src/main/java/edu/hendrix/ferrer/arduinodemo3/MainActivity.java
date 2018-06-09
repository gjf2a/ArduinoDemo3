package edu.hendrix.ferrer.arduinodemo3;

import android.content.Context;
import android.hardware.usb.UsbManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

// Much assistance obtained from:
//
// https://developer.android.com/guide/topics/connectivity/usb/host
// http://blog.blecentral.com/2015/10/01/handling-usb-connections-in-android/

// To communicate, use Interface 1, which has 2 Bulk Endpoints. Endpoint 0 is Host to Device
// and Endpoint 1 is Device to Host. Both Endpoints have a maximum packet size of 64.

public class MainActivity extends AppCompatActivity implements TalkerListener {

    //private EditText textView;
    private TextView textView;
    private ArduinoTalker talker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);
        //textView = findViewById(R.id.editText);
        //talker = new ArduinoTalker(getIntent()); // Doesn't work...
        deviceCheck();
    }

    public void deviceCheckHandler(View view) {
        deviceCheck();
    }

    private void deviceCheck() {
        if (talker == null || !talker.connected()) {
            talker = new ArduinoTalker((UsbManager) getSystemService(Context.USB_SERVICE));
            if (talker.connected()) {
                textView.setText("Device is good to go!");
                talker.addListener(this);
            } else {
                textView.setText(talker.getStatusMessage());
            }
        } else {
            textView.setText(talker.getStatusMessage());
        }
    }

    public void uppercaseSender(View view) {
        sendByte((byte)65);
    }

    public void lowercaseSender(View view) {
        sendByte((byte)97);
    }

    private void sendByte(byte b) {
        talker.send(new byte[]{b});
    }

    @Override
    public void sendComplete(int status) {
        update();
        talker.receive();
    }

    @Override
    public void receiveComplete(int status) {
        update();
    }

    @Override
    public void error() {
        update();
    }

    private void update() {
        runOnUiThread(new Runnable() {public void run(){
            textView.setText(talker.getStatusMessage());
        }});
    }

}
