package utb.fai;

import java.io.ObjectInputFilter;
import java.sql.ClientInfoStatus;

import org.eclipse.paho.client.mqttv3.*;

import utb.fai.API.HumiditySensor;
import utb.fai.API.IrrigationSystem;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Trida MQTT klienta pro mereni vlhkosti pudy a rizeni zavlazovaciho systemu.
 *
 * V teto tride implementuje MQTT klienta
 */
public class SoilMoistureMQTTClient {

    private MqttClient client;
    private HumiditySensor humiditySensor;
    private IrrigationSystem irrigationSystem;
    private Timer timer;

    /**
     * Vytvori instanci tridy MQTT klienta pro mereni vlhkosti pudy a rizeni
     * zavlazovaciho systemu
     *
     * @param sensor Senzor vlhkosti
     * @param irrigation Zarizeni pro zavlahu pudy
     */
    public SoilMoistureMQTTClient(HumiditySensor sensor, IrrigationSystem irrigation) {
        this.humiditySensor = sensor;
        this.irrigationSystem = irrigation;
    }

    /**
     * Metoda pro spusteni klienta
     */
    public void start() {
        try {
            client = new MqttClient(Config.BROKER, Config.CLIENT_ID);
            client.connect();

            // TYP : IN
            client.subscribe(Config.TOPIC_IN);
            client.setCallback(new MqttCallback() {

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String command = new String(message.getPayload());
                    switch (command) {
                        case Config.REQUEST_GET_HUMIDITY:
                            float humidity = humiditySensor.readRAWValue();
                            String publishHumidity = Config.RESPONSE_HUMIDITY + ";" + humidity;

                            client.publish(Config.TOPIC_OUT, new MqttMessage(publishHumidity.getBytes()));
                            break;

                        case Config.REQUEST_GET_STATUS:
                            String irrigationStatus = irrigationSystem.isActive() ? "irrigation_on" : "irrigation_off";
                            String statusMessage = Config.RESPONSE_STATUS + ";" + irrigationStatus;

                            client.publish(Config.TOPIC_OUT, new MqttMessage(statusMessage.getBytes()));
                            break;

                        case Config.REQUEST_START_IRRIGATION:
                            if (!irrigationSystem.isActive()) {
                                irrigationSystem.activate();
                                if (irrigationSystem.hasFault()) {
                                    String faultMessage = "fault;IRRIGATION_SYSTEM";
                                    client.publish(Config.TOPIC_OUT, new MqttMessage(faultMessage.getBytes()));
                                    return;
                                }
                                String startMessage = "status;irrigation_on";
                                client.publish(Config.TOPIC_OUT, new MqttMessage(startMessage.getBytes()));

                                timer = new Timer();
                                timer.schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        try {
                                            irrigationSystem.deactivate();
                                            if (irrigationSystem.hasFault()) {
                                                String faultMessage = "fault;IRRIGATION_SYSTEM";
                                                client.publish(Config.TOPIC_OUT, new MqttMessage(faultMessage.getBytes()));
                                                return;
                                            }
                                            String stopMessage = "status;irrigation_off";
                                            client.publish(Config.TOPIC_OUT, new MqttMessage(stopMessage.getBytes()));
                                        } catch (MqttException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }, 30000);
                            }
                            break;

                        case Config.REQUEST_STOP_IRRIGATION:
                                irrigationSystem.deactivate();
                                if (irrigationSystem.hasFault()) {
                                    String faultMessage = "fault;IRRIGATION_SYSTEM";
                                    client.publish(Config.TOPIC_OUT, new MqttMessage(faultMessage.getBytes()));
                                    return;
                                }
                                String stopMessage = "status;irrigation_off";
                                client.publish(Config.TOPIC_OUT, new MqttMessage(stopMessage.getBytes()));
                            
                            break;

                        default:
                            System.err.println("Unknown command received: " + command);
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {}

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    System.out.println("Message delivered: " + token.getMessageId());
                }
            });

            //Typ: OUT
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run(){
                    try {
                    float humidity = humiditySensor.readRAWValue();
                    if (irrigationSystem.hasFault()) {
                        String faultMessage = "fault;HUMIDITY_SENSOR";
                        client.publish(Config.TOPIC_OUT, new MqttMessage(faultMessage.getBytes()));
                        return;
                    }
                    String publishHumidity = Config.RESPONSE_HUMIDITY + ";" + humidity;
                    client.publish(Config.TOPIC_OUT, new MqttMessage(publishHumidity.getBytes()));
                    }
                    catch (MqttException e) {
                        e.printStackTrace();
                    }
                }
            }, 0,10000);

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}