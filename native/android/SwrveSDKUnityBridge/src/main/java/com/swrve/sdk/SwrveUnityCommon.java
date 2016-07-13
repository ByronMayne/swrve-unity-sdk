package com.swrve.sdk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import com.swrve.sdk.conversations.ui.ConversationActivity;
import com.unity3d.player.UnityPlayer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SwrveUnityCommon implements ISwrveCommon, ISwrveConversationSDK
{
    public final static String SWRVE_TEMPORARY_PATH_KEY = "swrveTemporaryPath";
    public final static String SDK_VERSION_KEY = "sdkVersion";
    public final static String PREFAB_NAME_KEY = "prefabName";
    public final static String DEVICE_INFO_KEY = "deviceInfo";

    private final static String LOG_TAG = "UnitySwrveCommon";

    private Map<String, Object> currentDetails;
    protected WeakReference<Context> context;

    private String sessionKey;

    private File cacheDir;

    public static boolean isInitialised() {
        return null != SwrveCommon.getInstance();
    }

    /***
     * This is the automatically called Constructor from SwrveUnityApplication
     * Application class, if used.
     */
    public SwrveUnityCommon(Context context) {
        this(context, null);
    }

    /***
     * This is the most proper Constructor to call from the Unity layer,
     * via a native plugin, with a jsonString of settings, which will be
     * then cached locally.
     *
     * @param jsonString JSON String of configuration
     */
    public SwrveUnityCommon(String jsonString) {
        this(UnityPlayer.currentActivity, jsonString);
    }

    private SwrveUnityCommon(Context context, String jsonString) {
        SwrveCommon.setSwrveCommon(this);

        SwrveLogger.d("UnitySwrveCommon constructor called with jsonString \"" + jsonString + "\"");

        if (context instanceof Activity) {
            this.context = new WeakReference<>(context.getApplicationContext());
        } else {
            this.context = new WeakReference<>(context);
        }

        SharedPreferences sp = this.context.get().getSharedPreferences("FILE", Context.MODE_PRIVATE);
        if(null == jsonString) {
            try {
                jsonString = sp.getString(LOG_TAG, "");
            } catch (Exception e) {
                SwrveLogger.e(LOG_TAG, "Error loading Unity settings from shared prefs", e);
            }
        }

        if(null != jsonString) {
            try {
                Gson gson = new Gson();
                this.currentDetails = gson.fromJson(jsonString, new TypeToken<Map<String, Object>>(){}.getType());

                resetDeviceInfo();
                SharedPreferences.Editor editor = sp.edit();
                editor.putString(LOG_TAG, jsonString);
                editor.apply();
            } catch (Exception e) {
                SwrveLogger.e(LOG_TAG, "Error loading settings from JSON", e);
            }
        }

        this.cacheDir = new File(getSwrveTemporaryPath());
        sessionKey = SwrveHelper.generateSessionToken(this.getApiKey(), this.getAppId(), getUserId());
    }

    private void resetDeviceInfo() {
        if(this.currentDetails.containsKey(DEVICE_INFO_KEY)) {
            LinkedTreeMap<String, Object> _deviceInfo = (LinkedTreeMap<String, Object>)this.currentDetails.get(DEVICE_INFO_KEY);
            try {
                JSONObject deviceInfo = new JSONObject("{}");
                for (Map.Entry<String, Object> entry: _deviceInfo.entrySet()) {
                    deviceInfo.put(entry.getKey(), entry.getValue());
                }
                this.currentDetails.remove(DEVICE_INFO_KEY);
                this.currentDetails.put(DEVICE_INFO_KEY, deviceInfo);
            }
            catch (JSONException ex) {
                SwrveLogger.e("Error while creating device info json object", ex);
            }
        }
    }

    private String readFile(String dir, String filename) {
        String fileContent = "";

        String filePath = new File(dir, filename).getPath();
        String hmacFile = filePath + getSigSuffix();
        String fileSignature = readFile(hmacFile);

        try {
            if (SwrveHelper.isNullOrEmpty(fileSignature)) {
                throw new SecurityException("Signature validation failed, signature empty");
            }
            String _fileContent = readFile(filePath);

            String computedSignature = SwrveHelper.createHMACWithMD5(_fileContent, getUniqueKey());

            if (!fileSignature.trim().equals(computedSignature.trim())) {
                throw new SecurityException("Signature validation failed, signatures mismatch");
            }
            fileContent = _fileContent;

        } catch (NoSuchAlgorithmException e) {
            SwrveLogger.e("SwrveSDK", "Computing signature failed because of invalid algorithm", e);
        } catch (InvalidKeyException e) {
            SwrveLogger.e("SwrveSDK", "Computing signature failed because of an invalid key", e);
        }

        return fileContent;
    }

    private void tryCloseCloseable(Closeable closeable) {
        if(null != closeable) {
            try {
                closeable.close();
            } catch (IOException e) {
                SwrveLogger.e(LOG_TAG, "Error closing closable: " + closeable, e);
            }
        }
    }

    private String readFile(String filePath) {
        StringBuilder text = new StringBuilder();

        InputStream is = null;
        InputStreamReader isr = null;
        BufferedReader br = null;

        try {
            // open input stream test.txt for reading purpose.
            is = new FileInputStream(filePath);

            // create new input stream reader
            isr = new InputStreamReader(is);

            // create new buffered reader
            br = new BufferedReader(isr);

            int value;

            // reads to the end of the stream
            while((value = br.read()) != -1)
            {
                // prints character
                text.append((char)value);
            }
            SwrveLogger.d("read file: " + filePath + ", content: " + text, "FileReader");

        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            // releases resources associated with the streams
            tryCloseCloseable(is);
            tryCloseCloseable(isr);
            tryCloseCloseable(br);
        }

        return text.toString();
    }

    private String getStringDetail(String key) {
        if(currentDetails.containsKey(key)) {
            return (String)currentDetails.get(key);
        }
        return null;
    }

    private int getIntDetail(String key) {
        if(currentDetails.containsKey(key)) {
            return ((Double)currentDetails.get(key)).intValue();
        }
        return 0;
    }

    @Override
    public String getApiKey() {
        return getStringDetail("apiKey");
    }

    @Override
    public String getSessionKey() {
        return this.sessionKey;
    }

    @Override
    public short getDeviceId() {
        if(currentDetails.containsKey("deviceId")) {
            return ((Double)currentDetails.get("deviceId")).shortValue();
        }
        return 0;
    }

    @Override
    public int getAppId() {
        return getIntDetail("appId");
    }

    @Override
    public String getUserId() {
        return getStringDetail("userId");
    }

    public String getSwrvePath() {
        return getStringDetail("swrvePath");
    }

    String getPrefabName() { return getStringDetail(PREFAB_NAME_KEY); }

    @Override
    public String getSwrveSDKVersion() {
        return getStringDetail(SDK_VERSION_KEY);
    }

    public String getSwrveTemporaryPath() {
        return getStringDetail(SWRVE_TEMPORARY_PATH_KEY);
    }

    public String getLocTag() {
        return getStringDetail("locTag");
    }

    public String getSigSuffix() {
        return getStringDetail("sigSuffix");
    }

    @Override
    public String getAppVersion() {
        return getStringDetail("appVersion");
    }

    @Override
    public String getUniqueKey() {
        return getStringDetail("uniqueKey");
    }

    @Override
    public String getBatchURL() {
        return getEventsServer() + getStringDetail("batchUrl");
    }

    @Override
    public void setLocationSegmentVersion(int locationSegmentVersion) {
        sendMessageUp("SetLocationSegmentVersion", Integer.toString(locationSegmentVersion));
    }

    @Override
    public String getCachedData(String userId, String key) {
        if(LOCATION_CAMPAIGN_CATEGORY.equals(key))
        {
            return readFile(getSwrvePath(), getLocTag() + userId);
        }
        return null;
    }

    public void showConversation(String conversation) {
        try {
            Intent intent = new Intent(context.get(), ConversationActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("conversation", new SwrveBaseConversation(new JSONObject(conversation), cacheDir));
            context.get().startActivity(intent);
        }
        catch (JSONException exc) {
            SwrveLogger.e(LOG_TAG, "Could not JSONify conversation, conversation string didn't have the correct structure.");
        }
    }

    public int conversationVersion() {
        return ISwrveConversationSDK.CONVERSATION_VERSION;
    }

    @Override
    public void userUpdate(Map<String, String> attributes) {
        Gson gson = new Gson();
        sendMessageUp("UserUpdate", gson.toJson(attributes));
    }

    private void sendMessageUp(String method, String msg)
    {
        UnityPlayer.UnitySendMessage(getPrefabName(), method, msg);
    }

    @Override
    public void sendEventsWakefully(Context context, ArrayList<String> events) {
        Intent intent = new Intent(context, SwrveUnityWakefulReceiver.class);
        intent.putStringArrayListExtra(SwrveUnityWakefulService.EXTRA_EVENTS, events);
        context.sendBroadcast(intent);
    }

    @Override
    public void queueConversationEvent(String eventParamName, String eventPayloadName, String page, int conversationId, Map<String, String> payload) {
        if (payload == null) {
            payload = new HashMap<>();
        }
        payload.put("event", eventPayloadName);
        payload.put("conversation", Integer.toString(conversationId));
        payload.put("page", page);

        SwrveLogger.d(LOG_TAG, "Sending view conversation event: " + eventParamName);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("name", eventParamName);

        ArrayList<String> conversationEvents = new ArrayList<>();
        try {
            conversationEvents.add(EventHelper.eventAsJSON("event", parameters, null, null));
        } catch (JSONException e) {
            SwrveLogger.e(LOG_TAG, "LocationCampaignEngageReceiver. Could not send the location engaged event", e);
        }
        sendEventsWakefully(context.get(), conversationEvents);
    }

    /***
     * Config area
     */

    @Override
    public String getEventsServer() {
        return getStringDetail("eventsServer");
    }

    @Override
    public int getHttpTimeout() {
        return getIntDetail("httpTimeout");
    }

    @Override
    public int getMaxEventsPerFlush() {
        return getIntDetail("maxEventsPerFlush");
    }

    @Override
    public JSONObject getDeviceInfo() throws JSONException {
        if(currentDetails.containsKey(DEVICE_INFO_KEY)) {
            return (JSONObject)currentDetails.get(DEVICE_INFO_KEY);
        }
        return null;
    }

    /***
     * eo Config
     */
}
