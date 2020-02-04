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
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.dachuangshouhuan.Utils.SystemTTS;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
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
    private Button btnAutoSend; // 触发自动获取数据逻辑的按钮，已隐藏，连接成功后自动执行
    private Button btnVoice;
    //private TextView tvStatus;
    private TextView tvStep, tvCal, tvBlo, tvBlp, tvBls, tvHeart;

    private boolean isActivityAlive = true;
    private String []send = {"step", "cal", "blo", "blp", "bls", "heart"}; // 6种读请求
    private Integer index = 0; // 当前待发送的下标

    private int threadCnt = 0; // 发送线程计数值，不应该大于1
    SystemTTS systemTTS;

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
        btnAutoSend = findViewById(R.id.btn_autosend);
        btnVoice = findViewById(R.id.btn_voice);
        tvStep = findViewById(R.id.tv_step);
        tvCal = findViewById(R.id.tv_cal);
        tvBlo = findViewById(R.id.tv_blo);
        tvBlp = findViewById(R.id.tv_blp);
        tvBls = findViewById(R.id.tv_bls);
        tvHeart = findViewById(R.id.tv_heart);
        resetUI();
        systemTTS = SystemTTS.getInstance(PhysiologicalActivity.this);
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

        // 正式版中该按钮已隐藏
        btnAutoSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnAutoSend.setEnabled(false);
                // 以下代码改由连接成功后自动执行
                index = 0;
                if (threadCnt != 0) { // 错误
                    new AlertDialog.Builder(PhysiologicalActivity.this)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setTitle("调试信息")
                            .setMessage("错误！当前有未关闭的发送线程")
                            .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            }).show();
                }
                threadCnt++; // 增加了一个线程
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        index = 0;
                        while (isActivityAlive && mState == UART_PROFILE_CONNECTED && mService != null) { // 子线程运行的条件
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
                                } catch (NullPointerException e) {
                                    e.printStackTrace();
                                }
                                try {
                                    index.wait(2000); // 阻塞发送线程，接收数据成功后唤醒
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        threadCnt--;
                    }
                }).start();
            }
        });



        btnVoice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String step = tvStep.getText().toString().split("步数")[0];
                String cal = tvCal.getText().toString();
                String blo = tvBlo.getText().toString().split("%")[0];
                String blp = tvBlp.getText().toString().replace("mmHg", "，");
                String bls = tvBls.getText().toString().split("mmol")[0];
                String heart = tvHeart.getText().toString();

                step = step.replace("-", "0");
                cal = cal.replace("-", "0");
                blo = blo.replace("-", "0");
                blp = blp.replace("-", "0");
                bls = bls.replace("-", "0");
                heart = heart.replace("-", "0");


                //systemTTS.playText("-\n步数".split("\n步数")[0].replace("-", "100"));
                systemTTS.playText("步伐数：" + step);
                systemTTS.playText("热量：" + cal);
                systemTTS.playText("血氧：百分之" + blo);
                systemTTS.playText("血压" + blp);
                systemTTS.playText("血糖：" + bls);
                systemTTS.playText("心率：" + heart);

            }
        });
    }

    public void setVoiceBtnEnable(boolean enable) {
        btnVoice.setEnabled(enable);
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
                        btnConnect.setText("断开连接");
                        ((TextView) findViewById(R.id.tv_device)).setText(mDevice.getName()+ " - ready");
                        mState = UART_PROFILE_CONNECTED;

                        btnAutoSend.setEnabled(true);

                        // 连接成功后循环更新数据


                        // 以下代码改由连接成功后自动执行
                        index = 0;
                        if (threadCnt != 0) { // 错误
                            new AlertDialog.Builder(PhysiologicalActivity.this)
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .setTitle("调试信息")
                                    .setMessage("错误！当前有未关闭的发送线程")
                                    .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            finish();
                                        }
                                    }).show();
                        }

                        threadCnt++; // 增加了一个线程
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                // 连接成功后等待3秒，不这样做就会掉线，原因不明
                                try {
                                    Thread.sleep(3000); // 2020年2月3日13:48:01
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                // 设置手环时间
                                SimpleDateFormat df = new SimpleDateFormat("MMddHHmm"); // 手环识别的时间格式
                                String timestamp = df.format(new Date());
                                try {
                                    Thread.sleep(200);
                                    //send data to service
                                    byte[]value = timestamp.getBytes("UTF-8");
                                    mService.writeRXCharacteristic(value);
                                } catch (UnsupportedEncodingException e) {
                                    e.printStackTrace();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                } catch (NullPointerException e) {
                                    e.printStackTrace();
                                }
                                // 循环更新数据
                                index = 0;
                                while (isActivityAlive && mState == UART_PROFILE_CONNECTED && mService != null) { // 子线程运行的条件
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
                                        } catch (NullPointerException e) {
                                            e.printStackTrace();
                                        }
                                        try {
                                            index.wait(2000); // 阻塞发送线程，接收数据成功后唤醒
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                                threadCnt--;
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

                        btnAutoSend.setEnabled(false);
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

                        // 服务器发回来的文本
                        String text = null;
                        try {
                            text = new String(txValue, "UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        try {
                            synchronized (index) {
                                try {
                                    if (index == 0) {
                                        int step = Integer.parseInt(text);
                                        tvStep.setText("" + step + "\n步数");
                                    } else if (index == 1) {
                                        int cal = Integer.parseInt(text);
                                        tvCal.setText("" + cal + "\n卡路里");
                                    } else if (index == 2) {
                                        double blo = Double.parseDouble(text);
                                        tvBlo.setText("" + blo + "\n%");
                                    } else if (index == 3) {
                                        String lo = text.split(" ")[1];
                                        String hi = text.split(" ")[0];
                                        double _lo = Double.parseDouble(lo);
                                        double _hi = Double.parseDouble(hi);
                                        tvBlp.setText("最低\n" + _lo + "\nmmHg\n\n最高\n" + _hi + "\nmmHg");
                                    } else if (index == 4) {
                                        double bls = Double.parseDouble(text);
                                        tvBls.setText("" + bls + "\nmmol/L");
                                    } else if (index == 5) {
                                        int heart = Integer.parseInt(text);
                                        tvHeart.setText("" + heart + "\n次/分钟");
                                    }
                                } catch (NumberFormatException e) {
                                    Log.d(TAG, "run: " + e.getMessage());
                                } catch (NullPointerException e) {
                                    Log.d(TAG, "run: " + e.getMessage());
                                }

                                index++;
                                if (index > 5) {
                                    index = 0;
                                }
                                index.notify(); // 继续发送

                            }
                        } catch (Exception e) {
                            Log.d(TAG, "run: " + e.getMessage());
                        }

                    }
                });
            }

            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
                showMessage("设备不支持，断开连接");
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
            showMessage("后台运行.\n退出请先断开连接");
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

    // 重置UI
    private void resetUI() {
        tvStep.setText("-\n步数");
        tvCal.setText("-\n卡路里");
        tvBlo.setText("-\n%");
        tvBlp.setText("最低\n-\nmmHg\n\n最高\n-\nmmHg");
        tvBls.setText("-\nmmol/L");
        tvHeart.setText("-\n次/分钟");
    }
}
