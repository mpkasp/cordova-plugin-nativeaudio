//
//
//  NativeAudio.java
//
//  Created by Sidney Bofah on 2014-06-26.
//

package com.rjfun.cordova.plugin.nativeaudio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;

import java.io.FileInputStream;
import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.util.Log;
import android.view.KeyEvent;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONObject;


public class NativeAudio extends CordovaPlugin implements AudioManager.OnAudioFocusChangeListener {
    private static final String TAG = "NativeAudio";
    
    /* options */
    public static final String OPT_FADE_MUSIC = "fadeMusic";

	public static final String ERROR_NO_AUDIOID="A reference does not exist for the specified audio id.";
	public static final String ERROR_AUDIOID_EXISTS="A reference already exists for the specified audio id.";

	public static final String SET_OPTIONS="setOptions";
	public static final String PRELOAD_SIMPLE="preloadSimple";
	public static final String PRELOAD_COMPLEX="preloadComplex";
	public static final String PLAY="play";
	public static final String STOP="stop";
	public static final String LOOP="loop";
	public static final String UNLOAD="unload";
    public static final String PAUSE="pause";
    public static final String RESUME="resume";
    public static final String ADD_COMPLETE_LISTENER="addCompleteListener";
	public static final String SET_VOLUME_FOR_COMPLEX_ASSET="setVolumeForComplexAsset";

	private static HashMap<String, NativeAudioAsset> assetMap;
    private static ArrayList<NativeAudioAsset> resumeList;
    private static HashMap<String, CallbackContext> completeCallbacks;
    private boolean fadeMusic = false;

    public void setOptions(JSONObject options) {
		if(options != null) {
			if(options.has(OPT_FADE_MUSIC)) this.fadeMusic = options.optBoolean(OPT_FADE_MUSIC);
		}
	}

	private PluginResult executePreload(JSONArray data) {
		String audioID;
		try {
			audioID = data.getString(0);
			if (!assetMap.containsKey(audioID)) {
				String assetPath = data.getString(1);
				Log.d(TAG, "preloadComplex - " + audioID + ": " + assetPath);

				double volume;
				if (data.length() <= 2) {
					volume = 1.0;
				} else {
					volume = data.getDouble(2);
				}

				int voices;
				if (data.length() <= 3) {
					voices = 1;
				} else {
					voices = data.getInt(3);
				}

				String fullPath = assetPath.replace("file://", "");

                Log.d(TAG, "[audio] about to play: " + fullPath);

				NativeAudioAsset asset = new NativeAudioAsset(
						fullPath, voices, (float)volume);
				assetMap.put(audioID, asset);

				return new PluginResult(Status.OK);
			} else {
				return new PluginResult(Status.ERROR, ERROR_AUDIOID_EXISTS);
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		} catch (IOException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}
	}

	private PluginResult executePlayOrLoop(String action, JSONArray data) {
		final String audioID;
        AudioManager am = (AudioManager)cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);

        int result = am.requestAudioFocus(this,
            // Use the music stream.
            AudioManager.STREAM_MUSIC,
            // Request permanent focus.
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);

        Log.d(TAG, "[play] requested audio focus: " + result);

		try {
			audioID = data.getString(0);
			//Log.d( TAG, "play - " + audioID );

			if (assetMap.containsKey(audioID)) {
				NativeAudioAsset asset = assetMap.get(audioID);
				if (LOOP.equals(action))
					asset.loop();
				else
					asset.play(new Callable<Void>() {
                        public Void call() throws Exception {
				if (completeCallbacks != null) {
				    CallbackContext callbackContext = completeCallbacks.get(audioID);
				    if (callbackContext != null) {
					JSONObject done = new JSONObject();
					done.put("id", audioID);
					callbackContext.sendPluginResult(new PluginResult(Status.OK, done));
				    }
				}
                            return null;
                        }
                    });
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		} catch (IOException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}

		return new PluginResult(Status.OK);
	}

	private PluginResult executeStop(JSONArray data) {
		String audioID;
		try {
			audioID = data.getString(0);
			//Log.d(TAG, "stop - " + audioID );

			if (assetMap.containsKey(audioID)) {
				NativeAudioAsset asset = assetMap.get(audioID);
				asset.stop();
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}

		return new PluginResult(Status.OK);
	}

    private PluginResult executePause(JSONArray data) {
        String audioID;
        try {
            audioID = data.getString(0);
            Log.d(TAG, "[audio] pause - " + audioID );
            if (assetMap.containsKey(audioID)) {
                NativeAudioAsset asset = assetMap.get(audioID);
                boolean wasPlaying = asset.pause();
                if (wasPlaying) {
                    resumeList.add(asset);
                }
            } else if (!assetMap.isEmpty()) {
                for (NativeAudioAsset asset : assetMap.values()) {
                    boolean wasPlaying = asset.pause();
                    if (wasPlaying) {
                        resumeList.add(asset);
                    }
                }
            } else {
                return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
            }
        } catch (JSONException e) {
            return new PluginResult(Status.ERROR, e.toString());
        }

        return new PluginResult(Status.OK);
    }

    private PluginResult executeResume(JSONArray data) {
        String audioID;
        try {
            audioID = data.getString(0);
            Log.d(TAG, "[audio] resume");
            if (assetMap.containsKey(audioID)) {
                Log.d(TAG, "[audio] resuming by audioId - " + audioID );
                NativeAudioAsset asset = assetMap.get(audioID);
                asset.resume();
            } else if (!resumeList.isEmpty()) {
                Log.d(TAG, "[audio] resuming all available.");
                while (!resumeList.isEmpty()) {
                    NativeAudioAsset asset = resumeList.remove(0);
                    asset.resume();
                }
            } else {
                Log.d(TAG, "[audio] can't find anything to resume!");
                return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
            }
        } catch (JSONException e) {
            return new PluginResult(Status.ERROR, e.toString());
        }

        return new PluginResult(Status.OK);
    }

	private PluginResult executeUnload(JSONArray data) {
		String audioID;
		try {
			audioID = data.getString(0);
			Log.d(TAG, "unload - " + audioID );

			if (assetMap.containsKey(audioID)) {
				NativeAudioAsset asset = assetMap.get(audioID);
				asset.unload();
				assetMap.remove(audioID);
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		} catch (IOException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}

		return new PluginResult(Status.OK);
	}

	private PluginResult executeSetVolumeForComplexAsset(JSONArray data) {
		String audioID;
		float volume;
		try {
			audioID = data.getString(0);
			volume = (float) data.getDouble(1);
			Log.d(TAG, "setVolume - " + audioID );

			if (assetMap.containsKey(audioID)) {
				NativeAudioAsset asset = assetMap.get(audioID);
				asset.setVolume(volume);
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}
		return new PluginResult(Status.OK);
	}
	@Override
	protected void pluginInitialize() {
		AudioManager am = (AudioManager)cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);

	    int result = am.requestAudioFocus(this,
            // Use the music stream.
            AudioManager.STREAM_MUSIC,
            // Request permanent focus.
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);

        Log.d(TAG, "[pluginInitialize] requested audio focus: " + result);

		// Allow android to receive the volume events
		this.webView.setButtonPlumbedToJs(KeyEvent.KEYCODE_VOLUME_DOWN, false);
		this.webView.setButtonPlumbedToJs(KeyEvent.KEYCODE_VOLUME_UP, false);
	}

	@Override
	public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext) {
		Log.d(TAG, "Plugin Called: " + action);

		PluginResult result = null;
		initSoundPool();

		try {
			if (SET_OPTIONS.equals(action)) {
                JSONObject options = data.optJSONObject(0);
                this.setOptions(options);
                callbackContext.sendPluginResult( new PluginResult(Status.OK) );

			} else if (PRELOAD_SIMPLE.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executePreload(data) );
		            }
		        });

			} else if (PRELOAD_COMPLEX.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executePreload(data) );
		            }
		        });

			} else if (PLAY.equals(action) || LOOP.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executePlayOrLoop(action, data) );
		            }
		        });

			} else if (STOP.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executeStop(data) );
		            }
		        });

            } else if (PAUSE.equals(action)) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        callbackContext.sendPluginResult( executePause(data) );
                    }
                });

            }else if (RESUME.equals(action)) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        callbackContext.sendPluginResult( executeResume(data) );
                    }
                });

            }else if (UNLOAD.equals(action)) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        executeStop(data);
                        callbackContext.sendPluginResult( executeUnload(data) );
                    }
                });
            } else if (ADD_COMPLETE_LISTENER.equals(action)) {
                if (completeCallbacks == null) {
                    completeCallbacks = new HashMap<String, CallbackContext>();
                }
                try {
                    String audioID = data.getString(0);
                    completeCallbacks.put(audioID, callbackContext);
                } catch (JSONException e) {
                    callbackContext.sendPluginResult(new PluginResult(Status.ERROR, e.toString()));
		}
	    } else if (SET_VOLUME_FOR_COMPLEX_ASSET.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
			public void run() {
	                        callbackContext.sendPluginResult( executeSetVolumeForComplexAsset(data) );
                    }
                 });
	    }
            else {
                result = new PluginResult(Status.OK);
            }
		} catch (Exception ex) {
			result = new PluginResult(Status.ERROR, ex.toString());
		}

		if(result != null) callbackContext.sendPluginResult( result );
		return true;
	}

	private void initSoundPool() {

		if (assetMap == null) {
			assetMap = new HashMap<String, NativeAudioAsset>();
		}

        if (resumeList == null) {
            resumeList = new ArrayList<NativeAudioAsset>();
        }
	}

    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            Log.d(TAG, "[onAudioFocusChange] AUDIOFOCUS_LOSS_TRANSIENT");
            // Pause playback
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            Log.d(TAG, "[onAudioFocusChange] AUDIOFOCUS_GAIN");
            // Resume playback
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            Log.d(TAG, "[onAudioFocusChange] AUDIOFOCUS_LOSS");
            // Stop playback
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        Log.d(TAG, "[audio] onPause - doing nothing");
        // for (HashMap.Entry<String, NativeAudioAsset> entry : assetMap.entrySet()) {
        //     NativeAudioAsset asset = entry.getValue();
        //     boolean wasPlaying = asset.pause();
        //     if (wasPlaying) {
        //         resumeList.add(asset);
        //     }
        // }
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        Log.d(TAG, "[audio] onResume - doing nothing");
        // while (!resumeList.isEmpty()) {
        //     NativeAudioAsset asset = resumeList.remove(0);
        //     asset.resume();
        // }
    }
}
