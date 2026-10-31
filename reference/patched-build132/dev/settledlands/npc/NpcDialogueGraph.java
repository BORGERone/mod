package dev.settledlands.npc;

import java.util.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Persistent dialogue topology. Runtime lookup is indexed, layout never affects gameplay. */
public final class NpcDialogueGraph {
    public static final int LINE = 0, BUTTON = 1, TRIGGER = 2, IF = 3, VARIABLE = 4, CHECK = 5, DAMAGE = 6, TIME = 7, TIMER = 8, ANIMATION = 9, QUEST = 10, MOVE = 11, MOBILITY = 12, RANDOM = 13, CUTSCENE = 14;
    public static final int TARGET_PLAYER = 0, TARGET_NPC = 1, TARGET_MONSTER = 2, TARGET_ANY = 3;
    public static final int DAMAGE_ANY = 0, DAMAGE_PLAYER = 1, DAMAGE_MONSTER = 2, DAMAGE_NON_PLAYER = 3;
    public static final int TIME_DAWN = 0, TIME_NOON = 1, TIME_SUNSET = 2, TIME_MIDNIGHT = 3, TIME_CUSTOM = 4;
    public static final int NORMAL = 0, TRAVEL = 1, ARRIVAL = 2;
    public static final int MAX_NODES = 2048, MAX_ID = 16383, MAX_CHOICES = 16, MAX_COORD = 32768;
    public static final int MAX_TEXT_UNITS = 600000;
    public static final int MAX_PACKET_BYTES = 1000000;
    public boolean enabled;
    public int entry = -1;
    public boolean hasCamera = false;
    public double panX = 60.0, panY = 90.0, zoom = 1.0;
    public final LinkedHashMap<Integer, Node> nodes = new LinkedHashMap<>();

    public static final class Node {
        public int id, type, x, y, next = -1, special, builtinTopic = -1;
        public int originTopic = -1, originStep = -1, originAnswer = -1;
        public String text = "", action = "", pointsName = "";
        public int relation, minRelation, maxRelation = 100, minPoints, maxPoints = NpcPoints.MAX_VALUE;
        public boolean pointsShared;
        public int falseNext = -1;
        public int varValue = 0;
        public int varCompare = 0;
        public int varOp = 0; // 0: ==, 1: !=, 2: >=, 3: >, 4: <=, 5: <
        public int varInputNode = -1;
        public int checkTarget = TARGET_PLAYER;
        public int checkRadius = 5;
        public int checkCooldown = 10;
        public int damageFilter = DAMAGE_PLAYER;
        public int damageCooldown = 1;
        public int minDamage = 1;
        public int timeMode = TIME_CUSTOM;
        public int timeTicks = 6000;
        public int timerInterval = 10;
        public boolean timerRepeat = true;
        public String animName = "";
        public String cutsceneName = "";
        public boolean animLoop = false;
        public String animBone = "";
        public String moveCoords = "";
        public double moveSpeed = 1.0;
        public boolean moveEscort = false;
        public String mobilityGroupId = "";
        public String mobilityGroupName = "";
        public int randomMin = 1;
        public int randomMax = 2;
        public final List<Integer> randomBranches = new ArrayList<>();
        public final List<Integer> choices = new ArrayList<>();
        public final List<Integer> triggers = new ArrayList<>();
        public final List<NpcProfile.Variant> variants = new ArrayList<>();
        public Node(int id, int type, int x, int y) { this.id=id; this.type=type; this.x=x; this.y=y; }
        public Node copy() {
            Node n=new Node(id,type,x,y);n.next=next;n.special=special;n.builtinTopic=builtinTopic;
            n.originTopic=originTopic;n.originStep=originStep;n.originAnswer=originAnswer;
            n.text=text;n.action=action;n.pointsName=pointsName;n.relation=relation;n.minRelation=minRelation;n.maxRelation=maxRelation;
            n.minPoints=minPoints;n.maxPoints=maxPoints;n.pointsShared=pointsShared;
            n.falseNext=falseNext;n.varValue=varValue;n.varCompare=varCompare;n.varOp=varOp;n.varInputNode=varInputNode;
            n.checkTarget=checkTarget;n.checkRadius=checkRadius;n.checkCooldown=checkCooldown;
            n.damageFilter=damageFilter;n.damageCooldown=damageCooldown;n.minDamage=minDamage;
            n.timeMode=timeMode;n.timeTicks=timeTicks;n.timerInterval=timerInterval;n.timerRepeat=timerRepeat;
            n.animName=animName;n.animLoop=animLoop;n.animBone=animBone;
            n.cutsceneName=cutsceneName;
            n.moveCoords=moveCoords;n.moveSpeed=moveSpeed;n.moveEscort=moveEscort;
            n.mobilityGroupId=mobilityGroupId;n.mobilityGroupName=mobilityGroupName;
            n.randomMin=randomMin;n.randomMax=randomMax;n.randomBranches.addAll(randomBranches);
            n.choices.addAll(choices);n.triggers.addAll(triggers);
            for(NpcProfile.Variant v:variants)n.variants.add(new NpcProfile.Variant(v.minRelation,v.text));
            return n;
        }
        public String lineFor(int relation) {
            String value=text; int threshold=-1;
            for(NpcProfile.Variant v:variants)if(v.minRelation<=relation&&v.minRelation>=threshold){value=v.text;threshold=v.minRelation;}
            return value;
        }
        public boolean relationVisible(int value) { return minRelation<=value && value<=maxRelation; }
    }

    public Node get(int id) { return nodes.get(id); }
    public Node add(int type,int x,int y) {
        if(nodes.size()>=MAX_NODES)return null;
        for(int id=0;id<=MAX_ID;id++)if(!nodes.containsKey(id)){
            Node n=new Node(id,type,x,y);nodes.put(id,n);return n;
        }
        return null;
    }
    public int specialNode(int role) { for(Node n:nodes.values())if(n.special==role)return n.id; return -1; }
    public int topicNode(int topic) { for(Node n:nodes.values())if(n.originTopic==topic&&n.originStep<0)return n.id;return -1; }

    public boolean connect(int from,int to) {
        return connect(from, to, 0);
    }
    public boolean connect(int from,int to,int portIndex) {
        Node a=get(from),b=get(to);
        if(a==null||b==null||a.special!=NORMAL||b.special!=NORMAL)return false;
        if(a.type==VARIABLE&&b.type==IF){
            b.varInputNode=a.id;
            if(!a.pointsName.isBlank()){b.pointsName=a.pointsName;b.pointsShared=a.pointsShared;}
            a.next=b.id;
            return true;
        }
        if(a.type==IF){
            if(portIndex==1){a.falseNext=to;}else{a.next=to;}
            return true;
        }
        if(b.type==TRIGGER){
            if(a.triggers.contains(to))return true;
            a.triggers.add(to);return true;
        }
        if(a.type==RANDOM){
            while(a.randomBranches.size()<=portIndex)a.randomBranches.add(-1);
            a.randomBranches.set(portIndex,to);
            return true;
        }
        if(a.type==TRIGGER){
            if(b.type==TRIGGER){if(a.triggers.contains(to))return true;a.triggers.add(to);return true;}
            a.next=to;return true;
        }
        if(b.type==BUTTON){
            if(a.type!=LINE)return false;
            if(a.choices.contains(to))return true;
            if(a.choices.size()>=MAX_CHOICES)return false;
            a.choices.add(to);return true;
        }
        a.next=to;return true;
    }
    public void disconnect(int from,int to) {
        Node a=get(from);if(a==null)return;
        if(a.next==to)a.next=-1;
        if(a.falseNext==to)a.falseNext=-1;
        if(a.varInputNode==to)a.varInputNode=-1;
        if(a.type==RANDOM){
            for(int i=0;i<a.randomBranches.size();i++)if(a.randomBranches.get(i)==to)a.randomBranches.set(i,-1);
        }
        a.choices.remove(Integer.valueOf(to));a.triggers.remove(Integer.valueOf(to));
    }
    public void disconnectOutputs(int id) {
        Node n=get(id);if(n!=null){n.next=-1;n.falseNext=-1;n.varInputNode=-1;n.choices.clear();n.triggers.clear();n.randomBranches.clear();}
    }
    public boolean remove(int id) {
        Node n=get(id);if(n==null||n.special!=NORMAL)return false;
        nodes.remove(id);for(Node v:nodes.values()){
            if(v.next==id)v.next=-1;
            if(v.falseNext==id)v.falseNext=-1;
            if(v.varInputNode==id)v.varInputNode=-1;
            for(int i=0;i<v.randomBranches.size();i++)if(v.randomBranches.get(i)==id)v.randomBranches.set(i,-1);
            v.choices.remove(Integer.valueOf(id));v.triggers.remove(Integer.valueOf(id));
        }
        if(entry==id)entry=-1;return true;
    }
    public NpcDialogueGraph copy() {
        NpcDialogueGraph g=new NpcDialogueGraph();g.enabled=enabled;g.entry=entry;g.hasCamera=hasCamera;g.panX=panX;g.panY=panY;g.zoom=zoom;
        for(Node n:nodes.values())g.nodes.put(n.id,n.copy());return g;
    }

    public boolean hasQuestNodes() {
        for (Node n : nodes.values()) {
            if (n.type == QUEST) return true;
        }
        return false;
    }

    /** A useful, strict error, not silent truncation/deletion of the original dialogue. Cycles are legal. */
    public String error() {
        if(nodes.size()>MAX_NODES)return "nodes";
        Node start=get(entry);if(enabled&&(start==null||start.type!=LINE||start.special!=NORMAL))return "entry";
        int units=0,travel=0,arrival=0;
        for(Map.Entry<Integer,Node> e:nodes.entrySet()){
            Node n=e.getValue();if(n==null||n.id!=e.getKey()||n.id<0||n.id>MAX_ID||(n.type<LINE||n.type>CUTSCENE))return "node";
            if(n.x<-MAX_COORD||n.x>MAX_COORD||n.y<-MAX_COORD||n.y>MAX_COORD)return "position";
            if(n.text==null||n.action==null||n.pointsName==null||n.cutsceneName==null||n.text.length()>(n.type==LINE?480:240)||n.action.length()>2048||n.pointsName.length()>32||n.cutsceneName.length()>64)return "text";
            units+=n.text.length()+n.action.length()+n.pointsName.length();
            if(n.minRelation<0||n.maxRelation>100||n.minRelation>n.maxRelation||n.relation<-100||n.relation>100)return "relation";
            if(n.minPoints<0||n.maxPoints>NpcPoints.MAX_VALUE||n.minPoints>n.maxPoints)return "points";
            if(n.builtinTopic < -1 || n.builtinTopic > 128)return "builtin";
            if(n.special<0||n.special>ARRIVAL||(n.special!=NORMAL&&n.type!=LINE))return "special";
            if(n.special==TRAVEL)travel++;if(n.special==ARRIVAL)arrival++;
            if(n.special!=NORMAL&&(n.next!=-1||!n.choices.isEmpty()||!n.triggers.isEmpty()||n.falseNext!=-1))return "special_links";
            if(n.type==CHECK){
                if(n.checkRadius<1||n.checkRadius>64)return "check_radius";
                if(n.checkCooldown<0||n.checkCooldown>3600)return "check_cooldown";
                if(n.checkTarget<0||n.checkTarget>3)return "check_target";
            }
            if(n.type==DAMAGE){
                if(n.damageFilter<0||n.damageFilter>3)return "damage_filter";
                if(n.damageCooldown<0||n.damageCooldown>3600)return "damage_cooldown";
                if(n.minDamage<0||n.minDamage>1000)return "min_damage";
            }
            if(n.type==TIME){
                if(n.timeMode<0||n.timeMode>4)return "time_mode";
                if(n.timeTicks<0||n.timeTicks>23999)return "time_ticks";
            }
            if(n.type==TIMER){
                if(n.timerInterval<1||n.timerInterval>86400)return "timer_interval";
            }
            if(n.type==RANDOM){
                if(n.randomMin<1||n.randomMax>64||n.randomMin>n.randomMax)return "random_range";
            }
            if(n.type==ANIMATION){
                if(n.animName==null||n.animName.length()>64)return "anim_name";
                units+=n.animName.length();
            }
            if(n.type==MOVE){
                if(n.moveCoords==null||n.moveCoords.length()>64)return "move_coords";
                if(n.moveSpeed<0.1||n.moveSpeed>10.0)return "move_speed";
                units+=n.moveCoords.length();
            }
            if(n.type==MOBILITY){
                if(n.mobilityGroupId!=null&&n.mobilityGroupId.length()>36)return "mobility_group_id";
                if(n.mobilityGroupName!=null&&n.mobilityGroupName.length()>32)return "mobility_group_name";
            }
            if(n.next!=-1){Node b=get(n.next);if(b==null||b.type==BUTTON||b.special!=NORMAL)return "link";}
            if(n.falseNext!=-1){Node b=get(n.falseNext);if(b==null||b.type==BUTTON||b.special!=NORMAL)return "false_link";}
            if(n.varInputNode!=-1){Node b=get(n.varInputNode);if(b==null||b.special!=NORMAL||b.type!=VARIABLE)return "var_input_link";}
            for(Integer tid:n.triggers){Node tb=tid==null?null:get(tid);if(tb==null||tb.type!=TRIGGER||tb.special!=NORMAL)return "trigger_link";}
            if(n.type!=LINE&&!n.choices.isEmpty()||n.choices.size()>MAX_CHOICES)return "choices";
            HashSet<Integer> seen=new HashSet<>();
            for(Integer id:n.choices){Node b=id==null?null:get(id);if(b==null||b.type!=BUTTON||b.special!=NORMAL||!seen.add(id))return "choice_link";}
            if(n.variants.size()>5)return "variants";
            for(NpcProfile.Variant v:n.variants){if(v==null||v.text==null||v.text.length()>480||v.minRelation<0||v.minRelation>100)return "variant";units+=v.text.length();}
            if(units>MAX_TEXT_UNITS)return "size";
        }
        return travel>1||arrival>1?"special_duplicate":"";
    }

    /** Constant-memory encoded NBT size preflight, used only at save/validation boundaries. */
    public static int encodedSize(CompoundTag tag) {
        final int[] count={0};
        try {
            NbtIo.write(tag,new DataOutputStream(new OutputStream(){
                public void write(int b){count[0]++;}
                public void write(byte[] b,int off,int len){count[0]+=len;}
            }));
            return count[0];
        } catch(IOException e){return Integer.MAX_VALUE;}
    }

    public CompoundTag save() {
        CompoundTag t=new CompoundTag();t.putInt("Version",1);t.putBoolean("Enabled",enabled);t.putInt("Entry",entry);
        t.putBoolean("HasCamera",hasCamera);t.putDouble("PanX",panX);t.putDouble("PanY",panY);t.putDouble("Zoom",zoom);
        ListTag list=new ListTag();
        for(Node n:nodes.values()){
            CompoundTag v=new CompoundTag();v.putInt("Id",n.id);v.putInt("Type",n.type);v.putInt("X",n.x);v.putInt("Y",n.y);v.putInt("Next",n.next);v.putInt("Special",n.special);
            v.putString("Text",n.text);v.putString("Action",n.action);v.putInt("BuiltinTopic",n.builtinTopic);
            v.putInt("OriginTopic",n.originTopic);v.putInt("OriginStep",n.originStep);v.putInt("OriginAnswer",n.originAnswer);
            v.putInt("Relation",n.relation);v.putInt("MinRelation",n.minRelation);v.putInt("MaxRelation",n.maxRelation);
            v.putString("PointsName",n.pointsName);v.putInt("MinPoints",n.minPoints);v.putInt("MaxPoints",n.maxPoints);v.putBoolean("PointsShared",n.pointsShared);
            v.putInt("FalseNext",n.falseNext);v.putInt("VarValue",n.varValue);v.putInt("VarCompare",n.varCompare);v.putInt("VarOp",n.varOp);v.putInt("VarInputNode",n.varInputNode);
            v.putInt("CheckTarget",n.checkTarget);v.putInt("CheckRadius",n.checkRadius);v.putInt("CheckCooldown",n.checkCooldown);
            v.putInt("DamageFilter",n.damageFilter);v.putInt("DamageCooldown",n.damageCooldown);v.putInt("MinDamage",n.minDamage);
            if (n.type == TIME) { v.putInt("TimeMode",n.timeMode); v.putInt("TimeTicks",n.timeTicks); }
            if (n.type == TIMER) { v.putInt("TimerInterval",n.timerInterval); v.putBoolean("TimerRepeat",n.timerRepeat); }
            if (n.type == ANIMATION) { v.putString("AnimName",n.animName); v.putBoolean("AnimLoop",n.animLoop); v.putString("AnimBone",n.animBone); }
            if (n.type == MOVE) { if(!n.moveCoords.isBlank()) v.putString("MoveCoords",n.moveCoords); if(n.moveSpeed != 1.0) v.putDouble("MoveSpeed",n.moveSpeed); if(n.moveEscort) v.putBoolean("MoveEscort",true); }
            if (n.type == MOBILITY) { if(!n.mobilityGroupId.isBlank()) v.putString("MobilityGroupId",n.mobilityGroupId); if(!n.mobilityGroupName.isBlank()) v.putString("MobilityGroupName",n.mobilityGroupName); }
            if (n.type == CUTSCENE) { if(!n.cutsceneName.isBlank()) v.putString("CutsceneName",n.cutsceneName); }
            if (n.type == RANDOM) { v.putInt("RandomMin",n.randomMin); v.putInt("RandomMax",n.randomMax); v.putIntArray("RandomBranches",n.randomBranches.stream().mapToInt(Integer::intValue).toArray()); }
            v.putIntArray("Choices",n.choices.stream().mapToInt(Integer::intValue).toArray());
            v.putIntArray("Triggers",n.triggers.stream().mapToInt(Integer::intValue).toArray());
            ListTag variants=new ListTag();for(NpcProfile.Variant a:n.variants){CompoundTag b=new CompoundTag();b.putInt("Min",a.minRelation);b.putString("Text",a.text);variants.add(b);}v.put("Variants",variants);
            list.add(v);
        }
        t.put("Nodes",list);return t;
    }
    public static NpcDialogueGraph load(CompoundTag t) {
        if(t==null||t.getInt("Version")!=1)return null;
        ListTag list=t.getList("Nodes",10);if(list.size()>MAX_NODES)return null;
        NpcDialogueGraph g=new NpcDialogueGraph();g.enabled=t.getBoolean("Enabled");g.entry=t.getInt("Entry");
        if(t.contains("HasCamera",1))g.hasCamera=t.getBoolean("HasCamera");
        else if(t.contains("PanX")||t.contains("Zoom"))g.hasCamera=true;
        if(t.contains("PanX",6))g.panX=t.getDouble("PanX");else if(t.contains("PanX",3))g.panX=t.getInt("PanX");
        if(t.contains("PanY",6))g.panY=t.getDouble("PanY");else if(t.contains("PanY",3))g.panY=t.getInt("PanY");
        if(t.contains("Zoom",6))g.zoom=t.getDouble("Zoom");
        g.zoom=Math.max(0.05,Math.min(2.0,g.zoom<=0.001?1.0:g.zoom));
        g.panX=Math.max(-100000.0,Math.min(100000.0,g.panX));
        g.panY=Math.max(-100000.0,Math.min(100000.0,g.panY));
        for(int i=0;i<list.size();i++){
            CompoundTag v=list.getCompound(i);Node n=new Node(v.getInt("Id"),v.getInt("Type"),v.getInt("X"),v.getInt("Y"));
            if(g.nodes.containsKey(n.id))return null;
            n.next=v.getInt("Next");n.special=v.getInt("Special");n.text=v.getString("Text");n.action=v.getString("Action");n.builtinTopic=v.getInt("BuiltinTopic");
            n.originTopic=v.getInt("OriginTopic");n.originStep=v.getInt("OriginStep");n.originAnswer=v.getInt("OriginAnswer");
            n.relation=v.getInt("Relation");n.minRelation=v.getInt("MinRelation");n.maxRelation=v.getInt("MaxRelation");
            n.pointsName=v.getString("PointsName");n.minPoints=v.getInt("MinPoints");n.maxPoints=v.getInt("MaxPoints");n.pointsShared=v.getBoolean("PointsShared");
            n.falseNext=v.contains("FalseNext")?v.getInt("FalseNext"):-1;
            n.varValue=v.getInt("VarValue");
            n.varCompare=v.getInt("VarCompare");
            n.varOp=v.getInt("VarOp");
            n.varInputNode=v.contains("VarInputNode")?v.getInt("VarInputNode"):-1;
            n.checkTarget=v.contains("CheckTarget")?v.getInt("CheckTarget"):TARGET_PLAYER;
            n.checkRadius=v.contains("CheckRadius")?v.getInt("CheckRadius"):5;
            if(n.checkRadius<1)n.checkRadius=5;
            n.checkCooldown=v.contains("CheckCooldown")?v.getInt("CheckCooldown"):10;
            n.damageFilter=v.contains("DamageFilter")?v.getInt("DamageFilter"):DAMAGE_PLAYER;
            n.damageCooldown=v.contains("DamageCooldown")?v.getInt("DamageCooldown"):1;
            n.minDamage=v.contains("MinDamage")?v.getInt("MinDamage"):1;
            n.timeMode=v.contains("TimeMode")?v.getInt("TimeMode"):TIME_CUSTOM;
            if(n.timeMode<0||n.timeMode>4)n.timeMode=TIME_CUSTOM;
            n.timeTicks=v.contains("TimeTicks")?v.getInt("TimeTicks"):6000;
            if(n.timeTicks<0||n.timeTicks>23999)n.timeTicks=6000;
            n.timerInterval=v.contains("TimerInterval")?v.getInt("TimerInterval"):10;
            if(n.timerInterval<1)n.timerInterval=10;
            n.timerRepeat=!v.contains("TimerRepeat")||v.getBoolean("TimerRepeat");
            n.animName=v.contains("AnimName")?v.getString("AnimName"):"";
            n.animLoop=v.contains("AnimLoop")&&v.getBoolean("AnimLoop");
            n.animBone=v.contains("AnimBone")?v.getString("AnimBone"):"";
            n.cutsceneName=v.contains("CutsceneName")?v.getString("CutsceneName"):"";
            n.moveCoords=v.contains("MoveCoords")?v.getString("MoveCoords"):"";
            n.moveSpeed=v.contains("MoveSpeed")?v.getDouble("MoveSpeed"):1.0;
            n.moveEscort=v.contains("MoveEscort")&&v.getBoolean("MoveEscort");
            n.mobilityGroupId=v.contains("MobilityGroupId")?v.getString("MobilityGroupId"):"";
            n.mobilityGroupName=v.contains("MobilityGroupName")?v.getString("MobilityGroupName"):"";
            n.randomMin=v.contains("RandomMin")?v.getInt("RandomMin"):1;
            n.randomMax=v.contains("RandomMax")?v.getInt("RandomMax"):2;
            if(n.randomMax<2)n.randomMax=2;
            if(n.randomMin<1)n.randomMin=1;
            if(v.contains("RandomBranches")){
                int[] rb=v.getIntArray("RandomBranches");
                for(int b:rb)n.randomBranches.add(b);
            }
            int[] choices=v.getIntArray("Choices");if(choices.length>MAX_CHOICES)return null;for(int id:choices)n.choices.add(id);
            int[] triggers=v.getIntArray("Triggers");for(int id:triggers)n.triggers.add(id);
            ListTag variants=v.getList("Variants",10);if(variants.size()>5)return null;
            for(int j=0;j<variants.size();j++){CompoundTag a=variants.getCompound(j);n.variants.add(new NpcProfile.Variant(a.getInt("Min"),a.getString("Text")));}
            g.nodes.put(n.id,n);
        }
        return g;
    }

    /** Non-destructive migration. Old topic/quest definitions remain in NpcProfile for built-in menus. */
    public static NpcDialogueGraph fromLegacy(NpcProfile p) {
        NpcDialogueGraph g=new NpcDialogueGraph();
        Node welcome=g.add(LINE,0,0);welcome.text=p.welcome;g.entry=welcome.id;
        Node last=welcome;
        for(int i=0;i<p.welcomeLines.size();i++){Node n=required(g.add(LINE,(i+1)*280,0));n.text=p.welcomeLines.get(i);last.next=n.id;last=n;}
        Node travel=required(g.add(LINE,0,-240));travel.text=p.escortTravelLine;travel.special=TRAVEL;
        Node arrival=required(g.add(LINE,280,-240));arrival.text=p.escortArrivalLine;arrival.special=ARRIVAL;
        int menuRoot=last.id;
        int[] roots=new int[p.topics.size()];Arrays.fill(roots,-1);
        int[][] lines=new int[p.topics.size()][];
        for(int ti=0;ti<p.topics.size();ti++){
            NpcProfile.Topic topic=p.topics.get(ti);Node button=required(g.add(BUTTON,280,180+ti*1050));button.text=topic.title;button.originTopic=ti;
            roots[ti]=button.id;
            if(last.choices.size()>=MAX_CHOICES){
                Node page=required(g.add(LINE,last.x+280,last.y));page.text=last.text;last.next=page.id;last=page;
            }
            last.choices.add(button.id);
            if(topic.type!=NpcProfile.TYPE_REPLY){button.builtinTopic=ti;lines[ti]=new int[0];continue;}
            lines[ti]=new int[topic.steps.size()];
            for(int si=0;si<topic.steps.size();si++){
                NpcProfile.Step step=topic.steps.get(si);Node n=required(g.add(LINE,560+si*560,180+ti*1050));
                n.text=step.line;n.action=step.action;n.originTopic=ti;n.originStep=si;
                for(NpcProfile.Variant v:step.variants)n.variants.add(new NpcProfile.Variant(v.minRelation,v.text));lines[ti][si]=n.id;
            }
            if(lines[ti].length>0)button.next=lines[ti][0];
        }
        for(int ti=0;ti<p.topics.size();ti++){
            NpcProfile.Topic topic=p.topics.get(ti);if(topic.type!=NpcProfile.TYPE_REPLY)continue;
            for(int si=0;si<topic.steps.size();si++){
                Node line=g.get(lines[ti][si]);NpcProfile.Step step=topic.steps.get(si);
                int implicit=NpcProfile.chainNext(topic,si);line.next=implicit>=0?lines[ti][implicit]:menuRoot;
                for(int ai=0;ai<step.answers.size();ai++){
                    NpcProfile.Answer answer=step.answers.get(ai);Node button=required(g.add(BUTTON,line.x+110,line.y+110+ai*120));
                    button.text=answer.player;button.action=answer.action;button.relation=answer.relation;
                    button.minRelation=answer.minRelation;button.maxRelation=answer.maxRelation;
                    button.pointsName=answer.pointsName;button.minPoints=answer.minPoints;button.maxPoints=answer.maxPoints;button.pointsShared=answer.pointsShared;
                    button.originTopic=ti;button.originStep=si;button.originAnswer=ai;line.choices.add(button.id);
                    int target=menuRoot,builtin=-1;
                    if(answer.next>=1000){int topicIndex=answer.next-1000;if(topicIndex<roots.length){Node r=g.get(roots[topicIndex]);target=r!=null?r.next:menuRoot;if(r!=null&&r.builtinTopic>=0)builtin=r.builtinTopic;}}
                    else if(answer.next>=0&&answer.next<lines[ti].length)target=lines[ti][answer.next];
                    if(!answer.reaction.isEmpty()){
                        Node reaction=required(g.add(LINE,button.x+280,button.y));reaction.text=answer.reaction;reaction.next=builtin>=0?-1:target;reaction.builtinTopic=builtin;button.next=reaction.id;
                    }else {button.next=builtin>=0?-1:target;button.builtinTopic=builtin;}
                }
            }
        }
        if(!g.error().isEmpty())throw new IllegalArgumentException("Legacy dialogue exceeds graph limits: "+g.error());
        return g;
    }
    private static Node required(Node n) { if(n==null)throw new IllegalArgumentException("Legacy dialogue exceeds graph node limit");return n; }

    public void applySpecialLines(NpcProfile p) {
        Node a=get(specialNode(TRAVEL)),b=get(specialNode(ARRIVAL));
        if(a!=null)p.escortTravelLine=a.text;if(b!=null)p.escortArrivalLine=b.text;
    }
}
