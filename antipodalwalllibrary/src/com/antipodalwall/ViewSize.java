package com.antipodalwall;

import android.os.Parcel;
import android.os.Parcelable;

public class ViewSize implements Parcelable {
	public final int w;
	public final int h;
	public final int index;
	public ViewSize(int w, int h, int index) {
		this.w = w;
		this.h = h;
		this.index = index;
	}
	
	private ViewSize(Parcel in) {
		this(in.readInt(), in.readInt(), in.readInt());
	}
	
	public static final Parcelable.Creator<ViewSize> CREATOR =
        new Parcelable.Creator<ViewSize>() {
          public ViewSize createFromParcel(Parcel in) {
            return new ViewSize(in);
          }
          public ViewSize[] newArray(int size) {
            return new ViewSize[size];
          }
    };
    
	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(w);
		dest.writeInt(h);
		dest.writeInt(index);
	}
}
