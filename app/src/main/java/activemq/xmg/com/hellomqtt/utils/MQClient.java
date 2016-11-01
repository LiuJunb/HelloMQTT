package activemq.xmg.com.hellomqtt.utils;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;

/**
 * Description :
 * Author : liujun
 * Email  : liujin2son@163.com
 * Date   : 2016/10/30 0030
 */

public class MQClient extends MqttClient {

    public MQClient(String serverURI, String clientId) throws MqttException {
        super(serverURI, clientId);
    }

    public MQClient(String serverURI, String clientId, MqttClientPersistence persistence) throws MqttException {
        super(serverURI, clientId, persistence);
    }

    /**
     * 自定义一个连接的方法
     * @param options
     * @param listener
     * @throws MqttSecurityException
     * @throws MqttException
     */
    public void connect(MqttConnectOptions options,IMqttActionListener listener) throws MqttSecurityException, MqttException {
        aClient.connect(options, (Object)null,listener).waitForCompletion(getTimeToWait());
    }

    /**
     *  自定义一个断开连接的方法
     * @param quiesceTimeout
     * @throws MqttException
     */
    public void disconnect(long quiesceTimeout,IMqttActionListener listener) throws MqttException {
        aClient.disconnect(quiesceTimeout, (Object)null,listener).waitForCompletion();
    }


}
