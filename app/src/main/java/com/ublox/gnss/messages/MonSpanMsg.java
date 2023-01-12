package com.ublox.gnss.messages;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class MonSpanMsg implements Parcelable {

    private int[] spectrum;
    private long span;
    private long res;
    private long center;
    private int pga;

    public MonSpanMsg() {
    }

    protected MonSpanMsg(Parcel in) {
        spectrum = in.createIntArray();
        span = in.readLong();
        res = in.readLong();
        center = in.readLong();
        pga = in.readInt();
    }

    public static final Creator<MonSpanMsg> CREATOR = new Creator<MonSpanMsg>() {
        @Override
        public MonSpanMsg createFromParcel(Parcel in) {
            return new MonSpanMsg(in);
        }

        @Override
        public MonSpanMsg[] newArray(int size) {
            return new MonSpanMsg[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeIntArray(spectrum);
        dest.writeLong(span);
        dest.writeLong(res);
        dest.writeLong(center);
        dest.writeInt(pga);
    }

    public int[] getSpectrum() {
        return spectrum;
    }

    public void setSpectrum(int[] spectrum) {
        this.spectrum = spectrum;
    }

    public long getSpan() {
        return span;
    }

    public void setSpan(long span) {
        this.span = span;
    }

    public long getRes() {
        return res;
    }

    public void setRes(long res) {
        this.res = res;
    }

    public long getCenter() {
        return center;
    }

    public void setCenter(long center) {
        this.center = center;
    }

    public int getPga() {
        return pga;
    }

    public void setPga(int pga) {
        this.pga = pga;
    }
}
