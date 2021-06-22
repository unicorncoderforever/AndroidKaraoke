package com.shri.karaokesdk.audiomodule.filter;

public class GainProcessor  {
    private double gain;

    public GainProcessor(double newGain) {
        setGain(newGain);
    }

    public void setGain(double newGain) {
        this.gain = newGain;
    }


    public boolean process(float[] sampleOutput) {
        float[] audioFloatBuffer = sampleOutput;
        for (int i = 0; i < audioFloatBuffer.length; i++) {
            float newValue = (float) (audioFloatBuffer[i] * gain);
            if (newValue > 1.0f) {
                newValue = 1.0f;
            } else if (newValue < -1.0f) {
                newValue = -1.0f;
            }
            audioFloatBuffer[i] = newValue;
        }
        return true;
    }

}
        // NOOP
