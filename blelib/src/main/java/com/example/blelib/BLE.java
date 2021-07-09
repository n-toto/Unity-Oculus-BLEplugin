package com.example.blelib;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.unity3d.player.UnityPlayer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BLE {
    private final static String TAG = BLE.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 1;

    private static final String PERIPHERAL_LOCAL_NAME = "MKR WiFi 1010";
    private static final UUID PERIPHERAL_SERVICE_UUID = UUID.fromString("BF9CB85F-620C-4A67-BDD2-1A64213F74CA");
    // squish rate
    private static final UUID PERIPHERAL_SQUISH_CHARACTERISTIC_UUID = UUID.fromString("5F83E23F-BCA1-42B3-B6F2-EA82BE46A93D");
    // heart rate
    private static final UUID PERIPHERAL_HR_CHARACTERISTIC_UUID = UUID.fromString("4A036388-DBDA-41B4-9905-760F65AEB72C");
    // press value
    private static final UUID PERIPHERAL_FORCE_CHARACTERISTIC_UUID = UUID.fromString("D0D9F78B-3D8B-46EE-AA31-B0279C31E692");

    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");

    private String mGameObjName;
    private String mCallBackName;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mSquishCharacteristic;
    private BluetoothGattCharacteristic mHrCharacteristic;
    private BluetoothGattCharacteristic mForceCharacteristic;
    private Handler mHandler;
    private BluetoothLeScanner mBluetoothLeScanner;

    // Stops scanning after 30 seconds.
    private static final long SCAN_PERIOD = 10000;

    public BLE(String gameObjName, String callBackName) {

        this.mGameObjName = gameObjName;
        this.mCallBackName = callBackName;

        mHandler = new Handler();

        if (!UnityPlayer.currentActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(UnityPlayer.currentActivity, "BLEをサポートしていません", Toast.LENGTH_SHORT).show();
            UnityPlayer.currentActivity.finish();
            return;
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager) UnityPlayer.currentActivity.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        if (mBluetoothAdapter == null) {
            Toast.makeText(UnityPlayer.currentActivity, "Bluetoothをサポートしていません", Toast.LENGTH_SHORT).show();
            UnityPlayer.currentActivity.finish();
            return;
        }

        onActive();
    }


    public void onActive() {
        Log.d(TAG, "onActive");
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            UnityPlayer.currentActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // runtime permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if(UnityPlayer.currentActivity.getApplicationContext().checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                Log.d(TAG, "permission not granted");
                UnityPlayer.currentActivity.requestPermissions(
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1024);
            }
            else {
                Log.d(TAG, "permission granted");
            }
        }


        scanLeDevice(true);
    }

    public void onPause() {
        Log.d(TAG, "onPause");
        scanLeDevice(false);

        if(mSquishCharacteristic != null){
            mBluetoothGatt.setCharacteristicNotification(
                    mSquishCharacteristic,
                    false
            );
        }

        if(mHrCharacteristic != null){
            mBluetoothGatt.setCharacteristicNotification(
                    mHrCharacteristic,
                    false
            );
        }

        if(mForceCharacteristic != null){
            mBluetoothGatt.setCharacteristicNotification(
                    mForceCharacteristic,
                    false
            );
        }

        if(mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
    }


    private void scanLeDevice(final boolean enable) {
//        ScanFilter scanFilter =
//                new ScanFilter.Builder()
//                        .setDeviceName("MKR WiFi 1010")
//                        .build();
//        ArrayList scanFilterList = new ArrayList();
//        scanFilterList.add(scanFilter);

//        ScanSettings scanSettings =
//                new ScanSettings.Builder()
//                        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
//                        .build();

        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothLeScanner.stopScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);
            Log.d(TAG, "scanLeDevice");
            mBluetoothLeScanner.startScan(mLeScanCallback);
        } else {
            mBluetoothLeScanner.stopScan(mLeScanCallback);
        }
    }

    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d("scan result:", String.valueOf(result));
            super.onScanResult(callbackType, result);

            if(PERIPHERAL_LOCAL_NAME.equals(result.getDevice().getName())){
                scanLeDevice(false);
                connect(result.getDevice());
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };


    private boolean connect(BluetoothDevice device) {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            return mBluetoothGatt.connect();
        }

        mBluetoothGatt = device.connectGatt(UnityPlayer.currentActivity, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection:" + device.getName());
        mBluetoothGatt.connect();
        return true;
    }

    private final BluetoothGattCallback mGattCallback =
            new BluetoothGattCallback() {
                int flag = 0;
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    // super.onConnectionStateChange(gatt, status, newState);
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        scanLeDevice(false);
                        Log.i(TAG, "Connected to GATT server.");
                        gatt.discoverServices();

                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.i(TAG, "Disconnected from GATT server.");
                    } else{
                        Log.i(TAG, "onConnectionStateChange:" + newState);
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    // super.onServicesDiscovered(gatt, status);
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        mSquishCharacteristic = gatt.getService(PERIPHERAL_SERVICE_UUID).
                                getCharacteristic(PERIPHERAL_SQUISH_CHARACTERISTIC_UUID);

                        gatt.setCharacteristicNotification(
                                mSquishCharacteristic,
                                true
                        );
                        BluetoothGattDescriptor descriptor = mSquishCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        mBluetoothGatt.writeDescriptor(descriptor);

                    } else {
                        Log.w(TAG, "onServicesDiscovered received: " + status);
                    }
                }

                @Override
                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    Log.d("descriptor", String.valueOf(flag));
                    if (flag == 2) return;
                    if (flag == 0) {
                        mHrCharacteristic = gatt.getService(PERIPHERAL_SERVICE_UUID).
                                getCharacteristic(PERIPHERAL_HR_CHARACTERISTIC_UUID);
                        gatt.setCharacteristicNotification(
                                mHrCharacteristic,
                                true
                        );
                        BluetoothGattDescriptor descriptor2 = mHrCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                        descriptor2.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        mBluetoothGatt.writeDescriptor(descriptor2);
                        flag = 1;
                    }
                    else if (flag == 1) {
                        mForceCharacteristic = gatt.getService(PERIPHERAL_SERVICE_UUID).
                                getCharacteristic(PERIPHERAL_FORCE_CHARACTERISTIC_UUID);
                        gatt.setCharacteristicNotification(
                                mForceCharacteristic,
                                true
                        );
                        BluetoothGattDescriptor descriptor3 = mForceCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                        descriptor3.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        mBluetoothGatt.writeDescriptor(descriptor3);
                        flag = 2;
                    }
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt,
                                                    BluetoothGattCharacteristic characteristic) {
                    if (characteristic.getUuid().equals(PERIPHERAL_SQUISH_CHARACTERISTIC_UUID)) {
                        byte[] b= characteristic.getValue();
                        float squish = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                        String str = "s:" + squish;
                        Log.d(TAG, str);
                        UnityPlayer.UnitySendMessage(mGameObjName, mCallBackName, str);
                    }

                    if (characteristic.getUuid().equals(PERIPHERAL_HR_CHARACTERISTIC_UUID)) {
                        int heartrate = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
                        String str = "h:" + heartrate;
                        Log.d(TAG, str);
                        UnityPlayer.UnitySendMessage(mGameObjName, mCallBackName, str);
                    }

                    if (characteristic.getUuid().equals(PERIPHERAL_FORCE_CHARACTERISTIC_UUID)) {
                        byte[] b= characteristic.getValue();
                        float forcevalue = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                        // float forcevalue = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0);
                        String str = "f:" + forcevalue;
                        Log.d(TAG, str);
                        UnityPlayer.UnitySendMessage(mGameObjName, mCallBackName, str);
                    }

                }
    };

}