package com.google.appinventor.components.runtime.util;

public class EddystoneTLM {
    int version = 0;
    int batteryVoltage = 0;
    double temperature = -128.0;
    int advertisingPDUCount = 0;
    int timeOnSincePowerOn = 0;
    String deviceAddress = null;

    public EddystoneTLM(String deviceAddress, int version, int batteryVoltage, double temperature,
                        int advertisingPDUCount, int timeOnSincePowerOn) {
        this.deviceAddress = deviceAddress;
        this.version = version;
        this.batteryVoltage = batteryVoltage;
        this.temperature = temperature;
        this.advertisingPDUCount = advertisingPDUCount;
        this.timeOnSincePowerOn = timeOnSincePowerOn;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }

    public int getVersion() {
        return version;
    }

    public int getBatteryVoltage() {
        return batteryVoltage;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getAdvertisingPDUCount() {
        return advertisingPDUCount;
    }

    public int getTimeOnSincePowerOn() {
        return timeOnSincePowerOn;
    }
}
