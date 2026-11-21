"""Minecraft pixel dimensions for the three joint-driven plant heads."""
import math

def segment_shape(index, suffix, pivot):
    # Five nested bark cuffs support the bud. Three tapered outer petals
    # belong to separate jaw drivers and open with the head animation.
    if suffix.startswith('Jaw_'):
        angle=(index/3+(int(suffix[-1])-1)/3)*math.tau
        dx,dz=math.cos(angle),math.sin(angle)
        size=[5.5+abs(dz)*4,13,5.5+abs(dx)*4]
        offset=[dx*7.5,6,dz*7.5]
    elif suffix=='Head':
        size,offset=[15,12,15],[0,3,0]
    else:
        rung=0 if suffix.endswith('Base') else int(suffix[-1])
        radii=[11,12,11,10,9,8]
        heights=[11,17,18,18,18,17]
        size=[radii[rung]*2,heights[rung],radii[rung]*1.8]
        offset=[0,heights[rung]/2-2,0]
    origin=[round(pivot[k]+offset[k]-size[k]/2,6) for k in range(3)]
    return origin,size
