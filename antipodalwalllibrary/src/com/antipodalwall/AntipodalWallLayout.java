package com.antipodalwall;

import java.util.LinkedList;
import java.util.Stack;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Adapter;
import android.widget.AdapterView;

public class AntipodalWallLayout extends AdapterView<Adapter> {

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

	/**
	 * The adaptor position of the last visible item (that is, the last item
	 * we've ever loaded from the adapter - it may not be currently drawn on the
	 * screen)
	 */
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
	private int mScrolledPosition = 0; // TODO Scroll position will be off because content height will differ!
	
	/** The default width spec of child items, for fitting into columns */
	private int mChildWidthSpec;
	
	/** Number of columns */
	private int mNumberOfColumns;
	
	/** Width of each column */
	private float mColumnWidth = 0;
	
	/** Left padding for views in this layout */
	private int mPaddingL;
	
	/** Top padding for views in this layout */
	private int mPaddingT;
	
	/** Right padding for views in this layout */
	private int mPaddingR;
	
	/** Bottom padding for views in this layout */
	private int mPaddingB;
	
	/** The height of the view on screen in pixels */
	int mParentHeight = 0;
	
	/**
	 * The height of the view including all children (including those
	 * off-screen)
	 */
	private int mFinalHeight = 0; // TODO Should this be saved?... Cos it'll be different - images are larger when landscape
	
	/** Horizontal spacing between views in this layout */
	private int mHorizontalSpacing;
	
	/** Vertical spacing between views in this layout */
	private int mVerticalSpacing;
	
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

	@SuppressWarnings("unchecked")
	public AntipodalWallLayout(Context context, AttributeSet attrs) {
		super(context, attrs);

		setWillNotDraw(false);

		// Load the attrs from the XML
		final TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.AntipodalWallAttrs);
		initializeScrollbars(a);
		
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
		
		// Use this as default if L/T/R/B not specified
		int defaultPadding = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_padding, 0); 
		// Specific paddings
		this.mPaddingL = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_paddingLeft, defaultPadding);
		this.mPaddingT = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_paddingTop, defaultPadding);
		this.mPaddingR = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_paddingRight, defaultPadding);
		this.mPaddingB = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_paddingBottom, defaultPadding);
		// Spacing
		this.mHorizontalSpacing = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_horizontalSpacing, 0);
		this.mVerticalSpacing = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_verticalSpacing, 0);

		a.recycle();
	}

	/**
	 * Scrolls the list. Handles not scrolling past the top and bottom of the
	 * list.
	 * 
	 * @param scrollDistance
	 *            The distance to scroll - negative for scrolling up.
	 */
	private void scrollList(int scrollDistance) {
		// Don't want to scroll upwards past 0 position.
		if(mScrolledPosition + scrollDistance < 0) {
			scrollDistance = -mScrolledPosition;
		} else if (mScrolledPosition + scrollDistance + mParentHeight > mFinalHeight) {
			// We should only stop scrolling if we've run out of views from the adapter.
			if(mLastItemPosition >= mAdapter.getCount() - 1) {
				// If our last position is the end of the adapter, then we've seen all views from the adapter.
				boolean stopScrolling = true;
				for(Stack<Integer> hiddenViews : mBottomHiddenViewsPerColumn){
					// If we still have hidden views, we're not at the bottom of the list. Keep scrolling.
					if(!hiddenViews.isEmpty()) {
						stopScrolling = false;
						break;
					}
				}
				if(stopScrolling) return;
			}
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
	 * Removes view that are outside of the visible part of the list.
	 * 
	 * @param offset
	 *            Offset of the visible area
	 */
	private void removeNonVisibleViews(final int offset) {
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
	
	/***
	 * Fills the list up and down from a given offset.
	 * 
	 * @param offset
	 *            The number of pixels to the top of where the list is scrolled
	 *            to.
	 */
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
		int shortestColumnIndex = findShortestColumnIndex(mColumns);
		int shortestEdge = mColumns[shortestColumnIndex].getBottom();
		View newBottomChild;
		int adapterIndex;
		while (shortestEdge - offset <= mParentHeight) {
			// We've reached the bottom of our previously seen views, need a new one.
			if(mBottomHiddenViewsPerColumn[shortestColumnIndex].isEmpty()) {
				// The adapter has run out of views - stop adding views.
				if(mLastItemPosition >= mAdapter.getCount() - 1) break;
				mLastItemPosition++;
				adapterIndex = mLastItemPosition;
			} else { // We've got a previously seen view to add.
				adapterIndex = mBottomHiddenViewsPerColumn[shortestColumnIndex].pop(); 
			}
			// Get the view (new or previously seen) from the adapter.
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
		int left = this.mPaddingL + (int) (this.mColumnWidth * columnNumber)
				+ (this.mHorizontalSpacing * columnNumber);
		int childHeight = child.getMeasuredHeight();
		int childWidth = child.getMeasuredWidth();
		int topOfChildView;
		if(layoutMode == LAYOUT_MODE_BELOW){
			topOfChildView = mColumns[columnNumber].getBottom();
		} else {
			topOfChildView = mColumns[columnNumber].getTop() - childHeight - mVerticalSpacing;
		}
		child.layout(left, 
				topOfChildView + this.mPaddingT,
				left + childWidth,
				topOfChildView + childHeight + this.mPaddingT);
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
		if (mCachedItemViews.size() != 0) {
			return mCachedItemViews.removeFirst();
		}
		return null;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// TODO This methods code is pretty damn ugly. Fix this shit.
		// If we don't have an adapter, we don't need to do anything
		if (mAdapter == null) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			return;
		}
		int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
		// Usable width for children once padding is removed
		int parentUsableWidth = parentWidth - this.mPaddingL - this.mPaddingR;
		if (parentUsableWidth < 0)
			parentUsableWidth = 0;

		this.mParentHeight = MeasureSpec.getSize(heightMeasureSpec);
		// Usable height for children once padding is removed
		int parentUsableHeight = this.mParentHeight - this.mPaddingT
				- this.mPaddingB;
		if (parentUsableHeight < 0)
			parentUsableHeight = 0;
		this.mColumnWidth = parentUsableWidth
				/ this.mNumberOfColumns
				- ((this.mHorizontalSpacing * (this.mNumberOfColumns - 1)) / this.mNumberOfColumns);
		if(mColumns == null) {
			mColumns = new Column[mNumberOfColumns];
			for(int i = 0; i < mNumberOfColumns; i++) {
				mColumns[i] = new Column(mVerticalSpacing);
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
			if(shortestColumnBottomEdge > 0)
				shortestColumnBottomEdge += newBottomchild.getMeasuredHeight() + mVerticalSpacing;
			else
				shortestColumnBottomEdge += newBottomchild.getMeasuredHeight();
			columnHeightsDuringMeasure[shortestColumnNumber] = shortestColumnBottomEdge;
			shortestColumnNumber = findShortestColumnIndex(columnHeightsDuringMeasure);
			shortestColumnBottomEdge = columnHeightsDuringMeasure[shortestColumnNumber];
		}
		
		// get the final heigth of the viewgroup. it will be that of the higher
		// column once all chidren is in place, plus the top & bottom padding
		this.mFinalHeight = columnHeightsDuringMeasure[findLongestColumnIndex(columnHeightsDuringMeasure)] 
		                                               + mPaddingB + mPaddingT;

		setMeasuredDimension(parentWidth, this.mFinalHeight);
	}
	
	/***
	 * Measures a View we plan on adding to this layout.
	 * 
	 * @param newChild
	 *            The view to measure.
	 */
	private void measureChild(View newChild) {
		int childHeightSpec;
		int originalWidth = newChild.getMeasuredWidth();
		int originalHeight = newChild.getMeasuredHeight();
		/**
		 * If either the measured height or width of the original is 0 that
		 * probably just means that whoever supplied our view hasn't specified
		 * the size of the view themselves. In this case we fall back to the
		 * default behaviour of specifying the width and allowing the height to
		 * grow.
		 * 
		 * It is advised to call View.measure(widthMeasureSpec,
		 * heightMeasureSpec); in your adapters getView(...) method with a
		 * specific width and height spec - so long as the "ratio" is correct.
		 * Not doing this can result in unexpected behaviour - specifically,
		 * images were being placed in columns with large gaps between them when
		 * using MeasureSpec.UNSPECIFIED. This was (as of Jan 1, 2013) tested on
		 * a Nexus One running 2.3.3.
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
		newChild.measure(mChildWidthSpec, childHeightSpec);
	}
	
	// Saving/restoring state, thanks StackOverflow:
	// http://stackoverflow.com/q/3542333/1217087
	@Override
	protected Parcelable onSaveInstanceState() {
	    //begin boilerplate code that allows parent classes to save state
	    Parcelable superState = super.onSaveInstanceState();

	    AntipodalWallSavedState ss = new AntipodalWallSavedState(superState);
	    //end
	    
	    ss.mNumberOfColumns = mNumberOfColumns;
	    ss.mColumns = mColumns;
	    
	    ss.mBottomHiddenViewsPerColumn = mBottomHiddenViewsPerColumn;
	    ss.mTopHiddenViewsPerColumn = mTopHiddenViewsPerColumn;
	    
	    ss.mFinalHeight = mFinalHeight;
	    
	    ss.mScrolledPosition = mScrolledPosition;
	    
	    ss.mLastItemPosition = mLastItemPosition;

	    return ss;
	}
	
	@Override
	public void onRestoreInstanceState(Parcelable state) {
		//begin boilerplate code so parent classes can restore state
	    if(!(state instanceof AntipodalWallSavedState)) {
	      super.onRestoreInstanceState(state);
	      return;
	    }

	    AntipodalWallSavedState ss = (AntipodalWallSavedState)state;
	    super.onRestoreInstanceState(ss.getSuperState());
	    //end
	    
	    mNumberOfColumns = ss.mNumberOfColumns;
	    mColumns = ss.mColumns;
	    
	    mBottomHiddenViewsPerColumn = ss.mBottomHiddenViewsPerColumn;
	    mTopHiddenViewsPerColumn = ss.mTopHiddenViewsPerColumn;
	    
	    mFinalHeight = ss.mFinalHeight;
	    
	    mScrolledPosition = ss.mScrolledPosition;
	    
	    mLastItemPosition = ss.mLastItemPosition;
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
		return this.mParentHeight - (this.mFinalHeight - this.mParentHeight);
	}

	@Override
	protected int computeVerticalScrollOffset() {
		return getScrollY();
	}

	@Override
	protected int computeVerticalScrollRange() {
		return this.mFinalHeight;
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
	
	/**
	 * Returns the index into the supplied array of the column which is the
	 * "shortest", that is, the column such that Column.getBottom() is less than
	 * each of the other columns in the array.
	 * 
	 * @param columns
	 *            An array of Columns you want the shortest one of
	 * @return The index of the shortest Column
	 */
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

	/**
	 * Returns the index into the supplied array of the lowest element, that is,
	 * the element that is less than each of the other elements in the array.
	 * 
	 * @param columns
	 *            An array of numbers you want the lowest one of
	 * @return The index of the lowest integer
	 */
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

	/**
	 * Returns the index into the supplied array of the highest element, that is,
	 * the element that is greater than each of the other elements in the array.
	 * 
	 * @param columns
	 *            An array of numbers you want the greatest one of
	 * @return The index of the greatest integer
	 */
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
			final long id = mAdapter.getItemId(index);
			performItemClick(itemView, index, id);
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
		final long id = mAdapter.getItemId(index);
		final OnItemLongClickListener listener = getOnItemLongClickListener();
		if (listener != null) {
			listener.onItemLongClick(this, itemView, index, id);
		}
	}
}
