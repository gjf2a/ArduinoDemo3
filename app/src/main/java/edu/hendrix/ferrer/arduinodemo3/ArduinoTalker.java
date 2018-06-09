package edu.hendrix.ferrer.arduinodemo3;

import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

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
    private UsbEndpoint host2Device, device2Host;
    private String statusMessage = "ok";
    private ArrayList<TalkerListener> listeners = new ArrayList<>();

    private final int INCOMING_SIZE = 1;

    public void addListener(TalkerListener listener) {
        listeners.add(listener);
    }

    public ArduinoTalker(UsbManager mUsbManager) {
        this.usbManager = mUsbManager;
        try {
            checkAllDevices(mUsbManager.getDeviceList());
        } catch (Exception e) {
            statusMessage = "Error: " + e.getMessage();
        }
    }

    // This always results in an exception:
    // String resource ID #0x2a03
    // I can't find a solution.
    // Hence, I created the alternate version above, which does seem to work.
    public ArduinoTalker(Intent intent, UsbManager mUsbManager) {
        this.usbManager = mUsbManager;
        device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device == null) {
            statusMessage = "Device attached, but not accessible";
        } else {
            try {
                processDevice();
            } catch (Exception e) {
                statusMessage = "Error:" + e.getMessage();
            }
        }
    }

    private void checkAllDevices(HashMap<String, UsbDevice> devices) {
        statusMessage = "Checking all devices";
        for (Map.Entry<String, UsbDevice> entry : devices.entrySet()) {
            device = entry.getValue();
            processDevice();
            if (deviceOk) {
                return;
            }
        }
        statusMessage += "\nNo suitable devices found";
    }

    private void processDevice() {
        int id = device.getVendorId();
        statusMessage += "\nConsidering Vendor: " + id;
        if (id == 10755 || id == 9025) {
            processAllInterfaces();
        }
    }

    private void processAllInterfaces() {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            if (isBulkInterface(device.getInterface(i))) {
                usbInterface = device.getInterface(i);
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
                } else if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    host2Device = endpoint;
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

    public void send(final byte[] bytes) {
        new Thread(new Runnable(){public void run() {
            try {
                UsbDeviceConnection connection = usbManager.openDevice(device);
                connection.claimInterface(usbInterface, true);
                statusMessage = "Opened send connection";
                int result = connection.bulkTransfer(host2Device, bytes, bytes.length, 0);
                connection.releaseInterface(usbInterface);
                connection.close();
                statusMessage = "Closed connection; code " + result;
                for (TalkerListener listener : listeners) {
                    listener.sendComplete(result);
                }
            } catch (Exception exc) {
                statusMessage = "SendError:" + exc.getClass().getSimpleName() + "; " + exc.getMessage();
                notifyError();
            }
        }}).start();
    }

    public void receive() {
        new Thread(new Runnable(){public void run() {
            try {
                UsbDeviceConnection connection = usbManager.openDevice(device);
                connection.claimInterface(usbInterface, true);
                statusMessage = "Opened receive connection";
                byte[] received = new byte[INCOMING_SIZE];
                int result = connection.bulkTransfer(device2Host, received, received.length, 0);
                connection.releaseInterface(usbInterface);
                connection.close();
                statusMessage = "Code: " + result;
                for (int r : received) {
                    statusMessage += ";" + r;
                }
                for (TalkerListener listener : listeners) {
                    listener.receiveComplete(result);
                }
            } catch (Exception exc) {
                statusMessage = "ReceiveError:" + exc.getClass().getSimpleName() + "; "+ exc.getMessage();
                notifyError();
            }
        }}).start();
    }
}
