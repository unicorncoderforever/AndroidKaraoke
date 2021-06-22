/*
 * Copyright (C) 2011 Jacquet Wong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shri.karaokesdk.audiomodule.resample;

import android.util.Log;

/**
 * Resample signal data (base on bytes)
 * 
 * @author jacquet
 *
 */
public class Resampler {

	public Resampler() {
	}

	/**
	 * Do resampling. Currently the amplitude is stored by short such that maximum bitsPerSample is 16 (bytePerSample is 2)
	 * @param sourceData    The source data in bytes
	 * @param bitsPerSample How many bits represents one sample (currently supports max. bitsPerSample=16)
	 * @param sourceRate    Sample rate of the source data
	 * @param targetRate    Sample rate of the target data
	 * @return re-sampled data
	 */
	public static byte[] reSample(byte[] sourceData, int sourceLen, int bitsPerSample,
								  int sourceRate, int targetRate, boolean isMono) {
		if (isMono) {
			return reSampleInternal(sourceData, sourceLen, bitsPerSample, sourceRate, targetRate);
		} else {
			return reSampleStereo(sourceData, sourceLen, bitsPerSample, sourceRate, targetRate);
		}
	}

	private static final String TAG = "Resampler";
	private static byte[] reSampleStereo(byte[] sourceData, int sourceLen, int bitsPerSample,
										 int sourceRate, int targetRate) {
		Log.e(TAG, "reSampleStereo: " );
		byte[] leftData = new byte[sourceLen / 2];
		byte[] rightData = new byte[sourceLen / 2];

		int monoIndex = 0;
		for (int index = 0; index < sourceLen; index += 4) {
			leftData[monoIndex] = sourceData[index];
			leftData[monoIndex + 1] = sourceData[index + 1];
			rightData[monoIndex] = sourceData[index + 2];
			rightData[monoIndex + 1] = sourceData[index + 3];
			monoIndex += 2;
		}

		byte[] resampledLeftData = reSampleInternal(leftData, leftData.length, bitsPerSample,
				sourceRate, targetRate);
		byte[] resampledRightData = reSampleInternal(rightData, rightData.length, bitsPerSample,
				sourceRate, targetRate);
		final int resampledMonoLength = resampledLeftData.length * 2;
		byte[] resampledData = new byte[resampledMonoLength];
		monoIndex = 0;
		for (int index = 0; index < resampledMonoLength; index += 4) {
			resampledData[index] = resampledLeftData[monoIndex];
			resampledData[index + 1] = resampledLeftData[monoIndex + 1];
			resampledData[index + 2] = resampledRightData[monoIndex];
			resampledData[index + 3] = resampledRightData[monoIndex + 1];
			monoIndex += 2;
		}
		return resampledData;
	}

	private static byte[] reSampleInternal(byte[] sourceData, int sourceLen, int bitsPerSample,
										   int sourceRate, int targetRate) {

		// make the bytes to amplitudes first
		int bytePerSample = bitsPerSample / 8;
//        int numSamples = sourceData.length / bytePerSample;
		int numSamples = sourceLen / bytePerSample;
		short[] amplitudes = new short[numSamples];    // 16 bit, use a short to store

		int pointer = 0;
		for (int i = 0; i < numSamples; i++) {
			short amplitude = 0;
			for (int byteNumber = 0; byteNumber < bytePerSample; byteNumber++) {
				// little endian
				amplitude |= (short) ((sourceData[pointer++] & 0xFF) << (byteNumber * 8));
			}
			amplitudes[i] = amplitude;
		}
		// end make the amplitudes

		// do interpolation
//        LinearInterpolation reSample = new LinearInterpolation();
		short[] targetSample = LinearInterpolation.interpolate(sourceRate, targetRate, amplitudes);
		int targetLength = targetSample.length;
		// end do interpolation

		//Remove the high frequency signals with a digital filter,
		// leaving a signal containing only half-sample-rated frequency information,
		// but still sampled at a rate of target sample rate. Usually FIR is used

		// convert the amplitude to bytes
		byte[] bytes;
		if (bytePerSample == 1) {
			bytes = new byte[targetLength];
			for (int i = 0; i < targetLength; i++) {
				bytes[i] = (byte) targetSample[i];
			}
		} else {
			// suppose bytePerSample==2
			bytes = new byte[targetLength * 2];
			for (int i = 0; i < targetSample.length; i++) {
				// little endian
				bytes[i * 2] = (byte) (targetSample[i] & 0xff);
				bytes[i * 2 + 1] = (byte) ((targetSample[i] >> 8) & 0xff);
			}
		}
		// end convert the amplitude to bytes

		return bytes;
	}
}