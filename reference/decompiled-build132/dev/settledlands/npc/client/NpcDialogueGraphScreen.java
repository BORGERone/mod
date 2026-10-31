package dev.settledlands.npc.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.settledlands.npc.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/** Pan/zoom, typed nodes, drag-to-connect and a scrollable inspector. No per-frame layout/network writes. */
public final class NpcDialogueGraphScreen extends Screen implements NpcUi.PopupHost {
    private final int entityId;
    private final int focus;
    private final Screen parent;
    public final List<NpcConfigScreen.MobilityGroupOption> mobilityGroups = new ArrayList<>();
    private NpcProfile profile;
    private NpcDialogueGraph graph;
    /** Tabbed sub-canvases: Main dialogue / Walk schedule / Anim behaviours (Unreal-style separate graphs). */
    private static final int TAB_MAIN=0,TAB_WALK=1,TAB_ANIM=2;
    private final NpcDialogueGraph[] graphs=new NpcDialogueGraph[3];
    private int tab=TAB_MAIN;
    private final NpcGraphCanvas canvas=new NpcGraphCanvas();
    private final NpcGraphContextMenu contextMenu=new NpcGraphContextMenu();
    private final Deque<NpcDialogueGraph> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private final Map<Integer,String> summaries=new HashMap<>();
    private final List<Label> labels=new ArrayList<>();
    private final List<AbstractWidget> inspectorWidgets=new ArrayList<>();
    private boolean buildingInspector;
    private int panelTop=38;
    private int topBarRight=64;
    public final Set<Integer> multiSelected = new LinkedHashSet<>();
    private boolean spaceDown;
    private boolean selectingBox;
    private double boxStartX, boxStartY, boxCurrentX, boxCurrentY;
    private double dragMouseWorldX, dragMouseWorldY;
    private final Map<Integer, int[]> dragOrigins = new HashMap<>();
    private int selected=-1,linkFrom=-1,linkFromPort=0,dragging=-1,sidebarScroll,sidebarHeight,revision,lastSent=-1;
    private boolean sentMain,sentWalk,sentAnim;
    private long closeWarnAt;
    private double dragX,dragY,panStartX,panStartY;
    private boolean panning,canvasPanMoved,dirty,suppress,focusOnInit=true;
    private long propertyUndoAt;
    private String status="",importError="";
    private int inspectorX,inspectorY,inspectorW,inspectorH;
    private AbstractWidget triggerBox;
    private NpcUi.NpcDropdown openDropdown;

    private boolean isSpaceDown() {
        if (getFocused() instanceof EditBox || getFocused() instanceof MultiLineEditBox) return false;
        try {
            long window = Minecraft.getInstance().getWindow().getWindow();
            if (window != 0 && InputConstants.isKeyDown(window, 32)) return true;
        } catch (Throwable ignored) {}
        return spaceDown;
    }

    private boolean isControlDownSafe() {
        try {
            return hasControlDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isShiftDownSafe() {
        try {
            return hasShiftDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void updateBoxSelection() {
        double minX = Math.min(boxStartX, boxCurrentX);
        double maxX = Math.max(boxStartX, boxCurrentX);
        double minY = Math.min(boxStartY, boxCurrentY);
        double maxY = Math.max(boxStartY, boxCurrentY);

        multiSelected.clear();
        for (NpcDialogueGraph.Node n : graph.nodes.values()) {
            double nx1 = canvas.screenX(n.x);
            double ny1 = canvas.screenY(n.y);
            double nx2 = nx1 + NpcGraphCanvas.NODE_W * canvas.zoom;
            double ny2 = ny1 + NpcGraphCanvas.NODE_H * canvas.zoom;
            if (!(nx2 < minX || nx1 > maxX || ny2 < minY || ny1 > maxY)) {
                multiSelected.add(n.id);
            }
        }
        if (multiSelected.size() == 1) {
            selected = multiSelected.iterator().next();
        } else if (multiSelected.isEmpty()) {
            selected = -1;
        } else {
            if (!multiSelected.contains(selected)) {
                selected = multiSelected.iterator().next();
            }
        }
    }
    private static final class Label {
        final String key;
        final int x;
        int y;
        Label(String key,int x,int y){this.key=key;this.x=x;this.y=y;}
    }

    @Override public void openDropdown(NpcUi.NpcDropdown dd){
        this.openDropdown=dd;
    }
    @Override public NpcUi.NpcDropdown activeDropdown(){
        return this.openDropdown;
    }

    public NpcDialogueGraphScreen(int entityId,NpcProfile profile,Screen parent,int focus) {
        super(Component.translatable("gui.settledlands.graph.title"));
        this.entityId=entityId;this.focus=focus;this.profile=profile.copy();this.parent=parent;
        if(parent instanceof NpcConfigScreen cfg){
            this.mobilityGroups.addAll(cfg.mobilityGroups);
        }
        try {graph=profile.dialogueGraph==null?NpcDialogueGraph.fromLegacy(profile):profile.dialogueGraph.copy();}
        catch(IllegalArgumentException e){graph=new NpcDialogueGraph();importError=e.getMessage();status=tr("gui.settledlands.graph.import_failed");}
        graphs[TAB_MAIN]=graph;
        graphs[TAB_WALK]=this.profile.walkGraph==null?null:this.profile.walkGraph.copy();
        graphs[TAB_ANIM]=this.profile.animGraph==null?null:this.profile.animGraph.copy();
        if(importError.isEmpty()){
            if(focus==-2||focus==-3)selected=graph.specialNode(focus==-2?NpcDialogueGraph.TRAVEL:NpcDialogueGraph.ARRIVAL);
            else if(focus>=0)selected=graph.topicNode(focus);
            else selected=graph.entry;
            if(focus!=-2&&focus!=-3&&!graph.enabled){graph.enabled=true;dirty=true;}
        }
    }
    public int entityId(){return entityId;}
    public static void open(int entityId,NpcProfile profile,Screen parent,int focus){
        if(profile!=null)Minecraft.getInstance().setScreen(new NpcDialogueGraphScreen(entityId,profile,parent,focus));
    }
    private String tr(String key){return Component.translatable(key).getString();}
    private NpcDialogueGraph.Node node(){return graph.get(selected);}
    private void changed(){dirty=true;revision++;summaries.remove(selected);}
    /** Switch the active canvas (Main/Walk/Anim). Each tab owns its graph and persisted camera. */
    private void switchTab(int target){
        if(target==tab||target<TAB_MAIN||target>TAB_ANIM)return;
        syncCamera();
        graphs[tab]=graph;
        tab=target;
        if(graphs[tab]==null){
            graphs[tab]=new NpcDialogueGraph();
            graphs[tab].enabled=false;
        }
        graph=graphs[tab];
        undo.clear();redo.clear();summaries.clear();multiSelected.clear();selected=-1;sidebarScroll=0;
        linkFrom=-1;dragging=-1;panning=false;selectingBox=false;
        contextMenu.cancel();
        if(this.openDropdown!=null){this.openDropdown.closePopup();this.openDropdown=null;}
        if(graph.hasCamera){canvas.panX=graph.panX;canvas.panY=graph.panY;canvas.zoom=graph.zoom<=0.001?1.0:graph.zoom;}
        else canvas.frameAll(graph);
        changed();widgets();
    }
    private void remember(){undo.addLast(graph.copy());while(undo.size()>24)undo.removeFirst();redo.clear();}
    private void propertyChange(Runnable change){if(suppress)return;long now=System.currentTimeMillis();if(now-propertyUndoAt>700){remember();propertyUndoAt=now;}change.run();changed();}

    @Override protected void init(){
        contextMenu.cancel();
        inspectorW=Math.min(230,Math.max(180,width/4));
        inspectorX=width-inspectorW-10;
        inspectorY=10;
        inspectorH=height-20;
        canvas.bounds(0,0,width,height);
        if(focusOnInit){
            if(graph.hasCamera){
                canvas.panX=graph.panX;canvas.panY=graph.panY;canvas.zoom=graph.zoom<=0.001?1.0:graph.zoom;
            }
            if(focus==-2||focus==-3||focus>=0){
                canvas.focus(node());
            }else if(!graph.hasCamera){
                canvas.focus(node());
            }
            focusOnInit=false;
        }
        widgets();
    }
    private Button button(String key,int x,int y,int w,Runnable action){
        return button(key,x,y,w,20,action);
    }
    private Button button(String key,int x,int y,int w,int h,Runnable action){
        Button widget=addRenderableWidget(Button.builder(Component.translatable(key),b->action.run()).bounds(x,y,w,h).build());
        if(buildingInspector)inspectorWidgets.add(widget);return widget;
    }
    private void widgets(){
        if(this.openDropdown!=null){this.openDropdown.closePopup();this.openDropdown=null;}
        clearWidgets();triggerBox=null;labels.clear();inspectorWidgets.clear();buildingInspector=false;suppress=true;

        // Floating Undo and Redo buttons directly over the graph
        button("gui.settledlands.graph.undo",10,10,24,20,()->history(false));
        button("gui.settledlands.graph.redo",38,10,24,20,()->history(true));

        // Graph tabs (Main / Walk / Anim) — independent canvases
        String[] tabKeys={"gui.settledlands.graph.tab_main","gui.settledlands.graph.tab_walk","gui.settledlands.graph.tab_anim"};
        int[] tabWidths={62,94,84};
        int tabX=66;
        for(int ti=0;ti<3;ti++){
            final int ft=ti;
            Button tb=button(tabKeys[ti],tabX,10,tabWidths[ti],20,()->switchTab(ft));
            tb.active=(ti!=tab);
            tabX+=tabWidths[ti]+4;
        }
        topBarRight=tabX-4;

        inspectorW=Math.min(230,Math.max(180,width/4));
        inspectorX=width-inspectorW-10;
        inspectorY=10;
        inspectorH=height-20;

        NpcDialogueGraph.Node n=node();
        int contentX=inspectorX+8;
        int contentW=inspectorW-16;
        int y=inspectorY+24-sidebarScroll;

        if(selected>=0&&n!=null){
            buildingInspector=true;
            if(n.type==NpcDialogueGraph.TRIGGER){
                label("gui.settledlands.graph.trigger_presets",contentX,y);y+=11;
                java.util.List<Component> presets=java.util.List.of(
                    Component.translatable("gui.settledlands.graph.preset_choose"),
                    Component.translatable("gui.settledlands.graph.preset_play"),
                    Component.translatable("gui.settledlands.graph.preset_go"),
                    Component.translatable("gui.settledlands.graph.preset_give"),
                    Component.translatable("gui.settledlands.graph.preset_relation"),
                    Component.translatable("gui.settledlands.graph.preset_shop"),
                    Component.translatable("gui.settledlands.graph.preset_close"),
                    Component.translatable("gui.settledlands.graph.preset_purgatory"),
                    Component.translatable("gui.settledlands.graph.preset_sound"),
                    Component.translatable("gui.settledlands.graph.preset_say")
                );
                AbstractWidget dd=addRenderableWidget(NpcUi.dropdown(contentX,y,contentW,presets,0,idx->{
                    if(idx<=0)return;
                    String tmpl=switch(idx){
                        case 1 -> "/play ";
                        case 2 -> "/go ";
                        case 3 -> "/give @p ";
                        case 4 -> "trigger:relation 5";
                        case 5 -> "trigger:shop";
                        case 6 -> "trigger:close";
                        case 7 -> "trigger:purgatory";
                        case 8 -> "/playsound ";
                        case 9 -> "/say ";
                        default -> "";
                    };
                    if(!tmpl.isEmpty()){
                        remember();n.action=tmpl;changed();
                        widgets();
                        if(triggerBox!=null){
                            triggerBox.setFocused(true);
                            setFocused(triggerBox);
                        }
                    }
                }));
                if(buildingInspector)inspectorWidgets.add(dd);
                y+=20;
                label("gui.settledlands.graph.trigger_command",contentX,y);y+=11;
                int actBoxH=calcBoxHeight(calcTextLines(n.action,contentW),20,80);
                MultiLineEditBox mb=multiLineTextBox(n.action,2048,contentX,y,contentW,20,80,Component.translatable("gui.settledlands.graph.trigger_hint"),value->n.action=value);
                triggerBox=mb;
                y+=actBoxH+4;

                y=incomingIfSection(n,contentX,y,contentW);

                int halfW=(contentW-4)/2;
                button("gui.settledlands.graph.unlink",contentX,y,halfW,16,()->{remember();graph.disconnectOutputs(n.id);changed();});
                button("gui.settledlands.graph.delete",contentX+halfW+4,y,contentW-halfW-4,16,()->{remember();graph.remove(n.id);selected=-1;changed();widgets();});
                y+=20;
            }else if(n.type==NpcDialogueGraph.VARIABLE){
                label("gui.settledlands.graph.var_name",contentX,y);y+=11;
                textBox(n.pointsName,32,contentX,y,contentW,16,v->n.pointsName=v);y+=20;

                label("gui.settledlands.graph.var_delta",contentX,y);y+=11;
                numberBox(n.varValue,-1000000,1000000,contentX,y,contentW,16,v->n.varValue=v);y+=20;

                button(n.pointsShared?"gui.settledlands.graph.shared_on":"gui.settledlands.graph.shared_off",contentX,y,contentW,16,()->{
                    remember();n.pointsShared=!n.pointsShared;changed();widgets();
                });
                y+=20;

                label("gui.settledlands.graph.action",contentX,y);y+=11;
                int actBoxH=calcBoxHeight(calcTextLines(n.action,contentW),16,60);
                MultiLineEditBox mb=multiLineTextBox(n.action,2048,contentX,y,contentW,16,60,Component.translatable("gui.settledlands.graph.trigger_hint"),value->n.action=value);
                triggerBox=mb;
                y+=actBoxH+4;

                int halfW=(contentW-4)/2;
                button("gui.settledlands.graph.unlink",contentX,y,halfW,16,()->{remember();graph.disconnectOutputs(n.id);changed();});
                button("gui.settledlands.graph.delete",contentX+halfW+4,y,contentW-halfW-4,16,()->{remember();graph.remove(n.id);selected=-1;changed();widgets();});
                y+=20;
            }else if(n.type==NpcDialogueGraph.IF){
                label("gui.settledlands.graph.var_op",contentX,y);y+=11;
                java.util.List<Component> ops=java.util.List.of(
                    Component.literal("==  (Равно)"),
                    Component.literal("!=  (Не равно)"),
                    Component.literal(">=  (Больше или равно)"),
                    Component.literal(">   (Больше)"),
                    Component.literal("<=  (Меньше или равно)"),
                    Component.literal("<   (Меньше)")
                );
                AbstractWidget opDd=addRenderableWidget(NpcUi.dropdown(contentX,y,contentW,ops,n.varOp,idx->{
                    remember();n.varOp=idx;changed();
                }));
                if(buildingInspector)inspectorWidgets.add(opDd);
                y+=20;

                label("gui.settledlands.graph.var_compare",contentX,y);y+=11;
                numberBox(n.varCompare,-1000000,1000000,contentX,y,contentW,16,v->n.varCompare=v);y+=20;

                label("gui.settledlands.graph.var_input",contentX,y);y+=11;
                if(n.varInputNode>=0){
                    NpcDialogueGraph.Node inNode=graph.get(n.varInputNode);
                    String inDesc=inNode!=null?("#"+inNode.id+(!inNode.pointsName.isBlank()?" ("+inNode.pointsName+")":"")):"#"+n.varInputNode;
                    label(Component.translatable("gui.settledlands.graph.var_input_linked",n.varInputNode).getString()+" "+inDesc,contentX,y);y+=12;
                    button("gui.settledlands.graph.unlink",contentX,y,contentW,16,()->{remember();n.varInputNode=-1;changed();widgets();});y+=20;
                }else{
                    label("gui.settledlands.graph.var_input_none",contentX,y);y+=12;
                    label("gui.settledlands.graph.var_name",contentX,y);y+=11;
                    textBox(n.pointsName,32,contentX,y,contentW,16,v->n.pointsName=v);y+=20;
                    button(n.pointsShared?"gui.settledlands.graph.shared_on":"gui.settledlands.graph.shared_off",contentX,y,contentW,16,()->{
                        remember();n.pointsShared=!n.pointsShared;changed();widgets();
                    });
                    y+=20;
                }

                label("gui.settledlands.graph.action",contentX,y);y+=11;
                int actBoxH=calcBoxHeight(calcTextLines(n.action,contentW),16,60);
                MultiLineEditBox mb=multiLineTextBox(n.action,2048,contentX,y,contentW,16,60,Component.translatable("gui.settledlands.graph.trigger_hint"),value->n.action=value);
                triggerBox=mb;
                y+=actBoxH+4;

                int halfW=(contentW-4)/2;
                button("gui.settledlands.graph.unlink",contentX,y,halfW,16,()->{remember();graph.disconnectOutputs(n.id);changed();});
                button("gui.settledlands.graph.delete",contentX+halfW+4,y,contentW-halfW-4,16,()->{remember();graph.remove(n.id);selected=-1;changed();widgets();});
                y+=20;
            }else if(n.type==NpcDialogueGraph.CHECK){
                label("gui.settledlands.graph.check_target",contentX,y);y+=11;
                java.util.List<Component> targets=java.util.List.of(
                    Component.translatable("gui.settledlands.graph.target_player"),
                    Component.translatable("gui.settledlands.graph.target_npc"),
                    Component.translatable("gui.settledlands.graph.target_monster"),
                    Component.translatable("gui.settledlands.graph.target_any")
                );
                AbstractWidget targetDd=addRenderableWidget(NpcUi.dropdown(contentX,y,contentW,targets,Math.max(0,Math.min(3,n.checkTarget)),idx->{
                    remember();n.checkTarget=idx;changed();
                }));
                if(buildingInspector)inspectorWidgets.add(targetDd);
                y+=20;

                label("gui.settledlands.graph.check_radius",contentX,y);y+=11;
                numberBox(n.checkRadius,1,64,contentX,y,contentW,16,v->n.checkRadius=Math.max(1,Math.min(64,v)));y+=20;

                label("gui.settledlands.graph.check_cooldown",contentX,y);y+=11;
                numberBox(n.checkCooldown,0,3600,contentX,y,contentW,16,v->n.checkCooldown=Math.max(0,Math.min(3600,v)));y+=20;

                y=incomingIfSection(n,contentX,y,contentW);

                label("gui.settledlands.graph.action",contentX,y);y+=11;
                int actBoxH=calcBoxHeight(calcTextLines(n.action,contentW),16,60);
                MultiLineEditBox mb=multiLineTextBox(n.action,2048,contentX,y,contentW,16,60,Component.translatable("gui.settledlands.graph.trigger_hint"),value->n.action=value);
                triggerBox=mb;
                y+=actBoxH+4;

                int halfW=(contentW-4)/2;
                button("gui.settledlands.graph.unlink",contentX,y,halfW,16,()->{remember();graph.disconnectOutputs(n.id);changed();});
                button("gui.settledlands.graph.delete",contentX+halfW+4,y,contentW-halfW-4,16,()->{remember();graph.remove(n.id);selected=-1;changed();widgets();});
                y+=20;
            }else if(n.type==NpcDialogueGraph.DAMAGE){
                label("gui.settledlands.graph.damage_source",contentX,y);y+=11;
                java.util.List<Component> filters=java.util.List.of(
                    Component.translatable("gui.settledlands.graph.damage_any"),
                    Component.translatable("gui.settledlands.graph.damage_player"),
                    Component.translatable("gui.settledlands.graph.damage_monster"),
                    Component.translatable("gui.settledlands.graph.damage_non_player")
                );
                AbstractWidget filterDd=addRenderableWidget(NpcUi.dropdown(contentX,y,contentW,filters,Math.max(0,Math.min(3,n.damageFilter)),idx->{
                    remember();n.damageFilter=idx;changed();
                }));
                if(buildingInspector)inspectorWidgets.add(filterDd);
                y+=20;

                label("gui.settledlands.graph.min_damage",contentX,y);y+=11;
                numberBox(n.minDamage,0,1000,contentX,y,contentW,16,v->n.minDamage=Math.max(0,Math.min(1000,v)));y+=20;

                label("gui.settledlands.graph.check_cooldown",contentX,y);y+=11;
                numberBox(n.damageCooldown,0,3600,contentX,y,contentW,16,v->n.damageCooldown=Math.max(0,Math.min(3600,v)));y+=20;

                y=incomingIfSection(n,contentX,y,contentW);

                label("gui.settledlands.graph.action",contentX,y);y+=11;
                int actBoxH=calcBoxHeight(calcTextLines(n.action,contentW),16,60);
                MultiLineEditBox mb=multiLineTextBox(n.action,2048,contentX,y,contentW,16,60,Component.translatable("gui.settledlands.graph.trigger_hint"),value->n.action=value);
                triggerBox=mb;
                y+=actBoxH+4;

                int halfW=(contentW-4)/2;
                button("gui.settledlands.graph.unlink",contentX,y,halfW,16,()->{remember();graph.disconnectOutputs(n.id);changed();});
                button("gui.settledlands.graph.delete",contentX+halfW+4,y,contentW-halfW-4,16,()->{remember();graph.remove(n.id);selected=-1;changed();widgets();});
                y+=20;
            }else if(n.type==NpcDialogueGraph.TIME){
                label("gui.settledlands.graph.time_mode",contentX,y);y+=11;
                java.util.List<Component> modes=java.util.List.of(
                    Component.translatable("gui.settledlands.graph.time_dawn"),
                    Component.translatable("gui.settledlands.graph.time_noon"),
                    Component.translatable("gui.settledlands.graph.time_sunset"),
                    Component.translatable("gui.settledlands.graph.time_midnight"),
                    Component.translatable("gui.settledlands.graph.time_custom")
                );
                AbstractWidget modeDd=addRenderableWidget(NpcUi.dropdown(contentX,y,contentW,modes,Math.max(0,Math.min(4,n.timeMode)),idx->{
                    remember();n.timeMode=idx;changed();widgets();
                }));
                if(buildingInspector)inspectorWidgets.add(modeDd);
                y+=20;

                if(n.timeMode==NpcDialogueGraph.TIME_CUSTOM){
                    label("gui.settledlands.graph.time_ticks",contentX,y);y+=11;
                    numberBox(n.timeTicks,0,23999,contentX,y,contentW,16,v->n.timeTicks=Math.max(0,Math.min(23999,v)));y+=20;
                }

                y=incomingIfSection(n,contentX,y,contentW);

                label("gui.settledlands.graph.action",contentX,y);y+=11;
                int actBoxH=calcBoxHeight(calcTextLines(n.action,contentW),16,60);
                MultiLineEditBox mb=multiLineTextBox(n.action,2048,contentX,y,contentW,16,60,Component.translatable("gui.settledlands.graph.trigger_hint"),value->n.action=value);
                triggerBox=mb;
                y+=actBoxH+4;

                int halfW=(contentW-4)/2;
                button("gui.settledlands.graph.unlink",contentX,y,halfW,16,()->{remember();graph.disconnectOutputs(n.id);changed();});
                button("gui.settledlands.graph.delete",contentX+halfW+4,y,contentW-halfW-4,16,()->{remember();graph.remove(n.id);selected=-1;changed();widgets();});
                y+=20;
            }else if(n.type==NpcDialogueGraph.TIMER){
                label("gui.settledlands.graph.timer_interval",contentX,y);y+=11;
                numberBox(n.timerInterval,1,86400,contentX,y,contentW,16,v->n.timerInterval=Math.max(1,Math.min(86400,v)));y+=20;

                label("gui.settledlands.graph.timer_repeat",contentX,y);y+=11;
                AbstractWidget repeatBtn=button(n.timerRepeat?"gui.settledlands.graph.timer_repeat_on":"gui.settledlands.graph.timer_repeat_off",contentX,y,contentW,16,()->{
                    remember();n.timerRepeat=!n.timerRepeat;changed();widgets();
                });
                y+=20;

                y=incomingIfSection(n,contentX,y,contentW);

                label("gui.settledlands.graph.action",contentX,y);y+=11;
                int actBoxH=calcBoxHeight(calcTextLines(n.action,contentW),16,60);
                MultiLineEditBox mb=multiLineTextBox(n.action,2048,contentX,y,contentW,16,60,Component.translatable("gui.settledlands.graph.trigger_hint"),value->n.action=value);
                triggerBox=mb;
                y+=actBoxH+4;

                int halfW=(contentW-4)/2;
                button("gui.settledlands.graph.unlink",contentX,y,halfW,16,()->{remember();graph.disconnectOutputs(n.id);changed();});
                button("gui.settledlands.graph.delete",contentX+halfW+4,y,contentW-halfW-4,16,()->{remember();graph.remove(n.id);selected=-1;changed();widgets();});
                y+=20;
            }else if(n.type==NpcDialogueGraph.ANIMATION){
                label("gui.settledlands.graph.anim_select",contentX,y);y+=11;
                List<String> availableAnims=new ArrayList<>();
                if(profile!=null&&profile.texture!=null&&!profile.texture.isBlank()){
                    dev.settledlands.model.BbModel m=dev.settledlands.model.BbModelManager.getModel(profile.texture);
                    if(m!=null)availableAnims.addAll(m.getAnimations().keySet());
                }
                if(availableAnims.isEmpty()){
                    dev.settledlands.model.BbModel provodnik=dev.settledlands.model.BbModelManager.getModel("provodnik.bbmodel");
                    if(provodnik!=null)availableAnims.addAll(provodnik.getAnimations().keySet());
                }
                Collections.sort(availableAnims);
                if(availableAnims.isEmpty()){
                    availableAnims.add("idle");
                    availableAnims.add("walk");
                    availableAnims.add("talk");
                }
                if(n.animName.isBlank()&&!availableAnims.isEmpty()){
                    n.animName=availableAnims.get(0);
                }
                List<Component> animItems=new ArrayList<>();
                int selectedAnimIdx=0;
                for(int i=0;i<availableAnims.size();i++){
                    String a=availableAnims.get(i);
                    animItems.add(Component.literal(a));
                    if(a.equalsIgnoreCase(n.animName)){
                        selectedAnimIdx=i;
                    }
                }
                final List<String> fAnims=availableAnims;
                AbstractWidget animDd=addRenderableWidget(NpcUi.dropdown(contentX,y,contentW,animItems,selectedAnimIdx,idx->{
                    remember();
                    if(idx>=0&&idx<fAnims.size()){
                        n.animName=fAnims.get(idx);
                    }
                    changed();widgets();
                }));
                if(buildingInspector)inspectorWidgets.add(animDd);
                y+=20;

                label("gui.settledlands.graph.anim_repeat",contentX,y);y+=11;
                AbstractWidget repeatBtn=button(n.animLoop?"gui.settledlands.graph.anim_repeat_on":"gui.settledlands.graph.anim_repeat_off",contentX,y,contentW,16,()->{
                    remember();n.animLoop=!n.animLoop;changed();widgets();
                });
                y+=20;

                label("gui.settledlands.graph.anim_bone",contentX,y);y+=11;
                List<String> boneKeys=new ArrayList<>();
                List<Component> boneLabels=new ArrayList<>();
                boneKeys.add("");
                boneLabels.add(Component.translatable("gui.settledlands.graph.anim_bone_all"));
                boneKeys.add("head");
                boneLabels.add(Component.translatable("gui.settledlands.graph.anim_bone_head"));
                boneKeys.add("right_arm");
                boneLabels.add(Component.translatable("gui.settledlands.graph.anim_bone_right_arm"));
                boneKeys.add("left_arm");
                boneLabels.add(Component.translatable("gui.settledlands.graph.anim_bone_left_arm"));
                boneKeys.add("body");
                boneLabels.add(Component.translatable("gui.settledlands.graph.anim_bone_body"));
                boneKeys.add("legs");
                boneLabels.add(Component.translatable("gui.settledlands.graph.anim_bone_legs"));

                if(profile!=null&&profile.texture!=null&&!profile.texture.isBlank()){
                    dev.settledlands.model.BbModel m=dev.settledlands.model.BbModelManager.getModel(profile.texture);
                    if(m!=null){
                        for(String pName:m.getPartsByName().keySet()){
                            if(!boneKeys.contains(pName)&&pName.length()>1){
                                boneKeys.add(pName);
                                boneLabels.add(Component.literal("🦴 "+pName));
                            }
                        }
                    }
                }
                int selectedBoneIdx=0;
                for(int i=0;i<boneKeys.size();i++){
                    if(boneKeys.get(i).equalsIgnoreCase(n.animBone)){
                        selectedBoneIdx=i;
                        break;
                    }
                }
                final List<String> fBoneKeys=boneKeys;
                AbstractWidget boneDd=addRenderableWidget(NpcUi.dropdown(contentX,y,contentW,boneLabels,selectedBoneIdx,idx->{
                    remember();
                    if(idx>=0&&idx<fBoneKeys.size()){
                        n.animBone=fBoneKeys.get(idx);
                    }
                    changed();widgets();
                }));
                if(buildingInspector)inspectorWidgets.add(boneDd);
                y+=20;

                y=incomingIfSection(n,contentX,y,contentW);

                label("gui.settledlands.graph.action",contentX,y);y+=11;
                int actBoxH=calcBoxHeight(calcTextLines(n.action,contentW),16,60);
                MultiLineEditBox mb=multiLineTextBox(n.action,2048,contentX,y,contentW,16,60,Component.translatable("gui.settledlands.graph.trigger_hint"),value->n.action=value);
                triggerBox=mb;
                y+=actBoxH+4;

                int halfW=(contentW-4)/2;
                button("gui.settledlands.graph.unlink",contentX,y,halfW,16,()->{remember();graph.disconnectOutputs(n.id);changed();});
                button("gui.settledlands.graph.delete",contentX+halfW+4,y,contentW-halfW-4,16,()->{remember();graph.remove(n.id);selected=-1;changed();widgets();});
                y+=20;
            }else if(n.type==NpcDialogueGraph.CUTSCENE){
                label("gui.settledlands.graph.cutscene_select",contentX,y);y+=11;
                List<String> scenes=new ArrayList<>(CutsceneCameraClient.scenes());
                List<Component> sceneItems=new ArrayList<>();
                for(String s:scenes)sceneItems.add(Component.literal(s));
                int selectedSceneIdx=-1;
                for(int i=0;i<scenes.size();i++){
                    if(scenes.get(i).equalsIgnoreCase(n.cutsceneName)){selectedSceneIdx=i;break;}
                }
                if(selectedSceneIdx<0&&!scenes.isEmpty()&&n.cutsceneName.isBlank()){
                    selectedSceneIdx=0;n.cutsceneName=scenes.get(0);
                }
                final List<String> fScenes=scenes;
                AbstractWidget sceneDd=addRenderableWidget(NpcUi.dropdown(contentX,y,contentW,sceneItems,Math.max(0,selectedSceneIdx),idx->{
                    remember();
                    if(idx>=0&&idx<fScenes.size()){
                        n.cutsceneName=fScenes.get(idx);
                    }
                    changed();widgets();
                }));
                if(buildingInspector)inspectorWidgets.add(sceneDd);
                y+=20;

                y=incomingIfSection(n,contentX,y,contentW);

                label("gui.settledlands.graph.action",contentX,y);y+=11;
                int actBoxH=calcBoxHeight(calcTextLines(n.action,contentW),16,60);
                MultiLineEditBox mb=multiLineTextBox(n.action,2048,contentX,y,contentW,16,60,Component.translatable("gui.settledlands.graph.trigger_hint"),value->n.action=value);
                triggerBox=mb;
                y+=actBoxH+4;

                int halfW=(contentW-4)/2;
                button("gui.settledlands.graph.unlink",contentX,y,halfW,16,()->{remember();graph.disconnectOutputs(n.id);changed();});
                button("gui.settledlands.graph.delete",contentX+halfW+4,y,contentW-halfW-4,16,()->{remember();graph.remove(n.id);selected=-1;changed();widgets();});
                y+=20;
            }else if(n.type==NpcDialogueGraph.QUEST){
                label("gui.settledlands.graph.quest_title",contentX,y);y+=11;
                int boxH=calcBoxHeight(calcTextLines(n.text,contentW),16,50);
                multiLineTextBox(n.text,240,contentX,y,contentW,16,50,value->n.text=value);
                y+=boxH+4;

                label("gui.settledlands.graph.min_relation",contentX,y);y+=11;
                numberBox(n.minRelation,0,100,contentX,y,contentW,16,v->n.minRelation=Math.max(0,Math.min(100,v)));
                y+=20;

                y=incomingIfSection(n,contentX,y,contentW);

                label("gui.settledlands.graph.action",contentX,y);y+=11;
                int actBoxH=calcBoxHeight(calcTextLines(n.action,contentW),16,60);
                MultiLineEditBox mb=multiLineTextBox(n.action,2048,contentX,y,contentW,16,60,Component.translatable("gui.settledlands.graph.trigger_hint"),value->n.action=value);
                triggerBox=mb;
                y+=actBoxH+4;

                label("gui.settledlands.graph.quest_hint",contentX,y);y+=14;

                int halfW=(contentW-4)/2;
                button("gui.settledlands.graph.unlink",contentX,y,halfW,16,()->{remember();graph.disconnectOutputs(n.id);changed();});
                button("gui.settledlands.graph.delete",contentX+halfW+4,y,contentW-halfW-4,16,()->{remember();graph.remove(n.id);selected=-1;changed();widgets();});
                y+=20;
            }else if(n.type==NpcDialogueGraph.MOVE){
                label("gui.settledlands.graph.move_coords",contentX,y);y+=11;
                textBox(n.moveCoords,64,contentX,y,contentW,16,v->n.moveCoords=v);y+=20;

                label("gui.settledlands.graph.move_speed",contentX,y);y+=11;
                doubleBox(n.moveSpeed,0.1,5.0,contentX,y,contentW,16,v->n.moveSpeed=Math.max(0.1,Math.min(5.0,v)));y+=20;

                label("gui.settledlands.graph.move_escort",contentX,y);y+=11;
                button(n.moveEscort?"gui.settledlands.graph.move_escort_on":"gui.settledlands.graph.move_escort_off",contentX,y,contentW,16,()->{
                    remember();n.moveEscort=!n.moveEscort;changed();widgets();
                });
                y+=20;

                y=incomingIfSection(n,contentX,y,contentW);

                label("gui.settledlands.graph.action",contentX,y);y+=11;
                int actBoxH=calcBoxHeight(calcTextLines(n.action,contentW),16,60);
                MultiLineEditBox mb=multiLineTextBox(n.action,2048,contentX,y,contentW,16,60,Component.translatable("gui.settledlands.graph.trigger_hint"),value->n.action=value);
                triggerBox=mb;
                y+=actBoxH+4;

                label("gui.settledlands.graph.move_hint",contentX,y);y+=14;

                int halfW=(contentW-4)/2;
                button("gui.settledlands.graph.unlink",contentX,y,halfW,16,()->{remember();graph.disconnectOutputs(n.id);changed();});
                button("gui.settledlands.graph.delete",contentX+halfW+4,y,contentW-halfW-4,16,()->{remember();graph.remove(n.id);selected=-1;changed();widgets();});
                y+=20;
            }else if(n.type==NpcDialogueGraph.MOBILITY){
                label("gui.settledlands.graph.mobility_group",contentX,y);y+=11;
                if(!this.mobilityGroups.isEmpty()){
                    List<Component> entries=new ArrayList<>();
                    entries.add(Component.translatable("gui.settledlands.graph.no_group"));
                    for(NpcConfigScreen.MobilityGroupOption opt:this.mobilityGroups){
                        entries.add(Component.literal(opt.name().isEmpty()?opt.id():opt.name()));
                    }
                    int selIdx=0;
                    for(int i=0;i<this.mobilityGroups.size();i++){
                        if(this.mobilityGroups.get(i).id().equals(n.mobilityGroupId)){selIdx=i+1;break;}
                    }
                    NpcUi.NpcDropdown groupPick=new NpcUi.NpcDropdown(contentX,y,contentW,entries,selIdx,idx->{
                        remember();
                        if(idx<=0){
                            n.mobilityGroupId="";
                            n.mobilityGroupName="";
                        }else{
                            NpcConfigScreen.MobilityGroupOption opt=this.mobilityGroups.get(idx-1);
                            n.mobilityGroupId=opt.id();
                            n.mobilityGroupName=opt.name();
                        }
                        changed();widgets();
                    });
                    this.addRenderableWidget(groupPick);
                    y+=22;
                }else{
                    textBox(n.mobilityGroupName.isEmpty()?n.mobilityGroupId:n.mobilityGroupName,32,contentX,y,contentW,16,v->{
                        n.mobilityGroupName=v;
                        if(n.mobilityGroupId.isEmpty()) n.mobilityGroupId=v;
                    });y+=20;
                }
                y=incomingIfSection(n,contentX,y,contentW);

                label("gui.settledlands.graph.action",contentX,y);y+=11;
                int actBoxH=calcBoxHeight(calcTextLines(n.action,contentW),16,60);
                MultiLineEditBox mb=multiLineTextBox(n.action,2048,contentX,y,contentW,16,60,Component.translatable("gui.settledlands.graph.trigger_hint"),value->n.action=value);
                triggerBox=mb;
                y+=actBoxH+4;

                label("gui.settledlands.graph.mobility_hint",contentX,y);y+=24;

                int halfW=(contentW-4)/2;
                button("gui.settledlands.graph.unlink",contentX,y,halfW,16,()->{remember();graph.disconnectOutputs(n.id);changed();});
                button("gui.settledlands.graph.delete",contentX+halfW+4,y,contentW-halfW-4,16,()->{remember();graph.remove(n.id);selected=-1;changed();widgets();});
                y+=20;
            }else if(n.type==NpcDialogueGraph.RANDOM){
                label("gui.settledlands.graph.random_range",contentX,y);y+=11;
                int halfFieldW=(contentW-6)/2;
                numberBox(n.randomMin,1,64,contentX,y,halfFieldW,16,v->{
                    remember();
                    n.randomMin=Math.max(1,Math.min(n.randomMax,v));
                    changed();
                });
                numberBox(n.randomMax,1,64,contentX+halfFieldW+6,y,halfFieldW,16,v->{
                    remember();
                    n.randomMax=Math.max(n.randomMin,Math.min(64,v));
                    while(n.randomBranches.size()<n.randomMax) n.randomBranches.add(-1);
                    while(n.randomBranches.size()>n.randomMax) n.randomBranches.remove(n.randomBranches.size()-1);
                    changed();widgets();
                });
                y+=20;

                label("gui.settledlands.graph.random_outputs",contentX,y);y+=11;
                label("1 .. "+n.randomMax+" ("+n.randomMax+" "+tr("gui.settledlands.graph.outputs")+")",contentX,y);y+=14;

                y=incomingIfSection(n,contentX,y,contentW);

                label("gui.settledlands.graph.action",contentX,y);y+=11;
                int actBoxH=calcBoxHeight(calcTextLines(n.action,contentW),16,60);
                MultiLineEditBox mb=multiLineTextBox(n.action,2048,contentX,y,contentW,16,60,Component.translatable("gui.settledlands.graph.trigger_hint"),value->n.action=value);
                triggerBox=mb;
                y+=actBoxH+4;

                int halfW=(contentW-4)/2;
                button("gui.settledlands.graph.unlink",contentX,y,halfW,16,()->{remember();graph.disconnectOutputs(n.id);changed();});
                button("gui.settledlands.graph.delete",contentX+halfW+4,y,contentW-halfW-4,16,()->{remember();graph.remove(n.id);selected=-1;changed();widgets();});
                y+=20;
            }else{
                label("gui.settledlands.graph.text",contentX,y);y+=11;
                if(n.type==NpcDialogueGraph.LINE){
                    int maxBoxH=Math.min(100,Math.max(50,inspectorH-140));
                    int boxH=calcBoxHeight(calcTextLines(n.text,contentW),20,maxBoxH);
                    multiLineTextBox(n.text,480,contentX,y,contentW,20,maxBoxH,value->n.text=value);
                    y+=boxH+4;
                }else{
                    int boxH=calcBoxHeight(calcTextLines(n.text,contentW),16,50);
                    multiLineTextBox(n.text,240,contentX,y,contentW,16,50,value->n.text=value);
                    y+=boxH+4;
                }
                if(n.special==NpcDialogueGraph.NORMAL){
                    label("gui.settledlands.graph.action",contentX,y);y+=11;
                    int actBoxH=calcBoxHeight(calcTextLines(n.action,contentW),16,60);
                    MultiLineEditBox mb=multiLineTextBox(n.action,2048,contentX,y,contentW,16,60,Component.translatable("gui.settledlands.graph.trigger_hint"),value->n.action=value);
                    triggerBox=mb;
                    y+=actBoxH+4;
                }
                if(n.special==NpcDialogueGraph.NORMAL){
                    if(n.type==NpcDialogueGraph.LINE){
                        button("gui.settledlands.graph.entry",contentX,y,contentW,16,()->{remember();graph.entry=n.id;changed();});
                        y+=20;
                        for(int i=0;i<n.variants.size();i++){
                            NpcProfile.Variant v=n.variants.get(i);
                            label("gui.settledlands.graph.variant",contentX,y);y+=11;
                            numberBox(v.minRelation,0,100,contentX,y,36,16,a->v.minRelation=a);
                            int varW=contentW-40;
                            int varBoxH=calcBoxHeight(calcTextLines(v.text,varW),16,60);
                            multiLineTextBox(v.text,480,contentX+40,y,varW,16,60,a->v.text=a);
                            y+=varBoxH+4;
                        }
                        if(n.variants.size()<5||!n.variants.isEmpty()){
                            int varBtnW=(contentW-4)/2;
                            if(n.variants.size()<5){
                                button("gui.settledlands.graph.add_variant",contentX,y,n.variants.isEmpty()?contentW:varBtnW,16,()->{remember();n.variants.add(new NpcProfile.Variant(0,""));changed();widgets();});
                            }
                            if(!n.variants.isEmpty()){
                                button("gui.settledlands.graph.del_variant",contentX+varBtnW+4,y,contentW-varBtnW-4,16,()->{remember();n.variants.remove(n.variants.size()-1);changed();widgets();});
                            }
                            y+=20;
                        }
                    }else{
                        label("gui.settledlands.graph.relation",contentX,y);y+=11;
                        numberBox(n.relation,-100,100,contentX,y,contentW,16,v->n.relation=v);y+=20;

                        int halfW=(contentW-6)/2;
                        label("gui.settledlands.graph.min_relation",contentX,y);
                        label("gui.settledlands.graph.max_relation",contentX+halfW+6,y);
                        y+=11;
                        numberBox(n.minRelation,0,100,contentX,y,halfW,16,v->n.minRelation=v);
                        numberBox(n.maxRelation,0,100,contentX+halfW+6,y,halfW,16,v->n.maxRelation=v);
                        y+=20;

                        label("gui.settledlands.graph.points",contentX,y);y+=11;
                        textBox(n.pointsName,32,contentX,y,contentW,16,v->n.pointsName=v);y+=20;

                        label("gui.settledlands.graph.min_points",contentX,y);
                        label("gui.settledlands.graph.max_points",contentX+halfW+6,y);
                        y+=11;
                        numberBox(n.minPoints,0,NpcPoints.MAX_VALUE,contentX,y,halfW,16,v->n.minPoints=v);
                        numberBox(n.maxPoints,0,NpcPoints.MAX_VALUE,contentX+halfW+6,y,halfW,16,v->n.maxPoints=v);
                        y+=20;

                        button(n.pointsShared?"gui.settledlands.graph.shared_on":"gui.settledlands.graph.shared_off",contentX,y,contentW,16,()->{
                            remember();n.pointsShared=!n.pointsShared;changed();widgets();
                        });
                        y+=20;
                    }
                    y=incomingIfSection(n,contentX,y,contentW);
                    if(n.builtinTopic>=0){label("gui.settledlands.graph.builtin",contentX,y);y+=14;}
                    int halfW=(contentW-4)/2;
                    button("gui.settledlands.graph.unlink",contentX,y,halfW,16,()->{remember();graph.disconnectOutputs(n.id);changed();});
                    button("gui.settledlands.graph.delete",contentX+halfW+4,y,contentW-halfW-4,16,()->{remember();graph.remove(n.id);selected=-1;changed();widgets();});
                    y+=20;
                }else{
                    label("gui.settledlands.graph.escort_note",contentX,y);y+=16;
                }
            }
        }
        sidebarHeight=y+sidebarScroll-(inspectorY+24);
        for(AbstractWidget widget:inspectorWidgets){
            widget.visible=widget.getY()>=inspectorY+22&&widget.getY()+widget.getHeight()<=inspectorY+inspectorH-4;
        }
        buildingInspector=false;suppress=false;
    }
    private int calcTextLines(String text,int width){
        String s=text==null||text.isEmpty()?" ":text;
        return Math.max(1,font.split(Component.literal(s),Math.max(20,width-14)).size());
    }
    private int calcBoxHeight(int lines,int minH,int maxH){
        int desired=10+lines*9;
        return Math.min(maxH,Math.max(minH,desired));
    }
    private MultiLineEditBox multiLineTextBox(String value,int max,int x,int y,int w,int minH,int maxH,Component placeholder,Consumer<String> setter){
        int lines=calcTextLines(value,w);
        int h=calcBoxHeight(lines,minH,maxH);
        MultiLineEditBox box=new MultiLineEditBox(font,x,y,Math.max(30,w),h,placeholder==null?Component.empty():placeholder,Component.empty());
        box.setValue(value==null?"":value);
        boolean[] inUpdate=new boolean[]{false};
        box.setValueListener(v->{
            if(inUpdate[0])return;
            if(v.length()>max){
                inUpdate[0]=true;
                v=v.substring(0,max);
                box.setValue(v);
                inUpdate[0]=false;
            }
            final String finalV=v;
            propertyChange(()->setter.accept(finalV));
            int newLines=calcTextLines(finalV,w);
            int newH=calcBoxHeight(newLines,minH,maxH);
            int oldH=box.getHeight();
            if(newH!=oldH){
                int delta=newH-oldH;
                box.setHeight(newH);
                for(Label l:labels){
                    if(l.y>box.getY()){
                        l.y+=delta;
                    }
                }
                for(AbstractWidget w2:inspectorWidgets){
                    if(w2!=box&&w2.getY()>=box.getY()+oldH-2){
                        w2.setY(w2.getY()+delta);
                    }
                }
                sidebarHeight+=delta;
                for(AbstractWidget w2:inspectorWidgets){
                    w2.visible=w2.getY()>=inspectorY+22&&w2.getY()+w2.getHeight()<=inspectorY+inspectorH-4;
                }
            }
        });
        addRenderableWidget(box);
        inspectorWidgets.add(box);
        return box;
    }
    private MultiLineEditBox multiLineTextBox(String value,int max,int x,int y,int w,int minH,int maxH,Consumer<String> setter){
        return multiLineTextBox(value,max,x,y,w,minH,maxH,Component.empty(),setter);
    }
    private void label(String key,int x,int y){labels.add(new Label(key,x,y));}
    private void label(String key,int y){label(key,inspectorX+8,y);}
    private EditBox textBox(String value,int max,int x,int y,int w,int h,Consumer<String> setter){
        EditBox b=new EditBox(font,x,y,Math.max(30,w),h,Component.empty());b.setMaxLength(max);b.setValue(value==null?"":value);
        b.setResponder(v->propertyChange(()->setter.accept(v)));addRenderableWidget(b);inspectorWidgets.add(b);return b;
    }
    private EditBox textBox(String value,int max,int y,int w,Consumer<String> setter){
        return textBox(value,max,inspectorX+8,y,w,20,setter);
    }
    private void numberBox(int value,int min,int max,int x,int y,int w,int h,Consumer<Integer> setter){
        EditBox b=textBox(Integer.toString(value),10,x,y,w,h,v->{try{int parsed=Integer.parseInt(v);if(parsed>=min&&parsed<=max)setter.accept(parsed);}catch(NumberFormatException ignored){}});
        b.setFilter(v->v.isEmpty()||v.equals("-")||v.matches("-?[0-9]{0,9}"));
    }
    private void numberBox(int value,int min,int max,int y,int w,Consumer<Integer> setter){
        numberBox(value,min,max,inspectorX+8,y,w,20,setter);
    }
    private void doubleBox(double value,double min,double max,int x,int y,int w,int h,Consumer<Double> setter){
        EditBox b=textBox(String.format(java.util.Locale.ROOT,"%.1f",value),6,x,y,w,h,v->{
            try{
                double parsed=Double.parseDouble(v);
                if(parsed>=min&&parsed<=max)setter.accept(parsed);
            }catch(NumberFormatException ignored){}
        });
        b.setFilter(v->v.isEmpty()||v.matches("[0-9]*\\.?[0-9]*"));
    }
    private int incomingIfSection(NpcDialogueGraph.Node n,int contentX,int y,int contentW){
        label("gui.settledlands.graph.incoming_if_condition",contentX,y);y+=11;
        List<Integer> incomingIfs=new ArrayList<>();
        for(NpcDialogueGraph.Node src:graph.nodes.values()){
            if(src.type==NpcDialogueGraph.IF&&(src.next==n.id||src.falseNext==n.id)){
                incomingIfs.add(src.id);
            }
        }
        if(!incomingIfs.isEmpty()){
            for(int ifId:incomingIfs){
                NpcDialogueGraph.Node ifNode=graph.get(ifId);
                boolean isTrue=ifNode!=null&&ifNode.next==n.id;
                String branchStr=isTrue?"True ✔":"False ✖";
                String ifDesc=ifNode!=null?("#"+ifNode.id+" ("+branchStr+")"):"#"+ifId;
                label(Component.translatable("gui.settledlands.graph.var_input_linked",ifId).getString()+" "+ifDesc,contentX,y);
                y+=12;
            }
            button("gui.settledlands.graph.unlink_if",contentX,y,contentW,16,()->{
                remember();
                for(NpcDialogueGraph.Node src:graph.nodes.values()){
                    if(src.type==NpcDialogueGraph.IF){
                        if(src.next==n.id)src.next=-1;
                        if(src.falseNext==n.id)src.falseNext=-1;
                    }
                }
                changed();widgets();
            });
            y+=20;
        }else{
            label("gui.settledlands.graph.incoming_if_none",contentX,y);y+=12;
        }
        return y;
    }
    private boolean isInsideInspector(double x,double y){
        return selected>=0&&x>=inspectorX&&x<=inspectorX+inspectorW&&y>=inspectorY&&y<=inspectorY+inspectorH;
    }
    private void add(int type){
        addAt(type,canvas.worldX((canvas.left+canvas.right)/2.0),canvas.worldY((canvas.top+canvas.bottom)/2.0));
    }
    private NpcDialogueGraph.Node addAt(int type,double x,double y){
        if(!importError.isEmpty())return null;
        if(graph.nodes.size()>=NpcDialogueGraph.MAX_NODES){status=tr("gui.settledlands.graph.limit");return null;}
        remember();
        NpcDialogueGraph.Node n=graph.add(type,0,0);
        if(n==null){status=tr("gui.settledlands.graph.limit");return null;}
        canvas.move(n,x,y);
        if(type==NpcDialogueGraph.LINE)n.text=tr("gui.settledlands.graph.new_line");
        else if(type==NpcDialogueGraph.BUTTON)n.text=tr("gui.settledlands.graph.new_button");
        else if(type==NpcDialogueGraph.RANDOM){
            n.text=""; n.action=""; n.randomMin=1; n.randomMax=2;
            n.randomBranches.clear(); n.randomBranches.add(-1); n.randomBranches.add(-1);
        }
        else {n.text="";n.action="";}
        selected=n.id;sidebarScroll=0;changed();widgets();
        return n;
    }
    private NpcDialogueGraph.Node duplicateNode(NpcDialogueGraph.Node src,double x,double y){
        if(src==null||!importError.isEmpty())return null;
        if(graph.nodes.size()>=NpcDialogueGraph.MAX_NODES){status=tr("gui.settledlands.graph.limit");return null;}
        remember();
        NpcDialogueGraph.Node copy=graph.add(src.type,0,0);
        if(copy==null){status=tr("gui.settledlands.graph.limit");return null;}
        canvas.move(copy,x,y);
        copy.text=src.text;
        copy.action=src.action;
        copy.special=src.special;
        copy.relation=src.relation;
        copy.minRelation=src.minRelation;
        copy.maxRelation=src.maxRelation;
        copy.pointsName=src.pointsName;
        copy.minPoints=src.minPoints;
        copy.maxPoints=src.maxPoints;
        copy.pointsShared=src.pointsShared;
        copy.builtinTopic=src.builtinTopic;
        copy.varValue=src.varValue;
        copy.varCompare=src.varCompare;
        copy.varOp=src.varOp;
        copy.varInputNode=src.varInputNode;
        copy.checkTarget=src.checkTarget;
        copy.checkRadius=src.checkRadius;
        copy.checkCooldown=src.checkCooldown;
        copy.variants.clear();
        for(NpcProfile.Variant v:src.variants){
            copy.variants.add(new NpcProfile.Variant(v.minRelation,v.text));
        }
        selected=copy.id;
        sidebarScroll=0;
        changed();
        widgets();
        status=tr("gui.settledlands.graph.copied");
        return copy;
    }
    private void history(boolean forward){
        Deque<NpcDialogueGraph> from=forward?redo:undo,to=forward?undo:redo;
        if(from.isEmpty())return;to.addLast(graph.copy());graph=from.removeLast();graphs[tab]=graph;summaries.clear();changed();widgets();
    }
    private void syncCamera(){graph.hasCamera=true;graph.panX=canvas.panX;graph.panY=canvas.panY;graph.zoom=canvas.zoom;}
    private boolean save(){
        syncCamera();
        if(!importError.isEmpty()){status=tr("gui.settledlands.graph.import_failed");return false;}
        graphs[tab]=graph;
        NpcDialogueGraph mainGraph=graphs[TAB_MAIN];
        if(mainGraph==null)mainGraph=graph;
        String mainError=mainGraph.error();
        CompoundTag t=new CompoundTag();t.putBoolean("GraphOnly",true);
        t.put("DialogueGraph",mainGraph.save());
        if(graphs[TAB_WALK]!=null)t.put("WalkGraph",graphs[TAB_WALK].save());
        if(graphs[TAB_ANIM]!=null)t.put("AnimGraph",graphs[TAB_ANIM].save());
        if(NpcDialogueGraph.encodedSize(t)>NpcDialogueGraph.MAX_PACKET_BYTES){status=tr("gui.settledlands.graph.too_large");return false;}
        sentMain=true;sentWalk=graphs[TAB_WALK]!=null;sentAnim=graphs[TAB_ANIM]!=null;
        lastSent=revision;NpcNet.sendToServer(new NpcNet.CfgSave(entityId,t));
        status=tr("gui.settledlands.graph.sending")+(mainError.isEmpty()?"":" ("+mainError+")");
        return true;
    }
    public void applySnapshot(NpcProfile snapshot){
        NpcProfile merged=snapshot.copy();
        if(!sentMain&&graphs[TAB_MAIN]!=null)merged.dialogueGraph=graphs[TAB_MAIN].copy();
        if(!sentWalk)merged.walkGraph=graphs[TAB_WALK]==null?null:graphs[TAB_WALK].copy();
        if(!sentAnim)merged.animGraph=graphs[TAB_ANIM]==null?null:graphs[TAB_ANIM].copy();
        profile=merged;
        if(parent instanceof NpcConfigScreen screen)screen.acceptGraphProfile(merged);
        if(lastSent>=0&&revision==lastSent){
            if(sentMain&&snapshot.dialogueGraph!=null){
                graphs[TAB_MAIN]=snapshot.dialogueGraph.copy();
                if(tab==TAB_MAIN)graph=graphs[TAB_MAIN];
            }
            if(sentWalk){
                graphs[TAB_WALK]=snapshot.walkGraph==null?null:snapshot.walkGraph.copy();
                if(tab==TAB_WALK&&graphs[TAB_WALK]!=null)graph=graphs[TAB_WALK];
            }
            if(sentAnim){
                graphs[TAB_ANIM]=snapshot.animGraph==null?null:snapshot.animGraph.copy();
                if(tab==TAB_ANIM&&graphs[TAB_ANIM]!=null)graph=graphs[TAB_ANIM];
            }
            dirty=false;summaries.clear();
            status=tr(sentMain?"gui.settledlands.graph.saved":"gui.settledlands.graph.saved_partial");
            widgets();
        }
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){
        if(this.openDropdown!=null){this.openDropdown.closePopup();this.openDropdown=null;}
        syncCamera();
        if(parent instanceof NpcConfigScreen screen){
            graphs[tab]=graph;
            profile.dialogueGraph=graphs[TAB_MAIN].copy();
            profile.walkGraph=graphs[TAB_WALK]==null?null:graphs[TAB_WALK].copy();
            profile.animGraph=graphs[TAB_ANIM]==null?null:graphs[TAB_ANIM].copy();
            screen.acceptGraphProfile(profile);
        }
        if(dirty&&!save()){
            long now=System.currentTimeMillis();
            if(now-closeWarnAt>8000){
                closeWarnAt=now;
                status=status+" "+tr("gui.settledlands.graph.exit_unsaved");
                return;
            }
        }
        Minecraft.getInstance().setScreen(parent);
    }

    @Override public boolean mouseClicked(double x,double y,int button){
        if(this.openDropdown!=null){
            NpcUi.NpcDropdown dd=this.openDropdown;
            boolean handled=dd.popupClicked(x,y);
            if(!dd.popupOpen()){
                this.openDropdown=null;
            }
            return true;
        }
        if(contextMenu.open){
            if(button==0&&contextMenu.isScrollbar(x,y)){
                contextMenu.scrollbarDrag(y);
                return true;
            }
            int row=contextMenu.rowAt(x,y);boolean inside=contextMenu.contains(x,y);
            double wx=contextMenu.worldX,wy=contextMenu.worldY;
            int connectFrom=contextMenu.pendingWireFrom;
            int connectPort=contextMenu.pendingWirePort;
            int targetNode=contextMenu.targetNodeId;
            contextMenu.close();
            if(button==0&&row>=0){
                if(targetNode>=0){
                    NpcDialogueGraph.Node src=graph.get(targetNode);
                    if(src!=null){
                        if(row==0){
                            if(multiSelected.contains(targetNode)&&multiSelected.size()>1){
                                Set<Integer> newCopies=new LinkedHashSet<>();
                                double baseDx=wx+24-src.x;
                                double baseDy=wy+24-src.y;
                                for(int id:new ArrayList<>(multiSelected)){
                                    NpcDialogueGraph.Node sn=graph.get(id);
                                    if(sn!=null){
                                        NpcDialogueGraph.Node cp=duplicateNode(sn,sn.x+baseDx,sn.y+baseDy);
                                        if(cp!=null)newCopies.add(cp.id);
                                    }
                                }
                                multiSelected.clear();
                                multiSelected.addAll(newCopies);
                                if(!newCopies.isEmpty())selected=newCopies.iterator().next();
                                widgets();
                                return true;
                            }
                            duplicateNode(src,wx+24,wy+24);
                            return true;
                        }else if(row==1){
                            remember();
                            if(multiSelected.contains(targetNode)&&multiSelected.size()>1){
                                for(int id:new ArrayList<>(multiSelected)){
                                    graph.remove(id);
                                }
                                multiSelected.clear();
                                selected=-1;
                                changed();widgets();
                                status=tr("gui.settledlands.graph.deleted");
                                return true;
                            }
                            if(graph.remove(targetNode)){
                                multiSelected.remove(targetNode);
                                if(selected==targetNode)selected=multiSelected.isEmpty()?-1:multiSelected.iterator().next();
                                changed();widgets();
                                status=tr("gui.settledlands.graph.deleted");
                            }
                            return true;
                        }
                    }
                }else{
                    int type=switch(row){
                        case 0 -> NpcDialogueGraph.LINE;
                        case 1 -> NpcDialogueGraph.BUTTON;
                        case 2 -> NpcDialogueGraph.TRIGGER;
                        case 3 -> NpcDialogueGraph.IF;
                        case 4 -> NpcDialogueGraph.VARIABLE;
                        case 5 -> NpcDialogueGraph.CHECK;
                        case 6 -> NpcDialogueGraph.DAMAGE;
                        case 7 -> NpcDialogueGraph.TIME;
                        case 8 -> NpcDialogueGraph.TIMER;
                        case 9 -> NpcDialogueGraph.ANIMATION;
                        case 10 -> NpcDialogueGraph.QUEST;
                        case 11 -> NpcDialogueGraph.MOVE;
                        case 12 -> NpcDialogueGraph.MOBILITY;
                        case 13 -> NpcDialogueGraph.RANDOM;
                        case 14 -> NpcDialogueGraph.CUTSCENE;
                        default -> NpcDialogueGraph.LINE;
                    };
                    NpcDialogueGraph.Node created=addAt(type,wx,wy);
                    if(created!=null&&connectFrom>=0){
                        if(graph.connect(connectFrom,created.id,connectPort)){
                            changed();
                            status=tr("gui.settledlands.graph.connected");
                        }
                    }
                    return true;
                }
            }
            if(button==0||inside)return true;
        }
        if(CommandTabCompleter.mouseClicked(this,x,y,button))return true;
        if(!canvas.contains(x,y))return super.mouseClicked(x,y,button);
        if(button==0&&isShiftDownSafe()){
            for(NpcDialogueGraph.Node a:graph.nodes.values()){
                int inPort=(a.next>=0&&graph.get(a.next)!=null&&graph.get(a.next).type==NpcDialogueGraph.IF&&a.type==NpcDialogueGraph.VARIABLE)?1:0;
                if(a.next>=0&&hitCable(a,0,graph.get(a.next),inPort,x,y)){remember();graph.disconnect(a.id,a.next);changed();return true;}
                if(a.type==NpcDialogueGraph.IF&&a.falseNext>=0&&hitCable(a,1,graph.get(a.falseNext),0,x,y)){remember();a.falseNext=-1;changed();return true;}
                if(a.type==NpcDialogueGraph.IF&&a.varInputNode>=0&&hitCable(graph.get(a.varInputNode),0,a,1,x,y)){remember();a.varInputNode=-1;changed();return true;}
                for(int id:new ArrayList<>(a.choices))if(hitCable(a,0,graph.get(id),0,x,y)){remember();graph.disconnect(a.id,id);changed();return true;}
                for(int id:new ArrayList<>(a.triggers))if(hitCable(a,0,graph.get(id),0,x,y)){remember();graph.disconnect(a.id,id);changed();return true;}
            }
        }
        setFocused(null);
        if(button==2){
            boolean disconnected=false;
            for(NpcDialogueGraph.Node a:graph.nodes.values()){
                int targetInPort=(a.next>=0&&graph.get(a.next)!=null&&graph.get(a.next).type==NpcDialogueGraph.IF&&a.type==NpcDialogueGraph.VARIABLE)?1:0;
                if(a.next>=0&&hitCable(a,0,graph.get(a.next),targetInPort,x,y)){
                    remember();graph.disconnect(a.id,a.next);changed();
                    status=tr("gui.settledlands.graph.disconnected");disconnected=true;break;
                }
                if(a.type==NpcDialogueGraph.IF&&a.falseNext>=0&&hitCable(a,1,graph.get(a.falseNext),0,x,y)){
                    remember();a.falseNext=-1;changed();
                    status=tr("gui.settledlands.graph.disconnected");disconnected=true;break;
                }
                if(a.type==NpcDialogueGraph.IF&&a.varInputNode>=0&&hitCable(graph.get(a.varInputNode),0,a,1,x,y)){
                    remember();a.varInputNode=-1;changed();
                    status=tr("gui.settledlands.graph.disconnected");disconnected=true;break;
                }
                for(int id:new ArrayList<>(a.choices)){
                    if(hitCable(a,0,graph.get(id),0,x,y)){
                        remember();graph.disconnect(a.id,id);changed();
                        status=tr("gui.settledlands.graph.disconnected");disconnected=true;break;
                    }
                }
                if(disconnected)break;
                for(int id:new ArrayList<>(a.triggers)){
                    if(hitCable(a,0,graph.get(id),0,x,y)){
                        remember();graph.disconnect(a.id,id);changed();
                        status=tr("gui.settledlands.graph.disconnected");disconnected=true;break;
                    }
                }
                if(disconnected)break;
                if(a.type==NpcDialogueGraph.RANDOM){
                    for(int p=0;p<a.randomBranches.size();p++){
                        int tid=a.randomBranches.get(p);
                        if(tid>=0&&hitCable(a,p,graph.get(tid),0,x,y)){
                            remember();a.randomBranches.set(p,-1);changed();
                            status=tr("gui.settledlands.graph.disconnected");disconnected=true;break;
                        }
                    }
                }
                if(disconnected)break;
            }
            if(disconnected)return true;
            int outPort=canvas.hitPort(graph,x,y,true);
            if(outPort>=0){
                NpcDialogueGraph.Node n=graph.get(outPort);
                if(n!=null){
                    remember();
                    if(n.type==NpcDialogueGraph.IF){
                        if(canvas.hitPortIndex==1)n.falseNext=-1;
                        else n.next=-1;
                    }else if(n.type==NpcDialogueGraph.RANDOM){
                        int p=canvas.hitPortIndex;
                        if(p>=0&&p<n.randomBranches.size())n.randomBranches.set(p,-1);
                    }else{
                        graph.disconnectOutputs(outPort);
                    }
                    changed();
                    status=tr("gui.settledlands.graph.disconnected");return true;
                }
            }
            int inPort=canvas.hitPort(graph,x,y,false);
            if(inPort>=0){
                remember();boolean any=false;
                for(NpcDialogueGraph.Node a:graph.nodes.values()){
                    if(a.next==inPort){a.next=-1;any=true;}
                    if(a.falseNext==inPort){a.falseNext=-1;any=true;}
                    if(a.varInputNode==inPort){a.varInputNode=-1;any=true;}
                    if(a.choices.remove(Integer.valueOf(inPort))){any=true;}
                    if(a.triggers.remove(Integer.valueOf(inPort))){any=true;}
                    if(a.type==NpcDialogueGraph.RANDOM){
                        for(int p=0;p<a.randomBranches.size();p++){
                            if(a.randomBranches.get(p)==inPort){
                                a.randomBranches.set(p,-1);any=true;
                            }
                        }
                    }
                }
                if(any){changed();status=tr("gui.settledlands.graph.disconnected");return true;}
            }
            panning=true;
            panStartX=x;
            panStartY=y;
            canvasPanMoved=false;
            return true;
        }
        if(button==1){
            int hitNode=canvas.hit(graph,x,y);
            contextMenu.press(x,y);
            contextMenu.targetNodeId=hitNode;
            if(hitNode>=0){
                if(!multiSelected.contains(hitNode)){
                    multiSelected.clear();
                    multiSelected.add(hitNode);
                }
                selected=hitNode;
                sidebarScroll=0;
                widgets();
            }
            return true;
        }
        if(button==0){
            // If clicking inside the floating inspector window, consume or let inspector handle it:
            if(isInsideInspector(x,y)){
                if(x>=inspectorX+inspectorW-20&&x<=inspectorX+inspectorW-4&&y>=inspectorY+2&&y<=inspectorY+18){
                    multiSelected.clear();selected=-1;sidebarScroll=0;widgets();return true;
                }
                return super.mouseClicked(x,y,button);
            }
            if(y>=6&&y<=32&&x>=10&&x<=topBarRight){
                return super.mouseClicked(x,y,button);
            }
            int output=canvas.hitPort(graph,x,y,true);
            if(output>=0){
                linkFrom=output;
                linkFromPort=canvas.hitPortIndex;
                selected=output;
                multiSelected.clear();
                multiSelected.add(output);
                sidebarScroll=0;
                widgets();
                return true;
            }
            int hitNode=canvas.hit(graph,x,y);
            if(hitNode>=0){
                if(!multiSelected.contains(hitNode)){
                    if(!isShiftDownSafe()&&!isControlDownSafe()){
                        multiSelected.clear();
                    }
                    multiSelected.add(hitNode);
                }
                selected=hitNode;
                sidebarScroll=0;
                widgets();
                remember();
                dragging=hitNode;
                dragMouseWorldX=canvas.worldX(x);
                dragMouseWorldY=canvas.worldY(y);
                dragOrigins.clear();
                for(int id:multiSelected){
                    NpcDialogueGraph.Node sn=graph.get(id);
                    if(sn!=null){
                        dragOrigins.put(id,new int[]{sn.x,sn.y});
                    }
                }
                return true;
            }
            // Pressed on empty canvas space:
            if(isSpaceDown()){
                panning=true;
                panStartX=x;
                panStartY=y;
                canvasPanMoved=false;
                return true;
            }else{
                selectingBox=true;
                boxStartX=boxCurrentX=x;
                boxStartY=boxCurrentY=y;
                if(!isShiftDownSafe()&&!isControlDownSafe()){
                    multiSelected.clear();
                    selected=-1;
                    sidebarScroll=0;
                    widgets();
                }
                return true;
            }
        }
        return false;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(button==1&&contextMenu.isPressed()){
            if(contextMenu.drag(x,y)){canvas.pan(contextMenu.deltaX,contextMenu.deltaY);syncCamera();}
            return true;
        }
        if(button==0&&contextMenu.open&&contextMenu.isScrollbar(x,y)){
            contextMenu.scrollbarDrag(y);
            return true;
        }
        if(CommandTabCompleter.mouseDragged(this,x,y,button,dx,dy))return true;
        if(isInsideInspector(x,y)){
            for(AbstractWidget w:inspectorWidgets){
                if(w.isMouseOver(x,y)&&w.mouseDragged(x,y,button,dx,dy))return true;
            }
            return true;
        }
        if(panning){
            if(Math.abs(x-panStartX)>3||Math.abs(y-panStartY)>3){
                canvasPanMoved=true;
            }
            canvas.pan(dx,dy);syncCamera();return true;
        }
        if(selectingBox){
            boxCurrentX=x;boxCurrentY=y;
            updateBoxSelection();
            return true;
        }
        if(linkFrom>=0)return true;
        if(dragging>=0){
            double curMouseWorldX=canvas.worldX(x);
            double curMouseWorldY=canvas.worldY(y);
            double deltaWorldX=curMouseWorldX-dragMouseWorldX;
            double deltaWorldY=curMouseWorldY-dragMouseWorldY;
            for(Map.Entry<Integer,int[]> entry:dragOrigins.entrySet()){
                NpcDialogueGraph.Node sn=graph.get(entry.getKey());
                if(sn!=null){
                    int[] orig=entry.getValue();
                    canvas.move(sn,orig[0]+deltaWorldX,orig[1]+deltaWorldY);
                }
            }
            changed();
            return true;
        }
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        if(button==1&&contextMenu.isPressed()){contextMenu.release(x,y,canvas);return true;}
        CommandTabCompleter.mouseReleased(this,x,y,button);
        if(linkFrom>=0){
            int to=canvas.hitPort(graph,x,y,false);if(to<0)to=canvas.hit(graph,x,y);
            if(to>=0){
                remember();
                if(graph.connect(linkFrom,to,linkFromPort)){changed();status=tr("gui.settledlands.graph.connected");}
                else status=tr("gui.settledlands.graph.bad_link");
            }else if(canvas.contains(x,y)){
                contextMenu.showForWire(x,y,canvas,linkFrom,linkFromPort);
            }
            linkFrom=-1;return true;
        }
        if(panning&&(button==0||button==2)){
            panning=false;
            return true;
        }
        if(selectingBox&&button==0){
            selectingBox=false;
            sidebarScroll=0;
            widgets();
            return true;
        }
        boolean consumed=panning||selectingBox||dragging>=0;
        panning=false;
        selectingBox=false;
        dragging=-1;
        dragOrigins.clear();
        return consumed||super.mouseReleased(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(this.openDropdown!=null){
            if(this.openDropdown.popupScrolled(x,y,(int)vertical))return true;
            this.openDropdown.closePopup();
            this.openDropdown=null;
            return true;
        }
        if(contextMenu.open){
            if(contextMenu.contains(x,y)){
                contextMenu.scroll(vertical>0 ? -1 : 1);
                return true;
            }
            contextMenu.close();
            return true;
        }
        if(CommandTabCompleter.mouseScrolled(this,x,y,vertical))return true;
        if(isInsideInspector(x,y)){
            for(AbstractWidget w:inspectorWidgets){
                if(w instanceof MultiLineEditBox mb){
                    if(mb.isMouseOver(x,y)&&mb.getInnerHeight()>mb.getHeight()){
                        if(mb.mouseScrolled(x,y,horizontal,vertical))return true;
                    }
                }
            }
            sidebarScroll=Math.max(0,Math.min(Math.max(0,sidebarHeight),sidebarScroll-(int)(vertical*18)));
            widgets();
            return true;
        }
        if(canvas.contains(x,y)){canvas.zoomAt(vertical,x,y);syncCamera();return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==32&&!(getFocused() instanceof EditBox)&&!(getFocused() instanceof MultiLineEditBox)){
            spaceDown=true;
        }
        if(this.openDropdown!=null&&key==256){
            this.openDropdown.closePopup();
            this.openDropdown=null;
            return true;
        }
        if(key==256&&(contextMenu.open||contextMenu.isPressed())){contextMenu.cancel();return true;}
        if(CommandTabCompleter.handleKey(this,key,scan,mods))return true;
        if(isControlDownSafe()&&key==83){save();return true;}
        if(!(getFocused() instanceof EditBox)&&!(getFocused() instanceof MultiLineEditBox)){
            if(isControlDownSafe()&&key==90){history(isShiftDownSafe());return true;}
            if(isControlDownSafe()&&key==89){history(true);return true;}
            if(key==261&&(!multiSelected.isEmpty()||selected>=0)){
                remember();
                boolean any=false;
                if(!multiSelected.isEmpty()){
                    for(int id:new ArrayList<>(multiSelected)){
                        if(graph.remove(id))any=true;
                    }
                    multiSelected.clear();
                    selected=-1;
                }else if(selected>=0){
                    any=graph.remove(selected);
                    selected=-1;
                }
                if(any){changed();widgets();}
                return true;
            }
            if(key==70){canvas.frameAll(graph);return true;}
        }
        if(key==256){
            if(linkFrom>=0||dragging>=0||panning||selectingBox){
                linkFrom=-1;dragging=-1;panning=false;selectingBox=false;return true;
            }
            if(getFocused() instanceof MultiLineEditBox||getFocused() instanceof EditBox){
                setFocused(null);return true;
            }
            if(!multiSelected.isEmpty()||selected>=0){
                multiSelected.clear();selected=-1;sidebarScroll=0;widgets();return true;
            }
        }
        return super.keyPressed(key,scan,mods);
    }
    @Override public boolean keyReleased(int key,int scan,int mods){
        if(key==32)spaceDown=false;
        return super.keyReleased(key,scan,mods);
    }

    /** Screen.render invokes this once before widgets; do not overpaint the canvas with a late background. */
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partial){
        g.fill(0,0,width,height,0xff10151d);g.fill(canvas.left,canvas.top,canvas.right,canvas.bottom,0xff181f2a);
        g.enableScissor(canvas.left,canvas.top,canvas.right,canvas.bottom);
        grid(g);wires(g,mouseX,mouseY);
        for(NpcDialogueGraph.Node n:graph.nodes.values())if(canvas.visible(n))drawNode(g,n);
        if(selectingBox){
            int bx1=(int)Math.min(boxStartX,boxCurrentX);
            int by1=(int)Math.min(boxStartY,boxCurrentY);
            int bx2=(int)Math.max(boxStartX,boxCurrentX);
            int by2=(int)Math.max(boxStartY,boxCurrentY);
            g.fill(bx1,by1,bx2,by2,0x3338bdf8);
            g.fill(bx1,by1,bx2,by1+1,0xff38bdf8);
            g.fill(bx1,by2-1,bx2,by2,0xff38bdf8);
            g.fill(bx1,by1,bx1+1,by2,0xff38bdf8);
            g.fill(bx2-1,by1,bx2,by2,0xff38bdf8);
        }
        g.disableScissor();

        if(selected>=0&&node()!=null){
            g.fill(inspectorX,inspectorY,inspectorX+inspectorW,inspectorY+inspectorH,0xee141c28);
            g.fill(inspectorX,inspectorY,inspectorX+inspectorW,inspectorY+1,0xff3b4e66);
            g.fill(inspectorX,inspectorY+inspectorH-1,inspectorX+inspectorW,inspectorY+inspectorH,0xff3b4e66);
            g.fill(inspectorX,inspectorY,inspectorX+1,inspectorY+inspectorH,0xff3b4e66);
            g.fill(inspectorX+inspectorW-1,inspectorY,inspectorX+inspectorW,inspectorY+inspectorH,0xff3b4e66);

            g.fill(inspectorX+1,inspectorY+1,inspectorX+inspectorW-1,inspectorY+20,0xff202c3d);
            g.fill(inspectorX+1,inspectorY+20,inspectorX+inspectorW-1,inspectorY+21,0xff3b4e66);

            NpcDialogueGraph.Node n=node();
            int color=n.type==NpcDialogueGraph.LINE?0xff8cc9ee:n.type==NpcDialogueGraph.BUTTON?0xffe3bd75:n.type==NpcDialogueGraph.TRIGGER?0xffd29bfd:n.type==NpcDialogueGraph.IF?0xff38bdf8:n.type==NpcDialogueGraph.VARIABLE?0xfffb923c:n.type==NpcDialogueGraph.CHECK?0xff34d399:0xfff43f5e;
            String heading=multiSelected.size()>1
                ?String.format(tr("gui.settledlands.graph.multiselected"),multiSelected.size())
                :(tr(n.type==NpcDialogueGraph.LINE?"gui.settledlands.graph.line":n.type==NpcDialogueGraph.BUTTON?"gui.settledlands.graph.button":n.type==NpcDialogueGraph.TRIGGER?"gui.settledlands.graph.trigger":n.type==NpcDialogueGraph.IF?"gui.settledlands.graph.if":n.type==NpcDialogueGraph.VARIABLE?"gui.settledlands.graph.variable":n.type==NpcDialogueGraph.CHECK?"gui.settledlands.graph.check":"gui.settledlands.graph.damage")+" #"+n.id);
            g.drawString(font,clip(heading,inspectorW-34),inspectorX+8,inspectorY+6,color,false);
            g.drawString(font,"✕",inspectorX+inspectorW-14,inspectorY+6,0xffaab8ca,false);

            if(sidebarHeight>0){
                int trackY=inspectorY+22;
                int trackH=inspectorH-26;
                int thumbH=Math.max(16,(int)((float)trackH*trackH/(trackH+sidebarHeight)));
                int maxOffset=trackH-thumbH;
                int thumbY=trackY+(int)((float)maxOffset*sidebarScroll/sidebarHeight);
                g.fill(inspectorX+inspectorW-4,trackY,inspectorX+inspectorW-1,trackY+trackH,0x33ffffff);
                g.fill(inspectorX+inspectorW-4,thumbY,inspectorX+inspectorW-1,thumbY+thumbH,0x88ffffff);
            }
        }
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        super.render(g,mouseX,mouseY,partial); // Background/canvas first, then standard widgets.
        if(selected>=0&&node()!=null){
            g.enableScissor(inspectorX+1,inspectorY+21,inspectorX+inspectorW-1,inspectorY+inspectorH-1);
            for(Label l:labels){
                if(l.y>=inspectorY+20&&l.y<inspectorY+inspectorH-8){
                    g.drawString(font,clip(tr(l.key),inspectorW-16),l.x,l.y,0xffaab8ca,false);
                }
            }
            g.disableScissor();
        }

        if(!status.isEmpty()&&!status.equals(tr("gui.settledlands.graph.controls"))&&!status.equals(tr("gui.settledlands.graph.unsaved"))){
            g.drawString(font,status,10,height-16,0xff84dab0,false);
        }
        g.drawString(font,Math.round(canvas.zoom*100)+"%",width-36,height-16,0x88aab8ca,false);

        CommandTabCompleter.render(this,g,mouseX,mouseY);
        if(this.openDropdown!=null){
            g.pose().pushPose();g.pose().translate(0,0,500);
            this.openDropdown.renderPopup(g,mouseX,mouseY);
            g.pose().popPose();
        }
        if(contextMenu.open)drawContextMenu(g,mouseX,mouseY);
    }
    private void drawContextMenu(GuiGraphics g,int mouseX,int mouseY){
        g.pose().pushPose();g.pose().translate(0,0,600);
        int x=contextMenu.left,y=contextMenu.top,w=contextMenu.width,h=contextMenu.height;
        g.enableScissor(x,y,x+w,y+h);
        g.fill(x,y,x+w,y+h,0xff111923);g.fill(x,y,x+w,y+1,0xff7f98b6);
        g.fill(x,y+h-1,x+w,y+h,0xff7f98b6);g.fill(x,y,x+1,y+h,0xff7f98b6);
        g.fill(x+w-1,y,x+w,y+h,0xff7f98b6);
        if(contextMenu.targetNodeId>=0){
            NpcDialogueGraph.Node target=graph.get(contextMenu.targetNodeId);
            String title=target!=null?(tr(target.type==NpcDialogueGraph.LINE?"gui.settledlands.graph.line":target.type==NpcDialogueGraph.BUTTON?"gui.settledlands.graph.button":target.type==NpcDialogueGraph.TRIGGER?"gui.settledlands.graph.trigger":target.type==NpcDialogueGraph.IF?"gui.settledlands.graph.if":target.type==NpcDialogueGraph.VARIABLE?"gui.settledlands.graph.variable":target.type==NpcDialogueGraph.CHECK?"gui.settledlands.graph.check":target.type==NpcDialogueGraph.DAMAGE?"gui.settledlands.graph.damage":target.type==NpcDialogueGraph.TIME?"gui.settledlands.graph.time":target.type==NpcDialogueGraph.TIMER?"gui.settledlands.graph.timer":target.type==NpcDialogueGraph.ANIMATION?"gui.settledlands.graph.anim":target.type==NpcDialogueGraph.CUTSCENE?"gui.settledlands.graph.cutscene":"gui.settledlands.graph.quest")+" #"+target.id):tr("gui.settledlands.graph.select");
            g.drawString(font,clip(title,w-16),x+8,y+6,0xffe3bd75,false);
            int hovered=contextMenu.rowAt(mouseX,mouseY);
            for(int row=0;row<2;row++){
                int ry=y+NpcGraphContextMenu.HEADER_H+row*NpcGraphContextMenu.ROW_H;
                if(row==hovered)g.fill(x+2,ry,x+w-2,ry+NpcGraphContextMenu.ROW_H,0xff34465d);
                String labelKey=row==0?"gui.settledlands.graph.copy_node":"gui.settledlands.graph.delete";
                int col=row==0?0xff84dab0:0xfff87171;
                g.drawString(font,clip(tr(labelKey),w-16),x+8,ry+7,col,false);
            }
        }else{
            g.drawString(font,clip(tr("gui.settledlands.graph.create"),w-24),x+8,y+6,0xffaab8ca,false);
            int hovered=contextMenu.rowAt(mouseX,mouseY);
            int listRight=x+w-NpcGraphContextMenu.SCROLLBAR_W-4;
            for(int vrow=0;vrow<NpcGraphContextMenu.VISIBLE_ROWS;vrow++){
                int actualRow=contextMenu.scrollRow+vrow;
                if(actualRow>=NpcGraphContextMenu.TOTAL_ROWS)break;
                int ry=y+NpcGraphContextMenu.HEADER_H+vrow*NpcGraphContextMenu.ROW_H;
                if(actualRow==hovered)g.fill(x+2,ry,listRight,ry+NpcGraphContextMenu.ROW_H,0xff34465d);
                String key=switch(actualRow){
                    case 0 -> "gui.settledlands.graph.add_line";
                    case 1 -> "gui.settledlands.graph.add_button";
                    case 2 -> "gui.settledlands.graph.add_trigger";
                    case 3 -> "gui.settledlands.graph.add_if";
                    case 4 -> "gui.settledlands.graph.add_variable";
                    case 5 -> "gui.settledlands.graph.add_check";
                    case 6 -> "gui.settledlands.graph.add_damage";
                    case 7 -> "gui.settledlands.graph.add_time";
                    case 8 -> "gui.settledlands.graph.add_timer";
                    case 9 -> "gui.settledlands.graph.add_anim";
                    case 10 -> "gui.settledlands.graph.add_quest";
                    case 11 -> "gui.settledlands.graph.add_move";
                    case 12 -> "gui.settledlands.graph.add_mobility";
                    case 13 -> "gui.settledlands.graph.add_random";
                    case 14 -> "gui.settledlands.graph.add_cutscene";
                    default -> "";
                };
                int col=switch(actualRow){
                    case 0 -> 0xff8cc9ee;
                    case 1 -> 0xffe3bd75;
                    case 2 -> 0xffd29bfd;
                    case 3 -> 0xff38bdf8;
                    case 4 -> 0xfffb923c;
                    case 5 -> 0xff34d399;
                    case 6 -> 0xfff43f5e;
                    case 7 -> 0xfffbbf24;
                    case 8 -> 0xffa78bfa;
                    case 9 -> 0xffec4899;
                    case 10 -> 0xfff59e0b;
                    case 11 -> 0xff818cf8;
                    case 12 -> 0xff2dd4bf;
                    case 13 -> 0xffa855f7;
                    default -> 0xffffffff;
                };
                g.drawString(font,clip(tr(key),listRight-x-12),x+8,ry+7,col,false);
            }
            int trackX=x+w-NpcGraphContextMenu.SCROLLBAR_W-2;
            int trackY=y+NpcGraphContextMenu.HEADER_H+2;
            int trackH=h-NpcGraphContextMenu.HEADER_H-4;
            g.fill(trackX,trackY,trackX+NpcGraphContextMenu.SCROLLBAR_W,trackY+trackH,0xff1e293b);
            int maxScroll=NpcGraphContextMenu.TOTAL_ROWS-NpcGraphContextMenu.VISIBLE_ROWS;
            int thumbH=Math.max(16,(trackH*NpcGraphContextMenu.VISIBLE_ROWS)/NpcGraphContextMenu.TOTAL_ROWS);
            int thumbY=trackY+(trackH-thumbH)*contextMenu.scrollRow/Math.max(1,maxScroll);
            boolean thumbHover=mouseX>=trackX&&mouseX<=trackX+NpcGraphContextMenu.SCROLLBAR_W&&mouseY>=thumbY&&mouseY<=thumbY+thumbH;
            g.fill(trackX,thumbY,trackX+NpcGraphContextMenu.SCROLLBAR_W,thumbY+thumbH,thumbHover?0xff94a3b8:0xff64748b);
        }
        g.disableScissor();g.pose().popPose();
    }
    private String clip(String text,int width){return font.plainSubstrByWidth(text==null?"":text,Math.max(1,width));}
    private void grid(GuiGraphics g){
        double step=32*canvas.zoom;while(step<16)step*=4;
        int startX=(int)(canvas.left+(canvas.panX%step+step)%step),startY=(int)(canvas.top+(canvas.panY%step+step)%step);
        for(double x=startX;x<canvas.right;x+=step)g.vLine((int)x,canvas.top,canvas.bottom,0xff242e3b);
        for(double y=startY;y<canvas.bottom;y+=step)g.hLine(canvas.left,canvas.right,(int)y,0xff242e3b);
    }
    private void drawNode(GuiGraphics g,NpcDialogueGraph.Node n){
        int color=n.special!=NpcDialogueGraph.NORMAL?0xff537f78:n.type==NpcDialogueGraph.LINE?0xff37688c:n.type==NpcDialogueGraph.BUTTON?0xff9a7640:n.type==NpcDialogueGraph.TRIGGER?0xff7b2cbf:n.type==NpcDialogueGraph.IF?0xff0e7490:n.type==NpcDialogueGraph.VARIABLE?0xffc2410c:n.type==NpcDialogueGraph.CHECK?0xff047857:n.type==NpcDialogueGraph.DAMAGE?0xff9f1239:n.type==NpcDialogueGraph.TIME?0xffb45309:n.type==NpcDialogueGraph.TIMER?0xff6d28d9:n.type==NpcDialogueGraph.ANIMATION?0xffbe185d:n.type==NpcDialogueGraph.CUTSCENE?0xffa21caf:n.type==NpcDialogueGraph.QUEST?0xffd97706:n.type==NpcDialogueGraph.MOVE?0xff4338ca:n.type==NpcDialogueGraph.MOBILITY?0xff0f766e:0xff6b21a8;
        double sx=canvas.screenX(n.x),sy=canvas.screenY(n.y);
        g.pose().pushPose();g.pose().translate(sx,sy,0);g.pose().scale((float)canvas.zoom,(float)canvas.zoom,1);
        int w=NpcGraphCanvas.NODE_W,h=NpcGraphCanvas.NODE_H;
        boolean isSel=(n.id==selected||multiSelected.contains(n.id));
        g.fill(-2,-2,w+2,h+2,isSel?0xffe9bd67:0xff46576b);
        g.fill(0,0,w,h,0xff26313f);g.fill(0,0,w,22,color);
        String name=n.special==NpcDialogueGraph.TRAVEL?tr("gui.settledlands.graph.travel"):n.special==NpcDialogueGraph.ARRIVAL?tr("gui.settledlands.graph.arrival"):n.type==NpcDialogueGraph.LINE?tr("gui.settledlands.graph.line"):n.type==NpcDialogueGraph.BUTTON?tr("gui.settledlands.graph.button"):n.type==NpcDialogueGraph.TRIGGER?tr("gui.settledlands.graph.trigger"):n.type==NpcDialogueGraph.IF?tr("gui.settledlands.graph.if"):n.type==NpcDialogueGraph.VARIABLE?tr("gui.settledlands.graph.variable"):n.type==NpcDialogueGraph.CHECK?tr("gui.settledlands.graph.check"):n.type==NpcDialogueGraph.DAMAGE?tr("gui.settledlands.graph.damage"):n.type==NpcDialogueGraph.TIME?tr("gui.settledlands.graph.time"):n.type==NpcDialogueGraph.TIMER?tr("gui.settledlands.graph.timer"):n.type==NpcDialogueGraph.ANIMATION?tr("gui.settledlands.graph.anim"):n.type==NpcDialogueGraph.CUTSCENE?tr("gui.settledlands.graph.cutscene"):n.type==NpcDialogueGraph.QUEST?tr("gui.settledlands.graph.quest"):n.type==NpcDialogueGraph.MOVE?tr("gui.settledlands.graph.move"):n.type==NpcDialogueGraph.MOBILITY?tr("gui.settledlands.graph.mobility"):tr("gui.settledlands.graph.random");
        g.drawString(font,clip(name+" #"+n.id,w-16),8,7,0xfff0f3f7,false);
        if(n.id==graph.entry)g.drawString(font,tr("gui.settledlands.graph.start"),8,78,0xff84dab0,false);
        if(n.type==NpcDialogueGraph.TRIGGER){
            String cmd=n.action.isBlank()?tr("gui.settledlands.graph.no_command"):n.action;
            g.drawString(font,clip("⚡ "+cmd,w-16),8,35,0xffe0c8ff,false);
        }else if(n.type==NpcDialogueGraph.VARIABLE){
            String vname=n.pointsName.isBlank()?tr("gui.settledlands.graph.no_var"):n.pointsName;
            g.drawString(font,clip("⛁ "+vname+(n.pointsShared?" ("+tr("gui.settledlands.graph.shared_on")+")":""),w-16),8,35,0xfffed7aa,false);
            String deltaStr=(n.varValue>=0?"+":"")+n.varValue;
            g.drawString(font,clip(tr("gui.settledlands.graph.var_delta")+": "+deltaStr,w-16),8,52,0xfffed7aa,false);
            if(!n.action.isBlank())g.drawString(font,tr("gui.settledlands.graph.trigger"),8,70,0xffe3bd75,false);
        }else if(n.type==NpcDialogueGraph.IF){
            String varName=n.varInputNode>=0?(graph.get(n.varInputNode)!=null&&!graph.get(n.varInputNode).pointsName.isBlank()?graph.get(n.varInputNode).pointsName:"#"+n.varInputNode):(n.pointsName.isBlank()?"?":n.pointsName);
            String opStr=switch(n.varOp){case 0->"==";case 1->"!=";case 2->">=";case 3->">";case 4->"<=";case 5->"<";default->"==";};
            g.drawString(font,clip("❓ "+varName+" "+opStr+" "+n.varCompare,w-16),8,35,0xffbae6fd,false);
            g.drawString(font,"True ✔",w-46,(int)(h*0.35-4),0xff4ade80,false);
            g.drawString(font,"False ✖",w-46,(int)(h*0.70-4),0xfff87171,false);
            if(!n.action.isBlank())g.drawString(font,tr("gui.settledlands.graph.trigger"),8,70,0xffe3bd75,false);
        }else if(n.type==NpcDialogueGraph.CHECK){
            String targetName=switch(n.checkTarget){
                case 0 -> tr("gui.settledlands.graph.target_player");
                case 1 -> tr("gui.settledlands.graph.target_npc");
                case 2 -> tr("gui.settledlands.graph.target_monster");
                case 3 -> tr("gui.settledlands.graph.target_any");
                default -> tr("gui.settledlands.graph.target_player");
            };
            g.drawString(font,clip("👁 "+tr("gui.settledlands.graph.target")+": "+targetName,w-16),8,35,0xffa7f3d0,false);
            g.drawString(font,clip("◎ "+tr("gui.settledlands.graph.check_radius")+": "+n.checkRadius+" "+tr("gui.settledlands.graph.blocks"),w-16),8,52,0xffa7f3d0,false);
            g.drawString(font,clip("⏱ "+tr("gui.settledlands.graph.check_cooldown")+": "+n.checkCooldown+"s",w-16),8,69,0xff6ee7b7,false);
            if(!n.action.isBlank())g.drawString(font,tr("gui.settledlands.graph.trigger"),8,85,0xffe3bd75,false);
        }else if(n.type==NpcDialogueGraph.DAMAGE){
            String filterName=switch(n.damageFilter){
                case 0 -> tr("gui.settledlands.graph.damage_any");
                case 1 -> tr("gui.settledlands.graph.damage_player");
                case 2 -> tr("gui.settledlands.graph.damage_monster");
                case 3 -> tr("gui.settledlands.graph.damage_non_player");
                default -> tr("gui.settledlands.graph.damage_player");
            };
            g.drawString(font,clip("🗡 "+tr("gui.settledlands.graph.damage_source")+": "+filterName,w-16),8,35,0xfffecdd3,false);
            g.drawString(font,clip("💥 "+tr("gui.settledlands.graph.min_damage")+": "+n.minDamage,w-16),8,52,0xfffecdd3,false);
            g.drawString(font,clip("⏱ "+tr("gui.settledlands.graph.check_cooldown")+": "+n.damageCooldown+"s",w-16),8,69,0xfffda4af,false);
            if(!n.action.isBlank())g.drawString(font,tr("gui.settledlands.graph.trigger"),8,85,0xffe3bd75,false);
        }else if(n.type==NpcDialogueGraph.TIME){
            String timeModeName=switch(n.timeMode){
                case 0 -> tr("gui.settledlands.graph.time_dawn");
                case 1 -> tr("gui.settledlands.graph.time_noon");
                case 2 -> tr("gui.settledlands.graph.time_sunset");
                case 3 -> tr("gui.settledlands.graph.time_midnight");
                case 4 -> tr("gui.settledlands.graph.time_custom")+" ("+n.timeTicks+")";
                default -> tr("gui.settledlands.graph.time_custom")+" ("+n.timeTicks+")";
            };
            g.drawString(font,clip("☀ "+tr("gui.settledlands.graph.time_mode")+": "+timeModeName,w-16),8,35,0xfffde68a,false);
            g.drawString(font,clip("⌛ "+tr("gui.settledlands.graph.time_ticks")+": "+n.timeTicks,w-16),8,52,0xfffde68a,false);
            if(!n.action.isBlank())g.drawString(font,tr("gui.settledlands.graph.trigger"),8,70,0xffe3bd75,false);
        }else if(n.type==NpcDialogueGraph.TIMER){
            g.drawString(font,clip("⏱ "+tr("gui.settledlands.graph.timer_interval")+": "+n.timerInterval+"s",w-16),8,35,0xffddd6fe,false);
            String repeatStr=n.timerRepeat?tr("gui.settledlands.graph.timer_repeat_on"):tr("gui.settledlands.graph.timer_repeat_off");
            g.drawString(font,clip("🔄 "+repeatStr,w-16),8,52,0xffddd6fe,false);
            if(!n.action.isBlank())g.drawString(font,tr("gui.settledlands.graph.trigger"),8,70,0xffe3bd75,false);
        }else if(n.type==NpcDialogueGraph.ANIMATION){
            String aName=n.animName.isBlank()?tr("gui.settledlands.graph.anim_none"):n.animName;
            g.drawString(font,clip("🎬 "+tr("gui.settledlands.graph.anim")+": "+aName,w-16),8,35,0xfffbcfe8,false);
            String repeatStr=n.animLoop?tr("gui.settledlands.graph.anim_repeat_on"):tr("gui.settledlands.graph.anim_repeat_off");
            String boneStr=n.animBone.isBlank()?tr("gui.settledlands.graph.anim_bone_all"):"🦴 "+n.animBone;
            g.drawString(font,clip("🔄 "+repeatStr+" | "+boneStr,w-16),8,52,0xfffbcfe8,false);
            if(!n.action.isBlank())g.drawString(font,tr("gui.settledlands.graph.trigger"),8,70,0xffe3bd75,false);
        }else if(n.type==NpcDialogueGraph.CUTSCENE){
            String cName=n.cutsceneName.isBlank()?tr("gui.settledlands.graph.cutscene_none"):n.cutsceneName;
            g.drawString(font,clip("🎥 "+tr("gui.settledlands.graph.cutscene")+": "+cName,w-16),8,35,0xfff5d0fe,false);
            if(!n.action.isBlank())g.drawString(font,tr("gui.settledlands.graph.trigger"),8,52,0xffe3bd75,false);
        }else if(n.type==NpcDialogueGraph.QUEST){
            String qTitle=n.text.isBlank()?tr("gui.settledlands.graph.quest_empty"):n.text;
            g.drawString(font,clip("📜 "+qTitle,w-16),8,35,0xfffde68a,false);
            if(n.minRelation>0)g.drawString(font,clip("❤ "+tr("gui.settledlands.graph.min_relation")+": "+n.minRelation,w-16),8,52,0xfffde68a,false);
            int ifIncoming=-1;
            boolean ifIsTrue=true;
            for(NpcDialogueGraph.Node src:graph.nodes.values()){
                if(src.type==NpcDialogueGraph.IF&&(src.next==n.id||src.falseNext==n.id)){
                    ifIncoming=src.id;
                    ifIsTrue=(src.next==n.id);
                    break;
                }
            }
            if(ifIncoming>=0){
                g.drawString(font,clip("❓ If #"+ifIncoming+" ("+(ifIsTrue?"True":"False")+")",w-16),8,69,0xff7dd3fc,false);
                if(!n.action.isBlank())g.drawString(font,tr("gui.settledlands.graph.trigger"),8,84,0xffe3bd75,false);
            }else{
                if(!n.action.isBlank())g.drawString(font,tr("gui.settledlands.graph.trigger"),8,70,0xffe3bd75,false);
            }
        }else if(n.type==NpcDialogueGraph.MOVE){
            String coordsStr=n.moveCoords.isBlank()?tr("gui.settledlands.graph.no_coords"):n.moveCoords;
            String escortBadge=n.moveEscort?(" | 🛡 "+tr("gui.settledlands.graph.move_escort_badge")):"";
            g.drawString(font,clip("🚶 "+coordsStr+escortBadge,w-16),8,35,0xffc7d2fe,false);
            String speedStr=String.format(java.util.Locale.ROOT,"%.1fx",n.moveSpeed);
            String modeStr=n.moveEscort?(" ("+tr("gui.settledlands.graph.move_escort")+")"):"";
            g.drawString(font,clip("⚡ "+tr("gui.settledlands.graph.move_speed")+": "+speedStr+modeStr,w-16),8,52,0xffc7d2fe,false);
            int ifIncoming=-1;
            boolean ifIsTrue=true;
            for(NpcDialogueGraph.Node src:graph.nodes.values()){
                if(src.type==NpcDialogueGraph.IF&&(src.next==n.id||src.falseNext==n.id)){
                    ifIncoming=src.id;
                    ifIsTrue=(src.next==n.id);
                    break;
                }
            }
            if(ifIncoming>=0){
                g.drawString(font,clip("❓ If #"+ifIncoming+" ("+(ifIsTrue?"True":"False")+")",w-16),8,69,0xff7dd3fc,false);
                if(!n.action.isBlank())g.drawString(font,tr("gui.settledlands.graph.trigger"),8,84,0xffe3bd75,false);
            }else{
                if(!n.action.isBlank())g.drawString(font,tr("gui.settledlands.graph.trigger"),8,70,0xffe3bd75,false);
            }
        }else if(n.type==NpcDialogueGraph.MOBILITY){
            String gName = n.mobilityGroupName.isBlank() ? (n.mobilityGroupId.isBlank() ? tr("gui.settledlands.graph.no_group") : n.mobilityGroupId) : n.mobilityGroupName;
            g.drawString(font,clip("🧭 "+gName,w-16),8,35,0xff5eead4,false);
            g.drawString(font,clip("📍 "+tr("gui.settledlands.graph.mobility_target_pt1"),w-16),8,50,0xff99f6e4,false);
            int ifIncoming=-1;
            boolean ifIsTrue=true;
            for(NpcDialogueGraph.Node src:graph.nodes.values()){
                if(src.type==NpcDialogueGraph.IF&&(src.next==n.id||src.falseNext==n.id)){
                    ifIncoming=src.id;
                    ifIsTrue=(src.next==n.id);
                    break;
                }
            }
            if(ifIncoming>=0){
                g.drawString(font,clip("❓ If #"+ifIncoming+" ("+(ifIsTrue?"True":"False")+")",w-16),8,69,0xff7dd3fc,false);
                if(!n.action.isBlank())g.drawString(font,tr("gui.settledlands.graph.trigger"),8,84,0xffe3bd75,false);
            }else{
                if(!n.action.isBlank())g.drawString(font,tr("gui.settledlands.graph.trigger"),8,70,0xffe3bd75,false);
            }
        }else if(n.type==NpcDialogueGraph.RANDOM){
            g.drawString(font,clip("🎲 "+tr("gui.settledlands.graph.random_range")+": ["+n.randomMin+" - "+n.randomMax+"]",w-16),8,35,0xffe9d5ff,false);
            g.drawString(font,clip("🔀 "+tr("gui.settledlands.graph.random_outputs")+": "+n.randomMax,w-16),8,50,0xffc084fc,false);
            if(!n.action.isBlank())g.drawString(font,tr("gui.settledlands.graph.trigger"),8,68,0xffe3bd75,false);
        }else{
            String summary=summaries.computeIfAbsent(n.id,id->clip(n.text.replace('\n',' '),w-16));
            g.drawString(font,summary,8,35,0xffd5dfed,false);
            if(!n.action.isBlank())g.drawString(font,tr("gui.settledlands.graph.trigger"),8,55,0xffe3bd75,false);
            if(n.builtinTopic>=0)g.drawString(font,tr("gui.settledlands.graph.builtin"),8,65,0xffaab8ca,false);
        }
        if(n.special==NpcDialogueGraph.NORMAL){
            if(n.type==NpcDialogueGraph.IF){
                g.fill(-5,(int)(h*0.35)-5,5,(int)(h*0.35)+5,0xffc4d8ea);
                g.fill(-5,(int)(h*0.70)-5,5,(int)(h*0.70)+5,0xfffb923c);
                g.fill(w-5,(int)(h*0.35)-5,w+5,(int)(h*0.35)+5,0xff4ade80);
                g.fill(w-5,(int)(h*0.70)-5,w+5,(int)(h*0.70)+5,0xfff87171);
            }else if(n.type==NpcDialogueGraph.RANDOM){
                g.fill(-5,h/2-5,5,h/2+5,0xffc4d8ea);
                int ports=Math.max(2,n.randomMax);
                for(int p=0;p<ports;p++){
                    int py=(int)(h*((p+1.0)/(ports+1.0)));
                    g.fill(w-5,py-4,w+5,py+4,0xffa855f7);
                    g.drawString(font,String.valueOf(p+1),w-12,py-4,0xffe9d5ff,false);
                }
            }else{
                g.fill(-5,h/2-5,5,h/2+5,0xffc4d8ea);
                int portCol=n.type==NpcDialogueGraph.LINE?0xff70b8e6:n.type==NpcDialogueGraph.BUTTON?0xffe3bd75:n.type==NpcDialogueGraph.TRIGGER?0xffc77dff:n.type==NpcDialogueGraph.VARIABLE?0xfffb923c:n.type==NpcDialogueGraph.CHECK?0xff34d399:n.type==NpcDialogueGraph.DAMAGE?0xfff43f5e:n.type==NpcDialogueGraph.TIME?0xfffbbf24:n.type==NpcDialogueGraph.TIMER?0xffa78bfa:n.type==NpcDialogueGraph.ANIMATION?0xffec4899:n.type==NpcDialogueGraph.QUEST?0xfff59e0b:n.type==NpcDialogueGraph.MOVE?0xff818cf8:0xff2dd4bf;
                g.fill(w-5,h/2-5,w+5,h/2+5,portCol);
            }
        }
        g.pose().popPose();
    }
    /** All visible cables in one bounded mesh/batch. Nodes/strings/layout are not rebuilt every frame. */
    private void wires(GuiGraphics g,int mx,int my){
        g.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableDepthTest();RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix=g.pose().last().pose();int segments=0;

        boolean canHover = canvas.contains(mx, my) && !contextMenu.open && linkFrom < 0 && dragging < 0 && !panning && canvas.hit(graph, mx, my) < 0 && canvas.hitPort(graph, mx, my, true) < 0 && canvas.hitPort(graph, mx, my, false) < 0;
        int hoverFrom = -1, hoverFromPort = 0, hoverTo = -1, hoverToPort = 0, hoverColor = 0;
        if (canHover) {
            findHover:
            for (NpcDialogueGraph.Node a : graph.nodes.values()) {
                if (a.next >= 0) {
                    NpcDialogueGraph.Node end = graph.get(a.next);
                    if (end != null) {
                        int inPort = (end.type == NpcDialogueGraph.IF && a.type == NpcDialogueGraph.VARIABLE) ? 1 : 0;
                        if (hitCable(a, 0, end, inPort, mx, my)) {
                            hoverFrom = a.id; hoverFromPort = 0; hoverTo = end.id; hoverToPort = inPort;
                            hoverColor = a.type == NpcDialogueGraph.IF ? 0xff4ade80 : a.type == NpcDialogueGraph.VARIABLE ? 0xfffb923c : a.type == NpcDialogueGraph.CHECK ? 0xff34d399 : a.type == NpcDialogueGraph.DAMAGE ? 0xfff43f5e : a.type == NpcDialogueGraph.TIME ? 0xfffbbf24 : a.type == NpcDialogueGraph.TIMER ? 0xffa78bfa : a.type == NpcDialogueGraph.ANIMATION ? 0xffec4899 : a.type == NpcDialogueGraph.QUEST ? 0xfff59e0b : a.type == NpcDialogueGraph.MOVE ? 0xff818cf8 : a.type == NpcDialogueGraph.MOBILITY ? 0xff2dd4bf : a.type == NpcDialogueGraph.LINE ? 0xff70b8e6 : a.type == NpcDialogueGraph.BUTTON ? 0xffe3bd75 : 0xffc77dff;
                            break findHover;
                        }
                    }
                }
                if (a.type == NpcDialogueGraph.IF && a.falseNext >= 0) {
                    NpcDialogueGraph.Node end = graph.get(a.falseNext);
                    if (end != null && hitCable(a, 1, end, 0, mx, my)) {
                        hoverFrom = a.id; hoverFromPort = 1; hoverTo = end.id; hoverToPort = 0;
                        hoverColor = 0xfff87171;
                        break findHover;
                    }
                }
                if (a.type == NpcDialogueGraph.IF && a.varInputNode >= 0 && a.varInputNode != a.next) {
                    NpcDialogueGraph.Node varSrc = graph.get(a.varInputNode);
                    if (varSrc != null && hitCable(varSrc, 0, a, 1, mx, my)) {
                        hoverFrom = varSrc.id; hoverFromPort = 0; hoverTo = a.id; hoverToPort = 1;
                        hoverColor = 0xfffb923c;
                        break findHover;
                    }
                }
                for (int id : a.choices) {
                    NpcDialogueGraph.Node end = graph.get(id);
                    if (end != null && hitCable(a, 0, end, 0, mx, my)) {
                        hoverFrom = a.id; hoverFromPort = 0; hoverTo = end.id; hoverToPort = 0;
                        hoverColor = 0xffe3bd75;
                        break findHover;
                    }
                }
                for (int id : a.triggers) {
                    NpcDialogueGraph.Node end = graph.get(id);
                    if (end != null && hitCable(a, 0, end, 0, mx, my)) {
                        hoverFrom = a.id; hoverFromPort = 0; hoverTo = end.id; hoverToPort = 0;
                        hoverColor = 0xffc77dff;
                        break findHover;
                    }
                }
                if (a.type == NpcDialogueGraph.RANDOM) {
                    for (int p = 0; p < a.randomBranches.size(); p++) {
                        int tid = a.randomBranches.get(p);
                        if (tid >= 0) {
                            NpcDialogueGraph.Node end = graph.get(tid);
                            if (end != null && hitCable(a, p, end, 0, mx, my)) {
                                hoverFrom = a.id; hoverFromPort = p; hoverTo = end.id; hoverToPort = 0;
                                hoverColor = 0xffa855f7;
                                break findHover;
                            }
                        }
                    }
                }
            }
        }

        for (NpcDialogueGraph.Node a : graph.nodes.values()) {
            if (a.next >= 0) {
                NpcDialogueGraph.Node end = graph.get(a.next);
                if (end != null) {
                    int inPort = (end.type == NpcDialogueGraph.IF && a.type == NpcDialogueGraph.VARIABLE) ? 1 : 0;
                    if (!(hoverFrom == a.id && hoverFromPort == 0 && hoverTo == end.id && hoverToPort == inPort)) {
                        int color = a.type == NpcDialogueGraph.IF ? 0xff4ade80 : a.type == NpcDialogueGraph.VARIABLE ? 0xfffb923c : a.type == NpcDialogueGraph.CHECK ? 0xff34d399 : a.type == NpcDialogueGraph.DAMAGE ? 0xfff43f5e : a.type == NpcDialogueGraph.TIME ? 0xfffbbf24 : a.type == NpcDialogueGraph.TIMER ? 0xffa78bfa : a.type == NpcDialogueGraph.ANIMATION ? 0xffec4899 : a.type == NpcDialogueGraph.QUEST ? 0xfff59e0b : a.type == NpcDialogueGraph.MOVE ? 0xff818cf8 : a.type == NpcDialogueGraph.MOBILITY ? 0xff2dd4bf : a.type == NpcDialogueGraph.LINE ? 0xff70b8e6 : a.type == NpcDialogueGraph.BUTTON ? 0xffe3bd75 : 0xffc77dff;
                        segments += cable(b, matrix, a, 0, end, inPort, color, false);
                    }
                }
            }
            if (a.type == NpcDialogueGraph.IF && a.falseNext >= 0) {
                NpcDialogueGraph.Node end = graph.get(a.falseNext);
                if (end != null && !(hoverFrom == a.id && hoverFromPort == 1 && hoverTo == end.id && hoverToPort == 0)) {
                    segments += cable(b, matrix, a, 1, end, 0, 0xfff87171, false);
                }
            }
            if (a.type == NpcDialogueGraph.IF && a.varInputNode >= 0 && a.varInputNode != a.next) {
                NpcDialogueGraph.Node varSrc = graph.get(a.varInputNode);
                if (varSrc != null && !(hoverFrom == varSrc.id && hoverFromPort == 0 && hoverTo == a.id && hoverToPort == 1)) {
                    segments += cable(b, matrix, varSrc, 0, a, 1, 0xfffb923c, false);
                }
            }
            if (a.type == NpcDialogueGraph.RANDOM) {
                for (int p = 0; p < a.randomBranches.size(); p++) {
                    int tid = a.randomBranches.get(p);
                    if (tid >= 0) {
                        NpcDialogueGraph.Node end = graph.get(tid);
                        if (end != null && !(hoverFrom == a.id && hoverFromPort == p && hoverTo == end.id && hoverToPort == 0)) {
                            segments += cable(b, matrix, a, p, end, 0, 0xffa855f7, false);
                        }
                    }
                }
            }
            for (int id : a.choices) {
                NpcDialogueGraph.Node end = graph.get(id);
                if (end != null && !(hoverFrom == a.id && hoverFromPort == 0 && hoverTo == end.id && hoverToPort == 0)) {
                    segments += cable(b, matrix, a, 0, end, 0, 0xffe3bd75, false);
                }
            }
            for (int id : a.triggers) {
                NpcDialogueGraph.Node end = graph.get(id);
                if (end != null && !(hoverFrom == a.id && hoverFromPort == 0 && hoverTo == end.id && hoverToPort == 0)) {
                    segments += cable(b, matrix, a, 0, end, 0, 0xffc77dff, false);
                }
            }
        }
        if (hoverFrom >= 0) {
            NpcDialogueGraph.Node fromNode = graph.get(hoverFrom);
            NpcDialogueGraph.Node toNode = graph.get(hoverTo);
            if (fromNode != null && toNode != null) {
                segments += cable(b, matrix, fromNode, hoverFromPort, toNode, hoverToPort, hoverColor, true);
            }
        }
        if (linkFrom >= 0) {
            NpcDialogueGraph.Node a = graph.get(linkFrom);
            if (a != null) {
                double x1 = canvas.portScreenX(a, true);
                double y1 = canvas.portScreenY(a, true, linkFromPort);
                segments += curve(b, matrix, x1, y1, mx, my, 0xffe8edf4, false);
            }
        }
        if (segments > 0) BufferUploader.drawWithShader(b.buildOrThrow()); else b.build();
        RenderSystem.enableDepthTest(); RenderSystem.enableCull(); RenderSystem.disableBlend();
    }
    private boolean hitCable(NpcDialogueGraph.Node a,int fromPort,NpcDialogueGraph.Node c,int toPort,double mx,double my){
        return canvas.hitCable(a,fromPort,c,toPort,mx,my);
    }
    private int cable(BufferBuilder b,Matrix4f m,NpcDialogueGraph.Node a,int fromPort,NpcDialogueGraph.Node c,int toPort,int color,boolean hovered){
        double x1=canvas.portScreenX(a,true),y1=canvas.portScreenY(a,true,fromPort),x2=canvas.portScreenX(c,false),y2=canvas.portScreenY(c,false,toPort);
        double margin=Math.max(25.0*canvas.zoom,Math.abs(x2-x1)*0.45);
        if(Math.max(x1,x2)+margin<canvas.left||Math.min(x1,x2)-margin>canvas.right||Math.max(y1,y2)<canvas.top||Math.min(y1,y2)>canvas.bottom)return 0;
        return curve(b,m,x1,y1,x2,y2,color,hovered);
    }
    private int curve(BufferBuilder b,Matrix4f m,double x1,double y1,double x2,double y2,int color,boolean hovered){
        double bend=Math.max(25.0*canvas.zoom,Math.abs(x2-x1)*0.45),px=x1,py=y1;
        double baseHalfWidth=canvas.zoom<0.30?Math.max(0.45,canvas.zoom/0.30*0.85):1.0;
        int segments=0;
        if(hovered){
            double glowWidth=baseHalfWidth+Math.max(1.2,1.6*Math.min(1.0,canvas.zoom));
            int glowColor=0x88000000|(color&0x00ffffff);
            px=x1;py=y1;
            for(int i=1;i<=20;i++){
                double t=i/20.0,u=1-t,x=u*u*u*x1+3*u*u*t*(x1+bend)+3*u*t*t*(x2-bend)+t*t*t*x2,y=u*u*u*y1+3*u*u*t*y1+3*u*t*t*y2+t*t*t*y2;
                double dx=x-px,dy=y-py,length=Math.sqrt(dx*dx+dy*dy);
                if(length>0.001){
                    double nx=-dy/length*glowWidth,ny=dx/length*glowWidth;
                    b.addVertex(m,(float)(px+nx),(float)(py+ny),0).setColor(glowColor);
                    b.addVertex(m,(float)(px-nx),(float)(py-ny),0).setColor(glowColor);
                    b.addVertex(m,(float)(x-nx),(float)(y-ny),0).setColor(glowColor);
                    b.addVertex(m,(float)(x+nx),(float)(y+ny),0).setColor(glowColor);
                }
                px=x;py=y;
            }
            segments+=20;
            double coreWidth=baseHalfWidth+0.5;
            int r=(color>>16)&0xff, g=(color>>8)&0xff, bl=color&0xff;
            int hr=Math.min(255,r+(int)((255-r)*0.75f));
            int hg=Math.min(255,g+(int)((255-g)*0.75f));
            int hb=Math.min(255,bl+(int)((255-bl)*0.75f));
            int coreColor=0xff000000|(hr<<16)|(hg<<8)|hb;
            px=x1;py=y1;
            for(int i=1;i<=20;i++){
                double t=i/20.0,u=1-t,x=u*u*u*x1+3*u*u*t*(x1+bend)+3*u*t*t*(x2-bend)+t*t*t*x2,y=u*u*u*y1+3*u*u*t*y1+3*u*t*t*y2+t*t*t*y2;
                double dx=x-px,dy=y-py,length=Math.sqrt(dx*dx+dy*dy);
                if(length>0.001){
                    double nx=-dy/length*coreWidth,ny=dx/length*coreWidth;
                    b.addVertex(m,(float)(px+nx),(float)(py+ny),0).setColor(coreColor);
                    b.addVertex(m,(float)(px-nx),(float)(py-ny),0).setColor(coreColor);
                    b.addVertex(m,(float)(x-nx),(float)(y-ny),0).setColor(coreColor);
                    b.addVertex(m,(float)(x+nx),(float)(y+ny),0).setColor(coreColor);
                }
                px=x;py=y;
            }
            segments+=20;
            return segments;
        }else{
            px=x1;py=y1;
            for(int i=1;i<=20;i++){
                double t=i/20.0,u=1-t,x=u*u*u*x1+3*u*u*t*(x1+bend)+3*u*t*t*(x2-bend)+t*t*t*x2,y=u*u*u*y1+3*u*u*t*y1+3*u*t*t*y2+t*t*t*y2;
                double dx=x-px,dy=y-py,length=Math.sqrt(dx*dx+dy*dy);
                if(length>0.001){
                    double nx=-dy/length*baseHalfWidth,ny=dx/length*baseHalfWidth;
                    b.addVertex(m,(float)(px+nx),(float)(py+ny),0).setColor(color);
                    b.addVertex(m,(float)(px-nx),(float)(py-ny),0).setColor(color);
                    b.addVertex(m,(float)(x-nx),(float)(y-ny),0).setColor(color);
                    b.addVertex(m,(float)(x+nx),(float)(y+ny),0).setColor(color);
                }
                px=x;py=y;
            }
            return 20;
        }
    }
}
