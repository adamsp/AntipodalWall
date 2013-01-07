package com.antipodalwall;

import java.util.LinkedList;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
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
	 * The top of the first item when the touch down event was received
	 */
	private int mListTopStart;

	/** The current top of the first item */
	private int mListTop;

	/**
	 * The offset from the top of the currently first visible item to the top of
	 * the first item
	 */
	private int mListTopOffset;

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
	
	private int[] mColumnHeights;
	
	/**
	 * Views acquired from the adapter during measure,
	 * should be re-used during layout. Key is the views
	 * position (either mLast or mFirstItemPosition).
	 */
	private SparseArray<View> mViewsAcquiredFromAdapterDuringMeasure;
	
	/**
	 * Maps a views unique ID to an integer describing
	 * which column it belongs to.
	 */
	private SparseArray<Integer> mViewIDToColumnNumberMap;

	/**
	 * The scroll position of the list. Should never drop below 0.
	 */
	private int mScrolledPosition = 0;
	
	/**
	 * The width spec of child items.
	 */
	private int mChildWidthSpec;
	
	private int columns;
	private float columnWidth = 0;
	private int paddingL;
	private int paddingT;
	private int paddingR;
	private int paddingB;
	/**
	 * The height of the view on screen in pixels.
	 */
	int parentHeight = 0;
	private int finalHeight = 0;
	private int y_move = 0;
	
	private int horizontalSpacing;
	private int verticalSpacing;

	public AntipodalWallLayout(Context context, AttributeSet attrs) {
		super(context, attrs);

		setWillNotDraw(false);

		// Load the attrs from the XML
		final TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.AntipodalWallAttrs);
		// - scrollbars
		initializeScrollbars(a);
		// - number of columns
		this.columns = a.getInt(
				R.styleable.AntipodalWallAttrs_android_columnCount, 1);
		if (this.columns < 1)
			this.columns = 1;
		mColumnHeights = new int[this.columns];
		
		// - general padding (padding was not being handled correctly)
		setGeneralPadding(a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_padding, 0));
		// - specific paddings
		this.paddingL = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_paddingLeft, 0);
		this.paddingT = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_paddingTop, 0);
		this.paddingR = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_paddingRight, 0);
		this.paddingB = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_paddingBottom, 0);
		// - spacing
		this.horizontalSpacing = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_horizontalSpacing, 0);
		this.verticalSpacing = a.getDimensionPixelSize(
				R.styleable.AntipodalWallAttrs_android_verticalSpacing, 0);
		
		mViewsAcquiredFromAdapterDuringMeasure = new SparseArray<View>();
		mViewIDToColumnNumberMap = new SparseArray<Integer>();

		a.recycle();
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
		View topChild = getChildAt(0);
		if(topChild != null)
			mListTopStart = getChildAt(0).getTop() - mListTopOffset;

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
		mListTop = mListTopStart + scrollDistance;
		scrollBy(0, scrollDistance);
		//final int offset = mListTop + mListTopOffset - getChildAt(0).getTop();
		removeNonVisibleViews(mScrolledPosition);
		if(scrollDistance > 0) {
			//final int offset = mListTop + mListTopOffset - getChildAt(0).getTop();
			fillListDown(mScrolledPosition, 0);
		} else if (scrollDistance < 0) {
			fillListUp(mScrolledPosition, 0);
		}
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
		int childCount = getChildCount();

		// if we are not at the bottom of the list and have more than one child
		if (mLastItemPosition != mAdapter.getCount() - 1 && childCount > 1) {
			// check if we should remove any views in the top
			View firstChild = getChildAt(0);
			while (firstChild != null && firstChild.getBottom() < offset) {
				// remove the top view
				removeViewInLayout(firstChild);
				childCount--;
				Log.d("AntipodalWall", "Removing child from top.");
				mCachedItemViews.addLast(firstChild);
				mFirstItemPosition++;

				// update the list offset (since we've removed the top child)
				mListTopOffset += firstChild.getMeasuredHeight();

				// Continue to check the next child only if we have more than
				// one child left
				if (childCount > 1) {
					firstChild = getChildAt(0);
				} else {
					firstChild = null;
				}
			}
		}

		// if we are not at the top of the list and have more than one child
		if (mFirstItemPosition != 0 && childCount > 1) {
			// check if we should remove any views in the bottom
			View lastChild = getChildAt(childCount - 1);
			int childId;
			int childColumnNumber;
			while (lastChild != null
					&& lastChild.getTop() > offset + parentHeight) {
				Log.d("AntipodalWall", "Removing child from bottom.");
				childId = lastChild.getId();
				childColumnNumber = mViewIDToColumnNumberMap.get(childId);
				mColumnHeights[childColumnNumber] -= lastChild.getHeight();
				
				// remove the bottom view
				removeViewInLayout(lastChild);
				childCount--;
				mCachedItemViews.addLast(lastChild);
				mLastItemPosition--;

				// Continue to check the next child only if we have more than
				// one child left
				if (childCount > 1) {
					lastChild = getChildAt(childCount - 1);
				} else {
					lastChild = null;
				}
			}
		}
	}

	/**
	 * Fills the list with child-views
	 * 
	 * @param offset
	 *            Offset of the visible area
	 */
	private void fillList(final int offset, int left) {
		Log.d("AntipodalWall", "fillList called");
		fillListDown(offset, left);

		fillListUp(offset, left);
	}

	/**
	 * Starts at the bottom and adds children until we've passed the list bottom
	 * 
	 * @param columnHeights
	 *            The heights of the columns
	 * @param offset
	 *            Offset of the visible area
	 */
	private void fillListDown(final int offset, int left) {
		Log.d("AntipodalWall", "fillListDown called");
		int columnNumber = findLowerColumn(mColumnHeights);
		int bottomEdge = mColumnHeights[columnNumber];
		while (bottomEdge - offset <= parentHeight
				&& mLastItemPosition < mAdapter.getCount() - 1) {
			
			mLastItemPosition++;
			View newBottomchild = mAdapter.getView(mLastItemPosition, getCachedView(), this);
			measureChild(newBottomchild);
			int childId = newBottomchild.getId();
			if(mViewIDToColumnNumberMap.indexOfKey(childId) < 0) {
				columnNumber = findLowerColumn(mColumnHeights);
				mViewIDToColumnNumberMap.put(childId, columnNumber);
			} else {
				columnNumber = mViewIDToColumnNumberMap.get(childId);
			}
			bottomEdge = mColumnHeights[columnNumber];
			addAndLayoutChild(newBottomchild, LAYOUT_MODE_BELOW, left, columnNumber);
			bottomEdge += newBottomchild.getMeasuredHeight();
			mColumnHeights[columnNumber] = bottomEdge;
			bottomEdge = mColumnHeights[findLowerColumn(mColumnHeights)];
		}
		
//		while (shortestColumnBottomEdge <= getHeight()
//				&& lastItemPositionDuringMeasure < mAdapter.getCount() - 1) {
//			lastItemPositionDuringMeasure++;
//			final View newBottomchild = mAdapter.getView(lastItemPositionDuringMeasure,
//					getCachedView(), this);
//			newBottomchild.measure(childWidthSpec, childHeightSpec);
//			shortestColumnBottomEdge += newBottomchild.getMeasuredHeight();
//			columnHeightsDuringMeasure[shortestColumnNumber] = shortestColumnBottomEdge;
//			shortestColumnNumber = findLowerColumn(columnHeightsDuringMeasure);
//			shortestColumnBottomEdge = columnHeightsDuringMeasure[shortestColumnNumber];
//		}
	}

	/**
	 * Starts at the top and adds children until we've passed the list top
	 * 
	 * @param topEdge
	 *            The top edge of the currently first child
	 * @param offset
	 *            Offset of the visible area
	 */
	private void fillListUp(final int offset, int left) {
		Log.d("AntipodalWall", "fillListUp called");
//		while (topEdge + offset > 0 && mFirstItemPosition > 0) {
//			mFirstItemPosition--;
//			final View newTopCild = mAdapter.getView(mFirstItemPosition,
//					getCachedView(), this);
//			// TODO Always adding in col 0
//			addAndMeasureChild(newTopCild, LAYOUT_MODE_ABOVE, 0, 0);
//			final int childHeight = newTopCild.getMeasuredHeight();
//			topEdge -= childHeight;
//
//			// update the list offset (since we added a view at the top)
//			mListTopOffset -= childHeight;
//		}
	}

	/**
	 * Adds a view as a child view and takes care of laying it out
	 * 
	 * @param child
	 *            The view to add
	 * @param layoutMode
	 *            Either LAYOUT_MODE_ABOVE or LAYOUT_MODE_BELOW
	 */
	private void addAndLayoutChild(final View child, final int layoutMode, int l, int columnNumber) {
		Log.d("AntipodalWall", "addAndLayoutChild called with columnNumber " + columnNumber
				+ " and left value of " + l);
		// TODO Add to the top code isn't done
		// We place each child in the column that has the less height to the
		// moment
		int left = this.paddingL + l + (int) (this.columnWidth * columnNumber)
				+ (this.horizontalSpacing * columnNumber);
		int childHeight = child.getMeasuredHeight();
		int childWidth = child.getMeasuredWidth();
		child.layout(left, mColumnHeights[columnNumber] + this.paddingT,
				left + childWidth,
				mColumnHeights[columnNumber] + childHeight
						+ this.paddingT);
		mColumnHeights[columnNumber] = mColumnHeights[columnNumber] + childHeight
				+ this.verticalSpacing;
		LayoutParams params = child.getLayoutParams();
		if (params == null) {
			params = new LayoutParams(LayoutParams.WRAP_CONTENT,
					LayoutParams.WRAP_CONTENT);
		}
		// -1 means put it at the end, 0 means at the beginning.
		final int index = layoutMode == LAYOUT_MODE_ABOVE ? 0 : -1;
		addViewInLayout(child, index, params, true);
		// TODO Is the below necessary?
//		LayoutParams params = child.getLayoutParams();
//		if (params == null) {
//			params = new LayoutParams(LayoutParams.WRAP_CONTENT,
//					LayoutParams.WRAP_CONTENT);
//		}
//		final int index = layoutMode == LAYOUT_MODE_ABOVE ? 0 : -1;
//		addViewInLayout(child, index, params, true);
//
//		final int itemWidth = getWidth();
//		child.measure(MeasureSpec.EXACTLY | itemWidth, MeasureSpec.UNSPECIFIED);
	}

	/**
	 * Positions the children at the "correct" positions
	 */
	private void positionItems() {
		int top = mListTop + mListTopOffset;

		for (int index = 0; index < getChildCount(); index++) {
			final View child = getChildAt(index);

			final int width = child.getMeasuredWidth();
			final int height = child.getMeasuredHeight();
			final int left = (getWidth() - width) / 2;

			child.layout(left, top, left + width, top + height);
			top += height;
		}

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
		// TODO Needed?
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		Log.d("AntipodalWall",  "onMeasure() called");
		// if we don't have an adapter, we don't need to do anything
		if (mAdapter == null) {
			return;
		}
		int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
		// Usable width for children once padding is removed
		int parentUsableWidth = parentWidth - this.paddingL - this.paddingR;
		if (parentUsableWidth < 0)
			parentUsableWidth = 0;

		this.parentHeight = MeasureSpec.getSize(heightMeasureSpec);
		// Usable height for children once padding is removed
		int parentUsableHeight = this.parentHeight - this.paddingT
				- this.paddingB;
		if (parentUsableHeight < 0)
			parentUsableHeight = 0;
		this.columnWidth = parentUsableWidth
				/ this.columns
				- ((this.horizontalSpacing * (this.columns - 1)) / this.columns);
		
		// force the width of the children to be that of the columns...
		mChildWidthSpec = MeasureSpec.makeMeasureSpec((int) this.columnWidth, MeasureSpec.EXACTLY);
		// ... but let them grow vertically
		int lastItemPositionDuringMeasure = mLastItemPosition;
		int[] columnHeightsDuringMeasure = mColumnHeights.clone();
		int shortestColumnNumber = findLowerColumn(columnHeightsDuringMeasure);
		int shortestColumnBottomEdge = columnHeightsDuringMeasure[shortestColumnNumber];
		while (shortestColumnBottomEdge <= getHeight()
				&& lastItemPositionDuringMeasure < mAdapter.getCount() - 1) {
			final View newBottomchild = mAdapter.getView(lastItemPositionDuringMeasure,
					getCachedView(), this);
			measureChild(newBottomchild);
			mViewsAcquiredFromAdapterDuringMeasure.put(lastItemPositionDuringMeasure, newBottomchild);
			mViewIDToColumnNumberMap.put(newBottomchild.getId(), shortestColumnNumber);
			
			shortestColumnBottomEdge += newBottomchild.getMeasuredHeight();
			columnHeightsDuringMeasure[shortestColumnNumber] = shortestColumnBottomEdge;
			shortestColumnNumber = findLowerColumn(columnHeightsDuringMeasure);
			shortestColumnBottomEdge = columnHeightsDuringMeasure[shortestColumnNumber];
			lastItemPositionDuringMeasure++;
		}
		
		// get the final heigth of the viewgroup. it will be that of the higher
		// column once all chidren is in place
		this.finalHeight = columnHeightsDuringMeasure[findHigherColumn(columnHeightsDuringMeasure)];

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
			double scaleRatio = originalWidth / columnWidth;
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
			fillListDown(0, 0);
		} else {
			removeNonVisibleViews(mScrolledPosition);
			fillList(mScrolledPosition, l);
		}
		invalidate();
	}

	@Override
	protected int computeVerticalScrollExtent() {
		return this.parentHeight - (this.finalHeight - this.parentHeight);
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
//		awakenScrollBars();
//		int eventaction = event.getAction();
//		switch (eventaction) {
//		case MotionEvent.ACTION_MOVE:
//			// handle vertical scrolling
//			if (isVerticalScrollBarEnabled()) {
//				if (event.getHistorySize() > 0) {
//					this.y_move = -(int) (event.getY() - event
//							.getHistoricalY(event.getHistorySize() - 1));
//					int result_scroll = getScrollY() + this.y_move;
//					if (result_scroll >= 0
//							&& result_scroll <= this.finalHeight
//									- this.parentHeight)
//						scrollBy(0, this.y_move);
//				}
//			}
//			break;
//		}
//		invalidate();
//		return true;
	}

	private int findLowerColumn(int[] columns) {
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

	private int findHigherColumn(int[] columns) {
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

	private void setGeneralPadding(int padding) {
		this.paddingL = padding;
		this.paddingT = padding;
		this.paddingR = padding;
		this.paddingB = padding;
	}

	@Override
	public Adapter getAdapter() {
		return mAdapter;
	}

	@Override
	public View getSelectedView() {
		throw new UnsupportedOperationException("Not supported");
	}

	@Override
	public void setAdapter(Adapter adapter) {
		mAdapter = adapter;
		removeAllViewsInLayout();
		requestLayout();
	}

	@Override
	public void setSelection(int position) {
		throw new UnsupportedOperationException("Not supported");
	}

}
