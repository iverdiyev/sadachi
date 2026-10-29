package ru.svoizadachi.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** A quiet PCM-driven indicator; never runs an autonomous animation loop. */
public final class VoiceWaveform extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float level;
    public VoiceWaveform(Context context){super(context);paint.setColor(ThemeSettings.accent(context));setContentDescription("Уровень микрофона");}
    public void level(float rms){
        float target=Math.max(0f,Math.min(1f,rms*14f));
        level+= (target>level?.42f:.18f)*(target-level);
        if(level<.01f)level=0f;
        invalidate();
    }
    public void stop(){level=0f;invalidate();}
    static float pcmRms(byte[] data,int size){
        if(data==null||size<2)return 0f;
        double total=0;int samples=Math.min(size,data.length)/2;
        for(int i=0;i<samples;i++){
            short sample=(short)((data[2*i]&0xff)|(data[2*i+1]<<8));
            double normalized=sample/32768.0;total+=normalized*normalized;
        }
        return (float)Math.sqrt(total/samples);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float density=getResources().getDisplayMetrics().density;
        float bar=3*density,gap=4*density,used=32*bar+31*gap;
        float scale=Math.min(1f,getWidth()/used);bar*=scale;gap*=scale;used=32*bar+31*gap;
        float left=(getWidth()-used)/2f,center=getHeight()/2f;
        float amplitude=ValueAnimator.areAnimatorsEnabled()?level:0f;
        for(int i=0;i<32;i++){
            float envelope=.35f+.65f*(1f-Math.abs((i-15.5f)/15.5f));
            float variation=.82f+.18f*(float)Math.sin(i*2.31f);
            float height=(5f+50f*amplitude*envelope*variation)*density;
            float x=left+i*(bar+gap);
            canvas.drawRoundRect(x,center-height/2f,x+bar,center+height/2f,bar/2f,bar/2f,paint);
        }
    }
}
