import dev.salt.airmouse.*;
import java.util.*;

public class CoreTests {
    static int passed=0;
    static class Event {String op;double x,y;int amount;Event(String o,double a,double b,int n){op=o;x=a;y=b;amount=n;}}
    static class Recorder implements TrackpadEngine.Sink {List<Event> events=new ArrayList<>();public void emit(String op,double x,double y,int amount){events.add(new Event(op,x,y,amount));}long count(String op){return events.stream().filter(e->e.op.equals(op)).count();}}
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    static void test(String name,Runnable r){r.run();passed++;System.out.println("PASS "+name);}
    static void tap(TrackpadEngine g,long t){g.begin(t,20,20);g.end(t+50);}
    static double[] multiply(double[] a,double[] b){return new double[]{a[0]*b[0]-a[1]*b[1]-a[2]*b[2]-a[3]*b[3],a[0]*b[1]+a[1]*b[0]+a[2]*b[3]-a[3]*b[2],a[0]*b[2]-a[1]*b[3]+a[2]*b[0]+a[3]*b[1],a[0]*b[3]+a[1]*b[2]-a[2]*b[1]+a[3]*b[0]};}
    static double[] quat(AirMotion a,long t,double[] q,boolean on){return a.quaternion(t,q[0],q[1],q[2],q[3],on);}
    public static void main(String[] args){
        test("single tap sends one left click",()->{Recorder r=new Recorder();TrackpadEngine g=new TrackpadEngine(r);tap(g,1000);check(r.count("left")==1,"single click");});
        test("double tap sends exactly two clicks",()->{Recorder r=new Recorder();TrackpadEngine g=new TrackpadEngine(r);tap(g,1000);tap(g,1140);check(r.count("left")==2,"double click count");});
        test("pointer drag without tap emits no click",()->{Recorder r=new Recorder();TrackpadEngine g=new TrackpadEngine(r);g.begin(1000,20,20);g.motion(1050,40,30,1,0,0,1);g.end(1100);check(r.count("move")==1&&r.count("left")==0,"move vs tap");});
        test("two-finger tap is right click only",()->{Recorder r=new Recorder();TrackpadEngine g=new TrackpadEngine(r);g.begin(1000,20,20);g.second(1030,40,20,40);g.fingerLift();g.end(1100);check(r.count("right")==1&&r.count("left")==0,"right tap");});
        test("vertical scroll does not click or zoom",()->{Recorder r=new Recorder();TrackpadEngine g=new TrackpadEngine(r);g.begin(1000,20,20);g.second(1020,40,20,40);g.motion(1040,20,40,2,40,40,40);g.end(1080);check(r.count("scroll")==1&&r.count("zoom")==0&&r.count("right")==0,"scroll classification");check(r.events.get(0).amount>0,"natural direction");});
        test("horizontal scroll is supported",()->{Recorder r=new Recorder();TrackpadEngine g=new TrackpadEngine(r);g.begin(1000,20,20);g.second(1020,40,20,40);g.motion(1040,40,20,2,60,20,40);g.end(1080);check(r.count("hscroll")==1,"horizontal scroll");});
        test("pinch out zooms without scrolling",()->{Recorder r=new Recorder();TrackpadEngine g=new TrackpadEngine(r);g.begin(1000,20,20);g.second(1020,40,20,40);g.motion(1040,20,20,2,40,20,60);g.end(1080);check(r.count("zoom")==1&&r.events.get(0).amount>0&&r.count("scroll")==0&&r.count("right")==0,"zoom out classification");});
        test("pinch in zooms out",()->{Recorder r=new Recorder();TrackpadEngine g=new TrackpadEngine(r);g.begin(1000,20,20);g.second(1020,40,20,80);g.motion(1040,20,20,2,40,20,50);g.end(1080);check(r.count("zoom")==1&&r.events.get(0).amount<0,"zoom in classification");});
        test("long press drag releases on lift",()->{Recorder r=new Recorder();TrackpadEngine g=new TrackpadEngine(r);g.begin(1000,20,20);g.tick(1450);g.motion(1460,40,20,1,0,0,1);g.end(1500);check(r.events.get(0).op.equals("left_down")&&r.events.get(1).op.equals("move")&&r.events.get(2).op.equals("left_up")&&r.count("left")==0,"drag ordering");});
        test("double-tap drag releases on cancellation",()->{Recorder r=new Recorder();TrackpadEngine g=new TrackpadEngine(r);tap(g,1000);g.begin(1150,20,20);g.motion(1200,40,20,1,0,0,1);g.cancel();check(r.count("left_down")==1&&r.count("left_up")==1,"cancel releases");});
        test("multi-touch lift suppresses accidental cursor move",()->{Recorder r=new Recorder();TrackpadEngine g=new TrackpadEngine(r);g.begin(1000,20,20);g.second(1020,40,20,40);g.fingerLift();g.motion(1060,50,20,1,0,0,1);g.end(1080);check(r.count("move")==0,"no pointer jump");});
        test("moving remaining finger suppresses accidental right click",()->{Recorder r=new Recorder();TrackpadEngine g=new TrackpadEngine(r);g.begin(1000,20,20);g.second(1020,40,20,40);g.fingerLift(60,20);g.motion(1060,90,20,1,0,0,1);g.end(1080);check(r.count("right")==0&&r.count("move")==0,"no accidental right click");});
        test("staggered two-finger release still right-clicks",()->{Recorder r=new Recorder();TrackpadEngine g=new TrackpadEngine(r);g.begin(1000,20,20);g.second(1020,40,20,40);g.fingerLift(60,20);g.motion(1060,61,20,1,0,0,1);g.end(1080);check(r.count("right")==1,"two-finger tap tolerates staggered release");});
        test("third finger cancels click and releases drag",()->{Recorder r=new Recorder();TrackpadEngine g=new TrackpadEngine(r);g.begin(1000,20,20);g.extraFinger();g.end(1080);check(r.events.isEmpty(),"third finger not click");});
        test("air hold does not drag or touch-move",()->{Recorder r=new Recorder();TrackpadEngine g=new TrackpadEngine(r);g.airMode=true;g.begin(1000,20,20);g.tick(1500);g.motion(1520,50,50,1,0,0,1);g.end(1800);check(r.events.isEmpty(),"air hold semantics");});
        test("air rotation suppresses accidental click",()->{Recorder r=new Recorder();TrackpadEngine g=new TrackpadEngine(r);g.airMode=true;g.begin(1000,20,20);g.airMotion(8,0);g.end(1080);check(r.count("left")==0,"no click on air movement");});
        test("gyro moves while held and stops on release",()->{AirMotion a=new AirMotion();a.gyro(1000000000L,0,1,0,true);double[] d=a.gyro(1020000000L,0,1,0,true);check(d[0]<-10,"yaw produces movement");d=a.gyro(1040000000L,0,1,0,false);check(d[0]==0&&d[1]==0,"hold release");});
        test("automatic flat grip maps z rotation",()->{AirMotion a=new AirMotion();a.gravity(0,0,9.8);a.gyro(1000000000L,0,0,1,true);check(a.gyro(1020000000L,0,0,1,true)[0]<-10,"flat yaw");});
        test("upright quaternion yaw produces horizontal movement",()->{AirMotion a=new AirMotion();double c=Math.sqrt(0.5);double[] initial={c,c,0,0};double[] delta={Math.cos(.01),0,Math.sin(.01),0};quat(a,1000000000L,initial,true);double[] out=quat(a,1020000000L,multiply(initial,delta),true);check(out[0]<-10&&Math.abs(out[1])<0.01,"quaternion axis projection");});
        test("quaternion sign flip does not jump",()->{AirMotion a=new AirMotion();quat(a,1000000000L,new double[]{1,0,0,0},true);double[] d=quat(a,1020000000L,new double[]{-1,0,0,0},true);check(d[0]==0&&d[1]==0,"q and -q equivalent");});
        test("recenter or sensor gap does not jump",()->{AirMotion a=new AirMotion();a.gyro(1000000000L,0,1,0,true);a.reset();check(a.gyro(1100000000L,0,1,0,true)[0]==0,"first sample reanchors");check(a.gyro(2100000000L,0,1,0,true)[0]==0,"long gap ignored");});
        test("reverse axis setting changes direction",()->{AirMotion a=new AirMotion();a.invertX=true;a.gyro(1000000000L,0,1,0,true);check(a.gyro(1020000000L,0,1,0,true)[0]>10,"invert axis");});
        test("gyro bias calibration removes steady drift",()->{AirMotion a=new AirMotion();a.bias[1]=0.03;a.gyro(1000000000L,0,.03,0,true);check(a.gyro(1020000000L,0,.03,0,true)[0]==0,"bias subtraction");});
        System.out.println(passed+" core tests passed");
    }
}
