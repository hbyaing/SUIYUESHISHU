package dev.minggulong.client;

import java.util.ArrayList;
import java.util.List;

/** Deterministic world-space effect, seconds; no game dependencies or entity motion. */
public final class HuangWuGeometry {
    public static final double DURATION = 4.32;
    public record V(double x,double y,double z) {
        V add(V b){return new V(x+b.x,y+b.y,z+b.z);}
        V mul(double s){return new V(x*s,y*s,z*s);}
        V cross(V b){return new V(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}
        V unit(){return mul(1/Math.max(1e-9,Math.sqrt(x*x+y*y+z*z)));}
    }
    public record C(float r,float g,float b,float a) {
        C alpha(double s){return new C(r,g,b,(float)(a*s));}
    }
    public record Tri(V a,V b,V c,C color,C colorB,C colorC,boolean solid) {
        public Tri(V a,V b,V c,C color,boolean solid){this(a,b,c,color,color,color,solid);}
    }
    static final C ICE=new C(.35f,.92f,1,1), WHITE=new C(.92f,1,1,1);
    static final C PINK=new C(1,.045f,.39f,1), RED=new C(.52f,.008f,.095f,1);
    static final C DARK=new C(.008f,.002f,.018f,1);
    static double clamp(double x){return Math.max(0,Math.min(1,x));}
    static double smooth(double a,double b,double t){double v=clamp((t-a)/(b-a));return v*v*(3-2*v);}
    static C mix(C a,C b,double k){return new C((float)(a.r+(b.r-a.r)*k),(float)(a.g+(b.g-a.g)*k),(float)(a.b+(b.b-a.b)*k),(float)(a.a+(b.a-a.a)*k));}
    static V rotate(V v,double tilt){return new V(v.x,v.y*Math.cos(tilt)-v.z*Math.sin(tilt),v.y*Math.sin(tilt)+v.z*Math.cos(tilt));}
    static void quad(List<Tri> out,V a,V b,V c,V d,C color,boolean solid){out.add(new Tri(a,b,c,color,solid));out.add(new Tri(a,c,d,color,solid));}
    static void gradientQuad(List<Tri> out,V a,V b,V c,V d,C inner,C outer){out.add(new Tri(a,b,c,inner,inner,outer,false));out.add(new Tri(a,c,d,inner,outer,outer,false));}
    static V spherical(double lat,double lon,double r){return new V(Math.cos(lat)*Math.cos(lon)*r,Math.sin(lat)*r,Math.cos(lat)*Math.sin(lon)*r);}
    static void sphere(List<Tri> o,double r,C color,boolean solid){
        for(int j=0;j<16;j++)for(int i=0;i<40;i++) {
            double a=-Math.PI/2+j*Math.PI/16,b=a+Math.PI/16,u=i*Math.PI*2/40,v=u+Math.PI*2/40;
            quad(o,spherical(a,u,r),spherical(b,u,r),spherical(b,v,r),spherical(a,v,r),color,solid);
        }
    }
    // Plane ring, with real back/front geometry and a soft cross-sectional opacity profile.
    static void ring(List<Tri> o,double radius,double width,double tilt,double t,C col,double alpha,int lanes){
        for(int k=0;k<lanes;k++)for(int i=0;i<128;i++) {
            double s0=(double)k/lanes,s1=(double)(k+1)/lanes,a=i*Math.PI*2/128,b=(i+1)*Math.PI*2/128;
            double waveA=1+.025*Math.sin(a*3-t*2)+.012*Math.sin(a*7+t*3),waveB=1+.025*Math.sin(b*3-t*2)+.012*Math.sin(b*7+t*3);
            double rr0=radius+(s0-.5)*width,rr1=radius+(s1-.5)*width;
            double plume=.77+.23*Math.sin(a*5-t*6+.5*Math.sin(a*9+t));
            C c0=col.alpha(alpha*Math.pow(Math.sin(Math.PI*s0),1.5)*plume),c1=col.alpha(alpha*Math.pow(Math.sin(Math.PI*s1),1.5)*plume);
            gradientQuad(o,rotate(new V(Math.cos(a)*rr0*waveA,0,Math.sin(a)*rr0*waveA),tilt),rotate(new V(Math.cos(b)*rr0*waveB,0,Math.sin(b)*rr0*waveB),tilt),rotate(new V(Math.cos(b)*rr1*waveB,0,Math.sin(b)*rr1*waveB),tilt),rotate(new V(Math.cos(a)*rr1*waveA,0,Math.sin(a)*rr1*waveA),tilt),c0,c1);
        }
    }
    static void ribbon(List<Tri> o,V[] pts,double width,C col,double alpha){
        V up=new V(.1,1,.17);
        for(int i=0;i<pts.length-1;i++) {
            V side=pts[i+1].add(pts[i].mul(-1)).cross(up).unit();
            double taper=Math.sin(Math.PI*(i+.5)/(pts.length-1));
            for(int k=0;k<7;k++) {
                double lo=((double)k/7-.5)*width*taper,hi=((double)(k+1)/7-.5)*width*taper;
                gradientQuad(o,pts[i].add(side.mul(lo)),pts[i+1].add(side.mul(lo)),pts[i+1].add(side.mul(hi)),pts[i].add(side.mul(hi)),col.alpha(alpha*taper*Math.pow(Math.sin(Math.PI*k/7),2)),col.alpha(alpha*taper*Math.pow(Math.sin(Math.PI*(k+1)/7),2)));
            }
        }
    }
    static void shard(List<Tri> o,V p,V dir,double size,C color){
        V d=dir.unit().mul(size),s=dir.cross(new V(.2,1,.3)).unit().mul(size*.30),v=d.cross(s).unit().mul(size*.18);
        V top=p.add(d),bot=p.add(d.mul(-.6));
        V[] belt={p.add(s),p.add(v),p.add(s.mul(-1)),p.add(v.mul(-1))};
        for(int k=0;k<4;k++){o.add(new Tri(top,belt[k],belt[(k+1)%4],color,false));o.add(new Tri(bot,belt[(k+1)%4],belt[k],color.alpha(.7),false));}
    }
    public static List<Tri> sample(double t){
        List<Tri> o=new ArrayList<>();if(t<0||t>=DURATION)return o;
        double gather=smooth(0,.85,t),hold=1-smooth(2.18,2.62,t),live=smooth(0,.18,t)*hold;
        double r=(.08+.91*gather)*(.10+.90*hold);
        if(hold>.001){
            C core=mix(WHITE,DARK,smooth(.65,1.15,t));sphere(o,r*.79,core,true);
            // A sloping, almost vertical cyan throat, like the reference, rather than a flat disk.
            double tilt=1.21;
            ring(o,r*.94,r*.20,tilt,t,ICE,live*.95,5);
            ring(o,r*.94,r*.052,tilt,t,WHITE,live,2);
            ring(o,r*1.02,r*.49,tilt,t,ICE,live*.18,10);
            ring(o,r*1.22,r*.65,tilt,t,PINK,live*.48,12);
            ring(o,r*1.42,r*1.12,tilt,t,RED,live*.35,12);
            for(int j=0;j<13;j++) {
                V[] pts=new V[42];double phase=j*2.399963+2.6*t;
                for(int i=0;i<pts.length;i++) {
                    double u=(double)i/(pts.length-1),radius=r*(.93+(1.3+.7*Math.sin(j*4.2)*Math.sin(j*4.2))*u),a=phase+u*3.5+.25*Math.sin(u*9-t*3+j);
                    pts[i]=rotate(new V(Math.cos(a)*radius,.38*Math.sin(u*7+phase)*u,Math.sin(a)*radius),tilt);
                }
                ribbon(o,pts,r*(j%3==0?.65:.38),j%3==0?PINK:RED,live*(j%3==0?.65:.70));
            }
            for(int j=0;j<64;j++) {
                double age=(t*.58+j*.6180339)%1,rad=r*(1+3*(1-age)),ang=j*2.399963+t*2.8+age*3.8;
                V pos=rotate(new V(Math.cos(ang)*rad,.38*Math.sin(j*1.7)*(1-age),Math.sin(ang)*rad),tilt);
                shard(o,pos,new V(-Math.sin(ang),.12,Math.cos(ang)),.018+.03*(j%4)/3.,(j%5==0?ICE:PINK).alpha(live*Math.sin(age*Math.PI)*.75));
            }
        }
        double flash=Math.exp(-Math.pow((t-2.60)/.085,2));
        if(flash>.01){sphere(o,.15+.65*flash,WHITE.alpha(flash),false);ring(o,.4+flash*.7,.55,1.21,t,PINK,flash*.6,7);}
        if(t>2.53){
            double e=t-2.53,fade=1-smooth(.65,1.65,e);
            ring(o,.13+4.2*e,.08+e*.045,1.21,t,ICE,fade*.8,4);
            ring(o,.10+3.6*e,.24,1.21+.18*e,t,PINK,fade*.52,6);
            for(int i=0;i<110;i++) {
                double y=1-2*(i+.5)/110.,a=i*2.399963,rr=Math.sqrt(1-y*y);
                V dir=new V(rr*Math.cos(a),y,rr*Math.sin(a));
                double speed=3.2+((i*73)%101)/30.;
                V pos=dir.mul(e*speed).add(new V(0,-.45*e*e,0));
                C c=(i%3==0?PINK:(i%4==0?WHITE:ICE)).alpha(fade*(.55+.45*Math.sin(i*7.1)*Math.sin(i*7.1)));
                shard(o,pos,dir,.04+.095*((i*11)%19)/19.,c);
            }
        }
        return o;
    }
}
