package com.codex.blacklight.client.renderer;

import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Exact closure of surviving joints, after collision and interpolation. Cut edges are absent. */
final class RagdollJointProjection {
    record Link(int parent,int child,Vec3 parentAnchor,Vec3 childAnchor) {}
    record Pose(Vec3 position,Quaternionf rotation,float scale) {}
    record Result(Vec3[] positions,int[] components) {
        void lift(double[] required){
            double[] maximum=new double[positions.length];
            for(int i=0;i<positions.length;i++)maximum[components[i]]=Math.max(maximum[components[i]],required[i]);
            for(int i=0;i<positions.length;i++)positions[i]=positions[i].add(0,maximum[components[i]],0);
        }
    }
    static Result close(List<Pose> poses,List<Link> links){
        int n=poses.size();Vec3[] positions=new Vec3[n];int[] components=new int[n];
        var children=new ArrayList<List<Link>>(n);boolean[] hasParent=new boolean[n];
        for(int i=0;i<n;i++){children.add(new ArrayList<>());positions[i]=poses.get(i).position;components[i]=i;}
        for(var l:links){children.get(l.parent).add(l);hasParent[l.child]=true;}
        var queue=new ArrayDeque<Integer>();boolean[] visited=new boolean[n];
        for(int i=0;i<n;i++)if(!hasParent[i]){queue.add(i);visited[i]=true;}
        while(!queue.isEmpty()){
            int p=queue.removeFirst();
            for(var l:children.get(p))if(!visited[l.child]){
                var parent=poses.get(p);var child=poses.get(l.child);
                positions[l.child]=positions[p].add(rotate(l.parentAnchor.scale(parent.scale),parent.rotation))
                        .subtract(rotate(l.childAnchor.scale(child.scale),child.rotation));
                components[l.child]=components[p];visited[l.child]=true;queue.add(l.child);
            }
        }
        return new Result(positions,components);
    }
    private static Vec3 rotate(Vec3 v,Quaternionf rotation){return new Vec3(rotation.transform(v.toVector3f()));}
    private RagdollJointProjection(){}
}
