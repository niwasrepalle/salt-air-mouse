package dev.salt.airmouse;

import android.app.*;
import android.os.*;
import android.hardware.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.net.*;
import java.io.*;
import java.util.concurrent.ArrayBlockingQueue;

public class MainActivity extends Activity implements SensorEventListener {
    final Handler ui=new Handler(); final Object linkLock=new Object();
    final int BG=0xff0b1018,SURFACE=0xff141e2b,TEXT=0xffedf5fa,MUTED=0xff91a2b8,ACCENT=0xff8cf0c2;
    TextView status,diagnostics; Button connectButton,touchButton,airButton;
    PadView pad; SensorManager sensors; Sensor motionSensor,gravitySensor;
    final AirMotion motion=new AirMotion(); TrackpadEngine gestures;
    boolean modal=false,air=false,held=false,holdToMove=true,resumed=false,calibrating=false;
    float sensitivity=1; boolean natural=true;
    double pendingX=0,pendingY=0; long sensorStart=0,lastSensor=0,calStart=0;
    int fallbackIndex=0,sensorCount=0; final double[] calSum=new double[3];int calSamples=0;
    final int[] sensorTypes={Sensor.TYPE_GAME_ROTATION_VECTOR,Sensor.TYPE_ROTATION_VECTOR,Sensor.TYPE_GYROSCOPE};
    volatile boolean connected=false;volatile Socket socket;volatile int generation=0;
    final ArrayBlockingQueue<JSONObject> queue=new ArrayBlockingQueue<>(48);
    final Runnable frame=new Runnable(){public void run(){
        gestures.tick(SystemClock.uptimeMillis());
        if(connected&&(Math.abs(pendingX)>0.01||Math.abs(pendingY)>0.01)){
            try{if(queue.size()<4)queue.offer(new JSONObject().put("op","move").put("x",pendingX).put("y",pendingY));}catch(Exception ignored){}
        }
        pendingX=pendingY=0;ui.postDelayed(this,16);
    }};
    final Runnable health=new Runnable(){public void run(){
        long now=SystemClock.elapsedRealtime();
        if(resumed&&motionSensor!=null&&now-lastSensor>1800&&now-sensorStart>2200&&fallbackIndex<sensorTypes.length-1){startSensor(fallbackIndex+1);}
        if(air){String source=motionSensor==null?"No motion sensor":motionSensor.getType()==Sensor.TYPE_GYROSCOPE?"Gyroscope":"Rotation sensor";
            String state=motionSensor==null?"Touchpad still works":now-lastSensor<1800?"Receiving · "+sensorCount+" updates/s":"No readings · check Sensors Off";
            diagnostics.setText(source+"  /  "+state);
        }else diagnostics.setText("Private Wi-Fi · "+(connected?"Connected":"Not paired"));
        sensorCount=0;
        if(calibrating&&System.nanoTime()-calStart>2500000000L){calibrating=false;status.setText("Calibration timed out. Check motion sensor readings.");}
        ui.postDelayed(this,1000);
    }};
    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density);}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    GradientDrawable shape(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    Button button(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(TEXT);b.setPadding(dp(12),0,dp(12),0);b.setMinWidth(0);b.setMinimumWidth(0);b.setBackground(shape(SURFACE,0xff2a394d));b.setOnClickListener(v->action.run());return b;}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    LinearLayout.LayoutParams weighted(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(3),0,dp(3),0);return p;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        sensors=(SensorManager)getSystemService(SENSOR_SERVICE);
        sensitivity=getPreferences(0).getFloat("sensitivity",1f);natural=getPreferences(0).getBoolean("natural",true);holdToMove=getPreferences(0).getBoolean("hold",true);
        motion.grip=getPreferences(0).getInt("grip",0);motion.invertX=getPreferences(0).getBoolean("invertX",false);motion.invertY=getPreferences(0).getBoolean("invertY",false);
        gestures=new TrackpadEngine((op,x,y,amount)->{if(op.equals("move")){pendingX+=x*sensitivity;pendingY+=y*sensitivity;}else send(op,amount);});gestures.natural=natural;
        LinearLayout root=column();root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(16),dp(12)+i.getSystemWindowInsetTop(),dp(16),dp(12)+i.getSystemWindowInsetBottom());return i;});
        setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("Salt Mouse",23,TEXT);header.addView(title,new LinearLayout.LayoutParams(0,dp(54),1));title.setGravity(Gravity.CENTER_VERTICAL);
        connectButton=button("Connect",()->connectionDialog());header.addView(connectButton,new LinearLayout.LayoutParams(dp(94),dp(40)));
        Button settings=button("⋯",()->settingsDialog());settings.setTextSize(26);settings.setContentDescription("Settings and gesture guide");LinearLayout.LayoutParams more=new LinearLayout.LayoutParams(dp(44),dp(40));more.setMargins(dp(8),0,0,0);header.addView(settings,more);root.addView(header);
        status=text("Your laptop, at your fingertips",13,MUTED);status.setMaxLines(2);root.addView(status,new LinearLayout.LayoutParams(-1,dp(42)));status.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout modes=new LinearLayout(this);touchButton=button("Trackpad",()->setMode(false));airButton=button("Air mouse",()->setMode(true));modes.addView(touchButton,weighted());modes.addView(airButton,weighted());root.addView(modes);
        pad=new PadView();LinearLayout.LayoutParams area=new LinearLayout.LayoutParams(-1,0,1);area.setMargins(0,dp(14),0,dp(12));root.addView(pad,area);
        TextView hint=text("Tap to click · Two-finger tap to right-click\nTwo fingers to scroll · Pinch to zoom",12,MUTED);hint.setGravity(Gravity.CENTER);root.addView(hint,new LinearLayout.LayoutParams(-1,dp(44)));
        diagnostics=text("Private Wi-Fi · Not paired",11,MUTED);diagnostics.setGravity(Gravity.CENTER);root.addView(diagnostics,new LinearLayout.LayoutParams(-1,dp(28)));
        setMode(false);ui.post(frame);ui.post(health);
    }
    void setMode(boolean next){
        stopGesture();air=next;gestures.airMode=air;motion.reset();
        touchButton.setTextColor(air?MUTED:BG);touchButton.setBackground(shape(air?SURFACE:ACCENT,0));
        airButton.setTextColor(air?BG:MUTED);airButton.setBackground(shape(air?ACCENT:SURFACE,0));
        if(air&&motionSensor==null)status.setText("No supported sensor. Check Sensors Off, or use Trackpad.");
        else status.setText(connected?(air?"Touch & hold the pad, then gently rotate your phone":"Connected · Ready to use"):"Connect to your laptop to begin");
        if(pad!=null)pad.invalidate();
    }
    void clearMovement(){pendingX=pendingY=0;motion.reset();for(JSONObject p:queue.toArray(new JSONObject[0]))if("move".equals(p.optString("op")))queue.remove(p);}
    void stopGesture(){if(gestures!=null)gestures.cancel();held=false;clearMovement();if(pad!=null){pad.pressed=false;pad.invalidate();}}
    void flushMotion(){
        if(connected&&(Math.abs(pendingX)>0.01||Math.abs(pendingY)>0.01)){
            try{if(!queue.offer(new JSONObject().put("op","move").put("x",pendingX).put("y",pendingY)))throw new IOException("Full queue");}
            catch(Exception e){disconnect();status.setText("Connection too slow. Connect again.");}
        }
        pendingX=pendingY=0;
    }
    void send(String op,int amount){
        if(!connected)return;flushMotion();if(!connected)return;
        try{JSONObject p=new JSONObject().put("op",op);if(op.equals("scroll")||op.equals("hscroll")||op.equals("zoom"))p.put("amount",amount);
            if(!queue.offer(p)){disconnect();status.setText("Connection too slow. Connect again.");}}
        catch(Exception ignored){}
    }
    void disconnect(){
        connected=false;held=false;gestures.cancel();clearMovement();
        synchronized(linkLock){generation++;Socket old=socket;socket=null;if(old!=null)try{old.close();}catch(Exception ignored){}}
        queue.clear();connectButton.setText("Connect");connectButton.setEnabled(true);pad.pressed=false;pad.invalidate();
    }
    EditText input(String hint,String value,int type){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine();e.setInputType(type);return e;}
    void connectionDialog(){
        stopGesture();modal=true;
        if(connected){AlertDialog activeDialog=new AlertDialog.Builder(this).setTitle("Connected to laptop").setMessage(getPreferences(0).getString("host","")).setPositiveButton("Disconnect",(d,w)->{disconnect();status.setText("Disconnected");}).setNegativeButton("Keep connected",null).create();activeDialog.setOnDismissListener(d->{modal=false;motion.reset();});activeDialog.show();return;}
        LinearLayout form=column();form.setPadding(dp(22),dp(8),dp(22),dp(8));
        TextView guide=text("Start the updated receiver on your laptop. Use the same private Wi-Fi.",14,MUTED);form.addView(guide);
        EditText host=input("Laptop IPv4 address",getPreferences(0).getString("host",""),17);form.addView(host);
        EditText code=input("6-digit pairing code","",2);form.addView(code);
        EditText port=input("Port",getPreferences(0).getString("port","8765"),2);form.addView(port);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Connect your laptop").setView(form).setPositiveButton("Connect",null).setNegativeButton("Cancel",null).create();
        dialog.setOnDismissListener(d->{modal=false;motion.reset();});
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String address=host.getText().toString().trim(),pin=code.getText().toString().trim();int number;
            try{number=Integer.parseInt(port.getText().toString());String[] parts=address.split("\\.");if(parts.length!=4||!pin.matches("[0-9]{6}")||number<1||number>65535)throw new Exception();for(String part:parts)if(!part.matches("[0-9]{1,3}")||Integer.parseInt(part)>255)throw new Exception();}
            catch(Exception e){code.setError("Check address, code and port");return;}
            dialog.dismiss();connect(address,pin,number);
        }));dialog.show();
    }
    void settingsDialog(){
        stopGesture();modal=true;ScrollView scroll=new ScrollView(this);LinearLayout body=column();body.setPadding(dp(22),dp(8),dp(22),dp(12));scroll.addView(body);
        body.addView(text("Pointer sensitivity",14,TEXT));SeekBar sensitivityBar=new SeekBar(this);sensitivityBar.setMax(275);sensitivityBar.setProgress(Math.round((sensitivity-0.25f)*100));body.addView(sensitivityBar);
        sensitivityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int n,boolean u){sensitivity=0.25f+n/100f;getPreferences(0).edit().putFloat("sensitivity",sensitivity).apply();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        Switch naturalSwitch=new Switch(this);naturalSwitch.setText("Natural scrolling");naturalSwitch.setChecked(natural);body.addView(naturalSwitch);naturalSwitch.setOnCheckedChangeListener((v,c)->{natural=c;gestures.natural=c;getPreferences(0).edit().putBoolean("natural",c).apply();});
        Switch holdSwitch=new Switch(this);holdSwitch.setText("Air: hold pad to move (recommended)");holdSwitch.setChecked(holdToMove);body.addView(holdSwitch);holdSwitch.setOnCheckedChangeListener((v,c)->{holdToMove=c;clearMovement();getPreferences(0).edit().putBoolean("hold",c).apply();pad.invalidate();});
        body.addView(text("Air mouse grip",14,TEXT));Spinner grip=new Spinner(this);grip.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Automatic (upright or flat)","Upright — screen faces you","Flat — screen faces up"}));grip.setSelection(motion.grip);body.addView(grip);
        grip.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int n,long id){motion.grip=n;motion.reset();getPreferences(0).edit().putInt("grip",n).apply();}public void onNothingSelected(AdapterView<?> p){}});
        Switch ix=new Switch(this);ix.setText("Reverse air left / right");ix.setChecked(motion.invertX);body.addView(ix);ix.setOnCheckedChangeListener((v,c)->{motion.invertX=c;getPreferences(0).edit().putBoolean("invertX",c).apply();});
        Switch iy=new Switch(this);iy.setText("Reverse air up / down");iy.setChecked(motion.invertY);body.addView(iy);iy.setOnCheckedChangeListener((v,c)->{motion.invertY=c;getPreferences(0).edit().putBoolean("invertY",c).apply();});
        Button center=button("Recenter pointer",()->{stopGesture();send("center",0);});body.addView(center);
        Button calibrate=button("Calibrate air sensor",()->calibrate());LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(44));cp.setMargins(0,dp(8),0,dp(8));body.addView(calibrate,cp);
        body.addView(text("Tap = left click\nTap twice quickly = double click\nTwo-finger tap = right click\nTwo-finger slide = scroll (vertical or horizontal)\nPinch apart / together = zoom in / out\nTouchpad: hold still, then slide = drag\nTouchpad: tap, then tap-and-hold / slide = drag\nLift or cancel = release drag\nAir: hold anywhere on pad and rotate phone; release to reposition.",13,MUTED));
        body.addView(text("Sensor: "+(motionSensor==null?"unavailable":motionSensor.getName())+"\nZoom uses Ctrl + wheel; support depends on the laptop app.",12,MUTED));
        AlertDialog settingsDialog=new AlertDialog.Builder(this).setTitle("Settings & gestures").setView(scroll).setPositiveButton("Done",null).create();settingsDialog.setOnDismissListener(d->{modal=false;motion.reset();});settingsDialog.show();
    }
    void calibrate(){
        clearMovement();if(motionSensor==null){status.setText("No supported motion sensor.");return;}
        if(motionSensor.getType()!=Sensor.TYPE_GYROSCOPE){status.setText("Air reference reset. Hold pad, then rotate phone.");return;}
        calibrating=true;calStart=System.nanoTime();calSamples=0;for(int n=0;n<3;n++)calSum[n]=0;status.setText("Keep phone still for one second...");
    }
    void connect(String address,String pin,int port){
        disconnect();final int ticket=generation;connectButton.setEnabled(false);status.setText("Connecting...");
        new Thread(()->{Socket local=new Socket();try{
            synchronized(linkLock){if(ticket!=generation)return;socket=local;}
            local.connect(new InetSocketAddress(address,port),3000);local.setTcpNoDelay(true);local.setSoTimeout(3000);
            BufferedReader in=new BufferedReader(new InputStreamReader(local.getInputStream(),"UTF-8"));BufferedWriter out=new BufferedWriter(new OutputStreamWriter(local.getOutputStream(),"UTF-8"));
            JSONObject paired=exchange(out,in,new JSONObject().put("op","pair").put("pin",pin));if(paired.optInt("protocol",1)<2)throw new IOException("Start the updated v2 receiver");
            synchronized(linkLock){if(ticket!=generation)return;connected=true;}
            ui.post(()->{if(ticket!=generation)return;connectButton.setText("Connected");connectButton.setEnabled(true);status.setText(air?"Hold pad, then rotate phone":"Connected · Ready to use");getPreferences(0).edit().putString("host",address).putString("port",String.valueOf(port)).apply();pad.invalidate();});
            while(ticket==generation&&connected){JSONObject p=queue.poll(1,java.util.concurrent.TimeUnit.SECONDS);if(p==null)p=new JSONObject().put("op","ping");exchange(out,in,p);}
        }catch(Exception e){final String message=e.getMessage();ui.post(()->{if(ticket==generation){disconnect();status.setText("Connection ended: "+message);}});}finally{try{local.close();}catch(Exception ignored){}}},"receiver-link").start();
    }
    JSONObject exchange(BufferedWriter out,BufferedReader in,JSONObject packet)throws Exception{
        out.write(packet.toString());out.write("\n");out.flush();String line=in.readLine();if(line==null)throw new IOException("Receiver stopped");JSONObject reply=new JSONObject(line);if(!reply.optBoolean("ok"))throw new IOException(reply.optString("error","Update the receiver"));return reply;
    }
    void startSensor(int from){
        sensors.unregisterListener(this);motionSensor=null;gravitySensor=null;motion.reset();sensorStart=SystemClock.elapsedRealtime();lastSensor=0;
        for(int n=from;n<sensorTypes.length;n++){Sensor candidate=sensors.getDefaultSensor(sensorTypes[n]);if(candidate!=null&&sensors.registerListener(this,candidate,SensorManager.SENSOR_DELAY_GAME)){motionSensor=candidate;fallbackIndex=n;break;}}
        if(motionSensor!=null&&motionSensor.getType()==Sensor.TYPE_GYROSCOPE){gravitySensor=sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);if(gravitySensor==null)gravitySensor=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);if(gravitySensor!=null)sensors.registerListener(this,gravitySensor,SensorManager.SENSOR_DELAY_GAME);}
    }
    @Override public void onSensorChanged(SensorEvent e){
        if(motionSensor==null)return;
        if(gravitySensor!=null&&e.sensor.getType()==gravitySensor.getType()){motion.gravity(e.values[0],e.values[1],e.values[2]);return;}
        if(e.sensor.getType()!=motionSensor.getType())return;
        lastSensor=SystemClock.elapsedRealtime();sensorCount++;
        if(calibrating&&e.sensor.getType()==Sensor.TYPE_GYROSCOPE){for(int n=0;n<3;n++)calSum[n]+=e.values[n];calSamples++;if(System.nanoTime()-calStart>1000000000L){for(int n=0;n<3;n++)motion.bias[n]=calSum[n]/calSamples;calibrating=false;status.setText("Calibration complete");motion.reset();}return;}
        motion.sensitivity=sensitivity;boolean active=air&&connected&&!modal&&!calibrating&&(holdToMove?held:!gestures.down||!gestures.multi);
        double[] delta;
        if(e.sensor.getType()==Sensor.TYPE_GYROSCOPE)delta=motion.gyro(e.timestamp,e.values[0],e.values[1],e.values[2],active);
        else{float[] q=new float[4];SensorManager.getQuaternionFromVector(q,e.values);delta=motion.quaternion(e.timestamp,q[0],q[1],q[2],q[3],active);}
        if(active){pendingX+=delta[0];pendingY+=delta[1];gestures.airMotion(delta[0],delta[1]);}
    }
    @Override public void onAccuracyChanged(Sensor s,int accuracy){}
    @Override protected void onResume(){super.onResume();resumed=true;startSensor(0);}
    @Override protected void onPause(){resumed=false;sensors.unregisterListener(this);calibrating=false;disconnect();status.setText("Paused · Connect again to resume");super.onPause();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);super.onDestroy();}

    class PadView extends View {
        final Paint paint=new Paint(3);boolean pressed=false,handling=false;float fingerX,fingerY;
        PadView(){super(MainActivity.this);setClickable(true);setContentDescription("Trackpad. Tap to click, two-finger tap for right click, two fingers to scroll, pinch to zoom. In air mode hold and rotate phone.");setBackground(shape(SURFACE,0xff26364a));setElevation(dp(1));}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float w=getWidth(),h=getHeight();paint.setColor(0xff233044);
            for(int x=dp(20);x<w;x+=dp(22))for(int y=dp(20);y<h;y+=dp(22))c.drawCircle(x,y,dp(0.65f),paint);
            float cx=w/2,cy=h/2-dp(18);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.5f));paint.setColor(pressed&&connected?ACCENT:0xff3b5068);c.drawCircle(cx,cy,dp(30),paint);
            if(air){c.drawLine(cx-dp(14),cy,cx+dp(14),cy,paint);c.drawLine(cx,cy-dp(14),cx,cy+dp(14),paint);}else{c.drawRoundRect(cx-dp(10),cy-dp(16),cx+dp(10),cy+dp(16),dp(7),dp(7),paint);c.drawLine(cx,cy-dp(15),cx,cy-dp(3),paint);}
            paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(TEXT);paint.setTextSize(dp(19));c.drawText(!connected?"Ready when you are":air?held?"Moving with you":holdToMove?"Hold to move":"Air mouse is active":"Your trackpad",cx,cy+dp(60),paint);
            paint.setColor(MUTED);paint.setTextSize(dp(12));c.drawText(!connected?"Tap Connect above":air?"Rotate left / right · Tilt up / down":"Move · Click · Scroll · Zoom",cx,cy+dp(84),paint);
            if(pressed){paint.setColor(0x408cf0c2);c.drawCircle(fingerX,fingerY,dp(23),paint);}
        }
        @Override public boolean performClick(){if(!handling)send("left",0);return super.performClick();}
        @Override public boolean onTouchEvent(MotionEvent e){
            if(!connected){if(e.getActionMasked()==MotionEvent.ACTION_UP)connectionDialog();return true;}
            getParent().requestDisallowInterceptTouchEvent(true);handling=true;float density=getResources().getDisplayMetrics().density;int a=e.getActionMasked(),count=e.getPointerCount();long t=e.getEventTime();
            fingerX=e.getX();fingerY=e.getY();
            if(a==MotionEvent.ACTION_DOWN){clearMovement();gestures.begin(t,e.getX()/density,e.getY()/density);pressed=true;held=air;}
            else if(a==MotionEvent.ACTION_POINTER_DOWN){held=false;clearMovement();if(count==2){double cx=(e.getX(0)+e.getX(1))/2/density,cy=(e.getY(0)+e.getY(1))/2/density;gestures.second(t,cx,cy,span(e)/density);}else gestures.extraFinger();}
            else if(a==MotionEvent.ACTION_MOVE){double cx=count>=2?(e.getX(0)+e.getX(1))/2/density:0,cy=count>=2?(e.getY(0)+e.getY(1))/2/density:0;gestures.motion(t,e.getX()/density,e.getY()/density,count,cx,cy,count>=2?span(e)/density:1);}
            else if(a==MotionEvent.ACTION_POINTER_UP){int remaining=e.getActionIndex()==0?1:0;gestures.fingerLift(e.getX(remaining)/density,e.getY(remaining)/density);held=false;}
            else if(a==MotionEvent.ACTION_UP){gestures.motion(t,e.getX()/density,e.getY()/density,1,0,0,1);gestures.end(t);held=false;pressed=false;if(air)clearMovement();performClick();}
            else if(a==MotionEvent.ACTION_CANCEL){gestures.cancel();held=false;pressed=false;clearMovement();}
            handling=false;invalidate();return true;
        }
        float span(MotionEvent e){return (float)Math.hypot(e.getX(1)-e.getX(0),e.getY(1)-e.getY(0));}
    }
}
