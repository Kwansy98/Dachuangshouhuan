package com.example.dachuangshouhuan;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.util.Date;

public class PhysiologicalActivity extends AppCompatActivity {
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int UART_PROFILE_READY = 10;
    public static final String TAG = "nRFUART";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private static final int STATE_OFF = 10;

    private int mState = UART_PROFILE_DISCONNECTED;
    private UartService mService = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null; // 蓝牙适配器


    private Button btnConnect;
    private Button btnDebug; // 触发自动获取数据逻辑的按钮，已隐藏，连接成功后自动执行
    private TextView tvStatus;
    private TextView tvStep, tvCal, tvBlo, tvBlp, tvBls, tvHeart;

    private boolean isActivityAlive = true;
    private String []send = {"step", "cal", "blo", "blp", "bls", "heart"}; // 6种读请求
    private Integer index = 0; // 当前待发送的下标

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_physiological);

        // 新版SDK需要动态获取权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1);
        }
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "蓝牙不可用", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        btnConnect = findViewById(R.id.btn_connect);
        btnDebug = findViewById(R.id.btn_debug);
        tvStatus = findViewById(R.id.tv_status);
        tvStep = findViewById(R.id.tv_step);
        tvCal = findViewById(R.id.tv_cal);
        tvBlo = findViewById(R.id.tv_blo);
        tvBlp = findViewById(R.id.tv_blp);
        tvBls = findViewById(R.id.tv_bls);
        tvHeart = findViewById(R.id.tv_heart);
        service_init();

        // Handle Disconnect & Connect button
        // 连接/断开连接
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mBtAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                }
                else {
                    if (btnConnect.getText().equals("连接")){
                        //Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices
                        Intent newIntent = new Intent(PhysiologicalActivity.this, DeviceListActivity.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    } else {
                        //Disconnect button pressed
                        if (mDevice!=null)
                        {
                            mService.disconnect();
                        }
                    }
                }
            }
        });

        btnDebug.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                btnDebug.setEnabled(false);
//                // 以下代码改由连接成功后自动执行
//                index = 0;
//                new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        index = 0;
//                        while (isActivityAlive && mState == UART_PROFILE_CONNECTED) { // 子线程运行的条件
//                            synchronized (index) {
//                                String message = send[index];
//                                byte[] value;
//                                try {
//                                    Thread.sleep(200);
//                                    //send data to service
//                                    value = message.getBytes("UTF-8");
//                                    mService.writeRXCharacteristic(value);
//                                } catch (UnsupportedEncodingException e) {
//                                    e.printStackTrace();
//                                } catch (InterruptedException e) {
//                                    e.printStackTrace();
//                                }
//                                try {
//                                    index.wait(200); // 阻塞发送线程，接收数据成功后唤醒
//                                } catch (InterruptedException e) {
//                                    e.printStackTrace();
//                                }
//                            }
//                        }
//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                tvStatus.setText("状态：无连接");
//                            }
//                        });
//                    }
//                }).start();
            }
        });
    }


    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            mService = null;
        }
    };



    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "UART_CONNECT_MSG");
                        btnConnect.setText("Disconnect");
                        ((TextView) findViewById(R.id.tv_device)).setText(mDevice.getName()+ " - ready");
                        mState = UART_PROFILE_CONNECTED;

                        //btnDebug.setEnabled(true);

                        // 连接成功后循环更新数据
                        index = 0;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                index = 0;
                                while (isActivityAlive && mState == UART_PROFILE_CONNECTED) { // 子线程运行的条件
                                    synchronized (index) {
                                        String message = send[index];
                                        byte[] value;
                                        try {
                                            Thread.sleep(200);
                                            //send data to service
                                            value = message.getBytes("UTF-8");
                                            mService.writeRXCharacteristic(value);
                                        } catch (UnsupportedEncodingException e) {
                                            e.printStackTrace();
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                        try {
                                            index.wait(200); // 阻塞发送线程，接收数据成功后唤醒
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        tvStatus.setText("状态：无连接");
                                    }
                                });
                            }
                        }).start();
                    }
                });
            }

            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "UART_DISCONNECT_MSG");
                        btnConnect.setText("连接");
                        ((TextView) findViewById(R.id.tv_device)).setText("无连接");
                        mState = UART_PROFILE_DISCONNECTED;
                        mService.close();

                        //btnDebug.setEnabled(false);
                    }
                });
            }

            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                mService.enableTXNotification();
            }

            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {
                final byte[] txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);

                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            // 服务器发回来的文本
                            String text = new String(txValue, "UTF-8");
                            synchronized (index) {
                                if (index == 0) {
                                    tvStep.setText("" + text);
                                    tvStatus.setText("状态：收到步伐数据");
                                } else if (index == 1) {
                                    tvCal.setText("" + text);
                                    tvStatus.setText("状态：收到卡路里数据");
                                } else if (index == 2) {
                                    tvBlo.setText("" + text);
                                    tvStatus.setText("状态：收到血氧数据");
                                } else if (index == 3) {
                                    tvBlp.setText("" + text);
                                    tvStatus.setText("状态：收到血压数据");
                                } else if (index == 4) {
                                    tvBls.setText("" + text);
                                    tvStatus.setText("状态：收到血糖数据");
                                } else if (index == 5) {
                                    tvHeart.setText("" + text);
                                    tvStatus.setText("状态：收到心率数据");
                                }

                                index++;
                                if (index > 5) {
                                    index = 0;
                                }
                                index.notify(); // 继续发送
                            }
                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                });
            }

            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
                showMessage("Device doesn't support UART. Disconnecting");
                mService.disconnect();
            }


        }
    };

    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);

        return intentFilter;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService= null;
        isActivityAlive = false;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data); // 我的修改
        switch (requestCode) {

            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

                    Log.d(TAG, "... onActivityResultdevice.address== " + mDevice + " mserviceValue " + mService);
                    ((TextView) findViewById(R.id.tv_device)).setText(mDevice.getName() + " - 连接中");
                    mService.connect(deviceAddress);


                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();

                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }


    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (mState == UART_PROFILE_CONNECTED) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            showMessage("running in background.\n             Disconnect to exit");
        }
        else {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.popup_title)
                    .setMessage(R.string.popup_message)
                    .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.popup_no, null)
                    .show();
        }
    }
}
