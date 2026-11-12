import dev.minggulong.client.HuangWuGeometry;
import java.io.*;
import java.nio.*;
import java.nio.file.*;

/** Shared Forge geometry capture for offline preview; little-endian float triangles. */
public class HuangWuExport {
    static void integer(OutputStream out,int i)throws IOException {out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(i).array());}
    public static void main(String[] args)throws Exception {
        try(var out=new BufferedOutputStream(Files.newOutputStream(Path.of(args[0])))) {
            int count=109;integer(out,count);
            for(int k=0;k<count;k++) {
                var mesh=HuangWuGeometry.sample(k*.04);
                for(boolean solid:new boolean[]{true,false}) {
                    var tris=mesh.stream().filter(x->x.solid()==solid).toList();integer(out,tris.size()*3);
                    ByteBuffer b=ByteBuffer.allocate(tris.size()*3*28).order(ByteOrder.LITTLE_ENDIAN);
                    for(var tri:tris)for(int i=0;i<3;i++) {
                        var v=new HuangWuGeometry.V[]{tri.a(),tri.b(),tri.c()}[i];
                        b.putFloat((float)v.x()).putFloat((float)v.y()).putFloat((float)v.z());
                        var c=new HuangWuGeometry.C[]{tri.color(),tri.colorB(),tri.colorC()}[i];b.putFloat(c.r()).putFloat(c.g()).putFloat(c.b()).putFloat(c.a());
                    }
                    out.write(b.array());
                }
            }
        }
    }
}
