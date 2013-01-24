package com.antipodalwall;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;

/***
 * Container for a View that exists inside a Column. Contains a View as well
 * as an index back into the adapter the view came from.
 * 
 * @author Adam Speakman
 * 
 */
public class ColumnView implements Parcelable {
	/***
	 * The View to display.
	 */
	public final View view;
	
	public final ViewSize viewSize;
	
	/***
	 * 
	 * @param index
	 *            The index into the Adapter.
	 * @param view
	 *            The View to display.
	 */
	public ColumnView(ViewSize vs, View view) {
		this.viewSize = vs;
		this.view = view;
	}
	
	private ColumnView(Parcel in) {
		this((ViewSize)in.readParcelable(null), null);
	}
	
	public static final Parcelable.Creator<ColumnView> CREATOR =
        new Parcelable.Creator<ColumnView>() {
          public ColumnView createFromParcel(Parcel in) {
            return new ColumnView(in);
          }
          public ColumnView[] newArray(int size) {
            return new ColumnView[size];
          }
    };

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeParcelable(viewSize, 0);
	}
}
