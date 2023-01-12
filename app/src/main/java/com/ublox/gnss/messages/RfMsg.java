package com.ublox.gnss.messages;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class RfMsg implements Parcelable {
    private int noisePerMS;
    private int agcCnt;
    private int cwSuppression;

    public RfMsg() {

    }

    protected RfMsg(Parcel in) {
        noisePerMS = in.readInt();
        agcCnt = in.readInt();
        cwSuppression = in.readInt();
    }

    public static final Creator<RfMsg> CREATOR = new Creator<RfMsg>() {
        @Override
        public RfMsg createFromParcel(Parcel in) {
            return new RfMsg(in);
        }

        @Override
        public RfMsg[] newArray(int size) {
            return new RfMsg[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(noisePerMS);
        dest.writeInt(agcCnt);
        dest.writeInt(cwSuppression);
    }

    public int getNoisePerMS() {
        return noisePerMS;
    }

    public void setNoisePerMS(int noisePerMS) {
        this.noisePerMS = noisePerMS;
    }

    public int getAgcCnt() {
        return agcCnt;
    }

    public void setAgcCnt(int agcCnt) {
        this.agcCnt = agcCnt;
    }

    public int getCwSuppression() {
        return cwSuppression;
    }

    public void setCwSuppression(int cwSuppression) {
        this.cwSuppression = cwSuppression;
    }
}
