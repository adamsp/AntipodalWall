package com.antipodalwall;

import java.util.LinkedList;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import android.view.View.MeasureSpec;

/***
 * A Column of Views for displaying on the screen. Has methods for adding to
 * the top and bottom, and retains the current top/bottom position of the
 * views (assuming top of the first view added is in position 0) for
 * convenience.
 * 
 * @author adam
 * 
 */
public class Column implements Parcelable {
	int verticalSpacing, top, bottom;
	LinkedList<ColumnView> viewsShown;
	
	public Column(int verticalSpacing) {
		this.verticalSpacing = verticalSpacing;
		viewsShown = new LinkedList<ColumnView>();
	}
	
	private Column(Parcel in) {
		Column c = new Column(in.readInt());
		c.top = in.readInt();
		c.bottom = in.readInt();
		in.readList(c.viewsShown, null);
	}
	
	/***
	 * Returns the current 'top' of the views that exist in this column.
	 * This is the number of pixels between the top of the first view that
	 * was added to this column (whether it is still present in this column
	 * or not) and the top of the first view still in this column. If the
	 * top view has never been popped off, this will be 0. If the top view
	 * has been popped off, this will be the top of the second view that was
	 * added to this column, etc.
	 * 
	 * @return The top of the current top view in this column.
	 */
	public int getTop() {
		return top;
	}
	
	/***
	 * Returns the current 'bottom' of the views that exist in this column.
	 * This is the number of pixels between the top of the first view that
	 * was added to this column (whether it is still present in this column
	 * or not) and the bottom of the last view that was added to this
	 * column.
	 * 
	 * @return The bottom of the column.
	 */
	public int getBottom() { 
		return bottom;
	}
	
	/***
	 * Returns the top view of this column and removes it from the
	 * column, updating the value of {@link #getTop()}.
	 * 
	 * @return The top view of the column, or null if there are no views
	 *         in this column.
	 */
	public ColumnView popTopView() {
		if(viewsShown.isEmpty()) {
			return null;
		} else {
			ColumnView colView = viewsShown.removeFirst();
			top += (colView.view.getHeight() + verticalSpacing);
			return colView;
		}
	}
	
	/***
	 * Returns the top view of this column without removing it.
	 * 
	 * @return The top view of the column, or null if there are no views
	 *         in this column.
	 */
	public ColumnView peekTopView() {
		if(viewsShown.isEmpty()) {
			return null;
		} else {
			return viewsShown.getFirst();
		}
	}
	
	/***
	 * Returns the bottom view of this column and removes it from the
	 * column, updating the value of {@link #getBottom()}.
	 * 
	 * @return The bottom view of the column, or null if there are no views
	 *         in this column.
	 */
	public ColumnView popBottomView() {
		if(viewsShown.isEmpty()) {
			return null;
		} else {
			ColumnView colView = viewsShown.removeLast();
			bottom -= (colView.view.getHeight() + verticalSpacing);
			return colView;
		}
	}

	/***
	 * Returns the bottom view of this column without removing it.
	 * 
	 * @return The bottom view of the column, or null if there are no views
	 *         in this column.
	 */
	public ColumnView peekBottomView() {
		if(viewsShown.isEmpty()) {
			return null;
		} else {
			return viewsShown.getLast();
		}
	}
	
	/***
	 * Adds a new ColumnView to the top of this column, updating the value of {@link #getTop()}.
	 * 
	 * @param v
	 *            The ColumnView to add to the top of the column.
	 */
	public void addTop(ColumnView v) {
		top -= (v.view.getHeight() + verticalSpacing);
		viewsShown.addFirst(v);
	}
	
	/***
	 * Adds a new ColumnView to the bottom of this column, updating the value of {@link #getBottom()}.
	 * 
	 * @param v
	 *            The ColumnView to add to the bottom of the column.
	 */
	public void addBottom(ColumnView v) {
		bottom += v.view.getHeight() + verticalSpacing;
		viewsShown.addLast(v);
	}

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(verticalSpacing);
		dest.writeInt(top);
		dest.writeInt(bottom);
		dest.writeList(viewsShown);
	}
	
	public static final Parcelable.Creator<Column> CREATOR =
        new Parcelable.Creator<Column>() {
          public Column createFromParcel(Parcel in) {
            return new Column(in);
          }
          public Column[] newArray(int size) {
            return new Column[size];
          }
    };

	/**
	 * Scales all values in this column by scaleValue. This is useful for when
	 * the View rotates or changes size and you need to update the size and
	 * offsets of child elements.
	 * 
	 * @param scaleValue
	 */
	public void scaleBy(double scaleValue) {
		top *= scaleValue;
		bottom *= scaleValue;
		verticalSpacing *= scaleValue;
		for(ColumnView colView : viewsShown) {
			colView.view.measure(MeasureSpec.makeMeasureSpec(colView.view.getMeasuredWidth(), MeasureSpec.EXACTLY),
					MeasureSpec.makeMeasureSpec(colView.view.getMeasuredHeight(), MeasureSpec.EXACTLY));
		}
	}
}
