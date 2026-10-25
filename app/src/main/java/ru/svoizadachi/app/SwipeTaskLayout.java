package ru.svoizadachi.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;

/** Horizontal completion gesture that leaves vertical scrolling to the list. */
public final class SwipeTaskLayout extends LinearLayout {
    private float startX,startY,delta;
    private boolean swiping;
    private final int slop;
    private Runnable complete;
    public SwipeTaskLayout(Context c){super(c);slop=ViewConfiguration.get(c).getScaledTouchSlop();}
    public void onComplete(Runnable action){complete=action;}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){startX=event.getRawX();startY=event.getRawY();swiping=false;delta=0;}
        else if(event.getActionMasked()==MotionEvent.ACTION_MOVE){
            float x=event.getRawX()-startX,y=event.getRawY()-startY;
            if(Math.abs(x)>slop*2&&Math.abs(x)>Math.abs(y)*1.4f){
                swiping=true;getParent().requestDisallowInterceptTouchEvent(true);return true;
            }
        }
        return swiping;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!swiping)return super.onTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE){
            delta=event.getRawX()-startX;
            setTranslationX(delta*.7f);setAlpha(Math.max(.45f,1-Math.abs(delta)/Math.max(1,getWidth())));
        }else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){
            boolean accepted=event.getActionMasked()==MotionEvent.ACTION_UP&&Math.abs(delta)>getWidth()*.26f;
            swiping=false;getParent().requestDisallowInterceptTouchEvent(false);
            if(accepted&&complete!=null){performClick();complete.run();}
            else if(ValueAnimator.areAnimatorsEnabled())animate().translationX(0).alpha(1).setDuration(260).setInterpolator(new OvershootInterpolator(.7f)).start();
            else {setTranslationX(0);setAlpha(1);}
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
