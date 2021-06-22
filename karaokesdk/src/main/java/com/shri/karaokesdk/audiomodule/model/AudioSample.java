package com.shri.karaokesdk.audiomodule.model;

public  class AudioSample {
            public final long audioPresentationTime;
            public final byte[] sampleData;
            int sampleId;
            static int idCount;

            public AudioSample(byte[] sData, long inAudioPresentationTimeNs) {
                audioPresentationTime = inAudioPresentationTimeNs;
                sampleData = sData;
                sampleId = idCount;
                idCount++;
            }
        }


