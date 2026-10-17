import json, numpy as np
from pathlib import Path
root=Path(__file__).resolve().parent/'model-v15'
data=json.loads((root/'sovereign_wings.asset.json').read_text())
def rotation(q):
    x,y,z,w=q;return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
def evaluate(poses):
    transforms={};pivots={};skin={}
    for b in data['bones']:
        n=b['name'];p=np.array(b['pivot']);parent=b['parent'];m=np.eye(4);m[:3,3]=p-(pivots[parent] if parent else 0);m[:3,:3]=rotation(poses[n])
        transforms[n]=(transforms[parent] if parent else np.eye(4))@m;pivots[n]=p
        skin[n]=transforms[n].copy();skin[n][:3,3]-=skin[n][:3,:3]@p
    lowest=[]
    for e in data['elements']:
        minimum=1e9
        for key,p in e['vertices'].items():
            a,b,t=e['_weights'][key];v=np.array(p+[1]);y=((skin[a]@v)*(1-t)+(skin[b]@v)*t)[1];minimum=min(minimum,y)
        lowest.append((minimum,e['name']))
    return sorted(lowest)
if __name__=='__main__':
    for name,pose in data['fold_poses'].items():print(name,evaluate(pose)[:10])
