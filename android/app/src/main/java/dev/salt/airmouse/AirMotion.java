package dev.salt.airmouse;

/** Gyro integration and relative quaternion fallback; no Android dependency. */
public final class AirMotion {
    public boolean invertX=false,invertY=false;
    public int grip=0; // 0 automatic, 1 upright, 2 flat
    double gx=0,gy=1,gz=0;
    public void gravity(double x,double y,double z){double n=Math.sqrt(x*x+y*y+z*z);if(n>0.1){gx=x/n;gy=y/n;gz=z/n;}}
    public double sensitivity=1;
    public final double[] bias=new double[3];
    long last=0; double fx=0,fy=0; double[] previous=null;
    public void reset(){last=0;previous=null;fx=fy=0;}
    public double[] gyro(long timestamp,double rx,double ry,double rz,boolean active){
        double dt=last==0?0:(timestamp-last)*1e-9;last=timestamp;
        if(!active||dt<=0||dt>0.15){fx=fy=0;return new double[]{0,0};}
        return project(rx-bias[0],ry-bias[1],rz-bias[2],dt);
    }
    public double[] quaternion(long timestamp,double w,double x,double y,double z,boolean active){
        double length=Math.sqrt(w*w+x*x+y*y+z*z);
        if(length<0.001||(Double.isNaN(length)||Double.isInfinite(length))){reset();return new double[]{0,0};}
        double[] now={w/length,x/length,y/length,z/length};
        w=now[0];x=now[1];y=now[2];z=now[3];
        gravity(2*(x*z-w*y),2*(y*z+w*x),1-2*(x*x+y*y));
        double dt=last==0?0:(timestamp-last)*1e-9;last=timestamp;
        double[] old=previous;previous=now;
        if(!active||old==null||dt<=0||dt>0.15){fx=fy=0;return new double[]{0,0};}
        // conjugate(previous) * current gives rotation in phone coordinates.
        double dw=old[0]*now[0]+old[1]*now[1]+old[2]*now[2]+old[3]*now[3];
        double dx=old[0]*now[1]-old[1]*now[0]-old[2]*now[3]+old[3]*now[2];
        double dy=old[0]*now[2]+old[1]*now[3]-old[2]*now[0]-old[3]*now[1];
        double dz=old[0]*now[3]-old[1]*now[2]+old[2]*now[1]-old[3]*now[0];
        if(dw<0){dw=-dw;dx=-dx;dy=-dy;dz=-dz;}
        double n=Math.sqrt(dx*dx+dy*dy+dz*dz),angle=2*Math.atan2(n,dw);
        if(n<1e-8||angle>0.35)return new double[]{0,0};
        return project(dx*angle/n/dt,dy*angle/n/dt,dz*angle/n/dt,dt);
    }
    double[] project(double rx,double ry,double rz,double dt){
        double horizontal=grip==1?ry:grip==2?rz:gx*rx+gy*ry+gz*rz;
        if(Math.abs(horizontal)<0.004)horizontal=0;
        if(Math.abs(rx)<0.004)rx=0;
        // Time-aware smoothing works for both slow and fast sensor streams.
        double alpha=1-Math.exp(-dt/0.025);
        fx+=alpha*(horizontal-fx);fy+=alpha*(rx-fy);
        double sx=invertX?1:-1,sy=invertY?1:-1;
        return new double[]{sx*fx*dt*1500*sensitivity,sy*fy*dt*1500*sensitivity};
    }
}
