#!/usr/bin/env python3
"""Run real build-245 death/altar/clone/repossess/wear code on Java 21.

Minecraft, NeoForge event dispatch, save/load, world rules and Mixin are
executable stand-ins, NOT a game test. The attribute fixture mirrors vanilla's
three operation passes and explicitly calls the mixin callback on dirty.
"""
import argparse
import pathlib
import subprocess
import tempfile

import wear_fixture245 as x
from patch_probe233 import Asm

v,r,f,t,p,w=x.v,x.r,x.f,x.t,x.p,x.w
E,SP,SL,L,SERVER,C,G=v.E,v.SP,v.SL,v.L,v.SERVER,v.C,v.G
NBT,PD,PM,KEY,INV,IT,ITEM,ITEMS,IE=v.NBT,v.PD,v.PM,v.KEY,v.INV,v.IT,v.ITEM,v.ITEMS,v.ITEM_ENTITY
BLOCK,POSS,PLAYERLIST=v.BLOCK,v.POSS,v.PLAYERLIST
DEATH,CLONE,POST,RESPAWN=v.DEATH,v.CLONE,v.POST,v.RESPAWN
H,AA,ATTR,MOD,OP,RL,LIVING=w.HOLDER,w.ATTRIBUTES,w.ATTR,w.MOD,w.OP,w.RL,w.LIVING


def harness():
    def main(cp):
        a=Asm(); out=cp.field('java/lang/System','out','Ljava/io/PrintStream;')
        def load(i): t.local(a,'aload',i)
        def store(i): t.local(a,'astore',i)
        def num(n): r.const(a,n)
        def str_(s): t.text(a,cp,s)
        def bool_(b): a.raw(0x04 if b else 0x03)
        def make(name,desc='()V',args=None): t.obj(a,cp,name,desc,args)
        def get(i,owner,name,desc): load(i); a.op(0xb4,cp.field(owner,name,desc))
        def put(i,owner,name,desc,emit): load(i); emit(); a.op(0xb5,cp.field(owner,name,desc))
        def val(label,typ,emit):
            a.op(0xb2,out); str_(label+'='); a.op(0xb6,cp.method('java/io/PrintStream','print','(Ljava/lang/String;)V'))
            a.op(0xb2,out); emit(); a.op(0xb6,cp.method('java/io/PrintStream','println','('+typ+')V'))
        def uuid(n=1):
            str_('00000000-0000-0000-0000-%012d'%n); a.op(0xb8,cp.method('java/util/UUID','fromString','(Ljava/lang/String;)Ljava/util/UUID;'))
        def data(i=3): load(i); a.op(0xb6,cp.method(E,'getPersistentData','()L'+NBT+';'))
        def flag(i,key): data(i); str_(key); a.op(0xb6,cp.method(NBT,'getBoolean','(Ljava/lang/String;)Z'))
        def has(i,key): data(i); str_(key); a.op(0xb6,cp.method(NBT,'contains','(Ljava/lang/String;)Z'))
        def count(i=3): data(i); str_(w.COUNT); a.op(0xb6,cp.method(NBT,'getInt','(Ljava/lang/String;)I'))
        def attr(i=3): load(i); a.op(0xb2,cp.field(AA,'MAX_HEALTH','L'+H+';')); a.op(0xb6,cp.method(LIVING,'getAttribute','(L'+H+';)L'+ATTR+';'))
        def maxhealth(i=3): load(i); a.op(0xb6,cp.method(LIVING,'getMaxHealth','()F'))
        def health(i=3): load(i); a.op(0xb6,cp.method(LIVING,'getHealth','()F'))
        def pos(i,x,y=64,z=2):
            for axis,n in zip('xyz',(x,y,z)): put(i,E,axis,'D',lambda n=n:(num(n),a.raw(0x87)))
        def size(i):
            get(i,SL,'near','Ljava/util/List;'); p.invokeinterface(a,cp,'java/util/List','size','()I',1)
        def env():
            a.op(0xb8,cp.method(POSS,'fixtureReset','()V')); bool_(False); a.op(0xb3,cp.field(r.POSS_SYS,'possessed','Z'))
            bool_(False); a.op(0xb3,cp.field(x.LONGSET,'enabled','Z'))
            for k in ('queued','second'): a.raw(0x01); a.op(0xb3,cp.field(PD,k,'Ljava/util/UUID;'))
            make(SERVER); store(1); make(KEY); store(10)
            make(SL,'(L'+KEY+';L'+SERVER+';)V',lambda:(load(10),load(1))); store(2)
            make(SL,'(L'+KEY+';L'+SERVER+';)V',lambda:(a.op(0xb2,cp.field(PM,'PURGATORY_LEVEL','L'+KEY+';')),load(1))); store(11)
            make(PLAYERLIST); store(9); put(1,SERVER,'players','L'+PLAYERLIST+';',lambda:load(9))
            put(1,SERVER,'world','L'+SL+';',lambda:load(2)); put(1,SERVER,'purgatory','L'+SL+';',lambda:load(11))
            make(SP,'(Ljava/util/UUID;L'+SL+';I)V',lambda:(uuid(),load(2),num(30))); store(3); pos(3,3)
            get(9,PLAYERLIST,'list','Ljava/util/List;'); load(3)
            p.invokeinterface(a,cp,'java/util/List','add','(Ljava/lang/Object;)Z',2); a.raw(0x57)
            make(BLOCK,'(III)V',lambda:(num(0),num(64),num(0))); store(5)
        def death(possessed=True):
            bool_(possessed); a.op(0xb3,cp.field(r.POSS_SYS,'possessed','Z'))
            make(DEATH,'(L'+LIVING+';)V',lambda:load(3)); store(6)
            for cls in (POSS,p.LIFECYCLE,PM): load(6); a.op(0xb8,cp.method(cls,'onPlayerDeath','(L'+DEATH+';)V'))
        def drop(amount,keep=False):
            # keepInventory is represented by no drop event items and retaining
            # the player's stand-in inventory; not an actual game-rule test.
            make('java/util/ArrayList'); store(16)
            if not keep:
                make(IT); store(14); put(14,IT,'count','I',lambda:num(amount))
                put(14,IT,'id','Ljava/lang/String;',lambda:str_('test:relic'))
                put(14,IT,'meta','Ljava/lang/String;',lambda:str_('enchanted'))
                make(IE,'(L'+IT+';)V',lambda:load(14)); store(17); load(16); load(17)
                a.op(0xb6,cp.method('java/util/ArrayList','add','(Ljava/lang/Object;)Z')); a.raw(0x57)
            make(r.DROP_EVENT,'(L'+LIVING+';Ljava/util/Collection;)V',lambda:(load(3),load(16)))
            a.op(0xb8,cp.method(POSS,'onPlayerDrops','(L'+r.DROP_EVENT+';)V'))
        def dropsize(): load(16); a.op(0xb6,cp.method('java/util/ArrayList','size','()I'))
        def clone():
            make(SP,'(Ljava/util/UUID;L'+SL+';I)V',lambda:(uuid(),load(2),num(30))); store(15)
            make(CLONE,'(L'+w.P+';L'+w.P+';)V',lambda:(load(3),load(15)))
            a.op(0xb8,cp.method(POSS,'onPlayerClone','(L'+CLONE+';)V'))
            get(9,PLAYERLIST,'list','Ljava/util/List;'); num(0); load(15)
            p.invokeinterface(a,cp,'java/util/List','set','(ILjava/lang/Object;)Ljava/lang/Object;',3); a.raw(0x57)
            load(15); store(3); make(RESPAWN,'(L'+w.P+';)V',lambda:load(3))
            a.op(0xb8,cp.method(PM,'onPlayerRespawn','(L'+RESPAWN+';)V'))
            bool_(False); a.op(0xb3,cp.field(r.POSS_SYS,'possessed','Z'))
        def latest_body():
            get(2,SL,'near','Ljava/util/List;'); size(2); a.raw(0x04,0x64)
            p.invokeinterface(a,cp,'java/util/List','get','(I)Ljava/lang/Object;',2); a.op(0xc0,cp.cls(C)); store(4)
        def emeralds(n):
            make(IT); store(14); put(14,IT,'count','I',lambda:num(n))
            put(14,IT,'kind','L'+ITEM+';',lambda:a.op(0xb2,cp.field(ITEMS,'EMERALD','L'+ITEM+';')))
            make(IE,'(L'+IT+';)V',lambda:load(14)); store(8); get(2,SL,'altarItems','Ljava/util/List;'); load(8)
            p.invokeinterface(a,cp,'java/util/List','add','(Ljava/lang/Object;)Z',2); a.raw(0x57)
        def altar(catch=False):
            load(2); load(5); a.op(0xb8,cp.method('fixture/TryAltar' if catch else PM,
                'call' if catch else 'tryReviveAtAltar',v.q.TRY_DESC)); a.raw(0x57)
        def tick():
            make(POST,'(L'+SERVER+';)V',lambda:load(1)); a.op(0xb8,cp.method(POSS,'onServerTick','(L'+POST+';)V'))
        def paid(): emeralds(9); altar(); tick()
        def watches(): a.op(0xb8,cp.method(POSS,'fixtureWatchCount','()I'))
        def map_has(field,owner): uuid(owner); a.op(0xb8,cp.method(POSS,'fixtureHas'+field,'(Ljava/util/UUID;)Z'))
        def money(): get(8,IE,'item','L'+IT+';'); a.op(0xb4,cp.field(IT,'count','I'))
        def outside_purg():
            load(3); a.op(0xb6,cp.method(SP,'serverLevel','()L'+SL+';')); load(2); a.jump(0xa6,'np_'+str(len(a.buf)))
            lab='np_'+str(len(a.buf)-3); a.raw(0x04); a.jump(0xa7,lab+'_end'); a.label(lab); a.raw(0x03); a.label(lab+'_end')
        def current(): load(3); a.op(0xb8,cp.method(w.WEAR,'resume',w.SPVOID))
        def set_base(n): attr(); num(n); a.raw(0x87); a.op(0xb6,cp.method(ATTR,'setBaseValue','(D)V'))
        def dirty(): load(3); a.op(0xb2,cp.field(AA,'MAX_HEALTH','L'+H+';')); a.op(0xb6,cp.method(LIVING,'fixtureDirty','(L'+H+';)V'))
        def location(ns,path):
            str_(ns); str_(path); a.op(0xb8,cp.method(RL,'fromNamespaceAndPath','(Ljava/lang/String;Ljava/lang/String;)L'+RL+';'))
        def buff(path,n,denom,op):
            attr(); a.op(0xbb,cp.cls(MOD)); a.raw(0x59); location('othermod',path); num(n); a.raw(0x87)
            if denom!=1: num(denom); a.raw(0x87,0x6f)
            a.op(0xb2,cp.field(OP,op,'L'+OP+';')); a.op(0xb7,cp.method(MOD,'<init>','(L'+RL+';DL'+OP+';)V'))
            a.op(0xb6,cp.method(ATTR,'addOrReplacePermanentModifier','(L'+MOD+';)V')); dirty()
        def remove_buff(path):
            attr(); location('othermod',path); a.op(0xb6,cp.method(ATTR,'removeModifier','(L'+RL+';)Z')); a.raw(0x57); dirty()

        # The entire 3-payment sequence uses the real stock spawner, drop
        # capture, event clone, no-queue transaction and real repossess.
        env(); val('start.max','F',maxhealth)
        for i in (1,2,3):
            death(); latest_body(); drop(5)
            val('death%d.captured'%i,'I',dropsize)
            clone(); val('ghost%d.count'%i,'I',count); val('ghost%d.max'%i,'F',maxhealth)
            val('payment%d.watch_before'%i,'I',watches)
            emeralds(9); altar(); val('payment%d.marked'%i,'Z',lambda:has(3,w.PAID))
            val('payment%d.watch_after'%i,'I',watches)
            val('payment%d.before_tick'%i,'I',count); tick()
            val('life%d.count'%i,'I',count); val('life%d.max'%i,'F',maxhealth)
            val('life%d.current'%i,'F',health); val('life%d.body_removed'%i,'Z',lambda:get(4,E,'removed','Z'))
            if i==1:
                # Replayed resume and a repeated paid notification for the
                # same death cannot count the same body twice.
                current(); data(); str_(w.PENDING); bool_(True); a.op(0xb6,cp.method(NBT,'putBoolean','(Ljava/lang/String;Z)V'))
                load(3); load(4); a.op(0xb8,cp.method(w.WEAR,'recordPaid',w.PAID_DESC)); current()
                val('replay.count','I',count); val('replay.max','F',maxhealth)
                # Defensive case: even a duplicated persisted paid UUID cannot
                # make the resume hook count the same body again.
                data(); str_(w.PAID); load(4); a.op(0xb6,cp.method(E,'getUUID','()Ljava/util/UUID;'))
                a.op(0xb6,cp.method(NBT,'putUUID','(Ljava/lang/String;Ljava/util/UUID;)V')); current()
                val('replay_forced.count','I',count); val('replay_forced.paid','Z',lambda:has(3,w.PAID))
                data(); str_(w.PENDING); a.op(0xb6,cp.method(NBT,'remove','(Ljava/lang/String;)V'))
        val('before4.bodies','I',lambda:size(2)); death(); drop(7)
        val('death4.bodies','I',lambda:size(2)); val('death4.id','Z',lambda:has(3,w.CURRENT))
        val('death4.drops','I',dropsize); val('death4.pending','Z',lambda:flag(3,w.PENDING))
        clone(); emeralds(9); altar(); tick()
        val('death4.after_count','I',count); val('death4.after_money','I',money); val('death4.after_outside','Z',outside_purg)
        val('death4.after_max','F',maxhealth)
        # A forged/stale paid marker for a different current death is ignored.
        env(); data(); str_(w.COUNT); num(2); a.op(0xb6,cp.method(NBT,'putInt','(Ljava/lang/String;I)V'))
        data(); str_(w.PAID); uuid(1); a.op(0xb6,cp.method(NBT,'putUUID','(Ljava/lang/String;Ljava/util/UUID;)V'))
        data(); str_(w.CURRENT); uuid(2); a.op(0xb6,cp.method(NBT,'putUUID','(Ljava/lang/String;Ljava/util/UUID;)V'))
        current(); val('mismatch.count','I',count); val('mismatch.max','F',maxhealth); val('mismatch.paid','Z',lambda:has(3,w.PAID))
        # Same final death with a keepInventory-style empty capture is not
        # touched by the mod: no corpse and no stolen/duplicated drop list.
        env(); data(); str_(w.COUNT); num(3); a.op(0xb6,cp.method(NBT,'putInt','(Ljava/lang/String;I)V'))
        death(); drop(7,keep=True); val('keep.bodies','I',lambda:size(2)); val('keep.drops','I',dropsize)
        val('keep.captured','Z',lambda:has(3,p.PLAYER_ITEMS))
        # Stale delayed watches from an earlier death must not create a body
        # after exhaustion; cancelling player A must leave player B's watch.
        env(); death(); load(3); store(19)
        make(SP,'(Ljava/util/UUID;L'+SL+';I)V',lambda:(uuid(2),load(2),num(30))); store(18)
        get(9,PLAYERLIST,'list','Ljava/util/List;'); load(18)
        p.invokeinterface(a,cp,'java/util/List','add','(Ljava/lang/Object;)Z',2); a.raw(0x57)
        load(18); store(3); pos(3,10); death(); val('watches.two_before','I',watches)
        val('watches.stock_a_before','Z',lambda:map_has('STOCK_CORPSES',1))
        val('watches.stock_b_before','Z',lambda:map_has('STOCK_CORPSES',2))
        load(19); store(3); data(); str_(w.COUNT); num(3); a.op(0xb6,cp.method(NBT,'putInt','(Ljava/lang/String;I)V'))
        death(); val('watches.two_after','I',watches); val('watches.bodies','I',lambda:size(2))
        val('watches.stock_a_after','Z',lambda:map_has('STOCK_CORPSES',1))
        val('watches.stock_b_after','Z',lambda:map_has('STOCK_CORPSES',2))
        # Let the second owner's original stock-body check run.
        put(2,SL,'gameTime','J',lambda:(num(40),a.raw(0x85)))
        # The healthy case has precisely B's watch. If a deliberately broken
        # mutant left extra stale watches, skip its unsupported fallback-spawn
        # API and report the earlier behavioral assertion instead.
        watches(); num(1); a.jump(0xa0,'skip_wear_guarantee')
        a.op(0xb8,cp.method(POSS,'fixtureGuarantee','()V')); a.label('skip_wear_guarantee')
        val('watches.after_guarantee','I',watches); val('watches.after_bodies','I',lambda:size(2))
        # Failed spawn retains pending appearance until exhaustion. Cleanup for
        # A must not throw away B's independently pending spawn/watch.
        env(); put(2,SL,'failSpawn','Z',lambda:bool_(True)); death(); load(3); store(19)
        make(SP,'(Ljava/util/UUID;L'+SL+';I)V',lambda:(uuid(2),load(2),num(30))); store(18)
        get(9,PLAYERLIST,'list','Ljava/util/List;'); load(18)
        p.invokeinterface(a,cp,'java/util/List','add','(Ljava/lang/Object;)Z',2); a.raw(0x57)
        load(18); store(3); death(); val('pending.two','I',watches)
        val('pending.a_before','Z',lambda:map_has('PENDING',1)); val('pending.b_before','Z',lambda:map_has('PENDING',2))
        load(19); store(3); data(); str_(w.COUNT); num(3); a.op(0xb6,cp.method(NBT,'putInt','(Ljava/lang/String;I)V'))
        death(); val('pending.after','I',watches)
        val('pending.a_after','Z',lambda:map_has('PENDING',1)); val('pending.b_after','Z',lambda:map_has('PENDING',2))
        # Paying for A leaves B's independent watch and stock-body marker.
        env(); death(); latest_body(); drop(1); clone(); load(3); store(19)
        make(SP,'(Ljava/util/UUID;L'+SL+';I)V',lambda:(uuid(2),load(2),num(30))); store(18)
        get(9,PLAYERLIST,'list','Ljava/util/List;'); load(18)
        p.invokeinterface(a,cp,'java/util/List','add','(Ljava/lang/Object;)Z',2); a.raw(0x57)
        load(18); store(3); pos(3,10); death(); load(19); store(3)
        # PossessionSystem is an isolated global stub in this fixture.
        bool_(False); a.op(0xb3,cp.field(r.POSS_SYS,'possessed','Z'))
        val('paid_multi.before','I',watches); emeralds(9); altar()
        val('paid_multi.after','I',watches); val('paid_multi.stock_a','Z',lambda:map_has('STOCK_CORPSES',1))
        val('paid_multi.stock_b','Z',lambda:map_has('STOCK_CORPSES',2)); tick()
        val('paid_multi.count','I',count)
        # A bodiless ordinary death is still untouched.
        env(); death(False); drop(4); val('ordinary.bodies','I',lambda:size(2)); val('ordinary.drops','I',dropsize)

        # Failure paths: lack of money, teleport false and exceptions before/
        # after arrival. Only the committed after-arrival path records payment.
        for label,mode in [('eight','eight'),('false','false'),('before','before'),('after','after')]:
            env(); death(); latest_body(); drop(1); clone()
            if mode=='false': put(3,SP,'failTeleport','Z',lambda:bool_(True))
            if mode=='before': put(3,SP,'throwTeleport','Z',lambda:bool_(True))
            if mode=='after': put(3,SP,'throwAfterTeleport','Z',lambda:bool_(True))
            emeralds(8 if mode=='eight' else 9); altar(catch=mode in ('before','after'))
            val(label+'.paid','Z',lambda:has(3,w.PAID)); val(label+'.money','I',money)
            tick(); val(label+'.count','I',count); val(label+'.max','F',maxhealth)

        # Actual death/clone -> independent exit -> tick, with the old
        # near-altar fallback both absent and present. No paid marker exists.
        for label,near in [('escape_far',False),('escape_near',True)]:
            env(); data(); str_(w.COUNT); num(2); a.op(0xb6,cp.method(NBT,'putInt','(Ljava/lang/String;I)V'))
            current(); death(); latest_body(); drop(2); clone()
            put(3,E,'world','L'+L+';',lambda:load(2)); pos(3,3 if near else 100,65,2)
            if near:
                bool_(True); a.op(0xb3,cp.field(x.LONGSET,'enabled','Z'))
                load(5); a.op(0xb6,cp.method(BLOCK,'asLong','()J')); a.raw(0x58)
            make(r.DIM_EVENT,'(L'+w.P+';L'+KEY+';L'+KEY+';)V',
                 lambda:(load(3),a.op(0xb2,cp.field(PM,'PURGATORY_LEVEL','L'+KEY+';')),load(10)))
            a.op(0xb8,cp.method(PM,'onDimensionChange','(L'+r.DIM_EVENT+';)V')); tick()
            val(label+'.count','I',count); val(label+'.max','F',maxhealth)
            val(label+'.possessed','Z',lambda:flag(3,'settledlands_possess'))
            val(label+'.paid','Z',lambda:has(3,w.PAID))
        # Self-return without paid marker reactivates existing wear but does
        # not advance it. A fresh NPC reset removes only our own modifier.
        env(); data(); str_(w.COUNT); num(2); a.op(0xb6,cp.method(NBT,'putInt','(Ljava/lang/String;I)V'))
        current(); val('self.count','I',count); val('self.max','F',maxhealth)
        load(3); a.op(0xb8,cp.method(w.WEAR,'deactivate',w.SPVOID)); val('self.ghost_max','F',maxhealth)
        current(); val('self.return_max','F',maxhealth); val('self.return_count','I',count)
        # Current health is not restored as a side effect of reset.
        load(3); num(7); a.raw(0x86); a.op(0xb6,cp.method(LIVING,'setHealth','(F)V'))
        buff('flat',10,1,'ADD_VALUE'); load(3); a.op(0xb8,cp.method(w.WEAR,'reset',w.SPVOID))
        val('new_body.count','I',count); val('new_body.max','F',maxhealth); val('new_body.current','F',health)

        # Exact final subtraction with other mods, including multiplicative
        # modifiers, live changes, attribute cap/minimum, and non-health dirty.
        env(); data(); str_(w.COUNT); num(1); a.op(0xb6,cp.method(NBT,'putInt','(Ljava/lang/String;I)V'))
        current(); buff('flat',10,1,'ADD_VALUE'); val('buff.flat','F',maxhealth)
        buff('total',1,2,'ADD_MULTIPLIED_TOTAL'); val('buff.total','F',maxhealth)
        set_base(24); dirty(); val('buff.base_change','F',maxhealth)
        remove_buff('flat'); val('buff.flat_removed','F',maxhealth)
        buff('base_mult',1,2,'ADD_MULTIPLIED_BASE'); val('buff.base_mult','F',maxhealth)
        buff('total2',1,1,'ADD_MULTIPLIED_TOTAL'); val('buff.total2','F',maxhealth)
        set_base(1000); dirty(); val('buff.cap','F',maxhealth)
        remove_buff('total'); remove_buff('base_mult'); remove_buff('total2'); set_base(1); dirty()
        val('buff.minimum','F',maxhealth); buff('flat',20,1,'ADD_VALUE'); val('buff.minimum_recover','F',maxhealth)
        # Non-death clone keeps wear while a death clone becomes bodiless.
        make(SP,'(Ljava/util/UUID;L'+SL+';I)V',lambda:(uuid(),load(2),num(30))); store(15)
        make(CLONE,'(L'+w.P+';L'+w.P+';Z)V',lambda:(load(3),load(15),bool_(False)))
        a.op(0xb8,cp.method(POSS,'onPlayerClone','(L'+CLONE+';)V'))
        val('end_clone.count','I',lambda:count(15)); val('end_clone.max','F',lambda:maxhealth(15))
        a.raw(0xb1); return a.finish()
    return f.fake_class('BodyWearHarness',[],[(9,'main','([Ljava/lang/String;)V',24,20,main)])


def run(java,jar):
    classes=x.build(jar); classes['BodyWearHarness']=harness()
    with tempfile.TemporaryDirectory(prefix='bodywear245-jvm-') as td:
        root=pathlib.Path(td)
        for name,raw in classes.items():
            path=root/(name+'.class'); path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes(raw)
        result=subprocess.run([str(java),'-Xshare:off','-Xverify:all','-cp',str(root),'BodyWearHarness'],text=True,capture_output=True)
        if result.returncode: raise RuntimeError(f'{jar}: JVM exit {result.returncode}\n{result.stdout}\n{result.stderr}')
        return dict(line.split('=',1) for line in result.stdout.splitlines() if '=' in line)


def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--java',required=True); ap.add_argument('--jar',required=True); args=ap.parse_args()
    got=run(args.java,args.jar)
    expected={
        'start.max':'20.0','death1.captured':'0','ghost1.count':'0','ghost1.max':'20.0','payment1.marked':'true',
        'payment1.watch_before':'1','payment1.watch_after':'0',
        'payment1.before_tick':'0','life1.count':'1','life1.max':'16.0','life1.current':'16.0','life1.body_removed':'true',
        'replay.count':'1','replay.max':'16.0','replay_forced.count':'1','replay_forced.paid':'false',
        'death2.captured':'0','ghost2.count':'1','ghost2.max':'20.0',
        'payment2.marked':'true','payment2.watch_before':'1','payment2.watch_after':'0',
        'payment2.before_tick':'1','life2.count':'2','life2.max':'12.0','life2.current':'12.0',
        'life2.body_removed':'true','death3.captured':'0','ghost3.count':'2','ghost3.max':'20.0','payment3.marked':'true',
        'payment3.watch_before':'1','payment3.watch_after':'0',
        'payment3.before_tick':'2','life3.count':'3','life3.max':'4.0','life3.current':'4.0','life3.body_removed':'true',
        'before4.bodies':'3','death4.bodies':'3','death4.id':'false','death4.drops':'1','death4.pending':'false',
        'death4.after_count':'3','death4.after_money':'9','death4.after_outside':'false','death4.after_max':'20.0',
        'mismatch.count':'2','mismatch.max':'12.0','mismatch.paid':'false',
        'keep.bodies':'0','keep.drops':'0','keep.captured':'false',
        'watches.two_before':'2','watches.two_after':'1','watches.bodies':'2',
        'watches.stock_a_before':'true','watches.stock_b_before':'true',
        'watches.stock_a_after':'false','watches.stock_b_after':'true',
        'watches.after_guarantee':'0','watches.after_bodies':'2',
        'pending.two':'2','pending.a_before':'true','pending.b_before':'true',
        'pending.after':'1','pending.a_after':'false','pending.b_after':'true',
        'paid_multi.before':'2','paid_multi.after':'1','paid_multi.stock_a':'false',
        'paid_multi.stock_b':'true','paid_multi.count':'1',
        'ordinary.bodies':'0','ordinary.drops':'1',
        'eight.paid':'false','eight.money':'8','eight.count':'0','eight.max':'20.0',
        'false.paid':'false','false.money':'9','false.count':'0','false.max':'20.0',
        'before.paid':'false','before.money':'9','before.count':'0','before.max':'20.0',
        'after.paid':'true','after.money':'0','after.count':'1','after.max':'16.0',
        'escape_far.count':'2','escape_far.max':'20.0','escape_far.possessed':'false','escape_far.paid':'false',
        'escape_near.count':'2','escape_near.max':'12.0','escape_near.possessed':'true','escape_near.paid':'false',
        'self.count':'2','self.max':'12.0','self.ghost_max':'20.0','self.return_max':'12.0','self.return_count':'2',
        'new_body.count':'0','new_body.max':'30.0','new_body.current':'7.0',
        'buff.flat':'26.0','buff.total':'41.0','buff.base_change':'47.0','buff.flat_removed':'32.0',
        'buff.base_mult':'50.0','buff.total2':'104.0','buff.cap':'1020.0','buff.minimum':'1.0',
        'buff.minimum_recover':'17.0','end_clone.count':'1','end_clone.max':'16.0'}
    bad={k:(got.get(k),v) for k,v in expected.items() if got.get(k)!=v}
    if bad: raise AssertionError(f'{bad}\n{got}')
    print('Body wear: 3 paid lives; fourth death/no body/normal drops; clone/replay; rollback/exception; other-mod flat and multipliers/cap; new body: PASS')
    print('Synthetic APIs and explicit dirty callback; actual Minecraft, NeoForge, mixin transformation and keepInventory rule were not run.')


if __name__=='__main__': main()
