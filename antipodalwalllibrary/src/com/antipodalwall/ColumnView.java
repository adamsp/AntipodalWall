package com.antipodalwall;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;

/***
 * Container for a View that exists inside a Column. Contains a View as well
 * as a details about the original view from the adapter.
 * 
 * @author Adam Speakman
 * 
 */
class ColumnView implements Parcelable {
	/***
	 * The View to display.
	 */
	public final View view;

    /**
     * Details of the original View from the adapter.
     */
	public final AdapterViewDetails details;
	
	/***
	 * 
	 * @param details
	 *            The details of the original view.
	 * @param view
	 *            The View to display.
	 */
	public ColumnView(AdapterViewDetails details, View view) {
		this.details = details;
		this.view = view;
	}
	
	private ColumnView(Parcel in) {
		this((AdapterViewDetails)in.readParcelable(AdapterViewDetails.class.getClassLoader()), null);
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
		dest.writeParcelable(details, 0);
	}
}
