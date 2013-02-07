package com.antipodalwall;

import java.util.LinkedList;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.View.MeasureSpec;

/***
 * A Column of Views for displaying on the screen. Has methods for adding to
 * the top and bottom, and retains the current top/bottom position of the
 * views (assuming top of the first view added is in position 0) for
 * convenience.
 * 
 * @author Adam Speakman
 * 
 */
class Column implements Parcelable {
	int verticalSpacing, top, bottom;
	LinkedList<ColumnView> viewsShown;
	LinkedList<AdapterViewDetails> topHiddenViews;
	LinkedList<AdapterViewDetails> bottomHiddenViews;

    /**
     *
     * @param verticalSpacing The amount of space (in pixels) between views.
     */
	public Column(int verticalSpacing) {
		this.verticalSpacing = verticalSpacing;
		viewsShown = new LinkedList<ColumnView>();
		topHiddenViews = new LinkedList<AdapterViewDetails>();
		bottomHiddenViews = new LinkedList<AdapterViewDetails>();
	}
	
	private Column(Parcel in) {
		Column c = new Column(in.readInt());
		c.top = in.readInt();
		c.bottom = in.readInt();
		in.readList(c.viewsShown, ColumnView.class.getClassLoader());
		in.readList(c.topHiddenViews, AdapterViewDetails.class.getClassLoader());
		in.readList(c.bottomHiddenViews, AdapterViewDetails.class.getClassLoader());
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
			top += (colView.view.getMeasuredHeight() + verticalSpacing);
			topHiddenViews.addLast(colView.details);
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
			bottom -= (colView.view.getMeasuredHeight() + verticalSpacing);
			bottomHiddenViews.addFirst(colView.details);
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
		top -= (v.view.getMeasuredHeight() + verticalSpacing);
		viewsShown.addFirst(v);
		if(!topHiddenViews.isEmpty())
			topHiddenViews.removeLast();
	}
	
	/***
	 * Adds a new ColumnView to the bottom of this column, updating the value of {@link #getBottom()}.
	 * 
	 * @param v
	 *            The ColumnView to add to the bottom of the column.
	 */
	public void addBottom(ColumnView v) {
		bottom += v.view.getMeasuredHeight() + verticalSpacing;
		viewsShown.addLast(v);
		if(!bottomHiddenViews.isEmpty())
			bottomHiddenViews.removeFirst();
	}
	
	public LinkedList<AdapterViewDetails> getTopHiddenViews() {
		return topHiddenViews;
	}
	
	public LinkedList<AdapterViewDetails> getBottomHiddenViews() {
		return bottomHiddenViews;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(verticalSpacing);
		dest.writeInt(top);
		dest.writeInt(bottom);
		dest.writeList(viewsShown);
		dest.writeList(topHiddenViews);
		dest.writeList(bottomHiddenViews);
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
	 * Resizes all views that this column knows about to fit the
     * specified width. This adjusts the top and bottom values of
     * this column, and re-measures the views shown.
	 * 
	 * @param columnWidth The width of the column.
	 */
	public void scaleBy(float columnWidth) {
		int topWithSpacing = 0;
		for(AdapterViewDetails vs : topHiddenViews) {
			double scaleRatio = vs.width / columnWidth;
			int newHeight = (int) (vs.height / scaleRatio);
			topWithSpacing += (newHeight + verticalSpacing);
		}
		
		int bottomWithSpacing = topWithSpacing;
		int widthSpec = MeasureSpec.makeMeasureSpec((int)columnWidth, MeasureSpec.EXACTLY);
		for(ColumnView cv : viewsShown) {
			double scaleRatio = cv.details.width / columnWidth;
			int newHeight = (int) (cv.details.height / scaleRatio);
			bottomWithSpacing += (newHeight + verticalSpacing);
			cv.view.measure(widthSpec,
					MeasureSpec.makeMeasureSpec(newHeight, MeasureSpec.EXACTLY));
		} 
		
		top = topWithSpacing;
		bottom = bottomWithSpacing;
	}
}
