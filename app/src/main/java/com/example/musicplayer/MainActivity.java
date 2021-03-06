package com.example.musicplayer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentTransaction;
import androidx.slidingpanelayout.widget.SlidingPaneLayout;
import androidx.viewpager.widget.ViewPager;

import android.Manifest;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.example.musicplayer.dataloader.SongLoader;
import com.example.musicplayer.fragments.AlbumDetailsFragment;
import com.example.musicplayer.fragments.AlbumFragment;
import com.example.musicplayer.fragments.ArtistFragment;
import com.example.musicplayer.fragments.MainFragment;
import com.example.musicplayer.fragments.SongsFragment;
import com.example.musicplayer.models.Song;
import com.example.musicplayer.music.PlayerServices;
import com.google.android.material.tabs.TabLayout;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import java.util.ArrayList;
import java.util.List;

import static com.example.musicplayer.music.PlayerServices.mRemot;

public class MainActivity extends AppCompatActivity implements ServiceConnection {

    private static final int KEY_PER = 123;
    private SlidingUpPanelLayout paneLayout;

    private PlayerServices.ServiceToken token;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)!=
                PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this, new String[]
                    {Manifest.permission.READ_EXTERNAL_STORAGE}, KEY_PER);
            return;
        } else {
            UiInitialize();
        }


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case KEY_PER:
                if (grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    UiInitialize();
                }
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }

    }

    private void UiInitialize() {

        token = PlayerServices.bindToService(this,this);

        ImageLoader.getInstance().init(ImageLoaderConfiguration.createDefault(this));

        paneLayout = (SlidingUpPanelLayout) findViewById(R.id.sliding_layout);

        Fragment fragment = new MainFragment();
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.main_container, fragment);
        transaction.commit();



    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (token != null){
            PlayerServices.unbindToService(token);
            token = null;
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        mRemot = MusicAIDL.Stub.asInterface(iBinder);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mRemot = null;
    }
}
