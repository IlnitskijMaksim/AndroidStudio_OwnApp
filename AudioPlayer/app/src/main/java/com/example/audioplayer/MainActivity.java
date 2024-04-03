package com.example.audioplayer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.audioplayer.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private MediaPlayer mediaPlayer;
    private ArrayList<String> songFiles;
    private ListView listView;
    private ArrayAdapter<String> adapter;

    private TextView songTitleControlPanel;
    private ImageView albumArt;
    private Button playBtn, previousBtn, nextBtn;
    private SeekBar seekBar;
    private TextView totalDurationTextView;
    private TextView currentDurationTextView;
    private int currentPosition = 0;
    private boolean isAnimationPlayed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUI();
    }

    private void initializeUI() {
        listView = findViewById(R.id.listView);
        songTitleControlPanel = findViewById(R.id.songTitleControlPanel);
        albumArt = findViewById(R.id.albumArt);
        playBtn = findViewById(R.id.playBtn);
        previousBtn = findViewById(R.id.previousBtn);
        nextBtn = findViewById(R.id.nextBtn);
        seekBar = findViewById(R.id.seekBar);
        findViewById(R.id.controlPanel).setVisibility(View.GONE);
        totalDurationTextView = findViewById(R.id.totalDurationTextView);
        currentDurationTextView = findViewById(R.id.currentDurationTextView);

        AssetManager assetManager = getAssets();
        songFiles = findSongs(assetManager);

        ArrayList<String> songTitles = new ArrayList<>();
        for (String file : songFiles) {
            String songTitle = getSongTitleFromFile(file);
            String artist = getArtistFromFile(file);
            songTitles.add(artist + " - " + songTitle);
        }

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, songTitles);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!isAnimationPlayed) {
                    findViewById(R.id.controlPanel).setVisibility(View.VISIBLE);

                    Animation slideUpAnimation = AnimationUtils.loadAnimation(MainActivity.this, R.anim.control_panel_anim);
                    findViewById(R.id.controlPanel).startAnimation(slideUpAnimation);

                    isAnimationPlayed = true;
                } else {
                    findViewById(R.id.controlPanel).setVisibility(View.VISIBLE);
                }

                currentPosition = position;
                stopAndPlayNewSong();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    playBtn.setBackgroundResource(android.R.drawable.ic_media_play);
                } else {
                    if (mediaPlayer != null) {
                        mediaPlayer.start();
                        playBtn.setBackgroundResource(android.R.drawable.ic_media_pause);
                    } else {
                        playSong(String.valueOf(new File(songFiles.get(currentPosition))));
                    }
                }
            }
        });

        previousBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPosition > 0) {
                    currentPosition--;
                } else {
                    currentPosition = songFiles.size() - 1;
                }
                stopAndPlayNewSong();
            }
        });

        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPosition < songFiles.size() - 1) {
                    currentPosition++;
                } else {
                    currentPosition = 0;
                }
                stopAndPlayNewSong();
            }
        });
    }

    private void stopAndPlayNewSong() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        playSong(songFiles.get(currentPosition));
    }

    private void setAlbumArt(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(file.getAbsolutePath());
        byte[] albumArtBytes = retriever.getEmbeddedPicture();
        if (albumArtBytes != null) {
            albumArt.setImageBitmap(BitmapFactory.decodeByteArray(albumArtBytes, 0, albumArtBytes.length));
        } else {
            albumArt.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    private void updateSeekBarProgress() {
        if (mediaPlayer != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        try {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    int currentPosition = mediaPlayer.getCurrentPosition();
                                    int totalDuration = mediaPlayer.getDuration();

                                    String currentDurationStr = millisecondsToTimer(currentPosition);
                                    currentDurationTextView.setText(currentDurationStr);

                                    String totalDurationStr = millisecondsToTimer(totalDuration);
                                    totalDurationTextView.setText(totalDurationStr);

                                    seekBar.setProgress(currentPosition);
                                }
                            });
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
        }
    }

    private String millisecondsToTimer(int milliseconds) {
        String finalTimerString = "";
        String secondsString = "";

        int hours = (milliseconds / (1000 * 60 * 60));
        int minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60);
        int seconds = ((milliseconds % (1000 * 60 * 60)) % (1000 * 60) / 1000);

        if (seconds < 10) {
            secondsString = "0" + seconds;
        } else {
            secondsString = "" + seconds;
        }

        finalTimerString = minutes + ":" + secondsString;

        return finalTimerString;
    }


    private void playSong(String fileName) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }

            AssetManager assetManager = getAssets();
            InputStream inputStream = assetManager.open("Music/" + fileName);

            File outputFile = new File(getFilesDir(), fileName);
            FileOutputStream outputStream = new FileOutputStream(outputFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            inputStream.close();
            outputStream.close();

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(outputFile.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();

            String artist = getArtistFromFile(fileName);

            String songName = fileName.replace(".mp3", "");

            String songTitleAndArtist =  artist + " - " + songName;

            songTitleControlPanel.setText(songTitleAndArtist);

            setAlbumArt(outputFile);

            if (mediaPlayer != null) {
                seekBar.setMax(mediaPlayer.getDuration());
            }
            updateSeekBarProgress();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getSongTitleFromFile(String fileName) {
        return fileName.replace(".mp3", "");
    }

    private String getArtistFromFile(String fileName) {
        try {
            AssetManager assetManager = getAssets();
            InputStream inputStream = assetManager.open("Music/" + fileName);
            File tempFile = File.createTempFile("temp", null, getCacheDir());
            FileOutputStream outputStream = new FileOutputStream(tempFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            inputStream.close();
            outputStream.close();

            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(tempFile.getAbsolutePath());

            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);

            tempFile.delete();

            return artist != null ? artist : "Unknown Artist";
        } catch (IOException e) {
            e.printStackTrace();
            return "Unknown Artist";
        }
    }

    private ArrayList<String> findSongs(AssetManager assetManager) {
        ArrayList<String> songFiles = new ArrayList<>();
        try {
            String[] files = assetManager.list("Music");
            for (String file : files) {
                if (file.endsWith(".mp3")) {
                    songFiles.add(file);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return songFiles;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
