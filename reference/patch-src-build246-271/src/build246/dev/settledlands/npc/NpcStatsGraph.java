package dev.settledlands.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** One non-executable characteristics node in the NPC dialogue graph. */
public final class NpcStatsGraph {
    public static final int TYPE = 16, DEFAULT = 10, MIN = 1, MAX = 100;
    private NpcStatsGraph() {}
    public static int clamp(int n) { return Math.max(MIN, Math.min(MAX, n)); }
    public static NpcDialogueGraph.Node find(NpcDialogueGraph graph) {
        if (graph == null) return null;
        for (NpcDialogueGraph.Node n : graph.nodes.values()) if (n != null && n.type == TYPE) return n;
        return null;
    }
    public static int health(NpcDialogueGraph graph) { NpcDialogueGraph.Node n=find(graph); return n==null ? DEFAULT : clamp(n.varValue); }
    public static int damage(NpcDialogueGraph graph) { NpcDialogueGraph.Node n=find(graph); return n==null ? DEFAULT : clamp(n.checkRadius); }
    public static int speed(NpcDialogueGraph graph) { NpcDialogueGraph.Node n=find(graph); return n==null ? DEFAULT : clamp(n.checkCooldown); }
    public static boolean ensure(NpcDialogueGraph graph) {
        if (graph == null || find(graph) != null) return false;
        int x=0,y=0,right=Integer.MIN_VALUE,top=Integer.MAX_VALUE;
        for (NpcDialogueGraph.Node n : graph.nodes.values()) {
            if (n == null) continue;
            right=Math.max(right,n.x); top=Math.min(top,n.y);
        }
        if (right != Integer.MIN_VALUE) x=Math.min(32768,Math.max(-32768,right+288));
        if (top != Integer.MAX_VALUE) y=top;
        NpcDialogueGraph.Node n=graph.add(TYPE,x,y);
        if (n==null) return false;
        n.varValue=n.checkRadius=n.checkCooldown=DEFAULT;
        return true;
    }
    public static String validate(String original, NpcDialogueGraph graph) {
        if (!original.isEmpty()) return original;
        NpcDialogueGraph.Node stats=null;
        for (NpcDialogueGraph.Node n:graph.nodes.values()) {
            if (n.type!=TYPE) continue;
            if (stats!=null) return "stats_duplicate";
            stats=n;
            if (n.varValue<MIN||n.varValue>MAX||n.checkRadius<MIN||n.checkRadius>MAX||n.checkCooldown<MIN||n.checkCooldown>MAX) return "stats_range";
            if (n.special!=0||graph.entry==n.id||n.next!=-1||n.falseNext!=-1||n.varInputNode!=-1||!n.choices.isEmpty()||!n.triggers.isEmpty()||!n.randomBranches.isEmpty()) return "stats_links";
        }
        if (graph.nodes.size()>2048&&stats==null) return "nodes";
        if (stats!=null) {
            int id=stats.id;
            for (NpcDialogueGraph.Node n:graph.nodes.values()) {
                if (n==stats) continue;
                if (n.next==id||n.falseNext==id||n.varInputNode==id||n.choices.contains(id)||n.triggers.contains(id)||(n.type==13&&n.randomBranches.contains(id))) return "stats_links";
            }
        }
        return "";
    }
    /** Check both decoded state and raw values before legacy normalization. */
    public static NpcDialogueGraph validateLoaded(NpcDialogueGraph graph, CompoundTag raw) {
        if (graph==null||raw==null||!validate("",graph).isEmpty()) return null;
        ListTag nodes=raw.getList("Nodes",10);
        for (int i=0;i<nodes.size();++i) {
            CompoundTag n=nodes.getCompound(i);
            if (n.getInt("Type")!=TYPE) continue;
            for (String key:new String[]{"VarValue","CheckRadius","CheckCooldown"}) {
                int value=n.getInt(key);
                if (!n.contains(key,3)||value<MIN||value>MAX) return null;
            }
        }
        return graph;
    }
    public static boolean remove(NpcDialogueGraph graph,int id) {
        NpcDialogueGraph.Node n=graph.get(id);
        return (n==null||n.type!=TYPE)&&graph.settledlands$removeOriginal(id);
    }
    public static NpcDialogueGraph.Node add(NpcDialogueGraph graph,int type,int x,int y) {
        NpcDialogueGraph.Node stats=find(graph);
        if (type==TYPE&&stats!=null) return null;
        if (graph.nodes.size()<2048) return graph.settledlands$addOriginal(type,x,y);
        if (graph.nodes.size()!=2048||(type!=TYPE&&stats==null)) return null;
        for (int id=0;id<=16383;++id) if (!graph.nodes.containsKey(id)) {
            NpcDialogueGraph.Node n=new NpcDialogueGraph.Node(id,type,x,y);
            graph.nodes.put(id,n); return n;
        }
        return null;
    }
    public static boolean connect(NpcDialogueGraph graph,int from,int to,int port) {
        NpcDialogueGraph.Node a=graph.get(from),b=graph.get(to);
        if ((a!=null&&a.type==TYPE)||(b!=null&&b.type==TYPE)) return false;
        return graph.settledlands$connectOriginal(from,to,port);
    }
}
