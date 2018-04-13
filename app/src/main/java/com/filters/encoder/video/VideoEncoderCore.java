/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.filters.encoder.video;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This class wraps up the core components used for surface-input video encoding.
 * <p>
 * Once created, frames are fed to the input surface.  Remember to provide the presentation
 * time stamp, and always call drainEncoder() before swapBuffers() to ensure that the
 * producer side doesn't get backed up.
 * <p>
 * This class is not thread-safe, with one exception: it is valid to use the input surface
 * on one thread, and drain the output on a different thread.
 */
public class VideoEncoderCore {
    public static final int SAMPLE_RATE = 44100;
    public static final int SAMPLES_PER_FRAME = 1024; // AAC
    public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final String TAG = "VideoEncoderCore";
    private static final boolean VERBOSE = true;
    // TODO: these ought to be configurable as well
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 30;               // 30fps
    private static final int IFRAME_INTERVAL = 5;           // 5 seconds between I-frames
    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";
    final int TOTAL_NUM_TRACKS = 2;
    int numTracksAdded = 0;
    MediaFormat videoFormat;
    long startWhen;
    private MediaCodec mAudioEncoder;
    private Surface mInputSurface;
    private MediaMuxer mMuxer;
    private MediaCodec mEncoder;
    private MediaCodec.BufferInfo mBufferInfo;
    private MediaCodec.BufferInfo mAudioBufferInfo;
    private TrackIndex mTrackIndex;
    private boolean mMuxerStarted;
    private TrackIndex mAudioTrackIndex;
    // Audio
    private boolean recording = false;
    private MediaFormat mAudioFormat;
    private AudioRecord audioRecord;

    private long lastEncodedAudioTimeStamp = 0;


    /**
     * Configures encoder and muxer state, and prepares the input Surface.
     */
    public VideoEncoderCore(int width, int height, int bitRate, File outputFile)
            throws IOException {
        mBufferInfo = new MediaCodec.BufferInfo();

        videoFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        mTrackIndex = new TrackIndex();

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        if (VERBOSE) {
            Log.d(TAG, "format: " + videoFormat);
        }

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mEncoder.createInputSurface();
        mEncoder.start();

        mAudioBufferInfo = new MediaCodec.BufferInfo();

        mAudioFormat = new MediaFormat();
        mAudioTrackIndex = new TrackIndex();

        mAudioFormat = new MediaFormat();
        mAudioFormat.setString(MediaFormat.KEY_MIME, AUDIO_MIME_TYPE);
        mAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mAudioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
        mAudioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        mAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
        mAudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 13312);

        mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
        mAudioEncoder.configure(mAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mAudioEncoder.start();


        // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
        // because our MediaFormat doesn't have the Goodies.  These can only be
        // obtained from the encoder after it has started processing data.
        //
        // We're not actually interested in multiplexing audio.  We just want to convert
        // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
        try {
            mMuxer = new MediaMuxer(outputFile.toString(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            throw new RuntimeException("MediaMuxer creation failed ", e);
        }

        mTrackIndex.index = -1;
//        mAudioTrackIndex = -1;
        mMuxerStarted = false;
    }

    /**
     * Returns the encoder's input surface.
     */
    public Surface getInputSurface() {
        return mInputSurface;
    }

    /**
     * Releases encoder resources.
     */
    public void release() {
        if (VERBOSE) {
            Log.d(TAG, "releasing encoder objects");
        }
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mMuxer != null) {
            // TODO: stop() throws an exception if you haven't fed it any data.  Keep track
            //       of frames submitted, and don't call stop() if we haven't written anything.
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }

    /**
     * Extracts all pending data from the encoder and forwards it to the muxer.
     * <p>
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     * <p>
     * We're just using the muxer to get a .mp4 file (instead of a raw H.264 stream).  We're
     * not recording audio.
     */
    public void drainEncoder(boolean endOfStream) {
        final int TIMEOUT_USEC = 10000;
        if (VERBOSE) {
            Log.d(TAG, "drainEncoder(" + endOfStream + ")");
        }

        if (endOfStream) {
            if (VERBOSE) {
                Log.d(TAG, "sending EOS to encoder");
            }
            mEncoder.signalEndOfInputStream();
        }

        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        while (true) {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break;      // out of while
                } else {
                    if (VERBOSE) {
                        Log.d(TAG, "no output available, spinning to await EOS");
                    }
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mEncoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (mMuxerStarted) {
                    throw new RuntimeException("format changed twice");
                }
                MediaFormat newFormat = mEncoder.getOutputFormat();
                Log.d(TAG, "encoder output format changed: " + newFormat);

                // now that we have the Goodies, start the muxer
                mTrackIndex.index = mMuxer.addTrack(newFormat);
                mMuxer.start();
                mMuxerStarted = true;
            } else if (encoderStatus < 0) {
                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                        encoderStatus);
                // let's ignore it
            } else {
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }

                if ((mAudioBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) {
                        Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    }
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    if (!mMuxerStarted) {
                        throw new RuntimeException("muxer hasn't started");
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

                    mMuxer.writeSampleData(mTrackIndex.index, encodedData, mBufferInfo);
                    if (VERBOSE) {
                        Log.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer, ts=" +
                                mBufferInfo.presentationTimeUs);
                    }
                }

                mEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly");
                    } else {
                        if (VERBOSE) {
                            Log.d(TAG, "end of stream reached");
                        }
                    }
                    break;      // out of while
                }
            }
        }
    }


    public void sendAudioToEncoder(boolean endOfStream) {
        setupAudioRecord();
        startAudioRecord();
        startWhen = System.nanoTime();
        if (!endOfStream) {
            drainEncoder(mAudioEncoder, mAudioBufferInfo, mAudioTrackIndex.index, endOfStream);
        }
        // send current frame data to encoder
        try {
            ByteBuffer[] inputBuffers = mAudioEncoder.getInputBuffers();
            int inputBufferIndex = mAudioEncoder.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                long presentationTimeNs = System.nanoTime();
                int inputLength = audioRecord.read(inputBuffer, SAMPLES_PER_FRAME * 10);
                if (inputLength == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "Audio read error");
                }

                long presentationTimeUs = (presentationTimeNs - startWhen) / 1000;
                Log.i(TAG, "queueing " + inputLength + " audio bytes with pts " + presentationTimeUs);
                if (endOfStream) {
                    Log.i(TAG, "EOS received in offerEncoder");
                    mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, inputLength, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    drainEncoder(mAudioEncoder, mAudioBufferInfo, mAudioTrackIndex.index, endOfStream);
                } else {
                    mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, inputLength, presentationTimeUs, 0);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "_offerAudioEncoder exception");
            t.printStackTrace();
        }
    }

    private void drainEncoder(MediaCodec encoder, MediaCodec.BufferInfo bufferInfo, int trackIndex, boolean endOfStream) {
        final int TIMEOUT_USEC = 100;
        if (VERBOSE) {
            Log.d(TAG, "drainEncoder(" + endOfStream + ")");
        }
        if (encoder == mEncoder && endOfStream) {
            // Audio data is queued with EOS flag when appropriate
            // However, since video comes directly from surface, we signal EOS here
            if (VERBOSE) {
                Log.d(TAG, "sending EOS to encoder");
            }
            encoder.signalEndOfInputStream();
        }
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        while (true) {
            int encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    if (VERBOSE) {
                        Log.d(TAG, "no output available. aborting drain");
                    }
                    break;      // out of while
                } else {
                    if (VERBOSE) {
                        Log.d(TAG, "no output available, spinning to await EOS");
                    }
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = encoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once

                if (mMuxerStarted) {
                    throw new RuntimeException("format changed after muxer start");
                }
                MediaFormat newFormat = encoder.getOutputFormat();

                // now that we have the Magic Goodies, start the muxer
                mTrackIndex.index = mMuxer.addTrack(newFormat);
                numTracksAdded++;
                Log.d(TAG, "encoder output format changed: " + ((encoder == mEncoder) ? videoFormat : mAudioFormat) + ". Added track index: " + mAudioTrackIndex);
                if (numTracksAdded == TOTAL_NUM_TRACKS) {
                    mMuxer.start();
                    mMuxerStarted = true;
                    Log.i(TAG, "All tracks added. Muxer started");
                }

            } else if (encoderStatus < 0) {
                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                        encoderStatus);
                // let's ignore it
            } else {
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }


                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) {
                        Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    }
                    bufferInfo.size = 0;
                }


                if (bufferInfo.size != 0) {
                    if (!mMuxerStarted) {
                        //throw new RuntimeException("muxer hasn't started");
                        encoder.releaseOutputBuffer(encoderStatus, false);
                        Log.w(TAG, "Muxer hasn't started, dropping encoded frames");
                        break;
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(bufferInfo.offset);
                    encodedData.limit(bufferInfo.offset + bufferInfo.size);
                    if (encoder == mAudioEncoder) {
                        if (bufferInfo.presentationTimeUs < lastEncodedAudioTimeStamp) {
                            bufferInfo.presentationTimeUs = lastEncodedAudioTimeStamp += 23219; // Magical AAC encoded frame time
                        }
                        lastEncodedAudioTimeStamp = bufferInfo.presentationTimeUs;
                    }
                    mMuxer.writeSampleData(mAudioTrackIndex.index, encodedData, bufferInfo);
                    if (encoder == mAudioEncoder) {
                        if (VERBOSE) {
                            Log.d(TAG, "sent " + bufferInfo.size + ((encoder == mEncoder) ? " video" : " audio") + " bytes to muxer with pts " + bufferInfo.presentationTimeUs);
                        }
                    }
                }

                encoder.releaseOutputBuffer(encoderStatus, false);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly");
                    } else {
                        if (VERBOSE) {
                            Log.d(TAG, "end of stream reached");
                        }
                    }
                    break;      // out of while
                }
            }
        }
        long endTime = System.nanoTime();
    }


    private void setupAudioRecord() {
        int min_buffer_size = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        int buffer_size = SAMPLES_PER_FRAME * 10;
        if (buffer_size < min_buffer_size) {
            buffer_size = ((min_buffer_size / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;
        }

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,       // source
                SAMPLE_RATE,                         // sample rate, hz
                CHANNEL_CONFIG,                      // channels
                AUDIO_FORMAT,                        // audio format
                buffer_size);                        // buffer size (bytes)
    }

    private void startAudioRecord() {
        if (audioRecord != null) {
            audioRecord.startRecording();
        }
    }

    public void stopRecording() {
        recording = false;
        if (audioRecord != null) {
            audioRecord.stop();
        }
    }

    // Can't pass an int by reference in Java...
    class TrackIndex {
        int index = 0;
    }
}
