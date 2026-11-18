package dev.eavfx;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

final class EaTerrainCut {
    private static final TicketType<Long> TICKET=TicketType.create("starcrowndoll_ea_cut",Long::compare);
    private static final double STEP=4;
    private final long id;
    private final Vec3 origin,axis;
    private final ServerLevel level;
    private final List<ChunkPos> tickets=new ArrayList<>(4);
    private double start;

    EaTerrainCut(long id,ServerLevel level,Vec3 origin,Vec3 axis){
        this.id=id;
        this.level=level;
        this.origin=origin;
        this.axis=axis;
    }

    boolean advance(ServerPlayer player){
        if(!player.getAbilities().mayBuild){close();return true;}
        var chunks=level.getChunkSource();
        for(int count=0;count<3&&start<EaServer.BEAM_LENGTH;count++){
            double end=Math.min(start+STEP,EaServer.BEAM_LENGTH);
            Vec3 from=origin.add(axis.scale(start)),to=origin.add(axis.scale(end));
            double xExtent=extent(axis.x),yExtent=extent(axis.y),zExtent=extent(axis.z);
            int minX=Mth.floor(Math.min(from.x,to.x)-xExtent),maxX=Mth.floor(Math.max(from.x,to.x)+xExtent);
            int minY=Math.max(level.getMinBuildHeight(),Mth.floor(Math.min(from.y,to.y)-yExtent));
            int maxY=Math.min(level.getMaxBuildHeight()-1,Mth.floor(Math.max(from.y,to.y)+yExtent));
            int minZ=Mth.floor(Math.min(from.z,to.z)-zExtent),maxZ=Mth.floor(Math.max(from.z,to.z)+zExtent);
            if(minY<=maxY){
                if(tickets.isEmpty()){
                    for(int cx=minX>>4;cx<=maxX>>4;cx++)for(int cz=minZ>>4;cz<=maxZ>>4;cz++){
                        var chunk=new ChunkPos(cx,cz);
                        if(!level.getWorldBorder().isWithinBounds(chunk))continue;
                        chunks.addRegionTicket(TICKET,chunk,0,id);
                        tickets.add(chunk);
                    }
                }
                for(ChunkPos chunk:tickets)if(chunks.getChunkNow(chunk.x,chunk.z)==null)return false;
                var pos=new BlockPos.MutableBlockPos();
                for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=minY;y<=maxY;y++){
                    if(!contains(origin,axis,x+.5,y+.5,z+.5,start,end))continue;
                    pos.set(x,y,z);
                    if(!level.getWorldBorder().isWithinBounds(pos)||!level.mayInteract(player,pos))continue;
                    var state=level.getBlockState(pos);
                    if(state.isAir()||state.getDestroySpeed(level,pos)<0)continue;
                    if(MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,pos.immutable(),state,player)))continue;
                    level.destroyBlock(pos,false,player);
                }
                close();
            }
            start=end;
        }
        return start>=EaServer.BEAM_LENGTH;
    }

    static boolean contains(Vec3 origin,Vec3 axis,double x,double y,double z,double from,double to){
        double dx=x-origin.x,dy=y-origin.y,dz=z-origin.z;
        double axial=dx*axis.x+dy*axis.y+dz*axis.z;
        if(axial<from||axial>=to)return false;
        double radius=EaServer.BEAM_RADIUS;
        return dx*dx+dy*dy+dz*dz-axial*axial<=radius*radius;
    }

    private static double extent(double component){
        return EaServer.BEAM_RADIUS*Math.sqrt(Math.max(0,1-component*component));
    }

    void close(){
        for(ChunkPos chunk:tickets)level.getChunkSource().removeRegionTicket(TICKET,chunk,0,id);
        tickets.clear();
    }
}
