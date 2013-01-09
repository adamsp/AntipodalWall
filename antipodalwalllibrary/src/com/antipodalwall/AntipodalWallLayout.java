package com.antipodalwall;

import java.util.LinkedList;
import java.util.Stack;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Adapter;
import android.widget.AdapterView;

public class AntipodalWallLayout extends AdapterView<Adapter> {
	
	private class ColumnView {
		public final int indexIntoAdapter;
		public final View view;
		public ColumnView(int index, View view) {
			this.indexIntoAdapter = index;
			this.view = view;
		}
	}
	
	private class Column {
		int top, bottom;
		LinkedList<ColumnView> viewsShown;
		
		public Column() {
			viewsShown = new LinkedList<ColumnView>();
		}
		
		public LinkedList<ColumnView> getViews() {
			return viewsShown;
		}
		
		public int getTop() {
			return top;
		}
		
		public int getBottom() { 
			return bottom;
		}
		
		public ColumnView popTopView() {
			if(viewsShown.isEmpty()) {
				return null;
			} else {
				ColumnView colView = viewsShown.removeFirst();
				top += colView.view.getHeight();
				return colView;
			}
		}
		
		public ColumnView peekTopView() {
			if(viewsShown.isEmpty()) {
				return null;
			} else {
				return viewsShown.getFirst();
			}
		}
		
		public ColumnView popBottomView() {
			if(viewsShown.isEmpty()) {
				return null;
			} else {
				ColumnView colView = viewsShown.removeLast();
				bottom -= colView.view.getHeight();
				return colView;
			}
		}
		
		public ColumnView peekBottomView() {
			if(viewsShown.isEmpty()) {
				return null;
			} else {
				return viewsShown.getLast();
			}
		}
		
		public void addTop(ColumnView v) {
			// TODO Position view
			top -= v.view.getHeight();
			viewsShown.addFirst(v);
		}
		
		public void addBottom(ColumnView v) {
			// TODO Position view
			bottom += v.view.getHeight();
			viewsShown.addLast(v);
		}
	}

	/** Represents an invalid child index */
	private static final int INVALID_INDEX = -1;

	/** Distance to drag before we intercept touch events */
	private static final int TOUCH_SCROLL_THRESHOLD = 10;

	/** Children added with this layout mode will be added below the last child */
	private static final int LAYOUT_MODE_BELOW = 0;

	/** Children added with this layout mode will be added above the first child */
	private static final int LAYOUT_MODE_ABOVE = 1;

	/** User is not touching the list */
	private static final int TOUCH_STATE_RESTING = 0;

	/** User is touching the list and right now it's still a "click" */
	private static final int TOUCH_STATE_CLICK = 1;

	/** User is scrolling the list */
	private static final int TOUCH_STATE_SCROLL = 2;

	/** The adapter with all the data */
	private Adapter mAdapter;

	/** Current touch state */
	private int mTouchState = TOUCH_STATE_RESTING;

	/** X-coordinate of the down event */
	private int mTouchStartX;

	/** Y-coordinate of the down event */
	private int mTouchStartY;

	/** The adaptor position of the first visible item */
	private int mFirstItemPosition;

	/** The adaptor position of the last visible item */
	private int mLastItemPosition;

	/** A list of cached (re-usable) item views */
	private final LinkedList<View> mCachedItemViews = new LinkedList<View>();

	/** Used to check for long press actions */
	private Runnable mLongPressRunnable;

	/** Reusable rect */
	private Rect mRect;
	
	/**
	 * The scroll position of the list. Should never drop below 0.
	 * This is how many pixels away from the 0 position we are.
	 */
	private int mScrolledPosition = 0;
	
	/**
	 * The width spec of child items.
	 */
	private int mChildWidthSpec;
	
	/** Number of columns */
	private int mNumberOfColumns;
	
	/** Width of each column */
	private float mColumnWidth = 0;
	
	private int paddingL;
	private int paddingT;
	private int paddingR;
	private int paddingB;
	/**
	 * The height of the view on screen in pixels.
	 */
	int mParentHeight = 0;
	private int finalHeight = 0;
	
	private int horizontalSpacing;
	private int verticalSpacing;
	
	/**
	 * Indexes into the adapter for the views above the currently drawn parts of
	 * each column.
	 */
	private Stack<Integer>[] mTopHiddenViewsPerColumn;
	/**
	 * Indexes into the adapter for the views below the currently drawn parts of
	 * each column.
	 */
	private Stack<Integer>[] mBottomHiddenViewsPerColumn;
	/**
	 * The columns of views to display.
	 */
	private Column[] mColumns;

	public AntipodalWallLayout(Context context, AttributeSet attrs) {
		super(context, attrs);

		setWillNotDraw(false);

		// Load the attrs from the XML
		final TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.AntipodalWallAttrs);
		// - scrollbars
		initializeScrollbars(a);
		// - number of columns
		this.mNumberOfColumns = a.getInt(
				R.styleable.AntipodalWallAttrs_android_columnCount, 1);
		if (this.mNumberOfColumns < 1)
			this.mNumberOfColumns = 1;
		mTopHiddenViewsPerColumn = new Stack[mNumberOfColumns];
		mBottomHiddenViewsPerColumn = new Stack[mNumberOfColumns];
		for(int i = 0; i < mNumberOfColumns; i++){
			mTopHiddenViewsPerColumn[i] = new Stack<Integer>();
			mBottomHiddenViewsPerColumn[i] = new Stack<Integer>();
		}
		
		int defaultPadding = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_padding, 0); 
		// - specific paddings
		this.paddingL = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_paddingLeft, defaultPadding);
		this.paddingT = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_paddingTop, defaultPadding);
		this.paddingR = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_paddingRight, defaultPadding);
		this.paddingB = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_paddingBottom, 0);
		// - spacing
		this.horizontalSpacing = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_horizontalSpacing, 0);
		this.verticalSpacing = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_verticalSpacing, 0);

		a.recycle();
	}

	/**
	 * Scrolls the list. Handles not scrolling past the top of the list.
	 * 
	 * @param scrollDistance
	 *            The distance to scroll
	 */
	private void scrollList(int scrollDistance) {
		if(mScrolledPosition + scrollDistance < 0) {
			scrollDistance = -mScrolledPosition;
		}
		mScrolledPosition += scrollDistance;
		scrollBy(0, scrollDistance);
		removeNonVisibleViews(mScrolledPosition);
		if(scrollDistance > 0) {
			fillListDown(mScrolledPosition);
		} else if (scrollDistance < 0) {
			fillListUp(mScrolledPosition);
		}
	}

	/**
	 * Removes view that are outside of the visible part of the list. Will not
	 * remove all views.
	 * 
	 * @param offset
	 *            Offset of the visible area
	 */
	private void removeNonVisibleViews(final int offset) {
		// We need to keep close track of the child count in this function. We
		// should never remove all the views, because if we do, we loose track
		// of were we are.
		ColumnView poppedView;
		for(int i = 0; i < mNumberOfColumns; i++) {
			// Remove hidden views from top of columns
			while(mColumns[i].peekTopView() != null 
					&& mColumns[i].peekTopView().view.getBottom() < offset) {
				poppedView = mColumns[i].popTopView();
				removeViewInLayout(poppedView.view);
				mTopHiddenViewsPerColumn[i].add(poppedView.indexIntoAdapter);
				mCachedItemViews.add(poppedView.view);
			}
			// Remove hidden views from bottom of columns
			while(mColumns[i].peekBottomView() != null 
					&& mColumns[i].peekBottomView().view.getTop() > (offset + mParentHeight)) {
				poppedView = mColumns[i].popBottomView();
				removeViewInLayout(poppedView.view);
				mBottomHiddenViewsPerColumn[i].add(poppedView.indexIntoAdapter);
			}
		}
	}
	
	private void fillList(final int offset) {
		fillListUp(offset);
		fillListDown(offset);
	}

	/**
	 * Starts at the bottom and adds children downwards until we've filled the
	 * view.
	 * 
	 * @param offset
	 *            Offset of the visible area
	 */
	private void fillListDown(final int offset) {
		Log.d("AntipodalWall", "fillListDown called with offset " + offset);
		int shortestColumnIndex = findShortestColumnIndex(mColumns);
		int shortestEdge = mColumns[shortestColumnIndex].getBottom();
		View newBottomChild;
		int adapterIndex;
		while (shortestEdge - offset <= mParentHeight) {
			// We've reached the bottom of our previously seen views, need a new one.
			if(mBottomHiddenViewsPerColumn[shortestColumnIndex].isEmpty()) {
				mLastItemPosition++;
				// The adapter has run out of views - stop adding views.
				if(mLastItemPosition >= mAdapter.getCount()) break;
				adapterIndex = mLastItemPosition;
			} else { // We've got a previously seen view to add.
				adapterIndex = mBottomHiddenViewsPerColumn[shortestColumnIndex].pop(); 
			}
			newBottomChild = mAdapter.getView(adapterIndex, getCachedView(), this);
			// We need to re-measure the child to fit.
			measureChild(newBottomChild);
			addAndLayoutChild(newBottomChild, LAYOUT_MODE_BELOW, shortestColumnIndex);
			mColumns[shortestColumnIndex].addBottom(new ColumnView(adapterIndex, newBottomChild));
			shortestColumnIndex = findShortestColumnIndex(mColumns);
			shortestEdge = mColumns[shortestColumnIndex].getBottom();
		}
	}

	/**
	 * Starts at the top and adds children upwards until we've filled the view.
	 * 
	 * @param offset
	 *            Offset of the visible area
	 */
	private void fillListUp(final int offset) {
		Log.d("AntipodalWall", "fillListUp called with offset " + offset);
		Column currentColumn;
		int adapterIndex;
		View newTopChild;
		for(int currentColumnIndex = 0; currentColumnIndex < mNumberOfColumns; currentColumnIndex++) {
			currentColumn = mColumns[currentColumnIndex];
			while (currentColumn.getTop() > offset) {
				// If we're filling up, we've always already seen these views,
				// so we can add until the stack is empty or our view is full.
				if(mTopHiddenViewsPerColumn[currentColumnIndex].isEmpty()) break;
				adapterIndex = mTopHiddenViewsPerColumn[currentColumnIndex].pop();
				newTopChild = mAdapter.getView(adapterIndex, getCachedView(), this);
				measureChild(newTopChild);
				addAndLayoutChild(newTopChild, LAYOUT_MODE_ABOVE, currentColumnIndex);
				currentColumn.addTop(new ColumnView(adapterIndex, newTopChild));
			}
		}
	}

	/**
	 * Adds a view as a child view and takes care of laying it out
	 * 
	 * @param child
	 *            The view to add
	 * @param layoutMode
	 *            Either LAYOUT_MODE_ABOVE or LAYOUT_MODE_BELOW
	 */
	private void addAndLayoutChild(final View child, final int layoutMode, int columnNumber) {
		Log.d("AntipodalWall", "addAndLayoutChild called with columnNumber " + columnNumber);
		// TODO Padding, Spacing
//		int left = this.paddingL + (int) (this.mColumnWidth * columnNumber)
//				+ (this.horizontalSpacing * columnNumber);
		int left = (int) (this.mColumnWidth * columnNumber);
		int childHeight = child.getMeasuredHeight();
		int childWidth = child.getMeasuredWidth();
		int topOfChildView;
		if(layoutMode == LAYOUT_MODE_BELOW){
			topOfChildView = mColumns[columnNumber].getBottom();
		} else {
			topOfChildView = mColumns[columnNumber].getTop() - childHeight;
		}
		// TODO Padding
//		child.layout(left, 
//				topOfChildView + this.paddingT,
//				left + childWidth,
//				topOfChildView + childHeight + this.paddingT);
		child.layout(left, 
				topOfChildView,
				left + childWidth,
				topOfChildView + childHeight);
		LayoutParams params = child.getLayoutParams();
		if (params == null) {
			params = new LayoutParams(LayoutParams.WRAP_CONTENT,
					LayoutParams.WRAP_CONTENT);
		}
		// -1 means put it at the end, 0 means at the beginning.
		final int index = layoutMode == LAYOUT_MODE_ABOVE ? 0 : -1;
		addViewInLayout(child, index, params, true);
		Log.d("AntipodalWall", "View child added - total of " + getChildCount() + " children.");
	}

	/**
	 * Checks if there is a cached view that can be used
	 * 
	 * @return A cached view or, if none was found, null
	 */
	private View getCachedView() {
		Log.d("AntipodalWall", "Returning a cached view from pool of size: " + mCachedItemViews.size());
		if (mCachedItemViews.size() != 0) {
			return mCachedItemViews.removeFirst();
		}
		return null;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// TODO Needed?
		//super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		Log.d("AntipodalWall",  "onMeasure() called");
		// if we don't have an adapter, we don't need to do anything
		if (mAdapter == null) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			return;
		}
		int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
		// TODO Padding
		// Usable width for children once padding is removed
		//int parentUsableWidth = parentWidth - this.paddingL - this.paddingR;
		int parentUsableWidth = parentWidth;
		if (parentUsableWidth < 0)
			parentUsableWidth = 0;

		this.mParentHeight = MeasureSpec.getSize(heightMeasureSpec);
		// TODO Padding
		// Usable height for children once padding is removed
//		int parentUsableHeight = this.mParentHeight - this.paddingT
//				- this.paddingB;
		int parentUsableHeight = this.mParentHeight;
		if (parentUsableHeight < 0)
			parentUsableHeight = 0;
		// TODO Spacing
//		this.mColumnWidth = parentUsableWidth
//				/ this.mNumberOfColumns
//				- ((this.horizontalSpacing * (this.mNumberOfColumns - 1)) / this.mNumberOfColumns);
		this.mColumnWidth = parentUsableWidth / this.mNumberOfColumns;
		if(mColumns == null) {
			mColumns = new Column[mNumberOfColumns];
			for(int i = 0; i < mNumberOfColumns; i++) {
				mColumns[i] = new Column();
			}
		}
		
		// force the width of the children to be that of the columns...
		mChildWidthSpec = MeasureSpec.makeMeasureSpec((int) this.mColumnWidth, MeasureSpec.EXACTLY);
		// ... but let them grow vertically
		int lastItemPositionDuringMeasure = mLastItemPosition;
		int[] columnHeightsDuringMeasure = new int[mNumberOfColumns];
		for(int i = 0; i < mNumberOfColumns; i++)
			columnHeightsDuringMeasure[i] = mColumns[i].getBottom();
		int shortestColumnNumber = findShortestColumnIndex(columnHeightsDuringMeasure);
		int shortestColumnBottomEdge = columnHeightsDuringMeasure[shortestColumnNumber];
		while (shortestColumnBottomEdge <= getHeight()
				&& lastItemPositionDuringMeasure < mAdapter.getCount() - 1) {
			lastItemPositionDuringMeasure++;
			final View newBottomchild = mAdapter.getView(lastItemPositionDuringMeasure,
					getCachedView(), this);
			measureChild(newBottomchild);
			shortestColumnBottomEdge += newBottomchild.getMeasuredHeight();
			columnHeightsDuringMeasure[shortestColumnNumber] = shortestColumnBottomEdge;
			shortestColumnNumber = findShortestColumnIndex(columnHeightsDuringMeasure);
			shortestColumnBottomEdge = columnHeightsDuringMeasure[shortestColumnNumber];
		}
		
		// get the final heigth of the viewgroup. it will be that of the higher
		// column once all chidren is in place
		this.finalHeight = columnHeightsDuringMeasure[findLongestColumnIndex(columnHeightsDuringMeasure)];

		setMeasuredDimension(parentWidth, this.finalHeight);
	}
	
	private void measureChild(View newBottomchild) {
		int childHeightSpec;
		int originalWidth = newBottomchild.getMeasuredWidth();
		int originalHeight = newBottomchild.getMeasuredHeight();
		/**
		 * If either the measured height or width of the original is 0 that
		 * probably just means that whoever supplied our view hasn't
		 * specified the size of the view themselves. In this case we fall
		 * back to the default behaviour of specifying the width and
		 * allowing the height to grow.
		 * 
		 * It is advised to call View.measure(widthMeasureSpec,
		 * heightMeasureSpec); in your adapters getView(...) method with a
		 * specific width and height spec. Not doing this can result in
		 * unexpected behaviour - specifically, images were being placed in
		 * columns with large gaps between them when using
		 * MeasureSpec.UNSPECIFIED. This was (as of Jan 1, 2013) tested on a
		 * Nexus One running 2.3.3.
		 * 
		 */
		if(originalWidth == 0 || originalHeight == 0) {
			childHeightSpec = MeasureSpec.makeMeasureSpec(0,
					MeasureSpec.UNSPECIFIED);
		} else {
			double scaleRatio = originalWidth / mColumnWidth;
			int newHeight = (int) (originalHeight / scaleRatio);
			childHeightSpec = MeasureSpec.makeMeasureSpec(newHeight,
					MeasureSpec.EXACTLY);
		}
		newBottomchild.measure(mChildWidthSpec, childHeightSpec);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed, l, t, r, b);
		Log.d("AntipodalWall",  "onLayout() called");
		// if we don't have an adapter, we don't need to do anything
		if (mAdapter == null) {
			return;
		}

		if (getChildCount() == 0) {
			mLastItemPosition = -1;
			mScrolledPosition = 0;
			fillListDown(mScrolledPosition);
		} else {
			removeNonVisibleViews(mScrolledPosition);
			fillList(mScrolledPosition);
		}
		invalidate();
	}

	@Override
	protected int computeVerticalScrollExtent() {
		return this.mParentHeight - (this.finalHeight - this.mParentHeight);
	}

	@Override
	protected int computeVerticalScrollOffset() {
		return getScrollY();
	}

	@Override
	protected int computeVerticalScrollRange() {
		return this.finalHeight;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (getChildCount() == 0) {
			Log.d("AntipodalWall", "Child Count 0. Returning false - touch event not handled.");
			return false;
		}
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			startTouch(event);
			break;

		case MotionEvent.ACTION_MOVE:
			if (mTouchState == TOUCH_STATE_CLICK) {
				startScrollIfNeeded(event);
			}
			if (mTouchState == TOUCH_STATE_SCROLL) {
				int eventY = (int) event.getY();
				int scrollDistance = - (eventY - mTouchStartY);
				scrollList(scrollDistance);
				// Reset the "start" position each time.
				mTouchStartY = eventY;
			}
			break;

		case MotionEvent.ACTION_UP:
			if (mTouchState == TOUCH_STATE_CLICK) {
				clickChildAt((int) event.getX(), (int) event.getY());
			}
			endTouch();
			break;

		default:
			endTouch();
			break;
		}
		return true;
	}
	
	private int findShortestColumnIndex(Column[] columns) {
		int shortest = columns[0].getBottom();
		int column = 0;
		for(int i = 1; i < columns.length; i++) {
			if(columns[i].getBottom() < shortest) {
				shortest = columns[i].getBottom();
				column = i;
			}
		}
		return column;
	}

	private int findShortestColumnIndex(int[] columns) {
		int minValue = columns[0];
		int column = 0;
		for (int i = 1; i < columns.length; i++) {
			if (columns[i] < minValue) {
				minValue = columns[i];
				column = i;
			}
		}
		return column;
	}

	private int findLongestColumnIndex(int[] columns) {
		int maxValue = columns[0];
		int column = 0;
		for (int i = 1; i < columns.length; i++) {
			if (columns[i] > maxValue) {
				maxValue = columns[i];
				column = i;
			}
		}
		return column;
	}

	@Override
	public Adapter getAdapter() {
		return mAdapter;
	}
	
	@Override
	public void setAdapter(Adapter adapter) {
		mAdapter = adapter;
		removeAllViewsInLayout();
		requestLayout();
	}
	
	@Override
	public View getSelectedView() {
		throw new UnsupportedOperationException("Not supported");
	}
	
	@Override
	public void setSelection(int position) {
		throw new UnsupportedOperationException("Not supported");
	}
	
	@Override
	public boolean onInterceptTouchEvent(final MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			startTouch(event);
			return false;

		case MotionEvent.ACTION_MOVE:
			return startScrollIfNeeded(event);

		default:
			endTouch();
			return false;
		}
	}

	/**
	 * Sets and initializes all things that need to when we start a touch
	 * gesture.
	 * 
	 * @param event
	 *            The down event
	 */
	private void startTouch(final MotionEvent event) {
		// save the start place
		mTouchStartX = (int) event.getX();
		mTouchStartY = (int) event.getY();

		// start checking for a long press
		startLongPressCheck();

		// we don't know if it's a click or a scroll yet, but until we know
		// assume it's a click
		mTouchState = TOUCH_STATE_CLICK;
	}

	/**
	 * Resets and recycles all things that need to when we end a touch gesture
	 */
	private void endTouch() {
		// remove any existing check for longpress
		removeCallbacks(mLongPressRunnable);

		// reset touch state
		mTouchState = TOUCH_STATE_RESTING;
	}
	
	/**
	 * Posts (and creates if necessary) a runnable that will when executed call
	 * the long click listener
	 */
	private void startLongPressCheck() {
		// create the runnable if we haven't already
		if (mLongPressRunnable == null) {
			mLongPressRunnable = new Runnable() {
				public void run() {
					if (mTouchState == TOUCH_STATE_CLICK) {
						final int index = getContainingChildIndex(mTouchStartX,
								mTouchStartY);
						if (index != INVALID_INDEX) {
							longClickChild(index);
						}
					}
				}
			};
		}

		// then post it with a delay
		postDelayed(mLongPressRunnable, ViewConfiguration.getLongPressTimeout());
	}

	/**
	 * Checks if the user has moved far enough for this to be a scroll and if
	 * so, sets the list in scroll mode
	 * 
	 * @param event
	 *            The (move) event
	 * @return true if scroll was started, false otherwise
	 */
	private boolean startScrollIfNeeded(final MotionEvent event) {
		final int xPos = (int) event.getX();
		final int yPos = (int) event.getY();
		if (xPos < mTouchStartX - TOUCH_SCROLL_THRESHOLD
				|| xPos > mTouchStartX + TOUCH_SCROLL_THRESHOLD
				|| yPos < mTouchStartY - TOUCH_SCROLL_THRESHOLD
				|| yPos > mTouchStartY + TOUCH_SCROLL_THRESHOLD) {
			// we've moved far enough for this to be a scroll
			removeCallbacks(mLongPressRunnable);
			mTouchState = TOUCH_STATE_SCROLL;
			return true;
		}
		return false;
	}

	/**
	 * Returns the index of the child that contains the coordinates given.
	 * 
	 * @param x
	 *            X-coordinate
	 * @param y
	 *            Y-coordinate
	 * @return The index of the child that contains the coordinates. If no child
	 *         is found then it returns INVALID_INDEX
	 */
	private int getContainingChildIndex(final int x, final int y) {
		if (mRect == null) {
			mRect = new Rect();
		}
		for (int index = 0; index < getChildCount(); index++) {
			getChildAt(index).getHitRect(mRect);
			if (mRect.contains(x, y)) {
				return index;
			}
		}
		return INVALID_INDEX;
	}

	/**
	 * Calls the item click listener for the child with at the specified
	 * coordinates
	 * 
	 * @param x
	 *            The x-coordinate
	 * @param y
	 *            The y-coordinate
	 */
	private void clickChildAt(final int x, final int y) {
		final int index = getContainingChildIndex(x, y);
		if (index != INVALID_INDEX) {
			final View itemView = getChildAt(index);
			final int position = mFirstItemPosition + index;
			final long id = mAdapter.getItemId(position);
			performItemClick(itemView, position, id);
		}
	}

	/**
	 * Calls the item long click listener for the child with the specified index
	 * 
	 * @param index
	 *            Child index
	 */
	private void longClickChild(final int index) {
		final View itemView = getChildAt(index);
		final int position = mFirstItemPosition + index;
		final long id = mAdapter.getItemId(position);
		final OnItemLongClickListener listener = getOnItemLongClickListener();
		if (listener != null) {
			listener.onItemLongClick(this, itemView, position, id);
		}
	}
}
