package edu.hendrix.ferrer.arduinodemo3;

import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by gabriel on 5/25/18.
 */

public class ArduinoTalker {
    private boolean deviceOk = false;
    private UsbManager usbManager;
    private UsbDevice device;
    private UsbInterface usbInterface;
    private UsbDeviceConnection connection;
    private UsbEndpoint host2Device, device2Host;
    private String statusMessage = "ok";
    private ArrayList<TalkerListener> listeners = new ArrayList<>();

    private static final int INCOMING_SIZE = 1;

    private static final String TAG = ArduinoTalker.class.getSimpleName();

    // From https://github.com/felHR85/UsbSerial/blob/master/usbserial/src/main/java/com/felhr/usbserial/CDCSerialDevice.java
    private static final int CDC_REQTYPE_HOST2DEVICE = 0x21;
    private static final int CDC_REQTYPE_DEVICE2HOST = 0xA1;

    private static final int CDC_SET_LINE_CODING = 0x20;
    private static final int CDC_GET_LINE_CODING = 0x21;
    private static final int CDC_SET_CONTROL_LINE_STATE = 0x22;

    private static final int CDC_CONTROL_LINE_ON = 0x0003;
    private static final int CDC_CONTROL_LINE_OFF = 0x0000;

    private static final int DEFAULT_BAUD_RATE = 9600;

    /***
     *  Default Serial Configuration
     *  Baud rate: DEFAULT_BAUD_RATE
     *  Data bits: 8
     *  Stop bits: 1
     *  Parity: None
     */
    private static final byte[] CDC_DEFAULT_LINE_CODING = new byte[] {
            (byte) (DEFAULT_BAUD_RATE & 0xff),
            (byte) (DEFAULT_BAUD_RATE >> 8 & 0xff),
            (byte) (DEFAULT_BAUD_RATE >> 16 & 0xff),
            (byte) (DEFAULT_BAUD_RATE >> 24 & 0xff),
            (byte) 0x00, // Offset 5 bCharFormat (1 Stop bit)
            (byte) 0x00, // bParityType (None)
            (byte) 0x08  // bDataBits (8)
    };

    public void addListener(TalkerListener listener) {
        listeners.add(listener);
    }

    public ArduinoTalker(UsbManager mUsbManager) {
        this.usbManager = mUsbManager;
        try {
            checkAllDevices(mUsbManager.getDeviceList());
        } catch (Exception e) {
            statusMessage = "Error: " + e.getMessage();
            Log.e(TAG, statusMessage);
        }
    }

    private void checkAllDevices(HashMap<String, UsbDevice> devices) {
        statusMessage = "Checking all devices";
        for (Map.Entry<String, UsbDevice> entry : devices.entrySet()) {
            device = entry.getValue();
            processDevice();
            if (deviceOk) {
                Log.i(TAG, "Device found");
                return;
            }
        }
        statusMessage += "\nNo suitable devices found";
        Log.i(TAG, "No device found");
    }

    private void processDevice() {
        int id = device.getVendorId();
        statusMessage += "\nConsidering Vendor: " + id;
        if (id == 10755 || id == 0x9025) {
            Log.i(TAG, "Processing interfaces for " + id);
            processAllInterfaces();
        }
    }

    private void processAllInterfaces() {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            Log.i(TAG, "Interface class:" + device.getInterface(i).getInterfaceClass());
            if (isBulkInterface(device.getInterface(i))) {
                Log.i(TAG, "Bulk interface");
                usbInterface = device.getInterface(i);
                connection = usbManager.openDevice(device);
                connection.claimInterface(usbInterface, true);
                byte[] encoding = copyDefaultLineCoding();
                int response = setControlCommand(CDC_SET_LINE_CODING, 0, encoding);
                response = setControlCommand(CDC_SET_CONTROL_LINE_STATE, CDC_CONTROL_LINE_ON, new byte[0]);
                Log.i(TAG, "Setting up endpoints");
                setupEndpoints();
                return;
            }
        }
    }

    // Pre: isBulkInterface(usbInterface)
    private void setupEndpoints() {
        for (int j = 0; j < usbInterface.getEndpointCount(); j++) {
            UsbEndpoint endpoint = usbInterface.getEndpoint(j);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    device2Host = endpoint;
                    Log.i(TAG, "device2Host set");
                } else if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    host2Device = endpoint;
                    Log.i(TAG, "host2Device set");
                }
            }
        }
        deviceOk = true;
    }

    private boolean isBulkInterface(UsbInterface inter) {
        boolean hasHost2Device = false, hasDevice2Host = false;
        for (int j = 0; j < inter.getEndpointCount(); j++) {
            UsbEndpoint endpoint = inter.getEndpoint(j);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    hasDevice2Host = true;
                } else if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    hasHost2Device = true;
                }
            }
        }
        return hasHost2Device && hasDevice2Host;
    }

    public boolean connected() {
        return deviceOk;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void notifyError() {
        for (TalkerListener listener: listeners) {
            listener.error();
        }
    }

    private byte[] copyDefaultLineCoding() {
        byte[] bytes = new byte[CDC_DEFAULT_LINE_CODING.length];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = CDC_DEFAULT_LINE_CODING[i];
        }
        return bytes;
    }

    public void close() {
        int response = setControlCommand(CDC_SET_CONTROL_LINE_STATE, CDC_CONTROL_LINE_OFF, new byte[0]);
        connection.releaseInterface(usbInterface);
        connection.close();
    }

    private int transfer(UsbEndpoint endpoint, byte[] bytes, String label) {
        try {
            Log.i(TAG,"Opened " + label + " connection");
            int result = connection.bulkTransfer(endpoint, bytes, bytes.length, 0);
            Log.i(TAG, "Closed connection; code " + result);
            statusMessage = "Code: " + result;
            for (int r : bytes) {
                statusMessage += ";" + r;
            }
            Log.i(TAG, label + ":" + statusMessage);
            return result;
        } catch (Exception exc) {
            statusMessage = label + "Error:" + exc.getClass().getSimpleName() + "; "+ exc.getMessage();
            Log.e(TAG, statusMessage);
            notifyError();
            return -1;
        }
    }

    // Adapted from https://github.com/felHR85/UsbSerial/blob/master/usbserial/src/main/java/com/felhr/usbserial/CDCSerialDevice.java
    private int setControlCommand(int request, int value, byte[] data) {
        int response = connection.controlTransfer(CDC_REQTYPE_HOST2DEVICE, request, value, 0, data, data.length, 0);
        Log.i(TAG,"Control Transfer Response: " + String.valueOf(response));
        return response;
    }

    public void send(final byte[] bytes) {
        new Thread(new Runnable(){public void run() {
            int result = transfer(host2Device, bytes, "Send");
            for (TalkerListener listener : listeners) {
                listener.sendComplete(result);
            }
        }}).start();
    }

    public void receive() {
        new Thread(new Runnable(){public void run() {
            byte[] received = new byte[INCOMING_SIZE];
            int result = transfer(device2Host, received, "Receive");
            for (TalkerListener listener : listeners) {
                listener.receiveComplete(result);
            }
        }}).start();
    }
}
