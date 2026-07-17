package com.megatech.fms;


import android.app.Dialog;
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.megatech.fms.helpers.Logger;

public class BaseDialog extends Dialog {
    public BaseDialog(@NonNull Context context) {
        super(context);
    }

    public BaseDialog(@NonNull Context context, int themeResId) {
        super(context, themeResId);
    }

    protected BaseDialog(@NonNull Context context, boolean cancelable, @Nullable OnCancelListener cancelListener) {
        super(context, cancelable, cancelListener);
    }

    private View findViewAt(ViewGroup  viewGroup, int x, int y)
    {
        for(int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ViewGroup) {
                View foundView = findViewAt((ViewGroup) child, x, y);
                if (foundView != null && foundView.isShown()) {
                    return foundView;
                }
            } else {
                int[] location = new int[2];
                child.getLocationOnScreen(location);
                Rect rect = new Rect(location[0], location[1], location[0] + child.getWidth(), location[1] + child.getHeight());
                if (rect.contains(x, y)) {
                    return child;
                }
            }
        }
        return null;
    }
    protected View findViewAtPos(int x, int y)
    {
        ViewGroup viewGroup = (ViewGroup)this.getWindow().getDecorView();
        return  findViewAt(viewGroup, x,y);
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {

            int x = (int) ev.getX();
            int y = (int) ev.getY();
            View v = findViewAtPos(x, y);
            if (v != null)
                Logger.appendLog("DLG", "Click: " + v.toString());
        }
        return super.dispatchTouchEvent(ev);

    }
}
