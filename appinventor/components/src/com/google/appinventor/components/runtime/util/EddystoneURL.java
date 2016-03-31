package com.google.appinventor.components.runtime.util;

public final class EddystoneURL extends EddystoneBeacon {
    String url;

    public EddystoneURL(int rssi, int txPower, String deviceAddress, String url) {
        super(rssi, txPower, deviceAddress);
        this.url = url;
    }

    public String getURL() {
        return this.url;
    }

    @Override
    public boolean equals(Object object) {
        if(!(object instanceof EddystoneURL) || object == null)
            return false;

        EddystoneURL other = (EddystoneURL)object;
        return url.equals(other.url) && deviceAddress.equals(other.getDeviceAddress());
    }
}
