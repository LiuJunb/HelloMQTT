package activemq.xmg.com.hellomqtt.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

import java.util.Locale;

import activemq.xmg.com.hellomqtt.callback.ConnectCallBackHandler;
import activemq.xmg.com.hellomqtt.callback.DisConnectCallBackHandler;
import activemq.xmg.com.hellomqtt.utils.MQClient;

public class MQService extends Service implements MqttCallback {

    public static final String DEBUG_TAG = "MQService"; // Debug TAG

    public static final String ACTION_START  = DEBUG_TAG + ".START"; // Action to start
    public static final String ACTION_STOP   = DEBUG_TAG + ".STOP"; // Action to stop
    public static final String ACTION_KEEPALIVE= DEBUG_TAG + ".KEEPALIVE"; // Action to keep alive used by alarm manager
    public static final String ACTION_RECONNECT= DEBUG_TAG + ".RECONNECT"; // Action to reconnect

    private static final boolean MQTT_CLEAN_SESSION = true; // Start a clean session?
    private static final java.lang.String CLINET_ID = "liujun";// client id
    private static final  String TOPIC = "android06";// a topic

    private MqttConnectOptions mOpts; // Connection Options
    private AlarmManager mAlarmManager; // Alarm manager to perform repeating tasks
    private ConnectivityManager mConnectivityManager; // To check for connectivity changes

    private boolean mStarted = false;   // Is the Client started?

    private static final int MQTT_PORT = 1883; // Broker Port
    private static final String MQTT_BROKER = "192.168.80.238"; // Broker URL or IP Address
    private static final String MQTT_URL_FORMAT = "tcp://%s:%d"; // URL Format normally don't change

    private MQClient mClient; // Mqtt Client

    private Handler mConnHandler;     // Seperate Handler thread for networking

    private MemoryPersistence mMemStore; // On Fail reverts to MemoryStore
    private MqttDefaultFilePersistence mDataStore; // Defaults to FileStore

    private static final int MQTT_KEEP_ALIVE = 1000; // KeepAlive Interval in MS

    /**保持长连接的主题*/
    private MqttTopic mKeepAliveTopic; // Instance Variable for Keepalive topic

    private static final String    MQTT_KEEP_ALIVE_MESSAGE = "hello word"; // Keep Alive message to send


    public static final int MQTT_QOS_0 = 0; // QOS Level 0 ( Delivery Once no confirmation )
    public static final int MQTT_QOS_1 = 1; // QOS Level 1 ( Delevery at least Once with confirmation )
    public static final int MQTT_QOS_2 = 2; // QOS Level 2 ( Delivery only once with confirmation with handshake )

    private static final int MQTT_KEEP_ALIVE_QOS = MQTT_QOS_0; // Default Keepalive QOS

    /**
     * 服务的构造器
     */
    public MQService() {

    }

    /**
     * 服务的绑定
     * @param intent
     * @return
     */
    @Override
    public IBinder onBind(Intent intent) {

        throw new UnsupportedOperationException("Not yet implemented");
    }

    //=============================下面的是向外暴露的方法==================
    /**
     * 开始连接ActiveMQ服务器
     * @param ctx
     */
    public static void actionStart(Context ctx) {
        Intent i = new Intent(ctx,MQService.class);
        i.setAction(ACTION_START);
        ctx.startService(i);
    }

    /**
     * 停止连接ActiveMQ服务器
     * @param ctx
     */
    public static void actionStop(Context ctx) {
        Intent i = new Intent(ctx,MQService.class);
        i.setAction(ACTION_STOP);
        ctx.startService(i);
    }



    //=============================下面的是服务生命周期的方法==================
    /**
     * 服务初始化回调函数
     */
    @Override
    public void onCreate() {
        super.onCreate();

        /**创建一个Handler*/
        mConnHandler = new Handler();
        try {
            /**新建一个本地临时存储数据的目录，该目录存储将要发送到服务器的数据，直到数据被发送到服务器*/
            mDataStore = new MqttDefaultFilePersistence(getCacheDir().getAbsolutePath());
        } catch(Exception e) {
            e.printStackTrace();
            /**新建一个内存临时存储数据的目录*/
            mDataStore = null;
            mMemStore = new MemoryPersistence();
        }
        /**连接的参数选项*/
        mOpts = new MqttConnectOptions();
        /**删除以前的Session*/
        mOpts.setCleanSession(MQTT_CLEAN_SESSION);
        // Do not set keep alive interval on mOpts we keep track of it with alarm's
        /**定时器用来实现心跳*/
        mAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        /**管理网络连接*/
        mConnectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    }


    /**
     * 服务器启动的回调的函数
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        String action = intent.getAction();
        Log.i(DEBUG_TAG,"Received action of " + action);

        if(action == null) {
            Log.i(DEBUG_TAG,"Starting service with no action\n Probably from a crash");
        } else {
            if(action.equals(ACTION_START)) {
                Log.i(DEBUG_TAG,"Received ACTION_START");
                start();/**开始连接*/
            } else if(action.equals(ACTION_STOP)) {
                stop();/**停止连接*/
            } else if(action.equals(ACTION_KEEPALIVE)) {
                keepAlive();/**保持连接*/
            } else if(action.equals(ACTION_RECONNECT)) {
                if(isNetworkAvailable()) {
                    reconnectIfNecessary();/**重新连接*/
                }
            }
        }
        return START_REDELIVER_INTENT;
    }

//=============================下面的三个方法是实现了MqttCallback接口重写的方法==================
    /**
     * 连接失败
     * @param throwable
     */
    @Override
    public void connectionLost(Throwable throwable) {
        stopKeepAlives();
        mClient = null;
        /**检查网络是否可用*/
        if(isNetworkAvailable()) {
            reconnectIfNecessary();
        }
    }

    /**
     * 接收到服务器推送来的消息
     * @param s
     * @param mqttMessage
     * @throws Exception
     */
    @Override
    public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
        Log.d(DEBUG_TAG,"============messageArrived==========");
        Log.d(DEBUG_TAG,s+" = "+new String(mqttMessage.getPayload()));
    }

    /**
     * 消息发送完成
     * @param iMqttDeliveryToken
     */
    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        Log.d(DEBUG_TAG,"============deliveryComplete==========");
    }

    //=============================下面的方法是其它的方法==================
    /**
     * 检查网络是否可用
     * to return the current connected state
     * @return boolean true if we are connected false otherwise
     */
    private boolean isNetworkAvailable() {
        NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();

        return (info == null) ? false : info.isConnected();
    }


    private synchronized void start() {
        /**判断是否已经连接到服务器*/
        if(mStarted) {
            Log.i(DEBUG_TAG,"Attempt to start while already started");
            return;
        }

          /**判断是否还在保持长连接*/
        if(hasScheduledKeepAlives()) {
              /**断开长连接*/
//            stopKeepAlives();
        }

        connect();

        /**注册一个监听网路连接改变的方法*/
//        registerReceiver(mConnectivityReceiver,new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

    }

    /**
     * Receiver that listens for connectivity chanes
     * via ConnectivityManager
     */
    private final BroadcastReceiver mConnectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(DEBUG_TAG,"Connectivity Changed...");
        }
    };

    /**
     * Query's the AlarmManager to check if there is
     * a keep alive currently scheduled
     * @return true if there is currently one scheduled false otherwise
     */
    private synchronized boolean hasScheduledKeepAlives() {
        Intent i = new Intent();
        i.setClass(this, MQService.class);
        i.setAction(ACTION_KEEPALIVE);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, i, PendingIntent.FLAG_NO_CREATE);
        return (pi != null) ? true : false;
    }

    /**
     * Connects to the broker with the appropriate datastore
     */
    private synchronized void connect() {
        /**创建一个url*/
        String url = String.format(Locale.US, MQTT_URL_FORMAT, MQTT_BROKER, MQTT_PORT);
        Log.i(DEBUG_TAG,"Connecting with URL: " + url);
        /**创建一个MQClient*/
        try {
            if(mDataStore != null) {
                Log.i(DEBUG_TAG,"Connecting with DataStore");
                mClient = new MQClient(url,CLINET_ID,mDataStore);
            } else {
                Log.i(DEBUG_TAG,"Connecting with MemStore");
                mClient = new MQClient(url,CLINET_ID,mMemStore);
            }
        } catch(MqttException e) {
            e.printStackTrace();
        }
        mConnHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mClient.connect(mOpts,new ConnectCallBackHandler());
                   /**开始订阅*/
                   mClient.subscribe(TOPIC, 0);
                    /**订阅的回调*/
                   mClient.setCallback(MQService.this);
                    mStarted = true; // Service is now connected
                    Log.i(DEBUG_TAG,"Successfully connected and subscribed starting keep alives");
                    /**保持长连接*/
                    startKeepAlives();
                } catch(MqttException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    /**
     * Attempts to stop the Mqtt client
     * as well as halting all keep alive messages queued
     * in the alarm manager
     */
    private synchronized void stop() {
        if(!mStarted) {
            Log.i(DEBUG_TAG,"Attemtpign to stop connection that isn't running");
            return;
        }
        if(mClient != null) {
            mConnHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        mClient.disconnect(3000,new DisConnectCallBackHandler());
                    } catch(MqttException ex) {
                        ex.printStackTrace();
                    }
                    mClient = null;
                    mStarted = false;
                    /**停止保持心跳，长连接*/
                    stopKeepAlives();
                }
            });
        }
       // unregisterReceiver(mConnectivityReceiver);
    }

    /**
     * 开始定时
     * Schedules keep alives via a PendingIntent
     * in the Alarm Manager
     */
    private void startKeepAlives() {
        Intent i = new Intent();
        i.setClass(this, MQService.class);
        i.setAction(ACTION_KEEPALIVE);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        mAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + MQTT_KEEP_ALIVE,
                MQTT_KEEP_ALIVE, pi);
    }

    /**
     *  取消定时
     * Cancels the Pending Intent
     * in the alarm manager
     */
    private void stopKeepAlives() {
        Intent i = new Intent();
        i.setClass(this, MQService.class);
        i.setAction(ACTION_KEEPALIVE);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        mAlarmManager.cancel(pi);
    }

    /**
     * 保持长连接
     * in the broker
     */
    private synchronized void keepAlive() {
        /**如果还在与ActiveMQ服务器连接*/
        if(isConnected()) {
            try {
//                startKeepAlives();
                /**发送一个心跳包，保持与服务器长连接*/
                sendKeepAlive();
                return;
            } catch(MqttConnectivityException ex) {
                ex.printStackTrace();
                /**如果保持长连接出现异常，重新连接*/
                reconnectIfNecessary();
            } catch(MqttPersistenceException ex) {
                ex.printStackTrace();
                stop();
            } catch(MqttException ex) {
                ex.printStackTrace();
                stop();
            }
        }
    }

    /**
     * 检查连接情况，如果没有连接就自动连接
     * and reconnects if it is required.
     */
    private synchronized void reconnectIfNecessary() {
        if(mStarted && mClient == null) {
            connect();
        }
    }

    /**
     * 检查与AcitiveMQ服务器的网络连接状态
     * @return true if its a match we are connected false if we aren't connected
     */
    private boolean isConnected() {
        if(mStarted && mClient != null && !mClient.isConnected()) {
            Log.i(DEBUG_TAG,"Mismatch between what we think is connected and what is connected");
        }

        if(mClient != null) {
            return (mStarted && mClient.isConnected()) ? true : false;
        }

        return false;
    }

    /**
     * 发送一个心跳包，保持长连接
     * @return MqttDeliveryToken specified token you can choose to wait for completion
     */
    private synchronized MqttDeliveryToken sendKeepAlive()
            throws MqttConnectivityException, MqttPersistenceException, MqttException {
        if(!isConnected())
            throw new MqttConnectivityException();

        if(mKeepAliveTopic == null) {
            mKeepAliveTopic = mClient.getTopic(TOPIC);

        }
        Log.i(DEBUG_TAG,"Sending Keepalive to " + MQTT_BROKER);

        MqttMessage message = new MqttMessage(MQTT_KEEP_ALIVE_MESSAGE.getBytes());
        message.setQos(MQTT_KEEP_ALIVE_QOS);
        /**发送一个心跳包给服务器，然后回调到：messageArrived 方法中*/
       return mKeepAliveTopic.publish(message);
    }

    /**
     * MqttConnectivityException Exception class
     */
    private class MqttConnectivityException extends Exception {
        private static final long serialVersionUID = -7385866796799469420L;
    }

}
