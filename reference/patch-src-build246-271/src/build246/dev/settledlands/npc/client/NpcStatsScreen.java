package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.NpcStatsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Displays a server snapshot; only an opaque nonce goes back to the server. */
public final class NpcStatsScreen extends Screen {
    private final NpcStatsNetwork.Open snapshot;
    private NpcStatsScreen(NpcStatsNetwork.Open snapshot){super(Component.translatable("gui.settledlands.stats.title"));this.snapshot=snapshot;}
    public static void install(){NpcStatsNetwork.setClientOpener(open->Minecraft.getInstance().setScreen(new NpcStatsScreen(open)));}
    @Override protected void init(){
        this.addRenderableWidget(Button.builder(Component.translatable("gui.settledlands.stats.enter"),button->{
            NpcNet.sendToServer(new NpcStatsNetwork.Enter(snapshot.entityId(),snapshot.nonceMost(),snapshot.nonceLeast()));this.onClose();
        }).bounds(width/2-80,height/2+58,160,20).build());
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){
        g.fill(0,0,width,height,0x99101824);
        int x=width/2-128,y=height/2-80;
        g.fill(x-2,y-2,x+258,y+164,0xff56677a);g.fill(x,y,x+256,y+162,0xff263442);
        g.drawCenteredString(font,Component.translatable("gui.settledlands.stats.title"),width/2,y+10,0xfff4f5f8);
        g.drawString(font,Component.translatable("gui.settledlands.stats.health_value",snapshot.health(),snapshot.health()*2),x+20,y+38,0xffe7edf4,false);
        g.drawString(font,Component.translatable("gui.settledlands.stats.damage_value",snapshot.damage(),snapshot.damage()*10),x+20,y+57,0xffe7edf4,false);
        g.drawString(font,Component.translatable("gui.settledlands.stats.speed_value",snapshot.speed(),snapshot.speed()*10),x+20,y+76,0xffe7edf4,false);
        if(!snapshot.eligible())g.drawCenteredString(font,Component.translatable("gui.settledlands.stats.needs_cult"),width/2,y+102,0xffffc17a);
    }
    @Override public boolean isPauseScreen(){return false;}
}
