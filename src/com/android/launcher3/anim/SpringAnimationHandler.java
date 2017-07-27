/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.anim;

import android.support.animation.FloatPropertyCompat;
import android.support.animation.SpringAnimation;
import android.support.animation.SpringForce;
import android.support.annotation.IntDef;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import com.android.launcher3.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

/**
 * Handler class that manages springs for a set of views that should all move based on the same
 * {@link MotionEvent}s.
 *
 * Supports setting either X or Y velocity on the list of springs added to this handler.
 */
public class SpringAnimationHandler<T> {

    private static final String TAG = "SpringAnimationHandler";
    private static final boolean DEBUG = false;

    private static final float VELOCITY_DAMPING_FACTOR = 0.175f;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({Y_DIRECTION, X_DIRECTION})
    public @interface Direction {}
    public static final int Y_DIRECTION = 0;
    public static final int X_DIRECTION = 1;
    private int mVelocityDirection;

    private VelocityTracker mVelocityTracker;
    private float mCurrentVelocity = 0;
    private boolean mShouldComputeVelocity = false;

    private AnimationFactory<T> mAnimationFactory;

    private ArrayList<SpringAnimation> mAnimations = new ArrayList<>();

    /**
     * @param direction Either {@link #X_DIRECTION} or {@link #Y_DIRECTION}.
     *                  Determines which direction we use to calculate and set the velocity.
     * @param factory   The AnimationFactory is responsible for initializing and updating the
     *                  SpringAnimations added to this class.
     */
    public SpringAnimationHandler(@Direction int direction, AnimationFactory<T> factory) {
        mVelocityDirection = direction;
        mAnimationFactory = factory;
    }

    /**
     * Adds a new or recycled animation to the list of springs handled by this class.
     *
     * @param view The view the spring is attached to.
     * @param object Used to initialize and update the spring.
     */
    public void add(View view, T object) {
        SpringAnimation spring = (SpringAnimation) view.getTag(R.id.spring_animation_tag);
        if (spring == null) {
            spring = mAnimationFactory.initialize(object);
            view.setTag(R.id.spring_animation_tag, spring);
        }
        mAnimationFactory.update(spring, object);
        spring.setStartVelocity(mCurrentVelocity);
        mAnimations.add(spring);
    }

    /**
     * Stops and removes the spring attached to {@param view}.
     */
    public void remove(View view) {
        SpringAnimation animation = (SpringAnimation) view.getTag(R.id.spring_animation_tag);
        if (animation.canSkipToEnd()) {
            animation.skipToEnd();
        }
        mAnimations.remove(animation);
    }

    public void addMovement(MotionEvent event) {
        int action = event.getActionMasked();
        if (DEBUG) Log.d(TAG, "addMovement#action=" + action);
        switch (action) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_DOWN:
                reset();
                break;
        }

        getVelocityTracker().addMovement(event);
        mShouldComputeVelocity = true;
    }

    public void animateToFinalPosition(float position) {
        if (DEBUG) Log.d(TAG, "animateToFinalPosition#computeVelocity=" + mShouldComputeVelocity);

        if (mShouldComputeVelocity) {
            computeVelocity();
            setStartVelocity(mCurrentVelocity);
        }

        int size = mAnimations.size();
        for (int i = 0; i < size; ++i) {
            mAnimations.get(i).animateToFinalPosition(position);
        }

        reset();
    }

    public boolean isRunning() {
        // All the animations run at the same time so we can just check the first one.
        return !mAnimations.isEmpty() && mAnimations.get(0).isRunning();
    }

    public void skipToEnd() {
        if (DEBUG) Log.d(TAG, "setStartVelocity#skipToEnd");
        if (DEBUG) Log.v(TAG, "setStartVelocity#skipToEnd", new Exception());

        int size = mAnimations.size();
        for (int i = 0; i < size; ++i) {
            if (mAnimations.get(i).canSkipToEnd()) {
                mAnimations.get(i).skipToEnd();
            }
        }
    }

    public void reset() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
        mCurrentVelocity = 0;
    }

    private void setStartVelocity(float velocity) {
        int size = mAnimations.size();
        for (int i = 0; i < size; ++i) {
            mAnimations.get(i).setStartVelocity(velocity);
        }
    }

    private void computeVelocity() {
        getVelocityTracker().computeCurrentVelocity(1000 /* millis */);

        mCurrentVelocity = isVerticalDirection()
                ? getVelocityTracker().getYVelocity()
                : getVelocityTracker().getXVelocity();
        mCurrentVelocity *= VELOCITY_DAMPING_FACTOR;
        mShouldComputeVelocity = false;

        if (DEBUG) Log.d(TAG, "computeVelocity=" + mCurrentVelocity);
    }

    private boolean isVerticalDirection() {
        return mVelocityDirection == Y_DIRECTION;
    }

    private VelocityTracker getVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        return mVelocityTracker;
    }

    /**
     * This interface is used to initialize and update the SpringAnimations added to the
     * {@link SpringAnimationHandler}.
     *
     * @param <T> The object that each SpringAnimation is attached to.
     */
    public interface AnimationFactory<T> {

        /**
         * Initializes a new Spring for {@param object}.
         */
        SpringAnimation initialize(T object);

        /**
         * Updates the value of {@param spring} based on {@param object}.
         */
        void update(SpringAnimation spring, T object);
    }

    /**
     * Helper method to create a new SpringAnimation for {@param view}.
     */
    public static SpringAnimation forView(View view, FloatPropertyCompat property, float finalPos) {
        SpringAnimation spring = new SpringAnimation(view, property, finalPos);
        spring.setStartValue(1f);
        spring.setSpring(new SpringForce(finalPos));
        return spring;
    }

}
