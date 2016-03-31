package com.google.appinventor.components.runtime.util;

public abstract class EddystoneBeacon {
    int rssi;
    int txPower;

    String deviceAddress;

    EddystoneTLM tlm = null;

    EddystoneBeacon(int rssi, int txPower, String deviceAddress) {
        this.rssi = rssi;
        this.txPower = txPower;
        this.deviceAddress = deviceAddress;
    }

    public final void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public final int getRssi() {
        return rssi;
    }

    public final int getTxPower() {
        return txPower;
    }

    public final EddystoneTLM getTelemetry() {
        return tlm;
    }

    public final void setTelemetry(EddystoneTLM tlm) {
        this.tlm = tlm;
    }

    public final String getDeviceAddress() {
        return deviceAddress;
    }

    public final void setDeviceAddress(String deviceAddress) {
        this.deviceAddress = deviceAddress;
    }

    @Override
    public abstract boolean equals(Object obj);

    // https://github.com/sandeepmistry/node-eddystone-beacon-scanner/blob/master/lib/eddystone-beacon-scanner.js
    final public double getDistance() {
        if (rssi == -1)
            return -1;
        else
            return Math.pow(10, ((txPower - rssi) - 41) / 20.0);
    }
}
