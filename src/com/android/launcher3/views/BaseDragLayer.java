/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.views;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.Utilities.SINGLE_FRAME_MS;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.launcher3.util.TouchController;

import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * A viewgroup with utility methods for drag-n-drop and touch interception
 */
public abstract class BaseDragLayer<T extends Context & ActivityContext>
        extends InsettableFrameLayout {

    public static final Property<LayoutParams, Integer> LAYOUT_X =
            new Property<LayoutParams, Integer>(Integer.TYPE, "x") {
                @Override
                public Integer get(LayoutParams lp) {
                    return lp.x;
                }

                @Override
                public void set(LayoutParams lp, Integer x) {
                    lp.x = x;
                }
            };

    public static final Property<LayoutParams, Integer> LAYOUT_Y =
            new Property<LayoutParams, Integer>(Integer.TYPE, "y") {
                @Override
                public Integer get(LayoutParams lp) {
                    return lp.y;
                }

                @Override
                public void set(LayoutParams lp, Integer y) {
                    lp.y = y;
                }
            };

    protected final int[] mTmpXY = new int[2];
    protected final Rect mHitRect = new Rect();

    protected final T mActivity;
    private final MultiValueAlpha mMultiValueAlpha;

    protected TouchController[] mControllers;
    protected TouchController mActiveController;
    private TouchCompleteListener mTouchCompleteListener;

    // Object controlling the current touch interaction
    private Object mCurrentTouchOwner;

    public BaseDragLayer(Context context, AttributeSet attrs, int alphaChannelCount) {
        super(context, attrs);
        mActivity = (T) ActivityContext.lookupContext(context);
        mMultiValueAlpha = new MultiValueAlpha(this, alphaChannelCount);
    }

    public boolean isEventOverView(View view, MotionEvent ev) {
        getDescendantRectRelativeToSelf(view, mHitRect);
        return mHitRect.contains((int) ev.getX(), (int) ev.getY());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getAction();

        if (action == ACTION_UP || action == ACTION_CANCEL) {
            if (mTouchCompleteListener != null) {
                mTouchCompleteListener.onTouchComplete();
            }
            mTouchCompleteListener = null;
        } else if (action == MotionEvent.ACTION_DOWN) {
            mActivity.finishAutoCancelActionMode();
        }
        return findActiveController(ev);
    }

    protected boolean findActiveController(MotionEvent ev) {
        if (com.android.launcher3.TestProtocol.sDebugTracing) {
            android.util.Log.d(com.android.launcher3.TestProtocol.NO_DRAG_TAG,
                    "mActiveController = null");
        }
        mActiveController = null;

        AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(mActivity);
        if (topView != null && topView.onControllerInterceptTouchEvent(ev)) {
            if (com.android.launcher3.TestProtocol.sDebugTracing) {
                android.util.Log.d(com.android.launcher3.TestProtocol.NO_DRAG_TAG,
                        "setting controller1: " + topView.getClass().getSimpleName());
            }
            mActiveController = topView;
            return true;
        }

        for (TouchController controller : mControllers) {
            if (controller.onControllerInterceptTouchEvent(ev)) {
                if (com.android.launcher3.TestProtocol.sDebugTracing) {
                    android.util.Log.d(com.android.launcher3.TestProtocol.NO_DRAG_TAG,
                            "setting controller1: " + controller.getClass().getSimpleName());
                }
                mActiveController = controller;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onRequestSendAccessibilityEvent(View child, AccessibilityEvent event) {
        // Shortcuts can appear above folder
        View topView = AbstractFloatingView.getTopOpenViewWithType(mActivity,
                AbstractFloatingView.TYPE_ACCESSIBLE);
        if (topView != null) {
            if (child == topView) {
                return super.onRequestSendAccessibilityEvent(child, event);
            }
            // Skip propagating onRequestSendAccessibilityEvent for all other children
            // which are not topView
            return false;
        }
        return super.onRequestSendAccessibilityEvent(child, event);
    }

    @Override
    public void addChildrenForAccessibility(ArrayList<View> childrenForAccessibility) {
        View topView = AbstractFloatingView.getTopOpenViewWithType(mActivity,
                AbstractFloatingView.TYPE_ACCESSIBLE);
        if (topView != null) {
            // Only add the top view as a child for accessibility when it is open
            addAccessibleChildToList(topView, childrenForAccessibility);
        } else {
            super.addChildrenForAccessibility(childrenForAccessibility);
        }
    }

    protected void addAccessibleChildToList(View child, ArrayList<View> outList) {
        if (child.isImportantForAccessibility()) {
            outList.add(child);
        } else {
            child.addChildrenForAccessibility(outList);
        }
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        if (child instanceof AbstractFloatingView) {
            // Handles the case where the view is removed without being properly closed.
            // This can happen if something goes wrong during a state change/transition.
            postDelayed(() -> {
                AbstractFloatingView floatingView = (AbstractFloatingView) child;
                if (floatingView.isOpen()) {
                    floatingView.close(false);
                }
            }, SINGLE_FRAME_MS);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        if (action == ACTION_UP || action == ACTION_CANCEL) {
            if (mTouchCompleteListener != null) {
                mTouchCompleteListener.onTouchComplete();
            }
            mTouchCompleteListener = null;
        }

        if (mActiveController != null) {
            if (com.android.launcher3.TestProtocol.sDebugTracing) {
                android.util.Log.d(com.android.launcher3.TestProtocol.NO_DRAG_TAG,
                        "BaseDragLayer before onControllerTouchEvent " +
                                mActiveController.getClass().getSimpleName());
            }
            return mActiveController.onControllerTouchEvent(ev);
        } else {
            if (com.android.launcher3.TestProtocol.sDebugTracing) {
                android.util.Log.d(com.android.launcher3.TestProtocol.NO_DRAG_TAG,
                        "BaseDragLayer no controller");
            }
            // In case no child view handled the touch event, we may not get onIntercept anymore
            return findActiveController(ev);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return dispatchTouchEvent(this, ev);
    }

    public boolean dispatchTouchEvent(Object caller, MotionEvent ev) {
        return verifyTouchDispatch(caller, ev) && super.dispatchTouchEvent(ev);
    }

    /**
     * Returns true if the {@param caller} is allowed to dispatch {@param ev} on this view,
     * false otherwise.
     */
    private boolean verifyTouchDispatch(Object caller, MotionEvent ev) {
        int action = ev.getAction();
        if (action == ACTION_DOWN) {
            if (mCurrentTouchOwner != null) {
                // Another touch in progress.
                ev.setAction(ACTION_CANCEL);
                super.dispatchTouchEvent(ev);
                ev.setAction(action);
            }
            mCurrentTouchOwner = caller;
            return true;
        }
        if (mCurrentTouchOwner != caller) {
            // Someone else is controlling the touch
            return false;
        }
        if (action == ACTION_UP || action == ACTION_CANCEL) {
            mCurrentTouchOwner = null;
        }
        return true;
    }

    /**
     * Determine the rect of the descendant in this DragLayer's coordinates
     *
     * @param descendant The descendant whose coordinates we want to find.
     * @param r The rect into which to place the results.
     * @return The factor by which this descendant is scaled relative to this DragLayer.
     */
    public float getDescendantRectRelativeToSelf(View descendant, Rect r) {
        mTmpXY[0] = 0;
        mTmpXY[1] = 0;
        float scale = getDescendantCoordRelativeToSelf(descendant, mTmpXY);

        r.set(mTmpXY[0], mTmpXY[1],
                (int) (mTmpXY[0] + scale * descendant.getMeasuredWidth()),
                (int) (mTmpXY[1] + scale * descendant.getMeasuredHeight()));
        return scale;
    }

    public float getLocationInDragLayer(View child, int[] loc) {
        loc[0] = 0;
        loc[1] = 0;
        return getDescendantCoordRelativeToSelf(child, loc);
    }

    public float getDescendantCoordRelativeToSelf(View descendant, int[] coord) {
        return getDescendantCoordRelativeToSelf(descendant, coord, false);
    }

    /**
     * Given a coordinate relative to the descendant, find the coordinate in this DragLayer's
     * coordinates.
     *
     * @param descendant The descendant to which the passed coordinate is relative.
     * @param coord The coordinate that we want mapped.
     * @param includeRootScroll Whether or not to account for the scroll of the root descendant:
     *          sometimes this is relevant as in a child's coordinates within the root descendant.
     * @return The factor by which this descendant is scaled relative to this DragLayer. Caution
     *         this scale factor is assumed to be equal in X and Y, and so if at any point this
     *         assumption fails, we will need to return a pair of scale factors.
     */
    public float getDescendantCoordRelativeToSelf(View descendant, int[] coord,
            boolean includeRootScroll) {
        return Utilities.getDescendantCoordRelativeToAncestor(descendant, this,
                coord, includeRootScroll);
    }

    /**
     * Inverse of {@link #getDescendantCoordRelativeToSelf(View, int[])}.
     */
    public void mapCoordInSelfToDescendant(View descendant, int[] coord) {
        Utilities.mapCoordInSelfToDescendant(descendant, this, coord);
    }

    public void getViewRectRelativeToSelf(View v, Rect r) {
        int[] loc = new int[2];
        getLocationInWindow(loc);
        int x = loc[0];
        int y = loc[1];

        v.getLocationInWindow(loc);
        int vX = loc[0];
        int vY = loc[1];

        int left = vX - x;
        int top = vY - y;
        r.set(left, top, left + v.getMeasuredWidth(), top + v.getMeasuredHeight());
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        // Consume the unhandled move if a container is open, to avoid switching pages underneath.
        return AbstractFloatingView.getTopOpenView(mActivity) != null;
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        View topView = AbstractFloatingView.getTopOpenView(mActivity);
        if (topView != null) {
            return topView.requestFocus(direction, previouslyFocusedRect);
        } else {
            return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
        }
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        View topView = AbstractFloatingView.getTopOpenView(mActivity);
        if (topView != null) {
            topView.addFocusables(views, direction);
        } else {
            super.addFocusables(views, direction, focusableMode);
        }
    }

    public void setTouchCompleteListener(TouchCompleteListener listener) {
        mTouchCompleteListener = listener;
    }

    public interface TouchCompleteListener {
        void onTouchComplete();
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    // Override to allow type-checking of LayoutParams.
    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    public AlphaProperty getAlphaProperty(int index) {
        return mMultiValueAlpha.getProperty(index);
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "DragLayer");
        if (mActiveController != null) {
            writer.println(prefix + "\tactiveController: " + mActiveController);
            mActiveController.dump(prefix + "\t", writer);
        }
        writer.println(prefix + "\tdragLayerAlpha : " + mMultiValueAlpha );
    }

    public static class LayoutParams extends InsettableFrameLayout.LayoutParams {
        public int x, y;
        public boolean customPosition = false;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.LayoutParams lp) {
            super(lp);
        }
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            final FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) child.getLayoutParams();
            if (flp instanceof LayoutParams) {
                final LayoutParams lp = (LayoutParams) flp;
                if (lp.customPosition) {
                    child.layout(lp.x, lp.y, lp.x + lp.width, lp.y + lp.height);
                }
            }
        }
    }
}
