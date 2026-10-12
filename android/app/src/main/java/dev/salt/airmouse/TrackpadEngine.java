package dev.salt.airmouse;

/** Platform-independent gesture state machine. Coordinates are density-independent pixels. */
public final class TrackpadEngine {
    public interface Sink { void emit(String op,double x,double y,int amount); }
    final Sink sink;
    public boolean airMode=false,natural=true;
    boolean down,moved,multi,drag,pinching,scrolling,suppressSingle,airMoved;
    long start,lastTap=-10000;
    double x,y,originX,originY,baseX,baseY,lastCX,lastCY,baseSpan,lastSpan,zoomRemainder,scrollX,scrollY;
    public TrackpadEngine(Sink s){sink=s;}
    public void start(long time,double px,double py){
        cancel();down=true;start=time;x=originX=px;y=originY=py;
        moved=multi=pinching=scrolling=suppressSingle=airMoved=false;zoomRemainder=scrollX=scrollY=0;
    }
    public void second(long time,double cx,double cy,double span){
        releaseDrag();multi=true;suppressSingle=true;lastTap=-10000;
        baseX=lastCX=cx;baseY=lastCY=cy;baseSpan=lastSpan=Math.max(1,span);
    }
    public void tick(long time){
        if(down&&!airMode&&!multi&&!moved&&!drag&&time-start>=450){drag=true;lastTap=-10000;sink.emit("left_down",0,0,0);}
    }
    public void motion(long time,double px,double py,int count,double cx,double cy,double span){
        if(!down)return;
        if(count>2){moved=true;suppressSingle=true;releaseDrag();return;}
        if(count==1){
            if(suppressSingle){if(Math.hypot(px-originX,py-originY)>5)moved=true;x=px;y=py;return;}
            double dx=px-x,dy=py-y;
            if(Math.hypot(px-originX,py-originY)>5){
                moved=true;
                // A second tap followed by a drag holds the left button.
                if(!airMode&&!drag&&start-lastTap<=350){drag=true;sink.emit("left_down",0,0,0);lastTap=-10000;}
            }
            if(!airMode&&(moved||drag))sink.emit("move",dx,dy,0);
            x=px;y=py;
            return;
        }
        span=Math.max(1,span);
        double ratio=Math.log(span/baseSpan);
        if(!pinching&&!scrolling){
            if(Math.abs(ratio)>0.065){pinching=true;moved=true;}
            else if(Math.hypot(cx-baseX,cy-baseY)>6){scrolling=true;moved=true;}
        }
        if(pinching){
            zoomRemainder+=Math.log(span/lastSpan)/0.12;
            int steps=(int)zoomRemainder;
            if(steps!=0){sink.emit("zoom",0,0,steps*120);zoomRemainder-=steps;}
        }else if(scrolling){
            double sign=natural?1:-1;
            scrollY+=(cy-lastCY)*4*sign;scrollX+=-(cx-lastCX)*4*sign;
            int v=(int)scrollY,h=(int)scrollX;
            if(v!=0){sink.emit("scroll",0,0,v);scrollY-=v;}
            if(h!=0){sink.emit("hscroll",0,0,h);scrollX-=h;}
        }
        // Preserve movement accumulated while deciding pinch vs scroll.
        if(pinching||scrolling){lastCX=cx;lastCY=cy;lastSpan=span;}
    }
    public void extraFinger(){moved=true;suppressSingle=true;releaseDrag();}
    public void fingerLift(){suppressSingle=true;}
    public void fingerLift(double px,double py){suppressSingle=true;x=originX=px;y=originY=py;}
    public void airMotion(double dx,double dy){if(Math.hypot(dx,dy)>0.2)airMoved=true;}
    public void end(long time){
        if(!down)return;
        boolean wasDrag=drag;releaseDrag();
        if(!wasDrag&&!moved&&!airMoved&&time-start<=280){
            sink.emit(multi?"right":"left",0,0,0);
            lastTap=multi?-10000:time;
        }else if(!wasDrag)lastTap=-10000;
        down=false;
    }
    void releaseDrag(){if(drag){drag=false;sink.emit("left_up",0,0,0);}}
    public void cancel(){releaseDrag();down=false;lastTap=-10000;}
    // Preserve previous tap across ACTION_DOWN for double-tap-and-drag.
    public void begin(long time,double px,double py){long previous=lastTap;start(time,px,py);lastTap=previous;}
}
