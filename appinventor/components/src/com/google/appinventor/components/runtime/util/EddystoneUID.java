package com.google.appinventor.components.runtime.util;

import java.util.Arrays;

public class EddystoneUID extends EddystoneBeacon {
    byte[] namespaceID = new byte[10];
    byte[] instance = new byte[6];

    public EddystoneUID(int rssi, int txPower, String deviceAddress, byte[] namespaceID, byte[] instance) {
        super(rssi, txPower, deviceAddress);
        this.namespaceID = namespaceID;
        this.instance = instance;
    }

    public byte[] getNamespaceID() {
        return namespaceID;
    }

    public String getNamespaceIDString() {
        return BeaconUtil.bytesToHex(namespaceID);
    }

    public byte[] getInstance() {
        return instance;
    }

    public String getInstanceString() {
        return BeaconUtil.bytesToHex(instance);
    }

    public String getBeaconIDString() {
        return getNamespaceIDString() + getInstanceString();
    }

    @Override
    public boolean equals(Object object) {
        if(!(object instanceof EddystoneUID) || object == null)
            return false;

        EddystoneUID other = (EddystoneUID)object;
        return getDeviceAddress().equals(other.getDeviceAddress())
                && Arrays.equals(namespaceID, other.namespaceID)
                && Arrays.equals(instance, other.instance);
    }
}
