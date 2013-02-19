package com.antipodalwall;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Contains details about a view from the adapter.
 *
 * This includes the original width and height of the view
 * (useful for re-calculating display size when orientation
 * changes) and the index back into the adapter (useful for
 * storing so we can easily retrieve views in the same order).
 *
 * @author Adam Speakman
 *
 */
class AdapterViewDetails implements Parcelable {
	public final int width;
	public final int height;
	public final int index;

    /**
     *
     * @param w Width of the view.
     * @param h Height of the view.
     * @param index Views index into the adapter.
     */
    public AdapterViewDetails(int w, int h, int index) {
		this.width = w;
		this.height = h;
		this.index = index;
	}
	
	private AdapterViewDetails(Parcel in) {
		this(in.readInt(), in.readInt(), in.readInt());
	}
	
	public static final Parcelable.Creator<AdapterViewDetails> CREATOR =
        new Parcelable.Creator<AdapterViewDetails>() {
          public AdapterViewDetails createFromParcel(Parcel in) {
            return new AdapterViewDetails(in);
          }
          public AdapterViewDetails[] newArray(int size) {
            return new AdapterViewDetails[size];
          }
    };
    
	@Override
	public int describeContents() {
		return 0;
	}
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(width);
		dest.writeInt(height);
		dest.writeInt(index);
	}
}
