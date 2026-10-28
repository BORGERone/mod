#!/usr/bin/env python3
"""Inspect a separately supplied Player Extension pack; no assets copied/distributed.
This checks only a conservative static signature, NOT provenance or in-game execution.
"""
import argparse,json,pathlib,zipfile
PARTS=('body','head','right_arm','left_arm','right_leg','left_leg');CHANNELS=('rx','ry','rz','tx','ty','tz')
def audit(read,model):
 entries={}
 def collect(obj):
  if isinstance(obj,dict):
   if isinstance(obj.get('animations'),list):
    for group in obj['animations']:
     if isinstance(group,dict):
      for k,v in group.items():entries.setdefault(k,str(v))
   for v in obj.values():collect(v)
  elif isinstance(obj,list):
   for v in obj:collect(v)
 def traverse(name,seen):
  if name in seen:return
  seen.add(name);obj=json.loads(read(name).decode('utf-8-sig'))
  # Imported animations are evaluated before the model's top-level outputs.
  for m in obj.get('models',[]):
   if isinstance(m,dict) and isinstance(m.get('model'),str):traverse(m['model'],seen)
  collect(obj)
 traverse(model,set())
 issues=[]
 if entries.get('var.Created_by_FreshLX_for_Fresh_Animations','').strip()!='1':issues.append('creator marker')
 for side,part in [('r','right_leg'),('l','left_leg')]:
  key='var.mvmnt_'+side+'legrx';e=entries.get(key,'')
  if not all(x in e for x in ('limb_speed','var.run','var.walk','cos(')):issues.append(key)
  if key not in entries.get(part+'.rx',''):issues.append(part+'.rx is not connected')
 for p in PARTS:
  for c in CHANNELS:
   if p+'.'+c not in entries:issues.append(p+'.'+c)
 return issues

def main():
 q=argparse.ArgumentParser();q.add_argument('--pack',type=pathlib.Path,required=True,help='resource pack zip, root dir or assets/minecraft/emf/cem dir');a=q.parse_args();pack=a.pack
 if pack.is_dir():
  cem=pack/'assets/minecraft/emf/cem'
  if not cem.is_dir():cem=pack
  def read(name):return (cem/name).read_bytes()
  for model in ('player.jem','player_slim.jem'):
   try:issues=audit(read,model)
   except (OSError,ValueError) as e:raise SystemExit(f'{model}: missing/invalid file: {e}')
   if issues:raise SystemExit(f'{model}: signature not supported: {issues}')
   print(model,'signature OK (not proof of provenance or in-game execution)')
 else:
  with zipfile.ZipFile(pack) as z:
   files=z.namelist();suffix='assets/minecraft/emf/cem/player.jem';matches=[n for n in files if n.endswith(suffix) and '/21-2/' not in n and '/20-3/' not in n]
   if len(matches)!=1:raise SystemExit('expected one base assets/minecraft/emf/cem/player.jem')
   prefix=matches[0][:-len('player.jem')]
   def read(name):return z.read(prefix+name)
   for model in ('player.jem','player_slim.jem'):
    try:issues=audit(read,model)
    except (KeyError,ValueError) as e:raise SystemExit(f'{model}: missing/invalid file: {e}')
    if issues:raise SystemExit(f'{model}: signature not supported: {issues}')
    print(model,'signature OK (not proof of provenance or in-game execution)')
if __name__=='__main__':main()
