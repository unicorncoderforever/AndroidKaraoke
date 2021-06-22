package com.shri.karaokesdk.audiomodule.model;

public class KaroukeAudioSample {
    public long audioPresentationTime;
    public byte[] sampleData;
    public short[] vocalData;
    int sampleId;
    static int idCount;

    public KaroukeAudioSample(byte[] sData,short[] vocalData, long inAudioPresentationTimeNs) {
        audioPresentationTime = inAudioPresentationTimeNs;
        sampleData = sData;
        this.vocalData  = vocalData;
        sampleId = idCount;
        idCount++;
    }
}
