package com.google.appinventor.components.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Collections;
import java.util.Comparator;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.util.ErrorMessages;
import com.google.appinventor.components.runtime.util.SdkLevel;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Handler;
import android.util.Log;
import android.content.Context;
import android.app.Activity;

/**
 * @author Tony Chan & Beibei ZHANG ( kwong3513@yahoo.com.hk & beibei.zhang@connect.polyu.hk )
 */

@DesignerComponent(version = YaVersion.BLE_COMPONENT_VERSION, description = "This is a trial version of BLE component, blocks need to be specified later", category = ComponentCategory.CONNECTIVITY, nonVisible = true, iconName = "images/ble.png")
@SimpleObject
@UsesPermissions(permissionNames = "android.permission.BLUETOOTH, " + "android.permission.BLUETOOTH_ADMIN")

public class BLE extends AndroidNonvisibleComponent implements Component {

	/**
	 * Basic Variable
	 */
	private static final String LOG_TAG = "BLEComponent";
	private final Activity activity;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothGatt currentBluetoothGatt;
	private int device_rssi = 0;
	private final Handler uiThread;
	private boolean mLogEnabled = true;
	private String mLogMessage;

	// testing
	// private List<BluetoothGattCharacteristic> mGattCharList;
	// private List<BluetoothGattDescriptor> mGattDes;

	/**
	 * BLE Info List
	 */
	private HashMap<String, BluetoothGatt> gattList;
	private String deviceInfoList = "";
	private List<BluetoothDevice> mLeDevices;
	private List<BluetoothGattService> mGattService;
	private BluetoothGattCharacteristic mGattChar;
	private HashMap<BluetoothDevice, Integer> mLeDeviceRssi;

	/**
	 * BLE Device Status
	 */
	private boolean isEnabled = false;
	private boolean isScanning = false;
	private boolean isConnected = false;
	private boolean isCharRead = false;
	private boolean isCharWrite = false;
	private boolean isServiceRead = false;

	/**
	 * GATT value
	 */
	private int battery = -1;
	private String tempUnit = "";
	private byte[] bodyTemp;
	private byte[] heartRate;
	private int linkLoss_value = -1;
	private int txPower = -1;
	private byte[] data;
	private byte[] descriptorValue;
	private int intValue = 0;
	private float floatValue = 0;
	private String stringValue="";
	private String byteValue="";
	private int intOffset = 0;
	private int strOffset = 0;
	private int floatOffset = 0;
	

	public BLE(ComponentContainer container) {
		super(container.$form());
		activity = (Activity) container.$context();
		mLeDevices = new ArrayList<BluetoothDevice>();
		mGattService = new ArrayList<BluetoothGattService>();
		mLeDeviceRssi = new HashMap<BluetoothDevice, Integer>();
		gattList = new HashMap<String, BluetoothGatt>();
		uiThread = new Handler();

		if (SdkLevel.getLevel() < SdkLevel.LEVEL_JELLYBEAN_MR2) {
			mBluetoothAdapter = null;
		} else {
			mBluetoothAdapter = newBluetoothAdapter(activity);
		}

		if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
			isEnabled = false;
			LogMessage("No Valid BTLE Device on platform", "e");
			form.dispatchErrorOccurredEvent(this, "WICEDSense", ErrorMessages.ERROR_BLUETOOTH_NOT_ENABLED);
		} else {
			isEnabled = true;
			LogMessage("BLE Device found", "i");
		}
	}

	private void LogMessage(String message, String level) {
		if (mLogEnabled) {
			mLogMessage = message;
			String errorLevel = "e";
			String warningLevel = "w";

			// push to appropriate logging
			if (level.equals(errorLevel)) {
				Log.e(LOG_TAG, message);
			} else if (level.equals(warningLevel)) {
				Log.w(LOG_TAG, message);
			} else {
				Log.i(LOG_TAG, message);
			}
		}
	}

	public static BluetoothAdapter newBluetoothAdapter(Context context) {
		final BluetoothManager bluetoothManager = (BluetoothManager) context
				.getSystemService(Context.BLUETOOTH_SERVICE);
		return bluetoothManager.getAdapter();
	}

	@SimpleFunction(description="Start Scanning BLE device.")
	public void StartScanning() {
		if (!mLeDevices.isEmpty()) {
			mLeDevices.clear();
			mLeDeviceRssi.clear();
		}
		mBluetoothAdapter.startLeScan(mLeScanCallback);
		LogMessage("StarScanning Successfully.", "i");
	}

	@SimpleFunction(description="Stop Scanning BLE device.")
	public void StopScanning() {
		mBluetoothAdapter.stopLeScan(mLeScanCallback);
		LogMessage("StopScanning Successfully.", "i");
	}

	@SimpleFunction(description="Connect to BLE device with index. Index specifies the position of DeviceList.")
	public void Connect(int index) {
		BluetoothGattCallback newGattCallback = null;
		currentBluetoothGatt = mLeDevices.get(index - 1).connectGatt(activity, false, initCallBack(newGattCallback));
		if(currentBluetoothGatt != null) {
			gattList.put(mLeDevices.get(index - 1).toString(), currentBluetoothGatt);
			LogMessage("Connect Successfully.", "i");
		} else {
			LogMessage("Connect Fail.", "e");
		}
	}

	@SimpleFunction(description="Connect to BLE device with address. Address specifies bluetooth address of the BLE device.")
	public void ConnectWithAddress(String address) {
		for (BluetoothDevice bluetoothDevice : mLeDevices) {
			if (bluetoothDevice.toString().equals(address)) {
				BluetoothGattCallback newGattCallback = null;
				currentBluetoothGatt = bluetoothDevice.connectGatt(activity, false, initCallBack(newGattCallback));
				if(currentBluetoothGatt != null) {
					gattList.put(bluetoothDevice.toString(), currentBluetoothGatt);
					LogMessage("Connect with Address Successfully.", "i");
					break;
				} else {
					LogMessage("Connect with Address Fail.", "e");
				}
			}
		}
	}

	@SimpleFunction(description="Disconnect from connected BLE device with address. Address specifies bluetooth address of the BLE device.")
	public void DisconnectWithAddress(String address) {
		if (gattList.containsKey(address)) {
			gattList.get(address).disconnect();
			isConnected = false;
			gattList.remove(address);
			LogMessage("Disconnect Successfully.", "i");
		} else {
			LogMessage("Disconnect Fail. No Such Address in the List", "e");
		}
	}

	
	@SimpleFunction(description="Write String value to a connected BLE device. Service UUID, Characteristic UUID and String value"
			+ "are required.")
	public void WriteStringValue(String service_uuid, String characteristic_uuid, String value) {
		writeChar(UUID.fromString(service_uuid), UUID.fromString(characteristic_uuid), value);
	}
	
	
	@SimpleFunction(description="Write Integer value to a connected BLE device. Service UUID, Characteristic UUID, Integer value"
			+ " and offset are required. Offset specifies the start position of writing data.")
	public void WriteIntValue(String service_uuid, String characteristic_uuid, int value, int offset) {
		writeChar(UUID.fromString(service_uuid), UUID.fromString(characteristic_uuid), value, BluetoothGattCharacteristic.FORMAT_SINT32, offset);
	}
	

	@SimpleFunction(description="Read Integer value from a connected BLE device. Service UUID, Characteristic UUID and offset"
			+ " are required. Offset specifies the start position of reading data.")
	public void ReadIntValue(String service_uuid, String characteristic_uuid, int intOffset) {
		this.intOffset = intOffset;
		readChar(UUID.fromString(service_uuid), UUID.fromString(characteristic_uuid));
	}
	
	
	@SimpleFunction(description="Read String value from a connected BLE device. Service UUID, Characteristic UUID and offset"
			+ " are required. Offset specifies the start position of reading data.")
	public void ReadStringValue(String service_uuid, String characteristic_uuid, int strOffset) {
		this.strOffset = strOffset;
		readChar(UUID.fromString(service_uuid), UUID.fromString(characteristic_uuid));
	}
	
	
	@SimpleFunction(description="Read Float value from a connected BLE device. Service UUID, Characteristic UUID and offset"
			+ " are required. Offset specifies the start position of reading data.")
	public void ReadFloatValue(String service_uuid, String characteristic_uuid, int floatOffset) {
		this.floatOffset = floatOffset;
		readChar(UUID.fromString(service_uuid), UUID.fromString(characteristic_uuid));
	}
	
	
	@SimpleFunction(description="Read Byte value from a connected BLE device. Service UUID and Characteristic UUID are required.")
	public void ReadByteValue(String service_uuid, String characteristic_uuid) {
		readChar(UUID.fromString(service_uuid), UUID.fromString(characteristic_uuid));
	}

	
	@SimpleFunction(description="Write Alert Level to a connected BLE device with Alert Level Service. Alert Level can be 0, 1 and 2."
			+ " 0 for no alert; 1 for mid alert; 2 for high alert.")
	public void WriteFindMe(int findMe_value) {
		if (findMe_value <= 2 && findMe_value >= 0) {
			writeChar(BLEList.FINDME_SER, BLEList.FINDME_CHAR, findMe_value, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
		}
	}

	
	@SimpleFunction(description="Set Link Loss value to a connected BLE device with Link Loss Service. Link Loss value can be 0, 1 and 2."
			+ " 0 for no alert; 1 for mid alert; 2 for high alert.")
	public void SetLinkLoss(int value) {
		if (value <= 2 && value >= 0) {
			linkLoss_value = value;
			writeChar(BLEList.LINKLOSS_SER, BLEList.LINKLOSS_CHAR, value, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
		}
	}
	

	@SimpleFunction(description="Read Battery level from a connected BLE device with Battery Service.")
	public void ReadBattery() {
		readChar(BLEList.BATTERY_LEVEL_SER, BLEList.BATTERY_LEVEL_CHAR);
	}

	
	@SimpleFunction(description="Read Temperature from a connected BLE device with Health Thermometer Service.")
	public void ReadTemperature() {
		readChar(BLEList.THERMOMETER_SER, BLEList.THERMOMETER_CHAR);
	}

	
	@SimpleFunction(description="Read Heart Rate from a connected BLE device with Heart Rate Service.")
	public void ReadHeartRate() {
		readChar(BLEList.HEART_RATE_SER, BLEList.HEART_RATE_MEASURE_CHAR);
	}

	
	@SimpleFunction(description="Read Tx power from a connected BLE device with Tx Power Service.")
	public void ReadTxPower() {
		readChar(BLEList.TXPOWER_SER, BLEList.TXPOWER_CHAR);
	}

	
	@SimpleFunction(description="Get the Rssi of found device by index. Index specifies the position of DeviceList.")
	public int FoundDeviceRssi(int index) {
		if (index <= mLeDevices.size())
			return mLeDeviceRssi.get(mLeDevices.get(index-1));
		else
			return -1;
	}

	
	@SimpleFunction(description="Get the name of found device by index. Index specifies the position of DeviceList.")
	public String FoundDeviceName(int index) {
		if (index <= mLeDevices.size()) {
			LogMessage("Device Name is found", "i");
			return mLeDevices.get(index-1).getName();
		} else {
			LogMessage("Device Name isn't found", "e");
			return null;
		}
	}

	
	@SimpleFunction(description="Get the address of found device by index. Index specifies the position of DeviceList.")
	public String FoundDeviceAddress(int index) {
		if (index <= mLeDevices.size()) {
			LogMessage("Device Address is found", "i");
			return mLeDevices.get(index-1).getAddress();
		} else {
			LogMessage("Device Address is found", "e");
			return "";
		}
	}

	
	@SimpleProperty(description="Return the battery level.", category = PropertyCategory.BEHAVIOR)
	public String BatteryValue() {
		if (isCharRead) {
			return Integer.toString(battery);
		} else {
			return "Cannot Read Battery Level";
		}
	}

	
	@SimpleProperty(description="Return the temperature.", category = PropertyCategory.BEHAVIOR)
	public String TemperatureValue() {
		if (isCharRead) {
			if ((int) bodyTemp[0] == 0) {
				tempUnit = "Celsius";
			} else {
				tempUnit = "Fahrenheit";
			}
			float mTemp = ((bodyTemp[2] & 0xff) << 8) + (bodyTemp[1] & 0xff);
			LogMessage("Temperature value is returned", "i");
			return mTemp + tempUnit;
		} else {
			LogMessage("Cannot read temperature value", "e");
			return "Cannot Read Temperature";
		}
	}

	
	@SimpleProperty(description="Return the heart rate.", category = PropertyCategory.BEHAVIOR)
	public String HeartRateValue() {
		if (isCharRead) {
			int mTemp = 0;
			if (((int) (heartRate[0] & 0x1)) == 0) {
				mTemp = (heartRate[1] & 0xff);
			} else {
				mTemp = (heartRate[2] & 0xff);
			}
			LogMessage("Heart rate value is returned", "i");
			return mTemp + "times/sec";
		} else {
			LogMessage("Cannot read heart rate value", "e");
			return "Cannot Read Heart Rate";
		}
	}

	
	@SimpleProperty(description="Return the Tx power.", category = PropertyCategory.BEHAVIOR)
	public int TxPower() {
		return txPower;
	}

	
	@SimpleProperty(description="Return the link loss value.", category = PropertyCategory.BEHAVIOR)
	public String LinkLossValue() {
		if (linkLoss_value == 0) {
			return "No Alert";
		} else if (linkLoss_value == 1) {
			return "Mid Alert";
		} else {
			return "High Alert";
		}
	}

	
	@SimpleProperty(description="Return true if BLE device is connected; Otherwise, return false.", category = PropertyCategory.BEHAVIOR)
	public boolean IsDeviceConnected() {
		if (isConnected) {
			return true;
		} else {
			return false;
		}
	}
	

	@SimpleProperty(description="Return a sorted BLE device list. The return type is String.", category = PropertyCategory.BEHAVIOR)
	public String DeviceList() {
		deviceInfoList = "";
		mLeDevices = sortDeviceList(mLeDevices);
		if (!mLeDevices.isEmpty()) {
			for (int i = 0; i < mLeDevices.size(); i++) {
				if (i != (mLeDevices.size() - 1)) {
					deviceInfoList += mLeDevices.get(i).getAddress() + " " + mLeDevices.get(i).getName() + " "
							+ Integer.toString(mLeDeviceRssi.get(mLeDevices.get(i))) + ",";
				} else {
					deviceInfoList += mLeDevices.get(i).getAddress() + " " + mLeDevices.get(i).getName() + " "
							+ Integer.toString(mLeDeviceRssi.get(mLeDevices.get(i)));
				}
			}
		}
		return deviceInfoList;
	}

	
	@SimpleProperty(description="Return the Rssi of connected device.", category = PropertyCategory.BEHAVIOR)
	public String ConnectedDeviceRssi() {
		return Integer.toString(device_rssi);
	}

	
	@SimpleProperty(description="Return Integer value of read value.", category = PropertyCategory.BEHAVIOR)
	public int IntGattValue() {
		return intValue;
	}

	
	@SimpleProperty(description="Return String value of read value.", category = PropertyCategory.BEHAVIOR)
	public String StringGattValue() {
		return stringValue;
	}

	
	@SimpleProperty(description="Return Byte value of read value.", category = PropertyCategory.BEHAVIOR)
	public String ByteGattValue() {
		return byteValue;
	}
	

	@SimpleEvent(description = "The event will be triggered when BLE device is connected.")
	public void Connected() {
		uiThread.post(new Runnable() {
			@Override
			public void run() {
				EventDispatcher.dispatchEvent(BLE.this, "Connected");
			}
		});
	}

	
	@SimpleEvent(description = "The event will be triggered when Rssi of found BLE device is changed.")
	public void RssiChanged() {
		uiThread.postDelayed(new Runnable() {
			@Override
			public void run() {
				EventDispatcher.dispatchEvent(BLE.this, "RssiChanged");
			}

		}, 1000);
	}

	
	@SimpleEvent(description = "The event will be triggered when a new BLE device is found.")
	public void DeviceFound() {
		EventDispatcher.dispatchEvent(this, "DeviceFound");
	}

	
	@SimpleEvent(description = "The event will be triggered when value from connected BLE device is read. The value"
			+ " can be byte, Integer, float and String.")
	public void ValueRead(final String byteValue, final int intValue, final float floatValue, final String stringValue) {
		uiThread.post(new Runnable() {
			@Override
			public void run() {
				EventDispatcher.dispatchEvent(BLE.this, "ValueRead", byteValue, intValue, floatValue, stringValue);
			}
		});
	}

	
	@SimpleEvent(description = "The event will be triggered when value from connected BLE device is changed. The value"
			+ " can be byte, Integer, float and String.")
	public void ValueChanged(final String byteValue, final int intValue, final float floatValue, final String stringValue) {
		uiThread.post(new Runnable() {
			@Override
			public void run() {
				EventDispatcher.dispatchEvent(BLE.this, "ValueChanged", byteValue, intValue, floatValue, stringValue);
			}
		});
	}
	
	
	@SimpleEvent(description = "The event will be triggered when value is successful write to connected BLE device.")
	public void ValueWrite() {
		uiThread.post(new Runnable() {
			@Override
			public void run() {
				EventDispatcher.dispatchEvent(BLE.this, "ValueWrite");
			}
		});
	}

	
	/**
	 * Functions
	 */
	// sort the device list by RSSI
	private List<BluetoothDevice> sortDeviceList(List<BluetoothDevice> deviceList) {
		Collections.sort(deviceList, new Comparator<BluetoothDevice>() {
			@Override
			public int compare(BluetoothDevice device1, BluetoothDevice device2) {
				int result = mLeDeviceRssi.get(device1) - mLeDeviceRssi.get(device2);
				return result;
			}
		});
		Collections.reverse(deviceList);
		return deviceList;
	}

	
	// add device when scanning
	private void addDevice(BluetoothDevice device, int rssi, byte[] scanRecord) {
		if (!mLeDevices.contains(device)) {
			mLeDevices.add(device);
			mLeDeviceRssi.put(device, rssi);
			DeviceFound();
		} else {
			mLeDeviceRssi.put(device, rssi);
		}
		RssiChanged();
	}

	
	// read characteristic based on UUID
	private void readChar(UUID ser_uuid, UUID char_uuid) {
		if (isServiceRead && !mGattService.isEmpty()) {
			for (int i = 0; i < mGattService.size(); i++) {
				if (mGattService.get(i).getUuid().equals(ser_uuid)) {
					
					BluetoothGattDescriptor desc = mGattService.get(i).getCharacteristic(char_uuid)
							.getDescriptor(BLEList.CHAR_CONFIG_DES);
					
					mGattChar = mGattService.get(i).getCharacteristic(char_uuid);
					
					if (desc != null) {
						if ((mGattService.get(i).getCharacteristic(char_uuid).getProperties() & 
							 BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
							desc.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
						} else {
							desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
						}
						currentBluetoothGatt.writeDescriptor(desc);
					}
					
					if(mGattChar != null) {
						currentBluetoothGatt.setCharacteristicNotification(mGattChar, true);
						isCharRead = currentBluetoothGatt.readCharacteristic(mGattChar);
					}
					break;
				}
			}
		}
		
		if(isCharRead == true) {
			LogMessage("Read Character Successfully.", "i");
		} else {
			LogMessage("Read Character Fail.", "i");
		}
	}

	
	// Write characteristic based on uuid
	private void writeChar(UUID ser_uuid, UUID char_uuid, int value, int format, int offset) {
		if (isServiceRead && !mGattService.isEmpty()) {
			for (int i = 0; i < mGattService.size(); i++) {
				if (mGattService.get(i).getUuid().equals(ser_uuid)) {
					mGattChar = mGattService.get(i).getCharacteristic(char_uuid);
					if (mGattChar != null) {
						mGattChar.setValue(value, format, offset);
						isCharWrite = currentBluetoothGatt.writeCharacteristic(mGattChar);
					}
					break;
				}
			}
		}
		
		if(isCharWrite == true) {
			LogMessage("Write Gatt Characteristic Successfully", "i");
		} else {
			LogMessage("Write Gatt Characteristic Fail", "e");
		}
	}

	private void writeChar(UUID ser_uuid, UUID char_uuid, String value) {
		if (isServiceRead && !mGattService.isEmpty()) {
			for (int i = 0; i < mGattService.size(); i++) {
				if (mGattService.get(i).getUuid().equals(ser_uuid)) {
					mGattChar = mGattService.get(i).getCharacteristic(char_uuid);
					if (mGattChar != null) {
						mGattChar.setValue(value);
						isCharWrite = currentBluetoothGatt.writeCharacteristic(mGattChar);
					}
					break;
				}
			}
		}
		
		if(isCharWrite == true) {
			LogMessage("Write Gatt Characteristic Successfully", "i");
		} else {
			LogMessage("Write Gatt Characteristic Fail", "e");
		}
	}

	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					isScanning = true;
					addDevice(device, rssi, scanRecord);
				}
			});
		}
	};

	public BluetoothGattCallback initCallBack(BluetoothGattCallback newGattCallback) {
		newGattCallback = this.mGattCallback;
		return newGattCallback;
	}

	BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				isConnected = true;
				gatt.discoverServices();
				gatt.readRemoteRssi();
				Connected();
			}
			
			if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				isConnected = false;
			}

		}

		@Override
		// New services discovered
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				mGattService = (ArrayList<BluetoothGattService>) gatt.getServices();
				isServiceRead = true;
			}
		}

		@Override
		// Result of a characteristic read operation
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (characteristic.getUuid().equals(BLEList.BATTERY_LEVEL_CHAR)) {
					battery = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
					isCharRead = true;
					ValueRead("", battery, 0,"");
				} else if (characteristic.getUuid().equals(BLEList.TXPOWER_CHAR)) {
					txPower = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
					isCharRead = true;
					ValueRead("", txPower, 0, "");
				} else if (characteristic.getUuid().equals(BLEList.LINKLOSS_CHAR)) {
					linkLoss_value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
					isCharRead = true;
					ValueRead("", linkLoss_value, 0, "");
				} else {
					data = characteristic.getValue();
					intValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, intOffset);
					stringValue = characteristic.getStringValue(strOffset);
					floatValue = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, floatOffset);
					byteValue = "";
					for (byte i : data) {
						byteValue += i;
					}
					isCharRead = true;
					ValueRead(byteValue, intValue, floatValue, stringValue);
				}
			}
		}

		@Override
		// Result of a characteristic read operation is changed
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			
			if (characteristic.getUuid().equals(BLEList.BATTERY_LEVEL_CHAR)) {
				battery = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
				isCharRead = true;
				ValueChanged("",battery, 0, "");
			} else if (characteristic.getUuid().equals(BLEList.THERMOMETER_CHAR)) {
				bodyTemp = characteristic.getValue();
				isCharRead = true;
				if ((int) bodyTemp[0] == 0) {
					tempUnit = "Celsius";
				} else {
					tempUnit = "Fahrenheit";
				}
				float mTemp = ((bodyTemp[2] & 0xff) << 8) + (bodyTemp[1] & 0xff);
				ValueChanged("", 0, 0, mTemp + tempUnit);
			} else if (characteristic.getUuid().equals(BLEList.HEART_RATE_MEASURE_CHAR)) {
				heartRate = characteristic.getValue();
				isCharRead = true;
				int mTemp = 0;
				if (((int) (heartRate[0] & 0x1)) == 0) {
					mTemp = (heartRate[1] & 0xff);
				} else {
					mTemp = (heartRate[2] & 0xff);
				}
				ValueChanged("", 0, 0, mTemp + "times/sec");
			} else if (characteristic.getUuid().equals(BLEList.TXPOWER_CHAR)) {
				txPower = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
				isCharRead = true;
				ValueChanged("", txPower, 0, "");
			} else if (characteristic.getUuid().equals(BLEList.LINKLOSS_CHAR)) {
				linkLoss_value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
				isCharRead = true;
				ValueChanged("", linkLoss_value, 0, "");
			} else {
				data = characteristic.getValue();
				//xx no 32
				intValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, intOffset);
				stringValue = characteristic.getStringValue(strOffset);
				//xx no float
				//floatValue = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, floatOffset);
				byteValue = "";
				for (byte i : data) {
					byteValue += i;
				}
				isCharRead = true;
				ValueChanged(byteValue, intValue, floatValue, stringValue);
			}
		}

		@Override
		// set value of characteristic
		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			LogMessage("Write Characteristic Successfully.", "i");
			ValueWrite();
		}

		@Override
		public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			descriptorValue = descriptor.getValue();
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			LogMessage("Write Descriptor Successfully.", "i");
		}

		@Override
		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
			device_rssi = rssi;
			RssiChanged();
		}
	};
}