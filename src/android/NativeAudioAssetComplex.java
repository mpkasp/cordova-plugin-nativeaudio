//
//
//  NativeAudioAssetComplex.java
//
//  Created by Sidney Bofah on 2014-06-26.
//

package com.rjfun.cordova.plugin.nativeaudio;

import java.io.IOException;
import java.util.concurrent.Callable;

import android.util.Log;
import java.io.FileDescriptor;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;

public class NativeAudioAssetComplex implements OnPreparedListener, OnCompletionListener {
	private static final String TAG = "NativeAudio";

	private static final int INVALID = 0;
	private static final int PREPARED = 1;
	private static final int PENDING_PLAY = 2;
	private static final int PLAYING = 3;
	private static final int PENDING_LOOP = 4;
	private static final int LOOPING = 5;

	private MediaPlayer mp;
	private int state;
    Callable<Void> completeCallback;

	public NativeAudioAssetComplex(String fd, float volume)  throws IOException
	{
		state = INVALID;
		mp = new MediaPlayer();
        mp.setOnCompletionListener(this);
        mp.setOnPreparedListener(this);
		mp.setDataSource(fd);
		mp.setAudioAttributes(new AudioAttributes.Builder()
			.setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
			.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
			.build());
		mp.setVolume(volume, volume);
		mp.prepare();
	}

	public void play(Callable<Void> completeCb) throws IOException
	{
		Log.d(TAG, "[audio] play called");
        completeCallback = completeCb;
		invokePlay( false );
	}

	private void invokePlay( Boolean loop )
	{
		Boolean playing = mp.isPlaying();
		if ( playing )
		{
			Log.d(TAG, "[audio] playing");
			mp.pause();
			mp.setLooping(loop);
			mp.seekTo(0);
			mp.start();
		}
		if ( !playing && state == PREPARED )
		{
			Log.d(TAG, "[audio] prepared");
			state = (loop ? PENDING_LOOP : PENDING_PLAY);
			onPrepared( mp );
		}
		else if ( !playing )
		{
			Log.d(TAG, "[audio] not playing");
			state = (loop ? PENDING_LOOP : PENDING_PLAY);
			mp.setLooping(loop);
			mp.start();
		}
	}

	public boolean pause()
	{
		try
		{
    				if ( mp.isPlaying() )
				{
					mp.pause();
					return true;
				}
        	}
		catch (IllegalStateException e)
		{
		// I don't know why this gets thrown; catch here to save app
		}
		return false;
	}

	public void resume()
	{
		mp.start();
	}

    public void stop()
	{
		try
		{
			if ( mp.isPlaying() )
			{
				state = INVALID;
				mp.pause();
				mp.seekTo(0);
	           	}
		}
	        catch (IllegalStateException e)
	        {
            // I don't know why this gets thrown; catch here to save app
	        }
	}

	public void setVolume(float volume)
	{
	        try
	        {
			mp.setVolume(volume,volume);
            	}
            	catch (IllegalStateException e)
		{
                // I don't know why this gets thrown; catch here to save app
		}
	}

	public void loop() throws IOException
	{
		invokePlay( true );
	}

	public void unload() throws IOException
	{
		this.stop();
		mp.release();
	}

	public void onPrepared(MediaPlayer mPlayer)
	{
		if (state == PENDING_PLAY)
		{
			mp.setLooping(false);
			mp.seekTo(0);
			mp.start();
			state = PLAYING;
		}
		else if ( state == PENDING_LOOP )
		{
			mp.setLooping(true);
			mp.seekTo(0);
			mp.start();
			state = LOOPING;
		}
		else
		{
			state = PREPARED;
			mp.seekTo(0);
		}
	}

	public void onCompletion(MediaPlayer mPlayer)
	{
		Log.d(TAG, "[audio] Complete.");
		if (state != LOOPING)
		{
			this.state = INVALID;
			try {
				Log.d(TAG, "[audio] would have stopped...");
				// this.stop();
				if (completeCallback != null)
                	completeCallback.call();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
}
