package com.shri.karaokesdk.audiomodule.utility;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
public class Utils {
    public static final int SAMPLE_GENERATED = 1024;
    private static final double  ONE_BY_INT_MIN = -3.051757812e-05;
    private static final double  ONE_BY_INT_MAX =  3.051850948e-05;
    public final static String TAG = "calls";


    /**
     * Returns the list of instant actions to be displayed
     */

    public static short[] byteToShort(byte[] data) {
        if (data != null) {
            short[] shorts = new short[data.length / 2];
            // to turn bytes to shorts as either big endian or little endian.
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
            return shorts;
        } else
            return null;
    }


    public static byte[] ShortToByte_ByteBuffer_Method(short[] input) {
        byte[] bytes2 = new byte[input.length * 2];
        ByteBuffer.wrap(bytes2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                .put(input);
        return bytes2;
    }

    public static float mix(float ip1,float ip2) {
        float op ;
        if ( ip1 < 0 && ip2 < 0 ) {
            // If both samples are negative, mixed signal must have an amplitude between  the lesser of A and B, and the minimum permissible negative amplitude
            op = (float) ((ip1 + ip2) - ((ip1 * ip2) * ONE_BY_INT_MIN));
        } else if ( ip1 > 0 && ip2 > 0 ) {
            // If both samples are positive, mixed signal must have an amplitude between the greater of A and B, and the maximum permissible positive amplitude
            op = (float) ((ip1 + ip2) - ((ip1 * ip2) * ONE_BY_INT_MAX));

        } else {
            // If samples are on opposite sides of the 0-crossing, mixed signal should reflect that samples cancel each other out somewhat
            op = ip1 + ip2;
        }

        if(op < -1f){
            op = -1f;
        }else if(op > 1f){
            op = 1f;
        }
        return op;

    }


    private static final float MAX_VALUE_OF_SHORT = 32768.0f;
   public static int shortToFloat(short[] input, float[] output) {
        try {
            int len = input.length;

            for (int i = 0; i < len; i=i+2) {
                output[i] = ((float) input[i] / MAX_VALUE_OF_SHORT);
                output[i + 1] = ((float) input[i + 1] / MAX_VALUE_OF_SHORT);
                if(output[i] < -1f){
                    output[i] = -1f;
                }else if(output[i] > 1f){
                    output[i] = 1f;
                }
                if(output[i + 1] < -1f){
                    output[i  + 1] = -1f;
                }else if(output[i + 1] > 1f){
                    output[i + 1] = 1f;
                }

            }
            return len;
        } catch (NullPointerException e) {
            e.printStackTrace();
            return 0;
        }
    }
    public static void floatToShort(float[] indata, int len, short[] processedBuf) {
        for (int i = 0; i < len; i= i+2) {
            processedBuf[i] = (short) (indata[i] * MAX_VALUE_OF_SHORT);
            processedBuf[i+1] = (short) (indata[i + 1] * MAX_VALUE_OF_SHORT);

        }
    }
    public static short[] getVocalessAudio(short[] shortAudioData) {
        for(int i = 0 ; i < shortAudioData.length;i = i + 2){
            short data = (short)((shortAudioData[i] - shortAudioData[i + 1])/2f);
            shortAudioData[i] = data;
            shortAudioData[i + 1] = data;

        }
        return  shortAudioData;
    }

    public static void scaleSamples(
            short samples[],
            int position,
            int numSamples,
            float volume) {
        int fixedPointVolume = (int)(volume*4096.0f);
        int start = position * 2;
        int stop = start + numSamples * 2;
//        Log.e(TAG, "scaleSamples: "+start + "stop "+ RECORDER_CHANNELS + " length "+samples.length);
        for(int xSample = start; xSample < stop; xSample++) {
            int value = (samples[xSample]*fixedPointVolume) >> 12;
            if(value > 32767) {
                value = 32767;
            } else if(value < -32767) {
                value = -32767;
            }
            samples[xSample] = (short)value;
        }
    }

}
