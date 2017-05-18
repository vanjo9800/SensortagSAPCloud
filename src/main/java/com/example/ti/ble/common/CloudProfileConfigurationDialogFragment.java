/**************************************************************************************************
 Filename:       CloudProfileConfigurationDialogFragment.java

 Copyright (c) 2013 - 2015 Texas Instruments Incorporated

 All rights reserved not granted herein.
 Limited License.

 Texas Instruments Incorporated grants a world-wide, royalty-free,
 non-exclusive license under copyrights and patents it now or hereafter
 owns or controls to make, have made, use, import, offer to sell and sell ("Utilize")
 this software subject to the terms herein.  With respect to the foregoing patent
 license, such license is granted  solely to the extent that any such patent is necessary
 to Utilize the software alone.  The patent license shall not apply to any combinations which
 include this software, other than combinations with devices manufactured by or for TI ('TI Devices').
 No hardware patent is licensed hereunder.

 Redistributions must preserve existing copyright notices and reproduce this license (including the
 above copyright notice and the disclaimer and (if applicable) source code license limitations below)
 in the documentation and/or other materials provided with the distribution

 Redistribution and use in binary form, without modification, are permitted provided that the following
 conditions are met:

 * No reverse engineering, decompilation, or disassembly of this software is permitted with respect to any
 software provided in binary form.
 * any redistribution and use are licensed by TI for use only with TI Devices.
 * Nothing shall obligate TI to provide you with source code for the software licensed and provided to you in object code.

 If software source code is provided to you, modification and redistribution of the source code are permitted
 provided that the following conditions are met:

 * any redistribution and use of the source code, including any resulting derivative works, are licensed by
 TI for use only with TI Devices.
 * any redistribution and use of any object code compiled from the source code and any resulting derivative
 works, are licensed by TI for use only with TI Devices.

 Neither the name of Texas Instruments Incorporated nor the names of its suppliers may be used to endorse or
 promote products derived from this software without specific prior written permission.

 DISCLAIMER.

 THIS SOFTWARE IS PROVIDED BY TI AND TI'S LICENSORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL TI AND TI'S LICENSORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 POSSIBILITY OF SUCH DAMAGE.


 **************************************************************************************************/
package com.example.ti.ble.common;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.ti.ble.sensortag.R;
import com.example.ti.util.PreferenceWR;

import java.util.Map;

/**
 * Created by ole on 15/04/15.
 */
public class CloudProfileConfigurationDialogFragment extends DialogFragment implements AdapterView.OnItemSelectedListener {

    public final static String PREF_CLOUD_SERVICE = "cloud_service";
    public final static String PREF_CLOUD_HOST = "cloud_host";
    public final static String PREF_CLOUD_DEVICEID = "cloud_deviceID";
    public final static String PREF_CLOUD_OAUTH_TOKEN = "cloud_oauth_token";
    public final static String PREF_CLOUD_MESSAGE_TYPE = "cloud_message_type";

    public final static String ACTION_CLOUD_CONFIG_WAS_UPDATED = "com.example.ti.ble.common.CloudProfileConfigurationDialogFragment.UPDATE";

    private String deviceId = "";
    private View v;

    SharedPreferences prefs = null;

    public static CloudProfileConfigurationDialogFragment newInstance(String devId) {
        CloudProfileConfigurationDialogFragment frag = new CloudProfileConfigurationDialogFragment();
        frag.deviceId = devId;
        Bundle args = new Bundle();
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder cloudDialog = new AlertDialog.Builder(getActivity())
                .setTitle("Cloud configuration")
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        Integer sel = ((Spinner) v.findViewById(R.id.cloud_spinner)).getSelectedItemPosition();
                        CloudProfileConfigurationDialogFragment.setCloudPref(PREF_CLOUD_SERVICE, sel.toString(), getActivity());
                        CloudProfileConfigurationDialogFragment.setCloudPref(PREF_CLOUD_HOST, ((EditText) v.findViewById(R.id.cloud_host)).getText().toString(), getActivity());
                        CloudProfileConfigurationDialogFragment.setCloudPref(PREF_CLOUD_DEVICEID, ((EditText) v.findViewById(R.id.cloud_deviceID)).getText().toString(), getActivity());
                        CloudProfileConfigurationDialogFragment.setCloudPref(PREF_CLOUD_OAUTH_TOKEN, ((EditText) v.findViewById(R.id.cloud_OAuth_token)).getText().toString(), getActivity());
                        CloudProfileConfigurationDialogFragment.setCloudPref(PREF_CLOUD_MESSAGE_TYPE, ((EditText) v.findViewById(R.id.cloud_messageType)).getText().toString(), getActivity());

                        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                        Map<String, ?> keys = prefs.getAll();
                        for (Map.Entry<String, ?> entry : keys.entrySet()) {
                            Log.d("CloudProfileConfig", entry.getKey() + ":" + entry.getValue().toString());
                        }

                        final Intent intent = new Intent(ACTION_CLOUD_CONFIG_WAS_UPDATED);
                        getActivity().sendBroadcast(intent);
                    }
                });

        LayoutInflater i = getActivity().getLayoutInflater();

        v = i.inflate(R.layout.cloud_config_dialog, null);
        cloudDialog.setTitle("Cloud Setup");
        Spinner spinner = (Spinner) v.findViewById(R.id.cloud_spinner);


        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this.getActivity(), R.array.cloud_config_dialog_cloud_services_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        adapter.notifyDataSetChanged();

        spinner.setOnItemSelectedListener(this);
        try {
            Integer sel = Integer.parseInt(CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_SERVICE, getActivity()), 10);
            spinner.setSelection(sel);
        } catch (Exception e) {
            e.printStackTrace();
        }
        cloudDialog.setView(v);
        return cloudDialog.create();


    }

    public void enterHost(String Host) {
        TextView t = (TextView) v.findViewById(R.id.cloud_host_label);
        EditText e = (EditText) v.findViewById(R.id.cloud_host);
        e.setEnabled(true);
        e.setText(Host);
        t.setAlpha(1.0f);
        e.setAlpha(1.0f);
    }

    public void enterDeviceID(String deviceId) {
        TextView t = (TextView) v.findViewById(R.id.cloud_deviceID_label);
        EditText e = (EditText) v.findViewById(R.id.cloud_deviceID);
        e.setEnabled(true);
        e.setText(deviceId);
        t.setAlpha(1.0f);
        e.setAlpha(1.0f);
    }

    public void enterOAuthToken(String OAuthToken) {
        TextView t = (TextView) v.findViewById(R.id.cloud_OAuth_token_label);
        EditText e = (EditText) v.findViewById(R.id.cloud_OAuth_token);
        e.setEnabled(true);
        e.setText(OAuthToken);
        t.setAlpha(1.0f);
        e.setAlpha(1.0f);
    }

    public void enterMessageType(String messageType) {
        TextView t = (TextView) v.findViewById(R.id.cloud_messageType_label);
        EditText e = (EditText) v.findViewById(R.id.cloud_messageType);
        e.setEnabled(true);
        e.setText(messageType);
        t.setAlpha(1.0f);
        e.setAlpha(1.0f);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Log.d("CloudProfileConfig", "onItemSelected :" + position);
        enterHost(CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_HOST, getActivity()));
        enterDeviceID(CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_DEVICEID, getActivity()));
        enterOAuthToken(CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_OAUTH_TOKEN, getActivity()));
        enterMessageType(CloudProfileConfigurationDialogFragment.retrieveCloudPref(CloudProfileConfigurationDialogFragment.PREF_CLOUD_MESSAGE_TYPE, getActivity()));
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        Log.d("CloudProfileConfig", "onNothingSelected" + parent);
    }

    public static String retrieveCloudPref(String prefName, Context con) {
        String preferenceKeyString = "pref_cloud_config_" + prefName;
        BluetoothLeService mBTLE = BluetoothLeService.getInstance();
        PreferenceWR p = new PreferenceWR(mBTLE.getConnectedDeviceAddress(), con);
        return p.getStringPreference(preferenceKeyString);
    }

    public static boolean setCloudPref(String prefName, String prefValue, Context con) {
        String preferenceKeyString = "pref_cloud_config_" + prefName;

        BluetoothLeService mBTLE = BluetoothLeService.getInstance();
        PreferenceWR p = new PreferenceWR(mBTLE.getConnectedDeviceAddress(), con);
        return p.setStringPreference(preferenceKeyString, prefValue);
    }

}
