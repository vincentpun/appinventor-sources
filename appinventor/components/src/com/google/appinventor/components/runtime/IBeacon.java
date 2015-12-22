package com.google.appinventor.components.runtime;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.util.Beacon;
import com.google.appinventor.components.runtime.util.ErrorMessages;
import com.google.appinventor.components.runtime.util.SdkLevel;
import com.google.appinventor.components.runtime.util.YailList;
import gnu.math.IntNum;

import java.util.*;

/**
 * @author Vincent Pun (vpun@ymail.com)
 */

@DesignerComponent(version = YaVersion.IBEACON_COMPONENT_VERSION, description = "Component for detecting Apple iBeacons.", category = ComponentCategory.CONNECTIVITY, nonVisible = true, iconName = "images/ibeacon.png")
@SimpleObject
@UsesPermissions(permissionNames = "android.permission.BLUETOOTH, " + "android.permission.BLUETOOTH_ADMIN")

public class IBeacon extends AndroidNonvisibleComponent implements Component {

    private static final String LOG_TAG = "IBeaconComponent";

    /**
     * BLE Components
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * States
     */
    private boolean isScanning = false;

    /**
     * Beacon filtering criteria
     */
    private UUID uuid;
    private int major;
    private int minor;

    private ArrayList<Object[]> beacons = new ArrayList<Object[]>();
    private Handler scanHandler = new Handler();
    private final Handler uiThread;

    private Runnable loopLeScan = new Runnable() {
        @Override
        public void run() {
            scanHandler.postDelayed(restartLeScan, 500);
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        }
    };

    private Runnable restartLeScan = new Runnable() {
        @Override
        public void run() {
            if (beacons.size() > 0)
                BeaconsFound(beacons.size());

            scanHandler.postDelayed(loopLeScan, 10);
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    };

    private Runnable ageBeacons = new Runnable() {
        @Override
        public void run() {
            for (Object[] beacon: beacons) {
                beacon[1] = (Integer)(beacon[1]) + 1;

                if ((Integer)beacon[1] >= 4) {
                    ((Beacon)beacon[0]).rssi = -1;

                    // Dispatch another event to let app update status
                    BeaconsFound(beacons.size());
                }
            }

            Iterator<Object[]> beaconsIterator = beacons.iterator();
            while (beaconsIterator.hasNext()) {
                Object[] beacon = beaconsIterator.next();

                if ((Integer)beacon[1] >= 10) {
                    beaconsIterator.remove();

                    if (beacons.size() == 0) {
                        // Dispatch another event to let app update status
                        uiThread.removeCallbacksAndMessages(null);
                        BeaconsFound(beacons.size());
                    }
                }
            }

            scanHandler.postDelayed(ageBeacons, 1000);
        }
    };

    private LeScanCallback mLeScanCallback = new LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            byte[] beaconData = getBeaconAdvertisement(rssi, scanRecord);
            if (beaconData == null)
                return;

            Beacon beacon = parseManufactureData(rssi, beaconData);

            if (beacon == null)
                return;

            // Filter UUID that are not in the "region"
            if (uuid != null && !beacon.uuid.equals(uuid))
                return;

            // Filter out non-matching major numbers
            if (uuid != null && major >= 0 && beacon.major != major)
                return;

            // Filter out non-matching minor numbers
            if (uuid != null && major >= 0 && minor >= 0 && beacon.minor != minor)
                return;

            int index = indexOfBeacon(beacon);

            if (index < 0) {
                beacons.add(new Object[]{beacon, 0});
            } else {
                ((Beacon)beacons.get(index)[0]).rssi = rssi;
                beacons.get(index)[1] = 0;
            }

            sortBeaconDistances();
        }
    };

    @SimpleEvent(description = "This will be called whenever for each scanning")
    public void BeaconsFound(final int numberOfBeacons) {
        uiThread.post(new Runnable() {
            @Override
            public void run() {
                EventDispatcher.dispatchEvent(IBeacon.this, "BeaconsFound", numberOfBeacons);
            }
        });
    }

    @SimpleFunction(description = "Get the beacon major number for the nth beacon.")
    public int Major(int beacon) {
        if (beacon > beacons.size())
            showError("Out of bounds");

        return ((Beacon)beacons.get(beacon - 1)[0]).major;
    }

    @SimpleFunction(description = "Get the beacon minor number for the nth beacon.")
    public int Minor(int beacon) {
        if (beacon > beacons.size())
            showError("Out of bounds");

        return ((Beacon)beacons.get(beacon - 1)[0]).minor;
    }

    @SimpleFunction(description = "Get the beacon UUID for the nth beacon.")
    public String UUID(int beacon) {
        if (beacon > beacons.size())
            showError("Out of bounds");

        return ((Beacon)beacons.get(beacon - 1)[0]).uuid.toString();
    }

    @SimpleFunction(description = "Get the beacon RSSI for the nth beacon.")
    public int RSSI(int beacon) {
        if (beacon > beacons.size())
            showError("Out of bounds");

        return ((Beacon)beacons.get(beacon - 1)[0]).rssi;
    }

    public IBeacon(ComponentContainer container) {
        super(container.$form());
        Activity activity = container.$context();
        uiThread = new Handler();

        // Only initialize Bluetooth adapter if Android version is higher than 4.3.
        if (SdkLevel.getLevel() < SdkLevel.LEVEL_JELLYBEAN_MR2) {
            mBluetoothAdapter = null;
        } else {
            mBluetoothAdapter = newBluetoothAdapter(activity);
            mBluetoothAdapter = newBluetoothAdapter(activity);
        }

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Log.e(LOG_TAG, "Bluetooth LE unsupported on device.");
            form.dispatchErrorOccurredEvent(this, "Initialization", ErrorMessages.ERROR_BLUETOOTH_NOT_ENABLED);
        }
    }

    private void showError(String message) {
        form.dispatchErrorOccurredEvent(this, message, 0);
        StopScanningForBeacons();
    }

    private static BluetoothAdapter newBluetoothAdapter(Context context) {
        final BluetoothManager bluetoothManager = (BluetoothManager) context
                .getSystemService(Context.BLUETOOTH_SERVICE);
        return bluetoothManager.getAdapter();
    }

    @SimpleFunction(description = "Start looking for iBeacons.")
    public void StartScanningForBeacons(YailList filter) {
        if (isScanning)
            return;

        int length = filter.size();
        String beacon_uuid = null;
        int major = -1;
        int minor = -1;

        if (length >= 1) {
            beacon_uuid = filter.getString(0);

            if (length >= 2) {
                Object value = filter.getObject(1);
                if (value instanceof IntNum) {
                    major = ((IntNum)value).intValue();
                } else {
                    major = Integer.parseInt((String)value);
                }
            }

            if (length >= 3) {
                Object value = filter.getObject(2);
                if (value instanceof IntNum) {
                    minor = ((IntNum)value).intValue();
                } else {
                    minor = Integer.parseInt((String)value);
                }
            }
        }

        this.uuid = beacon_uuid == null ? null : UUID.fromString(beacon_uuid);
        this.major = major;
        this.minor = minor;

        scanHandler.post(loopLeScan);
        scanHandler.postDelayed(ageBeacons, 1000);
        isScanning = true;
    }

    @SimpleFunction(description = "Stop looking for beacons.")
    public void StopScanningForBeacons() {
        if (!isScanning)
            return;

        uuid = null;
        major = -1;
        minor = -1;
        beacons.clear();

        uiThread.removeCallbacksAndMessages(null);
        scanHandler.removeCallbacks(loopLeScan);
        scanHandler.removeCallbacks(restartLeScan);
        scanHandler.removeCallbacks(ageBeacons);
        mBluetoothAdapter.stopLeScan(mLeScanCallback);
        isScanning = false;
    }

    private byte[] getBeaconAdvertisement(int rssi, final byte[] scanRecord) {
        int offset = 0;

        while (offset < scanRecord.length) {
            // Get the length of the AD structure
            int length = scanRecord[offset++];
            if (length == 0)
                continue;

            int type = scanRecord[offset++] & 0xFF;
            switch (type) {
                case 0xFF:
                    byte[] data = new byte[length - 1];
                    System.arraycopy(scanRecord, offset, data, 0, length - 1);
                    return data;
                default:
                    offset += length - 1;
                    continue;
            }
        }

        return null;
    }

    private Beacon parseManufactureData(int rssi, final byte[] data) {
        int offset = 0;
        if ( !(data[offset] == (byte)0x4C &&
                data[offset + 1] == (byte)0x00 &&
                data[offset + 2] == (byte)0x02 &&
                data[offset + 3] == (byte)0x15) ) {
            // Skip if this is not an iBeacon
            return null;
        }

        offset += 4;
        byte[] uuidBytes = new byte[16];
        System.arraycopy(data, offset, uuidBytes, 0, 16);

        String uuidWithNoDash = bytesToHex(uuidBytes);
        StringBuffer uuidBuffer = new StringBuffer(uuidWithNoDash);
        uuidBuffer.insert(20, '-');
        uuidBuffer.insert(16, '-');
        uuidBuffer.insert(12, '-');
        uuidBuffer.insert(8, '-');
        String uuid = uuidBuffer.toString();

        offset += 16;
        int major = (data[offset] & 0xFF) * 0x100 + (data[offset + 1] & 0xFF);
        int minor = (data[offset + 2] & 0xFF) * 0x100 + (data[offset + 3] & 0xFF);

        offset += 4;
        int txPower = (data[offset] & 0xFF) - 256;

        return new Beacon(UUID.fromString(uuid), major, minor, txPower, rssi);
    }

    // Byte Array to Hex String
    // http://stackoverflow.com/q/9655181/
    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private int indexOfBeacon(Beacon aBeacon) {
        int i = 0;
        for (Object[] beacon : beacons) {
            if (beacon[0].equals(aBeacon))
                return i;
            i++;
        }
        return -1;
    }

    private void sortBeaconDistances() {
        Collections.sort(beacons, new Comparator<Object[]>() {
            @Override
            public int compare(Object[] lhs, Object[] rhs) {
                return ((Beacon)lhs[0]).getAccuracy() < ((Beacon)rhs[0]).getAccuracy() ? -1 : ((Beacon)lhs[0]).getAccuracy() == ((Beacon)rhs[0]).getAccuracy() ? 0 : 1;
            }
        });
    }

}
