package com.antipodalwall;

import java.util.Stack;

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
	public Stack<Integer>[] mBottomHiddenViewsPerColumn;
	public Stack<Integer>[] mTopHiddenViewsPerColumn;
	public int mFinalHeight;
	public int mScrolledPosition;
	public int mLastItemPosition;
	public int mViewWidth;

	public AntipodalWallSavedState(Parcelable in) {
		super(in);
	}
	
	private AntipodalWallSavedState(Parcel in) {
		super(in);
		// mNumColumns
		mNumberOfColumns = in.readInt();
		
		// mColumns
		// TODO Classloader?...
		mColumns = (Column[]) in.readParcelableArray(null);

		// mBottomHiddenViewsPerColumn Stacks Array
		mBottomHiddenViewsPerColumn = new Stack[mNumberOfColumns];
		for(int i = 0; i < mNumberOfColumns; i++) {
			mBottomHiddenViewsPerColumn[i] = new Stack<Integer>();
			// number of Views in this Stack
			int size = in.readInt();
			int[] views = new int[size];
			// this Stack
			in.readIntArray(views);
			for(int hv = size - 1; hv >= 0; hv++) {
				mBottomHiddenViewsPerColumn[i].push(views[hv]);
			}
		}
		
		// mTopHiddenViewsPerColumn Stacks Array
		mTopHiddenViewsPerColumn = new Stack[mNumberOfColumns];
		for(int i = 0; i < mNumberOfColumns; i++) {
			mTopHiddenViewsPerColumn[i] = new Stack<Integer>();
			// number of Views in this Stack
			int size = in.readInt();
			int[] views = new int[size];
			// this Stack
			in.readIntArray(views);
			for(int hv = size - 1; hv >= 0; hv++) {
				mTopHiddenViewsPerColumn[i].push(views[hv]);
			}
		}
		
		// mFinalHeight
		mFinalHeight = in.readInt();
		
		// mScrolledPosition
		mScrolledPosition = in.readInt();
		
		// mLastItemPosition
		mLastItemPosition = in.readInt();
		
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

		// mBottomHiddenViewsPerColumn Stacks Array
		for (int i = 0; i < mNumberOfColumns; i++) {
			int size = mBottomHiddenViewsPerColumn[i].size();
			// number of Views in this Stack
			out.writeInt(size);
			int[] views = new int[size];
			for (int hv = 0; hv < size; hv++) {
				views[hv] = mBottomHiddenViewsPerColumn[i].pop();
			}
			// this Stack
			out.writeIntArray(views);
		}
		
		// mTopHiddenViewsPerColumn Stacks Array
		for (int i = 0; i < mNumberOfColumns; i++) {
			int size = mTopHiddenViewsPerColumn[i].size();
			// number of Views in this Stack
			out.writeInt(size);
			int[] views = new int[size];
			for (int hv = 0; hv < size; hv++) {
				views[hv] = mTopHiddenViewsPerColumn[i].pop();
			}
			// this Stack
			out.writeIntArray(views);
		}
		
		// mFinalHeight
		out.writeInt(mFinalHeight);
		
		// mScrolledPosition
		out.writeInt(mScrolledPosition);
		
		// mLastItemPosition
		out.writeInt(mLastItemPosition);
		
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
