package com.shri.karaokesdk.audiomodule.model;

/**
 * Created by preethirao on 14/05/16 AD.
 */

public class UCAudioSample {
    byte[] mInputFrame;
    long mPresentaionTime;
    boolean mIsEof;

    public UCAudioSample(byte[] inputFrame, long presentaionTime, boolean isEof) {
        mInputFrame = inputFrame;
        mPresentaionTime = presentaionTime;
        mIsEof = isEof;
    }

    //TODO: reuse mInputFrame
    public void setValue(byte[] inputFrame, long presentaionTime, boolean isEof) {
        mInputFrame = inputFrame;
        mPresentaionTime = presentaionTime;
        mIsEof = isEof;
    }

    public byte[] getInputFrame() {
        return mInputFrame;
    }

    public long getPresentaionTime() {
        return mPresentaionTime;
    }

    public boolean isEof() {
        return mIsEof;
    }

    public void setInputFrame(byte[] inputFrame) {
        mInputFrame = inputFrame;
    }

    public void setPresentaionTime(long presentaionTime) {
        mPresentaionTime = presentaionTime;
    }

    public void setEof(boolean isEof) {
        mIsEof = isEof;
    }
}
