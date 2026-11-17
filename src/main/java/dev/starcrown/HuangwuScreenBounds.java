package dev.starcrown;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Conservative projected box, clipped against the near plane before perspective division. */
record HuangwuScreenBounds(int x,int y,int width,int height) {
    static HuangwuScreenBounds project(Vector3f lo,Vector3f hi,Matrix4f matrix,Matrix4f projection,int width,int height){
        Matrix4f clip=new Matrix4f(projection).mul(matrix);Vector4f[] p=new Vector4f[8];
        for(int i=0;i<8;i++)p[i]=clip.transform(new Vector4f((i&1)==0?lo.x:hi.x,(i&2)==0?lo.y:hi.y,(i&4)==0?lo.z:hi.z,1));
        for(int plane=0;plane<6;plane++){
            boolean outside=true;
            for(Vector4f v:p){float d=switch(plane){case 0->v.x+v.w;case 1->v.w-v.x;case 2->v.y+v.w;case 3->v.w-v.y;case 4->v.z+v.w;default->v.w-v.z;};if(d>=0){outside=false;break;}}
            if(outside)return null;
        }
        float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
        for(int i=0;i<8;i++){
            Vector4f a=p[i];
            if(a.z+a.w>=0&&a.w>1e-6){float x=a.x/a.w,y=a.y/a.w;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
            for(int axis=1;axis<=4;axis<<=1){
                int j=i^axis;if(j<i)continue;Vector4f b=p[j];float da=a.z+a.w,db=b.z+b.w;
                if((da<0)==(db<0))continue;
                Vector4f v=new Vector4f(a).lerp(b,da/(da-db));if(v.w<=1e-6)continue;
                float x=v.x/v.w,y=v.y/v.w;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            }
        }
        if(!Float.isFinite(minX))return null;
        minX=Math.max(-1,minX);minY=Math.max(-1,minY);maxX=Math.min(1,maxX);maxY=Math.min(1,maxY);
        int x=Math.max(0,(int)Math.floor((minX*.5f+.5f)*width)-3),y=Math.max(0,(int)Math.floor((minY*.5f+.5f)*height)-3);
        int right=Math.min(width,(int)Math.ceil((maxX*.5f+.5f)*width)+3),top=Math.min(height,(int)Math.ceil((maxY*.5f+.5f)*height)+3);
        return right>x&&top>y?new HuangwuScreenBounds(x,y,right-x,top-y):null;
    }
    HuangwuScreenBounds scaled(int sourceWidth,int sourceHeight,int width,int height){
        int left=(int)Math.floor((double)x*width/sourceWidth),bottom=(int)Math.floor((double)y*height/sourceHeight);
        int right=(int)Math.ceil((double)(x+this.width)*width/sourceWidth),top=(int)Math.ceil((double)(y+this.height)*height/sourceHeight);
        return new HuangwuScreenBounds(left,bottom,right-left,top-bottom);
    }
}
