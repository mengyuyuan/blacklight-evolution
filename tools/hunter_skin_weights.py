"""Anatomical two-joint weights; no cross-chain thigh/chest influence.

Repair only: preserve existing continuous silhouette, topology and pixel atlas.
The reference is the current authored hunched hunter (head ~1:5 body height,
long forearms, digitigrade hind legs). Primary masses stay rigid; blend only
adjacent joints. No geometry reconstruction or additional surface noise.
"""
import math

def weight_vertex(point, volumes, parents):
    scores={}
    for center,scale,bone,inverse in volumes:
        p=[point[i]-center[i] for i in range(3)]
        local=[sum(inverse[i][j]*p[j] for j in range(3)) for i in range(3)]
        distance=sum((local[i]/max(1,scale[i]))**2 for i in range(3))
        scores[bone]=max(scores.get(bone,0),math.exp(-distance*3.6))
    primary=max(scores,key=scores.get)
    adjacent=[n for n in scores if parents.get(n)==primary or parents.get(primary)==n]
    secondary=max(adjacent,key=scores.get) if adjacent else primary
    total=scores[primary]+scores[secondary]
    w=scores[secondary]/total if total>1e-20 and secondary!=primary else 0
    return [primary,secondary,w]
