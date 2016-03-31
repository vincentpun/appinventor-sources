package com.google.appinventor.components.runtime.util;

/**
 * Helper methods for Beacons
 *
 * @author Vincent Pun
 *
 */
public class BeaconUtil {
    private BeaconUtil() {
    }

    // Byte Array to Hex String
    // http://stackoverflow.com/q/9655181/
    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexToByteArray(String string) {
        int length = string.length();
        byte[] byteArray = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            byteArray[i / 2] =
                    (byte)(Character.digit(string.charAt(i), 16) << 4 + Character.digit(string.charAt(i + 1), 16));
        }
        return byteArray;
    }

    public static int getTxPower(byte txPowerByte) {
        int txPower;

        // Tx Power is a signed 8-bit integer. We convert the 2s complemented integer back to a normal decimal.
        if (txPowerByte < 127)    // Positive: Convert to decimal.
            txPower = txPowerByte;
        else
            txPower = txPowerByte - 256;

        return txPower;
    }
}
