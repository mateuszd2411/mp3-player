package com.example.musicplayer.music;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.musicplayer.MusicAIDL;
import com.example.musicplayer.models.PlayBackTrack;
import com.example.musicplayer.songdb.SongPlayStatus;
import com.example.musicplayer.util.AxUtil;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class MusicService extends Service {

    private static final String TAG = "MusicService";


    private static final String TOGGLEPAUSE_ACTION = "com.example.musicplayer.togglepause";
    private static final String PLAY_ACTION = "com.example.musicplayer.ACTION_PLAY";
    private static final String PAUSE_ACTION = "com.example.musicplayer.ACTION_PAUSE";
    private static final String STOP_ACTION = "com.example.musicplayer.ACTION_STOP";
    private static final String NEXT_ACTION = "com.example.musicplayer.ACTION_NEXT";
    private static final String PREVIOUS_ACTION = "com.example.musicplayer.ACTION_PREVIOUS";



    private static final int SERVIRE_DIE = 10;
    private static final int FADE_UP = 11;
    private static final int FADE_DOWN = 12;
    private static final int FOCUSE_CHANGE = 13;
    private static final int GO_TO_NEXT_TRUCK = 22;

    private final IBinder I_BINDER = new SubStub(this);
    public static ArrayList<PlayBackTrack> mPlayList = new ArrayList<>(100);
    private SongPlayStatus mSongPlayStatus;
    private int mPlayPos = -1;
    private SharedPreferences preferences;
    private MyMidea mPlayer;
    private boolean isSuppossedToPlaying = false;
    private boolean mPausedByTransientLoosOfFocus = false;
    private AudioManager mAudioManager;
    private MyPlayerHandler myPlayerHandler;
    private HandlerThread mHandlerThread;


    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            commandhandler(intent);
        }
    };



    private AudioManager.OnAudioFocusChangeListener focusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int i) {
            myPlayerHandler.obtainMessage(FOCUSE_CHANGE,i,0).sendToTarget();

        }
    };

    @Override
    public boolean onUnbind(Intent intent) {

        mSongPlayStatus.saveSongInDb(mPlayList);
        if (isSuppossedToPlaying || mPausedByTransientLoosOfFocus){

            return true;

        }

        stopSelf();
        return true;

    }

    @Override
    public void onCreate() {
        super.onCreate();


        mSongPlayStatus = SongPlayStatus.getInstance(this);
        mPlayList = mSongPlayStatus.getSongToDb();
        preferences= getSharedPreferences("musicservice",0);

        mPlayPos = preferences.getInt("pos",0);
        mHandlerThread = new HandlerThread("MyPlayerHandler", Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        myPlayerHandler = new MyPlayerHandler(mHandlerThread.getLooper(), this);

        mPlayer = new MyMidea(this);
        mPlayer.setHandeler(myPlayerHandler);


        IntentFilter filter = new IntentFilter();
        filter.addAction(TOGGLEPAUSE_ACTION);
        filter.addAction(PLAY_ACTION);
        filter.addAction(PAUSE_ACTION);
        filter.addAction(NEXT_ACTION);
        filter.addAction(PREVIOUS_ACTION);
        filter.addAction(STOP_ACTION);

        registerReceiver(receiver,filter);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "onBind");
        return I_BINDER;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null){
            commandhandler(intent);
        }

        return START_NOT_STICKY;
    }

    private boolean isPlaying(){
        return isSuppossedToPlaying;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPlayer.release();
        mPlayer = null;
    }

    //////////All method


    private void commandhandler(Intent intent) {

        String action = intent.getAction();

        if (TOGGLEPAUSE_ACTION.equals(action)){
            if (isPlaying()){
                pause();
                mPausedByTransientLoosOfFocus = false;
            }else {
                play();
            }
        }else if (PLAY_ACTION.equals(action)){
            play();
        }else if (PAUSE_ACTION.equals(action)){
            pause();
            mPausedByTransientLoosOfFocus = false;
        }else if (NEXT_ACTION.equals(action)){
            goToNext();
        }else if (PREVIOUS_ACTION.equals(action)){
            previous_truck();
        }

    }

    private void previous_truck(){

    }

    private void goToNext(){
        if (mPlayPos <= mPlayList.size() && mPlayPos >= 0){
            mPlayPos++;
        }
        stop();
        play();
    }

    private long position(){
        if (mPlayer.mIsinitialized){
            return mPlayer.position();
        }
        return -1;
    }

    private void open(long[] list, int position, long sourceId, AxUtil.IdType idType) {


        synchronized (this){

            int mLenght = list.length;
            boolean newList = true;
            if (mPlayList.size() == mLenght){
                newList = false;

                for (int i=0; i<mLenght; i++){

                    if (list[i] != mPlayList.get(i).mId){
                        newList = true;
                        break;
                    }
                }
            }
            if (newList){
                addToPlayList(list,-1,sourceId,idType);
                mSongPlayStatus.saveSongInDb(mPlayList);
            }

            if (position >= 0){
                mPlayPos = position;
            }
        }


    }

    private void addToPlayList(long[] list, int position, long sourceId, AxUtil.IdType idType) {

        int addLenght = list.length;
        if (position < 0){
            mPlayList.clear();
            position = 0;
        }
        mPlayList.ensureCapacity(mPlayList.size()+addLenght);
        if (position > mPlayList.size()){
            position = mPlayList.size();
        }

        ArrayList<PlayBackTrack> mList = new ArrayList<>(addLenght);

        for (int i=0; i< addLenght; i++){
            mList.add(new PlayBackTrack(list[i], sourceId, idType,i));
        }

        mPlayList.addAll(mList);



    }


    public long getAudioId() {
        PlayBackTrack track = getCurrentTruck();
        if (track != null){
            return track.mId;
        }
        return -1;

    }

    public PlayBackTrack getCurrentTruck() {
        return getTrack(mPlayPos);
    }

    public synchronized PlayBackTrack getTrack(int index) {
        if (index != -1 && index < mPlayList.size()){
            return  mPlayList.get(index);
        }
        return null;
    }

    private int getQueuePosition() {
        synchronized (this) {
            return mPlayPos;
        }
    }

    public long[] getsaveIdList() {
        synchronized (this) {
            int lenght = mPlayList.size();
            long[] idL = new long[lenght];
            for (int i = 0; i < lenght; i++){
                idL[i] = mPlayList.get(i).mId;
            }
            return idL;
        }

    }

    private void pause() {
        if (isSuppossedToPlaying){
            mPlayer.pause();
            isSuppossedToPlaying = false;
        }

    }

    private void play() {

        mPlayer.setDataSource(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/" + mPlayList.get(mPlayPos).mId);
        mAudioManager.requestAudioFocus(focusChangeListener,AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        mPlayer.start();
        myPlayerHandler.removeMessages(FADE_DOWN);
        myPlayerHandler.sendEmptyMessage(FADE_UP);
        isSuppossedToPlaying = true;
        mPausedByTransientLoosOfFocus = true;

    }

    private void stop() {
        if (mPlayer.mIsinitialized){
            mPlayer.stop();
        }

    }

    ///////////////All method

    ////mediaplayer


    public class MyMidea implements MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener{

        private WeakReference<MusicService> mService;
        private MediaPlayer mMediaPlayer = new MediaPlayer();
        private boolean mIsinitialized = false;
        private Handler mHandler;
        private float mVolume;


        public MyMidea(MusicService service) {
            this.mService = new WeakReference<>(service);
        }

        public void setDataSource(String path){
            mIsinitialized = setDataPath(mMediaPlayer,path);
        }

        private boolean setDataPath(MediaPlayer mMediaPlayer, String path) {

            try {
                mMediaPlayer.reset();
                mMediaPlayer.setOnPreparedListener(null);
                if (path.startsWith("content://")){
                    mMediaPlayer.setDataSource(mService.get(), Uri.parse(path));
                } else {
                    mMediaPlayer.setDataSource(path);
                }

                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mMediaPlayer.prepare();
                mMediaPlayer.setOnErrorListener(this);
                mMediaPlayer.setOnCompletionListener(this);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return true;
        }

        public boolean mInitialized(){
            return mIsinitialized;
        }

        private void setHandeler(Handler handeler){
            mHandler = handeler;
        }

        public void start(){
            mMediaPlayer.start();
        }

        public void stop(){
            mMediaPlayer.stop();
            mIsinitialized = false;
        }

        public void pause(){
            mMediaPlayer.pause();
        }

        public void release(){
            stop();
            mMediaPlayer.release();
        }



        public long duration(){
            if (mMediaPlayer!=null && mInitialized()){
                return mMediaPlayer.getDuration();
            }
            return -1;
        }


        public long position(){
            if (mMediaPlayer!=null && mInitialized()) {
                return mMediaPlayer.getCurrentPosition();
            }
            return 0;
        }


        public void setVolume(float vol){
            mMediaPlayer.setVolume(vol,vol);
            mVolume = vol;
        }

        public long seek(long whereto){
            mMediaPlayer.seekTo((int) whereto);
            return whereto;
        }





        @Override
        public void onCompletion(MediaPlayer mediaPlayer) {

            if (mediaPlayer == mMediaPlayer){
                mHandler.sendEmptyMessage(GO_TO_NEXT_TRUCK);
            }

        }

        @Override
        public boolean onError(MediaPlayer player, int what, int extra) {

            switch (what){
                case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                    mIsinitialized = false;
                        mMediaPlayer.release();
                        mMediaPlayer = new MediaPlayer();
                        Message message = mHandler.obtainMessage(SERVIRE_DIE);
                        mHandler.sendMessageDelayed(message,2000);
                        break;
                        default:
                            break;
            }

            return false;
        }

        public int getAudioSessionId(){
            return  mMediaPlayer.getAudioSessionId();
        }
    }


    //// MediaPlayer

    //////PlayerHandler....................


    public class MyPlayerHandler extends Handler{
        private WeakReference<MusicService> mServise;
        private float mVolume = 1.0f;

        public MyPlayerHandler(@NonNull Looper looper, MusicService service) {
            super(looper);
            this.mServise = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {

            MusicService service = mServise.get();
            if (service==null){
                return;
            }

            synchronized (service){
                switch (msg.what){
                    case FADE_UP:
                        mVolume += 0.1f;
                        if (mVolume < 1.0f){
                            sendEmptyMessageDelayed(FADE_UP,10);
                        }else {
                            mVolume = 1.0f;
                        }
                        service.mPlayer.setVolume(mVolume);
                        break;
                    case FADE_DOWN:
                        mVolume -= 0.5f;
                        if (mVolume < 0.2f){
                            sendEmptyMessageDelayed(FADE_DOWN,10);
                        }else {
                            mVolume = 0.2f;
                        }
                        service.mPlayer.setVolume(mVolume);
                        break;
                    case GO_TO_NEXT_TRUCK:
                        goToNext();
                        break;
                    case FOCUSE_CHANGE:
                        switch (msg.arg1){
                            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                                removeMessages(FADE_UP);
                                sendEmptyMessage(FADE_DOWN);
                                break;
                            case AudioManager.AUDIOFOCUS_LOSS:
                                if (service.isSuppossedToPlaying){
                                    service.mPausedByTransientLoosOfFocus =false;
                                }
                                service.pause();
                                break;
                            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                                if (service.isSuppossedToPlaying){
                                    service.mPausedByTransientLoosOfFocus =true;
                                }
                                service.pause();
                                break;
                            case AudioManager.AUDIOFOCUS_GAIN:
                                if (!service.isSuppossedToPlaying && service.mPausedByTransientLoosOfFocus){
                                    service.mPausedByTransientLoosOfFocus =false;
                                    mVolume = 0.0f;
                                    service.mPlayer.setVolume(mVolume);
                                    service.play();
                                } else {
                                    removeMessages(FADE_DOWN);
                                    sendEmptyMessage(FADE_UP);
                                }

                                break;

                        }

                        break;

                }
            }

            super.handleMessage(msg);
        }
    }




    //////PlayerHandler....................

    private static final class SubStub extends MusicAIDL.Stub{
        private WeakReference<MusicService> mService;

        public SubStub(MusicService service) {
            this.mService = new WeakReference<>(service);
        }

        @Override
        public void open(long[] list, int position, long sourceId, int type) throws RemoteException {
            mService.get().open(list,position,sourceId, AxUtil.IdType.getInstance(type));
        }

        @Override
        public void play() throws RemoteException {
            mService.get().play();
        }

        @Override
        public void stop() throws RemoteException {
            mService.get().stop();
        }


        @Override
        public long getAudioId() throws RemoteException {
            return mService.get().getAudioId();
        }

        @Override
        public int getCurrentPos() throws RemoteException {
            return mService.get().getQueuePosition();
        }

        @Override
        public long[] getsaveIdList() throws RemoteException {
            return mService.get().getsaveIdList();
        }
    }




}
