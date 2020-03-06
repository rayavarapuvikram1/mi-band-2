package us.cpluspl.yonixw.readheartbatmiband2;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import android.bluetooth.BluetoothAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.UUID;

public class MainActivity extends AppCompatActivity implements BLEMiBand2Helper.BLEAction {
    public static final String LOG_TAG = "Yoni";

    Handler handler = new Handler(Looper.getMainLooper());
    BLEMiBand2Helper helper = null;

    TextView txtPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        helper = new BLEMiBand2Helper(MainActivity.this, handler);
        helper.addListener(this);

        initSoundHelper();

        // Get save path:
        txtPath = (TextView) findViewById(R.id.txtPath);
        txtPath.setText( getApplicationContext().getExternalFilesDir(null).getAbsolutePath());

        // Setup Bluetooth:
        helper.findBluetoothDevice(myBluetoothAdapter, "MI");
        helper.ConnectToGatt();

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        getTouchNotifications();
        setupHeartBeat();
    }


    @Override
    protected void onDestroy() {
        if (helper != null)
            helper.DisconnectGatt();
        super.onDestroy();
    }

    // Like network card, connect to all devices in Bluetooth (like PC in Netowrk)
    final BluetoothAdapter myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    public void btnRun(View view) {
        helper.findBluetoothDevice(myBluetoothAdapter, "MI");
        helper.ConnectToGatt();
    }


    public void setupHeartBeat() {
        /*
        Steps to read heartbeat:
            - Register Notification (like in touch press)
                - Extra step with description
            - Write predefined bytes to control_point to trigger measurement
            - Listener will get result
        */

        if (helper != null)
            helper.getNotificationsWithDescriptor(
                Consts.UUID_SERVICE_HEARTBEAT,
                Consts.UUID_NOTIFICATION_HEARTRATE,
                Consts.UUID_DESCRIPTOR_UPDATE_NOTIFICATION
            );

        // Need to wait before first trigger, maybe something about the descriptor....
        /*
        Toast.makeText(MainActivity.this, "Wait for heartbeat setup...", Toast.LENGTH_LONG).show();
        try {
            Thread.sleep(5000,0);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        */
    }

    public void getNewHeartBeat()  {
        if (helper == null || !helper.isConnected()) {
            Toast.makeText(MainActivity.this, "Please setup first!", Toast.LENGTH_SHORT).show();
            return;
        }

            helper.writeData(
                    Consts.UUID_SERVICE_HEARTBEAT,
                    Consts.UUID_START_HEARTRATE_CONTROL_POINT,
                    Consts.BYTE_NEW_HEART_RATE_SCAN
                    );
    }

    public void getTouchNotifications() {
        helper.getNotifications(
                Consts.UUID_SERVICE_MIBAND_SERVICE,
                Consts.UUID_BUTTON_TOUCH);
    }

    public void btnTest(View view) throws InterruptedException {
        getTouchNotifications();
    }

    public void btnSetuphearRate(View view) throws InterruptedException {
        setupHeartBeat();
    }

    public void btnTestHeartRate(View view) throws InterruptedException {
        getNewHeartBeat();
    }

    /* ===========  EVENTS (background thread) =============== */

    @Override
    public void onDisconnect() {

    }

    @Override
    public void onConnect() {

    }

    @Override
    public void onRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

    }

    @Override
    public void onWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

    }

    @Override
    public void onNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        UUID alertUUID = characteristic.getUuid();
        if (alertUUID.equals(Consts.UUID_NOTIFICATION_HEARTRATE)) {
            final byte hearbeat =
                    characteristic.getValue()[1];

            handler.post(new Runnable() {

                @Override
                public void run() {
                    Toast.makeText(MainActivity.this,
                            "Heartbeat: " + Byte.toString(hearbeat)
                            , Toast.LENGTH_SHORT).show();

                    // Set max volume and read heart beat.
                    setMaxVolume();
                    HearBeatVoice.readHeartbeat(mySounds, hearbeat);
                    mySounds.playAllAsync();
                }
            });
        }
        else if (alertUUID.equals(Consts.UUID_BUTTON_TOUCH)) {
            handler.post(new Runnable() {

                @Override
                public void run() {
                    getNewHeartBeat();
                    Toast.makeText(MainActivity.this,
                            "Button Press! "
                            , Toast.LENGTH_SHORT).show();
                }
            });
        }
    }



     /* ===========  Sounds =============== */

    int currentVolume = 0;
    AudioManager audiManager;

    public void setMaxVolume() {
        currentVolume = audiManager.getStreamVolume(audiManager.STREAM_MUSIC);
        int amStreamMusicMaxVol =
                (int) (audiManager.getStreamMaxVolume(audiManager.STREAM_MUSIC) * 0.60f);

        audiManager.setStreamVolume(audiManager.STREAM_MUSIC, amStreamMusicMaxVol, 0);
    }

    public void resumeVolume() {
        audiManager.setStreamVolume(audiManager.STREAM_MUSIC, currentVolume, 0);
    }

    SoundHelper mySounds;
    private void initSoundHelper() {
        audiManager = (AudioManager)getSystemService(getApplicationContext().AUDIO_SERVICE);
        mySounds = new SoundHelper(getApplicationContext());

        mySounds.setPlaybackFinishListener(new SoundHelper.PlaybackFinishListener() {
            @Override
            public void onPlaybackFinish() {
                Log.d(LOG_TAG,"Got SoundHelper finish event!");
                mySounds.releaseAllSounds();
                resumeVolume();
            }
        });
    }
}

/*
Credit and thanks:

https://github.com/lwis/miband-notifier/
http://allmydroids.blogspot.co.il/2014/12/xiaomi-mi-band-ble-protocol-reverse.html
https://github.com/Freeyourgadget/Gadgetbridge
http://stackoverflow.com/questions/20043388/working-with-ble-android-4-3-how-to-write-characteristics

// Available services\Characteristics:
http://jellygom.com/2016/09/30/Mi-Band-UUID.html
https://github.com/Freeyourgadget/Gadgetbridge/blob/master/app/src/main/java/nodomain/freeyourgadget/gadgetbridge/devices/miband/MiBand2Service.java
https://devzone.nordicsemi.com/question/310/notificationindication-difference/

// Heartbeat info
http://stackoverflow.com/questions/36311874/heart-rate-measuring-using-xiaomi-miband-and-ble
https://github.com/AlexanderHryk/MiFood/
again: http://stackoverflow.com/questions/20043388/working-with-ble-android-4-3-how-to-write-characteristics
https://github.com/dkhmelenko/miband-android/blob/master/miband-sdk/src/main/java/com/khmelenko/lab/miband/model/Protocol.java

http://stackoverflow.com/questions/7378936/how-to-show-toast-message-from-background-thread
http://stackoverflow.com/questions/6270132/create-a-custom-event-in-java

http://stackoverflow.com/questions/6537023/how-can-i-perform-arithmetic-operation-with-dates-in-java
*/
