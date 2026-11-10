package com.codex.blacklight.infection;

import java.util.*;

/** Turns smooth authored growth (horns, crests, plates) into Minecraft-style
 * voxel blocks on the host's pixel grid. Pure geometry: no game classes.
 * Surfaces are rasterised, closed shells are filled, and only exposed faces
 * are emitted, merged greedily per colour so the triangle count stays low. */
public final class TissueVoxels {
    /** One output triangle: 9 coordinates, outward normal and RGB colour. */
    public record Tri(float[] p,float nx,float ny,float nz,int color){}
    private static final int MAX_CELLS=160_000;
    private TissueVoxels(){}

    /**
     * @param tris  9 floats per triangle (model pixel units)
     * @param colors RGB per triangle
     * @param low/high the native cube that owns the growth
     * @param minProtrusion components staying within this distance of the cube stay smooth (wound beds)
     */
    public static List<Tri> convert(float[][] tris,int[] colors,float[] low,float[] high,float minProtrusion,long seed){
        int n=tris.length;if(n==0)return List.of();
        // Connected components by shared vertex positions.
        int[] parent=new int[n];for(int i=0;i<n;i++)parent[i]=i;
        Map<Long,Integer> owner=new HashMap<>();
        for(int t=0;t<n;t++)for(int j=0;j<3;j++){
            long key=key(tris[t][j*3],tris[t][j*3+1],tris[t][j*3+2]);
            Integer o=owner.putIfAbsent(key,t);if(o!=null)union(parent,o,t);
        }
        Map<Integer,List<Integer>> groups=new TreeMap<>();
        for(int t=0;t<n;t++)groups.computeIfAbsent(find(parent,t),k->new ArrayList<>()).add(t);
        float cell=Math.min(high[0]-low[0],Math.min(high[1]-low[1],high[2]-low[2]))>=3.5F?1:.5F;
        var result=new ArrayList<Tri>();var blocky=new ArrayList<Integer>();
        for(var group:groups.values()){
            float out=0;
            for(int t:group)for(int j=0;j<3;j++)for(int a=0;a<3;a++){
                float v=tris[t][j*3+a];out=Math.max(out,Math.max(low[a]-v,v-high[a]));
            }
            if(out>=minProtrusion)blocky.addAll(group);
            else for(int t:group)result.add(smooth(tris[t],colors[t]));
        }
        if(!blocky.isEmpty())result.addAll(voxels(tris,colors,blocky,low,cell,seed));
        return result;
    }
    private static Tri smooth(float[] p,int color){
        float ax=p[3]-p[0],ay=p[4]-p[1],az=p[5]-p[2],bx=p[6]-p[0],by=p[7]-p[1],bz=p[8]-p[2];
        float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx,l=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(l<1e-9F)l=1;return new Tri(p.clone(),nx/l,ny/l,nz/l,color);
    }
    private static List<Tri> voxels(float[][] tris,int[] colors,List<Integer> group,float[] cube,float cell,long seed){
        float[] min={Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE},max={-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE};
        for(int t:group)for(int j=0;j<3;j++)for(int a=0;a<3;a++){min[a]=Math.min(min[a],tris[t][j*3+a]);max[a]=Math.max(max[a],tris[t][j*3+a]);}
        // Grid aligned to the owning cube, so blocks sit on its native pixels.
        // A tiny inward bias keeps a block face flush with the hide from fighting it.
        float[] origin=new float[3];int[] size=new int[3];
        for(int a=0;a<3;a++){
            origin[a]=cube[a]+.003F+(float)Math.floor((min[a]-cube[a])/cell)*cell-cell;
            size[a]=(int)Math.ceil((max[a]-origin[a])/cell)+2;
        }
        while((long)size[0]*size[1]*size[2]>MAX_CELLS){
            cell*=2;
            for(int a=0;a<3;a++){origin[a]=cube[a]+.003F+(float)Math.floor((min[a]-cube[a])/cell)*cell-cell;size[a]=(int)Math.ceil((max[a]-origin[a])/cell)+2;}
        }
        int sx=size[0],sy=size[1],sz=size[2];int[] grid=new int[sx*sy*sz];Arrays.fill(grid,-1);
        for(int t:group){
            float[] p=tris[t];
            float e=Math.max(dist(p,0,1),Math.max(dist(p,1,2),dist(p,0,2)));
            int steps=Math.max(1,(int)Math.ceil(e/(cell*.35F)));
            for(int i=0;i<=steps;i++)for(int j=0;j<=steps-i;j++){
                float u=i/(float)steps,v=j/(float)steps,w=1-u-v;
                int x=(int)Math.floor((p[0]*w+p[3]*u+p[6]*v-origin[0])/cell);
                int y=(int)Math.floor((p[1]*w+p[4]*u+p[7]*v-origin[1])/cell);
                int z=(int)Math.floor((p[2]*w+p[5]*u+p[8]*v-origin[2])/cell);
                if(x<0||y<0||z<0||x>=sx||y>=sy||z>=sz)continue;
                int idx=(z*sy+y)*sx+x;if(grid[idx]<0)grid[idx]=colors[t]&0xffffff;
            }
        }
        // Flood the outside; any empty cell it cannot reach is solid interior.
        boolean[] outside=new boolean[grid.length];var queue=new ArrayDeque<Integer>();
        for(int z=0;z<sz;z++)for(int y=0;y<sy;y++)for(int x=0;x<sx;x++)
            if((x==0||y==0||z==0||x==sx-1||y==sy-1||z==sz-1)){int idx=(z*sy+y)*sx+x;if(grid[idx]<0&&!outside[idx]){outside[idx]=true;queue.add(idx);}}
        int[][] dirs={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        while(!queue.isEmpty()){
            int idx=queue.poll(),x=idx%sx,y=idx/sx%sy,z=idx/(sx*sy);
            for(int[] d:dirs){int X=x+d[0],Y=y+d[1],Z=z+d[2];if(X<0||Y<0||Z<0||X>=sx||Y>=sy||Z>=sz)continue;int k=(Z*sy+Y)*sx+X;if(grid[k]<0&&!outside[k]){outside[k]=true;queue.add(k);}}
        }
        var fill=new ArrayDeque<Integer>();
        for(int i=0;i<grid.length;i++)if(grid[i]>=0)fill.add(i);
        while(!fill.isEmpty()){
            int idx=fill.poll(),x=idx%sx,y=idx/sx%sy,z=idx/(sx*sy);
            for(int[] d:dirs){int X=x+d[0],Y=y+d[1],Z=z+d[2];if(X<0||Y<0||Z<0||X>=sx||Y>=sy||Z>=sz)continue;int k=(Z*sy+Y)*sx+X;if(grid[k]<0&&!outside[k]){grid[k]=grid[idx];fill.add(k);}}
        }
        // Pixel-art value noise: three steps per block, stable for the species.
        for(int i=0;i<grid.length;i++)if(grid[i]>=0){
            float s=TissueGrowth.sample(seed,i*7+13);float f=s<.28F?.86F:s>.80F?1.09F:1;
            grid[i]=scale(grid[i],f);
        }
        var out=new ArrayList<Tri>();
        for(int axis=0;axis<3;axis++)for(int sign=-1;sign<=1;sign+=2){
            int u=(axis+1)%3,v=(axis+2)%3;int[] dim={sx,sy,sz};
            int[] mask=new int[dim[u]*dim[v]];
            for(int layer=0;layer<dim[axis];layer++){
                Arrays.fill(mask,-1);
                for(int j=0;j<dim[v];j++)for(int i=0;i<dim[u];i++){
                    int[] c=new int[3];c[axis]=layer;c[u]=i;c[v]=j;
                    int here=grid[(c[2]*sy+c[1])*sx+c[0]];if(here<0)continue;
                    int[] nb=c.clone();nb[axis]+=sign;
                    boolean open=nb[axis]<0||nb[axis]>=dim[axis]||grid[(nb[2]*sy+nb[1])*sx+nb[0]]<0;
                    if(open)mask[j*dim[u]+i]=here;
                }
                for(int j=0;j<dim[v];j++)for(int i=0;i<dim[u];){
                    int color=mask[j*dim[u]+i];if(color<0){i++;continue;}
                    int w=1;while(i+w<dim[u]&&mask[j*dim[u]+i+w]==color)w++;
                    int h=1;grow:while(j+h<dim[v]){for(int k=0;k<w;k++)if(mask[(j+h)*dim[u]+i+k]!=color)break grow;h++;}
                    for(int y=0;y<h;y++)for(int k=0;k<w;k++)mask[(j+y)*dim[u]+i+k]=-1;
                    float plane=origin[axis]+(layer+(sign>0?1:0))*cell;
                    float u0=origin[u]+i*cell,u1=u0+w*cell,v0=origin[v]+j*cell,v1=v0+h*cell;
                    float[][] q={corner(axis,u,v,plane,u0,v0),corner(axis,u,v,plane,u1,v0),corner(axis,u,v,plane,u1,v1),corner(axis,u,v,plane,u0,v1)};
                    float[] n=new float[3];n[axis]=sign;
                    out.add(new Tri(new float[]{q[0][0],q[0][1],q[0][2],q[1][0],q[1][1],q[1][2],q[2][0],q[2][1],q[2][2]},n[0],n[1],n[2],color));
                    out.add(new Tri(new float[]{q[0][0],q[0][1],q[0][2],q[2][0],q[2][1],q[2][2],q[3][0],q[3][1],q[3][2]},n[0],n[1],n[2],color));
                    i+=w;
                }
            }
        }
        return out;
    }
    private static float[] corner(int axis,int u,int v,float plane,float a,float b){float[] p=new float[3];p[axis]=plane;p[u]=a;p[v]=b;return p;}
    private static float dist(float[] p,int i,int j){float x=p[i*3]-p[j*3],y=p[i*3+1]-p[j*3+1],z=p[i*3+2]-p[j*3+2];return (float)Math.sqrt(x*x+y*y+z*z);}
    private static int scale(int rgb,float f){
        int r=Math.min(255,Math.round((rgb>>16&255)*f)),g=Math.min(255,Math.round((rgb>>8&255)*f)),b=Math.min(255,Math.round((rgb&255)*f));
        return r<<16|g<<8|b;
    }
    private static long key(float x,float y,float z){
        long a=Math.round(x*2048),b=Math.round(y*2048),c=Math.round(z*2048);
        return (a&0x1fffff)<<42|(b&0x1fffff)<<21|(c&0x1fffff);
    }
    private static int find(int[] p,int i){while(p[i]!=i){p[i]=p[p[i]];i=p[i];}return i;}
    private static void union(int[] p,int a,int b){a=find(p,a);b=find(p,b);if(a!=b)p[Math.max(a,b)]=Math.min(a,b);}
}
