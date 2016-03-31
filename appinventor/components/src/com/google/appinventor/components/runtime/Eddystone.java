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
import com.google.appinventor.components.runtime.util.*;
import gnu.lists.LList;
import gnu.lists.Pair;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * @author Vincent Pun (vpun@ymail.com)
 */

@DesignerComponent(version = YaVersion.EDDYSTONE_COMPONENT_VERSION, description = "Component for detecting Google Eddystone.", category = ComponentCategory.CONNECTIVITY, nonVisible = true, iconName = "images/eddystone.png")
@SimpleObject
@UsesPermissions(permissionNames = "android.permission.BLUETOOTH, " + "android.permission.BLUETOOTH_ADMIN")

public class Eddystone extends AndroidNonvisibleComponent implements Component {

    private static final String LOG_TAG = "EddystoneComponent";

    private enum EddystoneType {
        URL, UID, TLM
    }

    /**
     * BLE Components
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * States
     */
    private boolean isScanningEddystoneURL = false;
    private boolean isScanningEddystoneUID = false;

    /**
     * Eddystone-UID filtering criteria
     */
    private byte[] namespace = null;
    private byte[] instance = null;

    private ArrayList<Object[]> eddystoneURLs = new ArrayList<Object[]>();
    private ArrayList<Object[]> eddystoneUIDs = new ArrayList<Object[]>();
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
            if (isScanningEddystoneUID && eddystoneUIDs.size() > 0)
                EddystoneUIDFound(getFlattenedEddystonesUIDs(eddystoneUIDs));

            if (isScanningEddystoneURL && eddystoneURLs.size() > 0)
                EddystoneURLFound(getFlattenedEddystonesURLs(eddystoneURLs));

            scanHandler.postDelayed(loopLeScan, 10);
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    };

    private Runnable ageEddystones = new Runnable() {
        @Override
        public void run() {
            ArrayList<ArrayList> eddystonesToAge = new ArrayList<ArrayList>();
            if (isScanningEddystoneUID)
                eddystonesToAge.add(eddystoneUIDs);
            if (isScanningEddystoneURL)
                eddystonesToAge.add(eddystoneURLs);

            Iterator<ArrayList> eddystonesToAgeIterator = eddystonesToAge.iterator();
            while (eddystonesToAgeIterator.hasNext()) {
                ArrayList<Object []> next = eddystonesToAgeIterator.next();

                for (Object[] eddystone : next) {
                    eddystone[1] = (Integer) (eddystone[1]) + 1;

                    if ((Integer) eddystone[1] >= 4) {
                        ((EddystoneBeacon) eddystone[0]).setRssi(-1);

                        // Dispatch another event to let app update status
                        uiThread.removeCallbacksAndMessages(null);

                        if (next == eddystoneUIDs)
                            EddystoneUIDFound(getFlattenedEddystonesUIDs(eddystoneUIDs));
                        else if (next == eddystoneURLs)
                            EddystoneURLFound(getFlattenedEddystonesURLs(eddystoneURLs));
                    }
                }

                Iterator<Object[]> eddystonesIterator = next.iterator();
                while (eddystonesIterator.hasNext()) {
                    Object[] eddystone = eddystonesIterator.next();

                    if ((Integer)eddystone[1] >= 10) {
                        eddystonesIterator.remove();

                        if (next.size() == 0) {
                            // Dispatch another event to let app update status
                            uiThread.removeCallbacksAndMessages(null);

                            if (next == eddystoneUIDs)
                                EddystoneUIDFound(getFlattenedEddystonesUIDs(eddystoneUIDs));
                            else if (next == eddystoneURLs)
                                EddystoneURLFound(getFlattenedEddystonesURLs(eddystoneURLs));
                        }
                    }
                }
            }

            scanHandler.postDelayed(ageEddystones, 1000);
        }
    };

    private LeScanCallback mLeScanCallback = new LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (isScanningEddystoneURL)
                handleScanRecord(EddystoneType.URL, device.getAddress(), rssi, scanRecord);

            if (isScanningEddystoneUID)
                handleScanRecord(EddystoneType.UID, device.getAddress(), rssi, scanRecord);

            // Scan TLM regardless.
            handleScanRecord(EddystoneType.TLM, device.getAddress(), rssi,scanRecord);
        }
    };

    @SimpleEvent(description = "This will be called whenever the list of found Eddystone-URLs has changed.")
    public void EddystoneURLFound(final YailList eddystoneURLs) {
        uiThread.post(new Runnable() {
            @Override
            public void run() {
                EventDispatcher.dispatchEvent(Eddystone.this, "EddystoneURLFound", eddystoneURLs);
            }
        });
    }

    @SimpleEvent(description = "This will be called whenever the list of found Eddystone-UIDs has changed.")
    public void EddystoneUIDFound(final YailList eddystoneUIDs) {
        uiThread.post(new Runnable() {
            @Override
            public void run() {
                EventDispatcher.dispatchEvent(Eddystone.this, "EddystoneUIDFound", eddystoneUIDs);
            }
        });
    }

    public Eddystone(ComponentContainer container) {
        super(container.$form());
        Activity activity = container.$context();
        uiThread = new Handler();

        // Only initialize Bluetooth adapter if Android version is higher than 4.3.
        if (SdkLevel.getLevel() < SdkLevel.LEVEL_JELLYBEAN_MR2)
            mBluetoothAdapter = null;
        else
            mBluetoothAdapter = newBluetoothAdapter(activity);

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Log.e(LOG_TAG, "Bluetooth LE unsupported on device.");
            form.dispatchErrorOccurredEvent(this, "Initialization", ErrorMessages.ERROR_BLUETOOTH_NOT_ENABLED);
        }
    }

    private static BluetoothAdapter newBluetoothAdapter(Context context) {
        final BluetoothManager bluetoothManager = (BluetoothManager) context
                .getSystemService(Context.BLUETOOTH_SERVICE);
        return bluetoothManager.getAdapter();
    }

    @SimpleFunction(description = "Start looking for Eddystone-URLs.")
    public void StartScanningForEddystoneURL() {
        if (isScanningEddystoneURL)
            return;

        initiateScanCallbacksIfNeeded();

        isScanningEddystoneURL = true;
    }

    @SimpleFunction(description = "Start looking for Eddystone-UIDs.")
    public void StartScanningForEddystoneUID(YailList filter) {
        if (isScanningEddystoneUID)
            return;

        int length = filter.size();
        byte[] namespaceBytes = null;
        byte[] instanceBytes = null;

        if (length >= 1) {
            String namespace = filter.getString(0);
            namespaceBytes = BeaconUtil.hexToByteArray(namespace);
            if (namespaceBytes == null || namespaceBytes.length != 10) {
                namespaceBytes = null;
            }

            if (length >= 2) {
                String instance = filter.getString(1);
                instanceBytes = BeaconUtil.hexToByteArray(instance);

                if (instanceBytes == null || instanceBytes.length != 6) {
                    instanceBytes = null;
                }
            }
        }

        this.namespace = namespaceBytes;
        this.instance = instanceBytes;

        initiateScanCallbacksIfNeeded();

        isScanningEddystoneUID = true;
    }

    @SimpleFunction(description = "Stop looking for all kinds of Eddystones.")
    public void StopScanningForAllEddystones() {
        if (!isScanningAnyEddystone())
            return;

        StopScanningForEddystoneURL();
        StopScanningForEddystoneUID();
    }

    @SimpleFunction(description = "Stop looking for Eddystone-URLs.")
    public void StopScanningForEddystoneURL() {
        if (!isScanningEddystoneURL)
            return;

        eddystoneURLs.clear();
        isScanningEddystoneURL = false;

        stopScanCallbacksIfNeeded();
    }

    @SimpleFunction(description = "Stop looking for Eddystone-UIDs.")
    public void StopScanningForEddystoneUID() {
        if (!isScanningEddystoneUID)
            return;

        eddystoneUIDs.clear();
        namespace = null;
        instance = null;
        isScanningEddystoneUID = false;

        stopScanCallbacksIfNeeded();
    }

    private boolean isScanningAnyEddystone() {
        return isScanningEddystoneUID || isScanningEddystoneURL;
    }

    private void initiateScanCallbacksIfNeeded() {
        if (isScanningAnyEddystone())
            return;

        scanHandler.post(loopLeScan);
        scanHandler.postDelayed(ageEddystones, 1000);
    }

    private void stopScanCallbacksIfNeeded() {
        if (!isScanningAnyEddystone())
            return;

        uiThread.removeCallbacksAndMessages(null);
        scanHandler.removeCallbacks(loopLeScan);
        scanHandler.removeCallbacks(restartLeScan);
        scanHandler.removeCallbacks(ageEddystones);
        mBluetoothAdapter.stopLeScan(mLeScanCallback);
    }

    private void handleScanRecord(EddystoneType type, String deviceAddress, int rssi, byte[] scanRecord) {
        byte[] eddystoneData = getEddystoneAdvertisement(type, scanRecord);

        if (eddystoneData != null)
            handleEddystoneDataAndUpdate(type, deviceAddress, rssi, eddystoneData);
    }

    private void handleEddystoneDataAndUpdate(EddystoneType type, String deviceAddress, int rssi, final byte[] data) {
        EddystoneBeacon parsedEddystone = null;
        ArrayList<Object[]> targetEddystones = null;

        switch(type) {
            case UID:
                parsedEddystone = parseEddystoneUID(deviceAddress, rssi, data);

                if (parsedEddystone == null)
                    return;

                if (namespace != null && !Arrays.equals(((EddystoneUID)parsedEddystone).getNamespaceID(), namespace))
                    return;

                if (instance != null && !Arrays.equals(((EddystoneUID)parsedEddystone).getInstance(), instance))
                    return;

                targetEddystones = eddystoneURLs;
                break;
            case URL:
                parsedEddystone = parseEddystoneURL(deviceAddress, rssi, data);
                targetEddystones = eddystoneUIDs;
                break;
            case TLM:
                // Special case
                EddystoneTLM tlm = parseEddystoneTLM(deviceAddress, data);

                if (tlm != null)
                    updateEddystonesTLM(tlm, rssi);

                return;
        }

        if (parsedEddystone == null || targetEddystones == null)
            return;

        updateEddystonesArray(parsedEddystone, rssi);
        sortEddystoneDistances(targetEddystones);
    }

    private void updateEddystonesTLM(EddystoneTLM tlm, int rssi) {
        ArrayList<ArrayList> eddystonesToSearch = new ArrayList<ArrayList>();

        if (isScanningEddystoneUID)
            eddystonesToSearch.add(eddystoneUIDs);
        if (isScanningEddystoneURL)
            eddystonesToSearch.add(eddystoneURLs);

        Log.e(LOG_TAG, eddystoneURLs.toString());

        for (ArrayList<Object[]> eddystoneArray: eddystonesToSearch) {
            for (Object[] eddystones: eddystoneArray) {
                EddystoneBeacon eddystone = (EddystoneBeacon)(eddystones[0]);

                if (eddystone.getDeviceAddress().equals(tlm.getDeviceAddress())) {
                    eddystone.setTelemetry(tlm);

                    updateEddystonesArray(eddystone, rssi);
                    sortEddystoneDistances(eddystoneArray);
                }
            }
        }
    }

    private void updateEddystonesArray(EddystoneBeacon eddystone, int rssi) {
        if (eddystone == null)
            return;

        ArrayList<Object[]> targetEddystones;

        if (eddystone instanceof EddystoneUID)
            targetEddystones = eddystoneUIDs;
        else if (eddystone instanceof EddystoneURL)
            targetEddystones = eddystoneURLs;
        else
            return;

        int index = indexOfEddystoneInEddystonesList(eddystone, targetEddystones);

        if (index < 0) {
            targetEddystones.add(new Object[] {eddystone, 0});
        } else {
            ((EddystoneBeacon) targetEddystones.get(index)[0]).setRssi(rssi);
            ((EddystoneBeacon) targetEddystones.get(index)[0]).setTelemetry(eddystone.getTelemetry());
            targetEddystones.get(index)[1] = 0;
        }
    }

    private byte[] getEddystoneAdvertisement(EddystoneType eddystoneType, final byte[] scanRecord) {
        if (eddystoneType == null)
            return null;

        int offset = 0;

        while (offset < scanRecord.length) {
            // Get the length of the AD structure
            int length = scanRecord[offset++];
            if (length == 0)
                continue;

            int type = (scanRecord[offset++] & 0xFF);
            switch (type) {
                case 0x03:
                case 0x16:
                    int serviceUUID =
                            (scanRecord[offset++] & 0xFF) * 0x100 + (scanRecord[offset++] & 0xFF);
                    if (serviceUUID != 0xAAFE)  // This is not an Eddystone.
                        return null;

                    if (type == 0x03)
                        continue;

                    int eddystoneFrameType = (scanRecord[offset++] & 0xFF);
                    if ((eddystoneType == EddystoneType.UID && eddystoneFrameType != 0x00)
                            || (eddystoneType == EddystoneType.URL && eddystoneFrameType != 0x10)
                            || (eddystoneType == EddystoneType.TLM && eddystoneFrameType != 0x20)) {
                        offset += length - 3;
                        continue;
                    }

                    return Arrays.copyOfRange(scanRecord, offset, offset + length - 4);
                default:
                    offset += length - 1;
                    continue;
            }
        }

        return null;
    }

    private EddystoneURL parseEddystoneURL(String deviceAddress, int rssi, final byte[] data) {
        int offset = 0;
        byte txPowerByte = data[offset++];
        int txPower = BeaconUtil.getTxPower(txPowerByte);

        StringBuffer urlBuffer = new StringBuffer();
        int urlScheme = data[offset++];

        if (urlScheme > 3) // Range of URL scheme is only 0-3
            return null;

        urlBuffer.append(EddystoneConstants.URL_SCHEME[urlScheme]);

        for (; offset < data.length; offset++) {
            int urlEncoding = data[offset];

            if (urlEncoding >= 0 && urlEncoding <= 13)
                urlBuffer.append(EddystoneConstants.URL_ENCODING[urlEncoding]);
            else if (urlEncoding >= 33 && urlEncoding <= 126)
                urlBuffer.append((char)urlEncoding);
        }

        String url = urlBuffer.toString();

        return new EddystoneURL(rssi, txPower, deviceAddress, url);
    }

    private EddystoneUID parseEddystoneUID(String deviceAddress, int rssi, final byte[] data) {
        int offset = 0;
        byte txPowerByte = data[offset++];
        int txPower = BeaconUtil.getTxPower(txPowerByte);

        byte[] namespaceID = Arrays.copyOfRange(data, offset, offset + 10);
        offset += 10;

        byte[] instance = Arrays.copyOfRange(data, offset, offset + 6);
        offset += 6;

        if (data[offset] != 0x00 || data[offset++] != 0x00)
            return null;

        return new EddystoneUID(rssi, txPower, deviceAddress, namespaceID, instance);
    }

    private EddystoneTLM parseEddystoneTLM(String deviceAddress, final byte[] tlmData) {
        int offset = 0;
        int version = tlmData[offset++];

        ByteBuffer byteBuffer;

        byte[] batteryVoltageBytes = Arrays.copyOfRange(tlmData, offset, offset + 2);
        byteBuffer = ByteBuffer.wrap(batteryVoltageBytes);
        int batteryVoltage = byteBuffer.getShort();
        offset += 2;

        int temperatureFixed = tlmData[offset++];
        int temperatureFraction = tlmData[offset++];
        double temperature = (temperatureFixed * 0x100 + temperatureFraction) / 256.0;  // 8.8 fixed-point notation

        byte[] advertisingPDUCountBytes = Arrays.copyOfRange(tlmData, offset, offset + 4);
        byteBuffer = ByteBuffer.wrap(advertisingPDUCountBytes);
        int advertisingPDUCount = byteBuffer.getInt();
        offset += 4;

        byte[] secondCountBytes = Arrays.copyOfRange(tlmData, offset, offset + 4);
        byteBuffer = ByteBuffer.wrap(secondCountBytes);
        int secondCount = byteBuffer.getInt();

        return new EddystoneTLM(deviceAddress, version, batteryVoltage, temperature, advertisingPDUCount, secondCount);
    }

    private int indexOfEddystoneInEddystonesList(EddystoneBeacon anEddystonBeacon, ArrayList<Object[]> eddystones) {
        int i = 0;
        for (Object[] eddystone : eddystones) {
            if (anEddystonBeacon.equals(eddystone[0]))
                return i;
            i++;
        }
        return -1;
    }

    private YailList getFlattenedEddystonesURLs(ArrayList<Object[]> eddystoneURLs) {
        ArrayList<YailList> flattenedEddystones = new ArrayList<YailList>();

        for(Object[] eddystoneURLRecord: eddystoneURLs) {
            EddystoneURL eddystoneURL = (EddystoneURL) eddystoneURLRecord[0];
            EddystoneTLM tlm = eddystoneURL.getTelemetry();

            int numberOfItems = 4;
            if (tlm != null)
                numberOfItems++;

            Object[] pairs = new Pair[numberOfItems];
            pairs[0] = YailList.makeList(new Object[] {"URL", eddystoneURL.getURL()});
            pairs[1] = YailList.makeList(new Object[] {"TX Power", String.valueOf(eddystoneURL.getTxPower())});
            pairs[2] = YailList.makeList(new Object[] {"RSSI", String.valueOf(eddystoneURL.getRssi())});
            pairs[3] = YailList.makeList(new Object[] {"Distance", String.valueOf(eddystoneURL.getDistance())});

            if (tlm != null)
                pairs[4] = YailList.makeList(new Object[] {"TLM", getFlattenedEddystoneTLM(tlm)});

            YailList eddystoneRecord = YailList.makeList(pairs);
            flattenedEddystones.add(eddystoneRecord);
        }

        return YailList.makeList(flattenedEddystones.toArray(new YailList[flattenedEddystones.size()]));
    }

    private YailList getFlattenedEddystonesUIDs(ArrayList<Object[]> eddystoneUIDs) {
        ArrayList<YailList> flattenedEddystones = new ArrayList<YailList>();

        for(Object[] eddystoneUIDRecord: eddystoneUIDs) {
            EddystoneUID eddystoneUID = (EddystoneUID)eddystoneUIDRecord[0];
            EddystoneTLM tlm = eddystoneUID.getTelemetry();

            int numberOfItems = 6;
            if (tlm != null)
                numberOfItems++;

            Object[] pairs = new Object[numberOfItems];

            pairs[0] =
                    YailList.makeList(new Object[] {"Namespace", eddystoneUID.getNamespaceIDString()});
            pairs[1] =
                    YailList.makeList(new Object[] {"Instance", eddystoneUID.getInstanceString()});
            pairs[2] =
                    YailList.makeList(new Object[] {"Beacon ID", eddystoneUID.getBeaconIDString()});
            pairs[3] =
                    YailList.makeList(new Object[] {"TX Power", String.valueOf(eddystoneUID.getTxPower())});
            pairs[4] =
                    YailList.makeList(new Object[] {"RSSI", String.valueOf(eddystoneUID.getRssi())});
            pairs[5] =
                    YailList.makeList(new Object[] {"Distance", String.valueOf(eddystoneUID.getDistance())});

            if (tlm != null)
                pairs[6] = YailList.makeList(new Object[] {"TLM", getFlattenedEddystoneTLM(tlm)});

            YailList eddystoneRecord = YailList.makeList(pairs);
            flattenedEddystones.add(eddystoneRecord);
        }

        return YailList.makeList(flattenedEddystones.toArray(new YailList[flattenedEddystones.size()]));
    }

    private YailList getFlattenedEddystoneTLM(EddystoneTLM eddystoneTLM) {
        if (eddystoneTLM == null)
            return null;

        Object[] items = new Object[5];

        items[0] =
                YailList.makeList(new Object[]{"Version", String.valueOf(eddystoneTLM.getVersion())});
        items[1] =
                YailList.makeList(new Object[]{"Battery Voltage", String.valueOf(eddystoneTLM.getBatteryVoltage())});
        items[2] =
                YailList.makeList(new Object[]{"Temperature", String.valueOf(eddystoneTLM.getTemperature())});
        items[3] =
                YailList.makeList
                        (new Object[] {"Advertising PDU Count", String.valueOf(eddystoneTLM.getAdvertisingPDUCount())});
        items[4] =
                YailList.makeList
                        (new Object[] {"Time Since Power On", String.valueOf(eddystoneTLM.getTimeOnSincePowerOn())});

        return YailList.makeList(items);
    }

    private void sortEddystoneDistances(ArrayList<Object[]> targetEddystones) {
        if (targetEddystones == null)
            return;

        Collections.sort(targetEddystones, new Comparator<Object[]>() {
            @Override
            public int compare(Object[] lhs, Object[] rhs) {
                return ((EddystoneBeacon)lhs[0]).getDistance() < ((EddystoneBeacon)rhs[0]).getDistance() ? -1 :
                        ((EddystoneBeacon)lhs[0]).getDistance() == ((EddystoneBeacon)rhs[0]).getDistance() ? 0 :
                                1;
            }
        });
    }

}
