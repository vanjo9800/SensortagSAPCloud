/**************************************************************************************************
 * Filename:       SAPCloudProfile.java
 * <p>
 * Copyright (c) 2013 - 2015 Texas Instruments Incorporated
 * <p>
 * All rights reserved not granted herein.
 * Limited License.
 * <p>
 * Texas Instruments Incorporated grants a world-wide, royalty-free,
 * non-exclusive license under copyrights and patents it now or hereafter
 * owns or controls to make, have made, use, import, offer to sell and sell ("Utilize")
 * this software subject to the terms herein.  With respect to the foregoing patent
 * license, such license is granted  solely to the extent that any such patent is necessary
 * to Utilize the software alone.  The patent license shall not apply to any combinations which
 * include this software, other than combinations with devices manufactured by or for TI ('TI Devices').
 * No hardware patent is licensed hereunder.
 * <p>
 * Redistributions must preserve existing copyright notices and reproduce this license (including the
 * above copyright notice and the disclaimer and (if applicable) source code license limitations below)
 * in the documentation and/or other materials provided with the distribution
 * <p>
 * Redistribution and use in binary form, without modification, are permitted provided that the following
 * conditions are met:
 * <p>
 * No reverse engineering, decompilation, or disassembly of this software is permitted with respect to any
 * software provided in binary form.
 * any redistribution and use are licensed by TI for use only with TI Devices.
 * Nothing shall obligate TI to provide you with source code for the software licensed and provided to you in object code.
 * <p>
 * If software source code is provided to you, modification and redistribution of the source code are permitted
 * provided that the following conditions are met:
 * <p>
 * any redistribution and use of the source code, including any resulting derivative works, are licensed by
 * TI for use only with TI Devices.
 * any redistribution and use of any object code compiled from the source code and any resulting derivative
 * works, are licensed by TI for use only with TI Devices.
 * <p>
 * Neither the name of Texas Instruments Incorporated nor the names of its suppliers may be used to endorse or
 * promote products derived from this software without specific prior written permission.
 * <p>
 * DISCLAIMER.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY TI AND TI'S LICENSORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL TI AND TI'S LICENSORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 **************************************************************************************************/
package com.example.ti.ble.common;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;

import com.example.ti.ble.sensortag.R;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class SAPCloudProfile extends GenericBluetoothProfile {
    MqttAndroidClient client;
    MemoryPersistence memPer;
    final String addrShort;
    static SAPCloudProfile mThis;
    Map<String, String> valueMap = new HashMap<String, String>();
    Timer publishTimer;
    private boolean ready;
    private WakeLock wakeLock;
    BroadcastReceiver cloudConfigUpdateReceiver;
    cloudConfig config;
    SAPCloudTableRow cloudView;

    public SAPCloudProfile(final Context con, BluetoothDevice device, BluetoothGattService service, BluetoothLeService controller) {
        super(con, device, service, controller);
        this.tRow = new SAPCloudTableRow(con);
        this.tRow.setOnClickListener(null);

        config = readCloudConfigFromPrefs();

        String addr = mBTDevice.getAddress();
        String[] addrSplit = addr.split(":");
        int[] addrBytes = new int[6];
        for (int ii = 0; ii < 6; ii++) {
            addrBytes[ii] = Integer.parseInt(addrSplit[ii], 16);
        }
        ready = false;
        this.addrShort = String.format("%02x%02x%02x%02x%02x%02x", addrBytes[0], addrBytes[1], addrBytes[2], addrBytes[3], addrBytes[4], addrBytes[5]);

        Log.d("SAPCloudProfile", "Device ID : " + addrShort);
        this.tRow.sl1.setVisibility(View.INVISIBLE);
        this.tRow.sl2.setVisibility(View.INVISIBLE);
        this.tRow.sl3.setVisibility(View.INVISIBLE);
        this.tRow.title.setText("Cloud View");
        this.tRow.setIcon("sensortag2cloudservice", "", "");
        this.tRow.value.setText("Device ID : " + addr);

        cloudView = (SAPCloudTableRow) this.tRow;

        if (config != null) {
            cloudView.pushToCloud.setClickable(true);
            cloudView.pushToCloudCaption.setText("Push to cloud:");
            Log.d("SAPCloudProfile", "Stored cloud configuration" + "\r\n" + config.toString());
        } else {
            config = new cloudConfig();
            cloudView.pushToCloud.setClickable(false);
            cloudView.pushToCloudCaption.setText("Cloud not configured");
            Log.d("SAPCloudProfile", "Stored cloud configuration was corrupt, resetting");
        }

        cloudView.pushToCloud.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    connect();
                } else {
                    disconnect();
                }
            }
        });


        cloudView.configureCloud.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CloudProfileConfigurationDialogFragment dF = CloudProfileConfigurationDialogFragment.newInstance(addrShort);

                final Activity act = (Activity) context;
                dF.show(act.getFragmentManager(), "CloudConfig");


            }
        });

        mThis = this;
        cloudConfigUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(CloudProfileConfigurationDialogFragment.ACTION_CLOUD_CONFIG_WAS_UPDATED)) {
                    Log.d("SAPCloudProfile", "Cloud configuration was updated !");
                    Log.d("SAPCloudProfile", "Old cloud configuration was :" + config.toString());
                    config = readCloudConfigFromPrefs();
                    Log.d("SAPCloudProfile", "New cloud configuration :" + config.toString());
                    if (client != null) {
                        try {
                            if (client.isConnected()) {
                                disconnect();
                                connect();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    cloudView.pushToCloud.setClickable(true);
                    cloudView.pushToCloudCaption.setText("Push to cloud:");
                }
            }
        };
        this.context.registerReceiver(cloudConfigUpdateReceiver, makeCloudConfigUpdateFilter());
    }

    public void cloudConnectionError() {
        ((SAPCloudTableRow) tRow).pushToCloud.setChecked(false);
        new AlertDialog.Builder(context)
                .setTitle("Cloud Connection Error")
                .setMessage("The app could not connect to the cloud. Please make sure you have entered the information accurately.")
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
        disconnect();
    }

    public boolean disconnect() {
        try {
            ((SAPCloudTableRow) tRow).setCloudConnectionStatusImage(context.getResources().getDrawable(R.drawable.cloud_disconnected));
            if (config.service == 1) {
                webSocket.disconnect();
            }
            if (publishTimer != null) {
                publishTimer.cancel();
            }
            if (client != null) {
                Log.d("SAPCloudProfile", "Disconnecting from cloud : " + client.getServerURI() + "," + client.getClientId());
                if (client.isConnected()) client.disconnect();
                client.unregisterResources();
                client = null;
                memPer = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    WebSocket webSocket;
    public String MqttPublishTopic;

    public boolean connect() {

        Log.d("SAPCloudProfile", "Chosen service " + config.service);

        if (config.service == 0) { //Via HTTP API
            new SendPostRequest().execute(new JSONObject());
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            ready = true;
            publishTimer = new Timer();
            HTTPTimerTask task = new HTTPTimerTask();
            publishTimer.schedule(task, 1000, 1000);
        }

        if (config.service == 1) { //Via WebSocket API

            try {
                webSocket = new WebSocketFactory().createSocket("wss://" + config.host + "/com.sap.iotservices.mms/v1/api/ws/data/" + config.deviceID, 5000);
            } catch (IOException e) {
                e.printStackTrace();
            }

            webSocket.addListener(new WebSocketAdapter() {
                @Override
                public void onConnected(WebSocket socket, Map<String, List<String>> headers) throws Exception {
                    Log.i("SAPCloudProfile", "WebSocket successfully connected");
                }

                @Override
                public void onSendError(WebSocket socket, WebSocketException reason, WebSocketFrame frame) throws Exception {
                    cloudConnectionError();
                    Log.e("SAPCloudProfile", "Error while sending data via WebSocket");
                }

                @Override
                public void onConnectError(WebSocket socket, WebSocketException reason) {
                    cloudConnectionError();
                    Log.e("SAPCloudProfile", "WebSocket connection error " + reason.getMessage());
                }
            });

            webSocket.addHeader("Authorization", "Bearer " + config.OAuthToken);
            webSocket.connectAsynchronously();

            publishTimer = new Timer();
            WebSocketTimerTask task = new WebSocketTimerTask();
            publishTimer.schedule(task, 1000, 1000);
        }

        if (config.service > 1) { //MQTT
            String url = "", username = "", password = "";
            if (config.service == 2) { //MQTT over WebSockets
                url = "wss://" + config.host + "/com.sap.iotservices.mms/v1/api/ws/mqtt";
                username = config.deviceID;
                password = config.OAuthToken;
                MqttPublishTopic = "iot/data/" + config.deviceID;
            }

            try {
                memPer = new MemoryPersistence();
                Log.d("SAPCloudProfile", "Cloud Broker URL : " + url);
                client = new MqttAndroidClient(this.context, url, config.deviceID, memPer);
                MqttConnectOptions options = new MqttConnectOptions();
                options.setUserName(username);
                options.setPassword(password.toCharArray());
                options.setCleanSession(true);
                options.setKeepAliveInterval(5000);

                client.connect(options, this.context, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken iMqttToken) {
                        Log.d("SAPCloudProfile", "Connected to cloud : " + client.getServerURI() + "," + client.getClientId());
                        ((SAPCloudTableRow) tRow).setCloudConnectionStatusImage(context.getResources().getDrawable(R.drawable.cloud_connected));
                    }

                    @Override
                    public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
                        Log.e("SAPCloudProfile", "Connection to cloud failed !");
                        Log.e("SAPCloudProfile", "Error: " + throwable.toString() + " " + throwable.getLocalizedMessage());
                        throwable.printStackTrace();
                        cloudConnectionError();
                    }
                });
            } catch (MqttException e) {
                e.printStackTrace();
            }
            ready = true;
            publishTimer = new Timer();
            MQTTTimerTask task = new MQTTTimerTask();
            publishTimer.schedule(task, 1000, 1000);
        }
        return true;
    }

    public void addSensorValueToPendingMessage(String variableName, String Value) {
        this.valueMap.put(variableName, Value);
    }

    public void addSensorValueToPendingMessage(Map.Entry<String, String> e) {
        this.valueMap.put(e.getKey(), e.getValue());
    }

    @Override
    public void onPause() {
        super.onPause();
        this.context.unregisterReceiver(cloudConfigUpdateReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        this.context.registerReceiver(cloudConfigUpdateReceiver, makeCloudConfigUpdateFilter());
    }

    @Override
    public void enableService() {

    }

    @Override
    public void disableService() {

    }

    @Override
    public void configureService() {

    }

    @Override
    public void deConfigureService() {
    }

    @Override
    public void didUpdateValueForCharacteristic(BluetoothGattCharacteristic c) {
    }

    @Override
    public void didReadValueForCharacteristic(BluetoothGattCharacteristic c) {
    }

    public static SAPCloudProfile getInstance() {
        return mThis;
    }

    public class SendPostRequest extends AsyncTask<JSONObject, String, Boolean> {
        @Override
        public void onPreExecute() {
        }

        @Override
        public Boolean doInBackground(JSONObject... params) {

            JSONObject data = params[0];
            URL url;
            HttpURLConnection connection = null;
            try {
                url = new URL("https://" + config.host + "/com.sap.iotservices.mms/v1/api/http/data/" + config.deviceID);
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json;charset=utf-8");
                connection.setRequestProperty("Authorization", "Bearer " + config.OAuthToken);
                connection.connect();

                JSONObject toSend = new JSONObject();
                toSend.put("mode", "async");
                toSend.put("messageType", config.messageType);

                JSONArray messages = new JSONArray();
                if (data.length() > 0) {
                    messages.put(data);
                }
                toSend.put("messages", messages);

                //Send request
                DataOutputStream wr = new DataOutputStream(
                        connection.getOutputStream());
                wr.writeBytes(toSend.toString());
                wr.flush();
                wr.close();

                Integer responseCode = connection.getResponseCode();
                connection.disconnect();
                return (responseCode.equals(200)) || (responseCode.equals(202));

            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        public void onPostExecute(Boolean result) {
            if (!result) {
                cloudConnectionError();
                Log.e("SAPCloudProfile", "Error while sending data via HTTP");
            }
        }
    }

    class HTTPTimerTask extends TimerTask {
        @Override
        public void run() {
            try {
                final Activity activity = (Activity) context;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((SAPCloudTableRow) tRow).setCloudConnectionStatusImage(activity.getResources().getDrawable(R.drawable.cloud_connected_tx));
                    }
                });
                Map<String, String> dict = new HashMap<String, String>();
                dict.putAll(valueMap);
                JSONObject data = new JSONObject();

                for (Map.Entry<String, String> entry : dict.entrySet()) {
                    String var = entry.getKey();
                    String val = entry.getValue();

                    data.put(var, val);
                }

                Long timestamp = System.currentTimeMillis() / 1000;
                data.put("timestamp", timestamp.toString());

                if (data.length() > 0) {
                    new SendPostRequest().execute(data);
                    try {
                        Thread.sleep(60);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((SAPCloudTableRow) tRow).setCloudConnectionStatusImage(activity.getResources().getDrawable(R.drawable.cloud_connected));
                    }
                });
            } catch (Exception e) {
                cloudConnectionError();
                e.printStackTrace();
            }
        }
    }

    class WebSocketTimerTask extends TimerTask {
        @Override
        public void run() {
            try {
                final Activity activity = (Activity) context;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((SAPCloudTableRow) tRow).setCloudConnectionStatusImage(activity.getResources().getDrawable(R.drawable.cloud_connected_tx));
                    }
                });
                Map<String, String> dict = new HashMap<String, String>();
                dict.putAll(valueMap);
                JSONObject data = new JSONObject();

                for (Map.Entry<String, String> entry : dict.entrySet()) {
                    String var = entry.getKey();
                    String val = entry.getValue();

                    data.put(var, val);
                }

                Long timestamp = System.currentTimeMillis() / 1000;
                data.put("timestamp", timestamp.toString());

                if (data.length() > 0) {

                    JSONObject toSend = new JSONObject();
                    toSend.put("mode", "async");
                    toSend.put("messageType", config.messageType);

                    JSONArray messages = new JSONArray();
                    if (data.length() > 0) {
                        messages.put(data);
                    }
                    toSend.put("messages", messages);

                    webSocket.sendText(toSend.toString());

                    try {
                        Thread.sleep(60);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((SAPCloudTableRow) tRow).setCloudConnectionStatusImage(activity.getResources().getDrawable(R.drawable.cloud_connected));
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    class MQTTTimerTask extends TimerTask {
        @Override
        public void run() {
            if (ready) {
                try {
                    final Activity activity = (Activity) context;
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ((SAPCloudTableRow) tRow).setCloudConnectionStatusImage(activity.getResources().getDrawable(R.drawable.cloud_connected_tx));
                        }
                    });
                    Map<String, String> dict = new HashMap<String, String>();
                    dict.putAll(valueMap);
                    JSONObject data = new JSONObject();

                    for (Map.Entry<String, String> entry : dict.entrySet()) {
                        String var = entry.getKey();
                        String val = entry.getValue();

                        data.put(var, val);
                    }

                    Long timestamp = System.currentTimeMillis() / 1000;
                    data.put("timestamp", timestamp.toString());

                    if (data.length() > 0) {

                        JSONObject toSend = new JSONObject();
                        toSend.put("mode", "async");
                        toSend.put("messageType", config.messageType);

                        JSONArray messages = new JSONArray();
                        if (data.length() > 0) {
                            messages.put(data);
                        }
                        toSend.put("messages", messages);
                        client.publish(MqttPublishTopic, toSend.toString().getBytes(), 0, false);
                        try {
                            Thread.sleep(60);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ((SAPCloudTableRow) tRow).setCloudConnectionStatusImage(activity.getResources().getDrawable(R.drawable.cloud_connected));
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                Log.d("SAPCloudProfile", "MQTTTimerTask ran, but MQTT not ready");
            }
        }
    }


    private static IntentFilter makeCloudConfigUpdateFilter() {
        final IntentFilter fi = new IntentFilter();
        fi.addAction(CloudProfileConfigurationDialogFragment.ACTION_CLOUD_CONFIG_WAS_UPDATED);
        return fi;
    }

    class cloudConfig extends Object {
        public Integer service;
        public String host;
        public String deviceID;
        public String OAuthToken;
        public String messageType;

        cloudConfig() {

        }

        @Override
        public String toString() {
            String s = new String();
            s = "Cloud configuration :\r\n";
            s += "Service : " + service + "\r\n";
            s += "Host : " + host + "\r\n";
            s += "Device ID: " + deviceID + "\r\n";
            s += "OAuth Token : " + OAuthToken + "\r\n";
            s += "Message Type : " + messageType + "\r\n";
            return s;
        }

    }

    public cloudConfig readCloudConfigFromPrefs() {
        cloudConfig c = new cloudConfig();
        try {
            c.service = Integer.parseInt(CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_SERVICE, this.context), 10);
            c.host = CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_HOST, this.context);
            c.deviceID = CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_DEVICEID, this.context);
            c.OAuthToken = CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_OAUTH_TOKEN, this.context);
            c.messageType = CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_MESSAGE_TYPE, this.context);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return c;
    }

    public void writeCloudConfigToPrefs(cloudConfig c) {
        CloudProfileConfigurationDialogFragment.setCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_SERVICE, c.service.toString(), this.context);
        CloudProfileConfigurationDialogFragment.setCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_HOST, c.host, this.context);
        CloudProfileConfigurationDialogFragment.setCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_DEVICEID, c.deviceID, this.context);
        CloudProfileConfigurationDialogFragment.setCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_OAUTH_TOKEN, c.OAuthToken, this.context);
        CloudProfileConfigurationDialogFragment.setCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_MESSAGE_TYPE, c.messageType, this.context);
    }
}
