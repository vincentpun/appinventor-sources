package com.google.appinventor.components.runtime.util;

import java.util.UUID;

public final class Beacon {

    enum Distance {
        Unknown,
        Immediate,
        Near,
        Far
    };

    public UUID uuid;
    public int major;
    public int minor;
    public int txPower;
    public int rssi;

    public Beacon(UUID uuid, int major, int minor, int txPower, int rssi) {
        this.uuid = uuid;
        this.major = major;
        this.minor = minor;
        this.txPower = txPower;
        this.rssi = rssi;
    }

    // Uses AltBeacon's algorithm
    // http://stackoverflow.com/q/20416218/
    public double getAccuracy() {
        if (rssi == 0) {
            return -1.0;
        }

        double ratio = rssi * 1.0 / txPower;
        if (ratio < 1.0) {
            return Math.pow(ratio, 10);
        } else {
            double accuracy =  (0.89976) * Math.pow(ratio, 7.7095) + 0.111;
            return accuracy;
        }
    }

    public Distance getDistance() {
        double accuracy = getAccuracy();
        if (accuracy == -1.0) {
            return Distance.Unknown;
        } else if (accuracy < 1) {
            return Distance.Immediate;
        } else if (accuracy < 3) {
            return Distance.Near;
        } else {
            return Distance.Far;
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Beacon))
            return false;

        if (object == this)
            return true;

        Beacon target = (Beacon)object;
        return (target.uuid.equals(this.uuid)) && (target.major == this.major) && (target.minor == this.minor);
    }

}
