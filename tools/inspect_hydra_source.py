"""Read-only source-space diagnostic; all import bytecode disabled by the driver."""
from export_encounter_source_motion import *
path=P1/'Hydra.p3d';all_nodes=nodes(path);rig=skeleton(all_nodes,'hydra_skeleton')
for name in ['hyd_act_idle','hyd_slam','hyd_sweep','hyd_sidesweep']:
 clip=decoder.decode(next(n for n in all_nodes if n['type']==0x121000 and decoder.text8(n['payload'],4)[0]==name))
 print(name,clip['frames'])
 for t in [0,20,30,33,35,44,50,60,70,80,90]:
  if t>=clip['frames']:continue
  w=world(rig,clip,t)
  print(t,{n:np.round(w[n][:3,3],3).tolist() for n in ['Character_Root','full_rig_body1','full_rig_body3','Head','Claw_L','Claw_R']})
