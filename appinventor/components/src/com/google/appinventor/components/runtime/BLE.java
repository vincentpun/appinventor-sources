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
 * BLE provides scanning BLE device and connection
 *
 * By Tony Chan & Bain ZHANG @ PolyU (kwong3513@yahoo.com.hk &
 * 12131354d@connect.polyu.hk)
 */

@DesignerComponent(version = YaVersion.BLE_COMPONENT_VERSION, description = "This is a trial version of BLE component, blocks need to be specified later", category = ComponentCategory.CONNECTIVITY, nonVisible = true, iconName = "images/ble.png")
@SimpleObject
@UsesPermissions(permissionNames = "android.permission.BLUETOOTH, " + "android.permission.BLUETOOTH_ADMIN")

public class BLE extends AndroidNonvisibleComponent
		implements Component {

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
	private String mLogMessage = "";
	
	//testing
	//private List<BluetoothGattCharacteristic> mGattCharList;
	//private List<BluetoothGattDescriptor> mGattDes;

	/**
	 * BLE Info List
	 */
	private HashMap<String, BluetoothGatt> gattList;
	private String deviceInfoList = "";
	private List<BluetoothDevice> mLeDevices;
	private List<BluetoothGattService> mGattService;
	private BluetoothGattCharacteristic mGattChar;
	//private HashMap<BluetoothDevice, Integer> mLeDeviceRssi;
	private HashMap<BluetoothDevice, Integer> mLeDeviceRssi;
	private HashMap<BluetoothDevice, byte[]> mLeDeviceAd;

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
	 * For Furture Developement public final static String ACTION_GATT_CONNECTED
	 * = "com.example.bluetooth.le.ACTION_GATT_CONNECTED"; public final static
	 * String ACTION_GATT_DISCONNECTED =
	 * "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED"; public final static
	 * String ACTION_GATT_SERVICES_DISCOVERED =
	 * "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED"; public final
	 * static String ACTION_DATA_AVAILABLE =
	 * "com.example.bluetooth.le.ACTION_DATA_AVAILABLE"; public final static
	 * String EXTRA_DATA = "com.example.bluetooth.le.EXTRA_DATA"; private
	 * BluetoothGatt mBluetoothGatt; private String message="";
	 */

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
	private int intValue;
	private String stringValue;
	private String byteValue;

	/**
	 * Later
	 * 
	 * @param container,
	 *            component will be placed in
	 */
	public BLE(ComponentContainer container) {
		super(container.$form());
		activity = (Activity) container.$context();
		
		//testing
		//mGattCharList = new ArrayList<BluetoothGattCharacteristic>();
		//mGattDes = new ArrayList<BluetoothGattDescriptor>();
		mLeDeviceAd = new HashMap<BluetoothDevice, byte[]>();
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

	@SimpleFunction
	public void ScanDeviceStart() {
		mBluetoothAdapter.startLeScan(mLeScanCallback);
	}

	@SimpleFunction
	public void ScanDeviceStop() {
		mBluetoothAdapter.stopLeScan(mLeScanCallback);
	}
	
	@SimpleFunction
	public void SelectConnectedDevice(String address) {
		currentBluetoothGatt = gattList.get(address);
	}

	@SimpleFunction
	public void ConnectToDevice(int index) {
		BluetoothGattCallback newGattCallback=null;
		currentBluetoothGatt = mLeDevices.get(index - 1).connectGatt(activity, false, initCallBack(newGattCallback));
		gattList.put(mLeDevices.get(index - 1).toString(), currentBluetoothGatt);
	}
	
	@SimpleFunction
    public void ConnectToDeviceByAddress(String address) {
    	for(BluetoothDevice bluetoothDevice: mLeDevices){
    		if(bluetoothDevice.toString().equals(address)){
    			BluetoothGattCallback newGattCallback=null;
    			currentBluetoothGatt = bluetoothDevice.connectGatt(activity, false, initCallBack(newGattCallback));
    			gattList.put(bluetoothDevice.toString(), currentBluetoothGatt);
    			break;
    		}
    	}
    }
	
	@SimpleFunction
	public void DisconnectToDevice(String address) {
		if (gattList.containsKey(address)){
			gattList.get(address).disconnect();
			isConnected=false;
			gattList.remove(address);
		}
	}
	
	@SimpleFunction
	public void WriteValue(String service_uuid,String characteristic_uuid, String value) {
		writeChar(UUID.fromString(characteristic_uuid), UUID.fromString(service_uuid), value);
	}
	
	@SimpleFunction
	public void ReadValue(String service_uuid,String characteristic_uuid) {
		readChar(UUID.fromString(characteristic_uuid), UUID.fromString(service_uuid));
	}

	@SimpleFunction
	public void WriteFindMeValue(int setFindMe) {
		if (setFindMe <= 2 && setFindMe >= 0) {
			writeChar(BLEList.FINDME_CHAR, BLEList.FINDME_SER, setFindMe, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
		}
	}
	
	@SimpleFunction
    public void SetLinkLossValue(int value) {
    	if (value <= 2 && value >= 0){
    		linkLoss_value=value;
    		writeChar(BLEList.LINKLOSS_CHAR, BLEList.LINKLOSS_SER, value, BluetoothGattCharacteristic.FORMAT_UINT8,0);
    	}
    }
	
	@SimpleFunction
	public void ReadBatteryValue() {
		readChar(BLEList.BATTERY_LEVEL_CHAR, BLEList.BATTERY_LEVEL_SER);
	}

	@SimpleFunction
	public void ReadTemperatureValue() {
		readChar(BLEList.THERMOMETER_CHAR, BLEList.THERMOMETER_SER);
	}

	@SimpleFunction
	public void ReadHeartRateValue() {
		readChar(BLEList.HEART_RATE_MEASURE_CHAR, BLEList.HEART_RATE_SER);
	}
	
	@SimpleFunction
    public void ReadTxPower() {
        readChar(BLEList.TXPOWER_CHAR, BLEList.TXPOWER_SER);
    }
    
    @SimpleFunction
    public void ReadLinkLossValue() {
        readChar(BLEList.LINKLOSS_CHAR, BLEList.LINKLOSS_SER);
    }
    
    @SimpleFunction
  	public int FoundDeviceRssi(int index) {
  		if(index<=mLeDevices.size())
  			return mLeDeviceRssi.get(mLeDevices.get(index));
  		else
  			return -1;
  	}

  	@SimpleFunction
  	public String FoundDeviceName(int index) {
  		if(index<=mLeDevices.size())
  			return mLeDevices.get(index).getName();
  		else
  			return "";
  	}

  	@SimpleFunction
  	public String FoundDeviceUUID(int index) {
  		if(index<=mLeDevices.size())
  			return mLeDevices.get(index).getAddress();
  		else
  			return "";
  	}

	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public String BatteryValue() {
		if (isCharRead) {
			return Integer.toString(battery);
		} else {
			return "Cannot Read Battery Level";
		}
	}

	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public String TemperatureValue() {
		if (isCharRead) {
			if ((int) bodyTemp[0] == 0) {
				tempUnit = "Celsius";
			} else {
				tempUnit = "Fahrenheit";
			}
			float mTemp = ((bodyTemp[2] & 0xff) << 8) + (bodyTemp[1] & 0xff);
			return mTemp + tempUnit;
		} else {
			return "Cannot Read Temperature";
		}
	}

	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public String HeartRateValue() {
		if (isCharRead) {
			int mTemp = 0;
			if (((int) (heartRate[0] & 0x1)) == 0) {
				mTemp = (heartRate[1] & 0xff);
			} else {
				mTemp = (heartRate[2] & 0xff);
			}
			return mTemp + "times/sec";
		} else {
			return "Cannot Read Heart Rate";
		}
	}
	
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
    public int TxPower() {
		return txPower;
    }
    
    @SimpleProperty(category = PropertyCategory.BEHAVIOR)
    public String LinkLossValue() {
    	if(linkLoss_value==0){
    		return "No Alert";
    	}
    	else if(linkLoss_value==1){
    		return "Mid Alert";
    	}
    	else
    		return "High Alert";
    }
    
    /*@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public String DescriptorValue() {
		String value="";
		for(byte i:data){
			value+=i;
		}
		return value;
	}*/
	
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public boolean IsDeviceConnected() {
		if (isConnected) {
			return true;
		} else {
			return false;
		}
	}
	
	/*
//--------------------------------testing-------------------------------------------------------------------------------------
	@SimpleFunction
	public void ReadCharValue(int index){
		currentBluetoothGatt.readCharacteristic(mGattCharList.get(index-1));
	}
	
	@SimpleFunction
	public void GetChar(int index){
		mGattCharList = mGattService.get(index-1).getCharacteristics();
		String list="";
		if (!mGattCharList.isEmpty()) {
			for (int i = 0; i < mGattCharList.size(); i++) {
				if (i != (mGattCharList.size() - 1))
					list += mGattCharList.get(i).getUuid() + ",";
				else
					list += mGattCharList.get(i).getUuid() ;
			}
		}
		return list;
	}

	@SimpleFunction
	public void GetDes(int index){
		mGattDes = mGattCharList.get(index-1).getDescriptors();
		String list="";
		if (!mGattDes.isEmpty()) {
			for (int i = 0; i < mGattDes.size(); i++) {
				if (i != (mGattDes.size() - 1))
					list += mGattDes.get(i).getUuid() + ",";
				else
					list += mGattDes.get(i).getUuid() ;
			}
		}
		return list;
	}
	
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public String SerList() {
		String list="";
		if (!mGattService.isEmpty()) {
			for (int i = 0; i < mGattService.size(); i++) {
				if (i != (mGattService.size() - 1))
					list += mGattService.get(i).getUuid() + ",";
				else
					list += mGattService.get(i).getUuid() ;
			}
		}
		return list;
	}
	*/
	
//----------------------------------------------------------------------------------------------------------------------------
	
	
	
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public String DeviceList() {
		deviceInfoList = "";
		mLeDevices = sortDeviceList(mLeDevices);
		if (!mLeDevices.isEmpty()) {
			for (int i = 0; i < mLeDevices.size(); i++) {
				if (i != (mLeDevices.size() - 1))
					deviceInfoList += mLeDevices.get(i).toString() + " " + mLeDevices.get(i).getName() + " "
							+ Integer.toString(mLeDeviceRssi.get(mLeDevices.get(i))) + ",";
				else
					deviceInfoList += mLeDevices.get(i).toString() + " " + mLeDevices.get(i).getName() + " "
							+ Integer.toString(mLeDeviceRssi.get(mLeDevices.get(i)));
			}
		}
		return deviceInfoList;
	}

	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public String ConnectedDeviceRssi() {
		return Integer.toString(device_rssi);
	}
	
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public int IntGattValue() {
		//ByteBuffer wrapped = ByteBuffer.wrap(data);
		//int intValue = wrapped.getInt();
		return intValue;
	}
	
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public String StringGattValue() {
		String stringValue = new String(data);
		return stringValue;
	}
	
	@SimpleProperty(category = PropertyCategory.BEHAVIOR)
	public String ByteGattValue() {
		String dataString="";
		for(byte i:data)
			dataString += i;
		return dataString;
	}

	@SimpleEvent(description = "")
	public void GetConnected() {
		EventDispatcher.dispatchEvent(this, "Connected");
	}
	
	@SimpleEvent(description = "")
	public void RssiChanged() {
		uiThread.postDelayed(new Runnable() {
			@Override
			public void run() {
				EventDispatcher.dispatchEvent(BLE.this, "RssiChanged");
			}

		}, 1000);
	}

	@SimpleEvent(description = "")
	public void DeviceFound() {
		EventDispatcher.dispatchEvent(this, "DeviceFound");
	}

	@SimpleEvent(description = "")
	public void ValueRead(final int intValue, final String stringValue, final String byteValue) {
		uiThread.post(new Runnable() {
			@Override
			public void run() {
				EventDispatcher.dispatchEvent(BLE.this, "ValueRead", intValue, stringValue, byteValue);
			}
		});
	}
	
	@SimpleEvent(description = "")
	public void ValueChanged(final int intValue, final String stringValue, final String byteValue) {
		uiThread.post(new Runnable() {
			@Override
			public void run() {
				EventDispatcher.dispatchEvent(BLE.this, "ValueChanged", intValue, stringValue, byteValue);
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
			mLeDeviceAd.put(device, scanRecord);
			DeviceFound();
		} else {
			mLeDeviceRssi.put(device, rssi);
		}
		RssiChanged();
	}

	// read characteristic based on UUID
	private void readChar(UUID char_uuid, UUID ser_uuid) {
		if (isServiceRead && !mGattService.isEmpty()) {
			for (int i = 0; i < mGattService.size(); i++) {
				if (mGattService.get(i).getUuid().equals(ser_uuid)) {
					BluetoothGattDescriptor desc = mGattService.get(i).getCharacteristic(char_uuid).getDescriptor(BLEList.CHAR_CONFIG_DES);
					if(desc!=null){
						if (mGattService.get(i).getCharacteristic(char_uuid).getProperties()>=32) {
							desc.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
						}
						else {
							desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
						}
						currentBluetoothGatt.writeDescriptor(desc);
					}
					currentBluetoothGatt.setCharacteristicNotification(mGattService.get(i).getCharacteristic(char_uuid),true);
					currentBluetoothGatt.readCharacteristic(mGattService.get(i).getCharacteristic(char_uuid));
				}
			}
		}
	}

	// Write characteristic based on uuid
	private void writeChar(UUID char_uuid, UUID ser_uuid, int value, int format, int offset) {
		if (isServiceRead && !mGattService.isEmpty()) {
			for (int i = 0; i < mGattService.size(); i++) {
				if (mGattService.get(i).getUuid().equals(ser_uuid)) {
					mGattChar = mGattService.get(i).getCharacteristic(char_uuid);
					if(mGattChar!=null)
					{
						mGattChar.setValue(value, format, offset);
						currentBluetoothGatt.writeCharacteristic(mGattChar);
					}
				}
			}
		}
	}
	
	private void writeChar(UUID char_uuid, UUID ser_uuid, String value) {
		if (isServiceRead && !mGattService.isEmpty()) {
			for (int i = 0; i < mGattService.size(); i++) {
				if (mGattService.get(i).getUuid().equals(ser_uuid)) {
					mGattChar = mGattService.get(i).getCharacteristic(char_uuid);
					if(mGattChar!=null)
					{
						mGattChar.setValue(value);
						currentBluetoothGatt.writeCharacteristic(mGattChar);
					}
				}
			}
		}
	}
	
	/*
	public void printScanRecord (byte[] scanRecord) {
		try {
	        String decodedRecord = new String(scanRecord,"UTF-8");
	        Log.d("DEBUG","decoded String : " + ByteArrayToString(scanRecord));
	    } catch (UnsupportedEncodingException e) {
	        e.printStackTrace();
	    }
		ByteArrayToString(scanRecord);
		List<AdRecord> records = AdRecord.parseScanRecord(scanRecord);
	}

	public static String ByteArrayToString(byte[] ba)
	{
	  StringBuilder hex = new StringBuilder(ba.length * 2);
	  for (byte b : ba)
	    hex.append(b + " ");

	  return hex.toString();
	}

	public static class AdRecord {

	    public AdRecord(int length, int type, byte[] data) {
	        String decodedRecord = "";
	        try {
	            decodedRecord = new String(data,"UTF-8");

	        } catch (UnsupportedEncodingException e) {
	            e.printStackTrace();
	        }

	        Log.d("DEBUG", "Length: " + length + " Type : " + type + " Data : " + ByteArrayToString(data));         
	    }

	    public static List<AdRecord> parseScanRecord(byte[] scanRecord) {
	        List<AdRecord> records = new ArrayList<AdRecord>();
	        int index = 0;
	        while (index < scanRecord.length) {
	            int length = scanRecord[index++];
	            //Done once we run out of records
	            if (length == 0) break;
	            int type = scanRecord[index];
	            //Done if our record isn't a valid type
	            if (type == 0) break;
	            byte[] data = Arrays.copyOfRange(scanRecord, index+1, index+length);
	            records.add(new AdRecord(length, type, data));
	            //Advance
	            index += length;
	        }
	        return records;
	    }
	}*/
	
	// -----------------------------------callback-------------------------------------------------
	// scan callback
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
	
	public BluetoothGattCallback initCallBack(BluetoothGattCallback newGattCallback){
		newGattCallback=this.mGattCallback;
		return newGattCallback;
	}
	
	BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				isConnected = true;
				gatt.discoverServices();
				gatt.readRemoteRssi();
				GetConnected();
			}
			if(newState == BluetoothProfile.STATE_DISCONNECTED){
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
					ValueRead(battery, "", "");
					//ValueRead(battery);
				} else if(characteristic.getUuid().equals(BLEList.TXPOWER_CHAR)) {
                    txPower = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    isCharRead=true;
                    ValueRead(txPower, "", "");
                    //ValueRead(txPower);
                } else if(characteristic.getUuid().equals(BLEList.LINKLOSS_CHAR)) {
                    linkLoss_value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    isCharRead=true;
                    ValueRead(linkLoss_value, "", "");
                    //ValueRead(linkLoss_value);
                } else {
                	data = characteristic.getValue();
                	intValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                	stringValue = new String(data);
                	byteValue="";
            		for(byte i:data)
            			byteValue += i;
					isCharRead=true;
					ValueRead(intValue, stringValue, byteValue);
                }
			}
		}

		@Override
		// Result of a characteristic read operation is changed
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			if (characteristic.getUuid().equals(BLEList.BATTERY_LEVEL_CHAR)) {
				battery = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
				isCharRead = true;
				ValueChanged(battery, "", "");
				//ValueChanged(battery);
			} else if (characteristic.getUuid().equals(BLEList.THERMOMETER_CHAR)) {
				bodyTemp = characteristic.getValue();
				isCharRead = true;
				if ((int) bodyTemp[0] == 0) {
					tempUnit = "Celsius";
				} else {
					tempUnit = "Fahrenheit";
				}
				float mTemp = ((bodyTemp[2] & 0xff) << 8) + (bodyTemp[1] & 0xff);
				ValueChanged(-1, mTemp + tempUnit, "");
				//ValueChanged(bodyTemp);
			}  else if (characteristic.getUuid().equals(BLEList.HEART_RATE_MEASURE_CHAR)) {
				heartRate = characteristic.getValue();
				isCharRead = true;
				int mTemp = 0;
				if (((int) (heartRate[0] & 0x1)) == 0) {
					mTemp = (heartRate[1] & 0xff);
				} else {
					mTemp = (heartRate[2] & 0xff);
				}
				ValueChanged(-1,mTemp + "times/sec","");
			} else if(characteristic.getUuid().equals(BLEList.TXPOWER_CHAR)) {
                txPower = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                isCharRead = true;
                ValueChanged(txPower,"","");
                //ValueChanged(txPower);
            } else if(characteristic.getUuid().equals(BLEList.LINKLOSS_CHAR)) {
                linkLoss_value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                isCharRead = true;
                ValueChanged(linkLoss_value,"","");
                //ValueChanged(linkLoss_value);
            } else {
            	data = characteristic.getValue();
            	intValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            	stringValue = new String(data);
            	byteValue="";
        		for(byte i:data)
        			byteValue += i;
				isCharRead=true;
				ValueChanged(intValue, stringValue, byteValue);
            }
		}

		@Override
		// set value of characteristic
		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			LogMessage("Write Characteristic Successfully.","i");
		}

		@Override
		public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			descriptorValue = descriptor.getValue();
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			LogMessage("Write Descriptor Successfully.","i");
		}

		@Override
		// get the RSSI
		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
			device_rssi = rssi;
			RssiChanged();
		}
	};
	
	
}