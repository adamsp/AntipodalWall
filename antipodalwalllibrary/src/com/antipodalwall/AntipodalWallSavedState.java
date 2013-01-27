package com.antipodalwall;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.View.BaseSavedState;

/**
 * Saving state for the layout! Thanks, Stack Overflow:
 * http://stackoverflow.com/q/3542333/1217087
 * 
 * @author adam
 * 
 */
public class AntipodalWallSavedState extends BaseSavedState {
	
	public int mNumberOfColumns;
	public Column[] mColumns;
	public int mFinalHeight;
	public int mScrolledPosition;
	public int mNextItemPosition;
	public int mViewWidth;

	public AntipodalWallSavedState(Parcelable in) {
		super(in);
	}
	
	private AntipodalWallSavedState(Parcel in) {
		super(in);
		// mNumColumns
		mNumberOfColumns = in.readInt();
		
		// mColumns
		mColumns = (Column[]) in.readParcelableArray(Column.class.getClassLoader());
		
		// mFinalHeight
		mFinalHeight = in.readInt();
		
		// mScrolledPosition
		mScrolledPosition = in.readInt();
		
		// mNextItemPosition
		mNextItemPosition = in.readInt();
		
		// mViewWidth
		mViewWidth = in.readInt();
	}
	
	@Override
	public void writeToParcel(Parcel out, int flags) {
		super.writeToParcel(out, flags);
		// mNumColumns
		out.writeInt(mNumberOfColumns);

		// mColumns
		out.writeParcelableArray(mColumns, 0);
		
		// mFinalHeight
		out.writeInt(mFinalHeight);
		
		// mScrolledPosition
		out.writeInt(mScrolledPosition);
		
		// mNextItemPosition
		out.writeInt(mNextItemPosition);
		
		// mViewWidth
		out.writeInt(mViewWidth);
	}

    public static final Parcelable.Creator<AntipodalWallSavedState> CREATOR =
        new Parcelable.Creator<AntipodalWallSavedState>() {
          public AntipodalWallSavedState createFromParcel(Parcel in) {
            return new AntipodalWallSavedState(in);
          }
          public AntipodalWallSavedState[] newArray(int size) {
            return new AntipodalWallSavedState[size];
          }
    };
	
}
