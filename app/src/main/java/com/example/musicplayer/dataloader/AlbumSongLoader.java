package com.example.musicplayer.dataloader;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.example.musicplayer.models.Song;

import java.util.ArrayList;
import java.util.List;

public class AlbumSongLoader {

    public static List<Song> getAllAlbumSongs(Context context, long _id){

        List<Song> albumSongList = new ArrayList<>();

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{
                "_id",//0
                "title",//1
                "album",//2
                "artist_id",//3
                "artist",//4
                "duration",//5
                "track"//6
        };

        String sortOrder = MediaStore.Audio.Media.DEFAULT_SORT_ORDER;
        String selection = "is_music=1 and title !='' and album_id="+_id;
        Cursor cursor = context.getContentResolver().query(uri, projection, selection, null, sortOrder);

        if (cursor!=null&&cursor.moveToFirst()){
            do{

                int trackNumber = cursor.getInt(6);
                while (trackNumber>=1000){
                    trackNumber-=1000;
                }
                albumSongList.add(new Song(cursor.getLong(0),cursor.getString(1),_id,cursor.getString(2),
                        cursor.getLong(3), cursor.getString(4),cursor.getInt(5),trackNumber));

            }while (cursor.moveToNext());

            if (cursor!=null){
                cursor.close();
            }
        }


        return albumSongList;
    }
}
