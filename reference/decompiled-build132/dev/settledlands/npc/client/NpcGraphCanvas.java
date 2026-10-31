package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcProfile;
import java.util.List;

/** Pure viewport math, no renderer/world. The node model stores world-space coordinates. */
public final class NpcGraphCanvas {
    public static final int NODE_W=220, NODE_H=100, PORT=8;
    public static final double MIN_ZOOM=0.05, MAX_ZOOM=2.0;
    public double panX=60, panY=90, zoom=1;
    public int left=8,top=38,right=800,bottom=500;
    public int hitPortIndex = 0;

    public void bounds(int left,int top,int right,int bottom){this.left=left;this.top=top;this.right=Math.max(left+1,right);this.bottom=Math.max(top+1,bottom);}
    public double worldX(double screenX){return (screenX-left-panX)/zoom;}
    public double worldY(double screenY){return (screenY-top-panY)/zoom;}
    public double screenX(double worldX){return left+panX+worldX*zoom;}
    public double screenY(double worldY){return top+panY+worldY*zoom;}
    public boolean contains(double x,double y){return left<=x&&x<right&&top<=y&&y<bottom;}
    public void pan(double dx,double dy){panX=clamp(panX+dx,-100000,100000);panY=clamp(panY+dy,-100000,100000);}
    public void zoomAt(double amount,double mouseX,double mouseY){
        double x=worldX(mouseX),y=worldY(mouseY);double next=clamp(zoom*Math.pow(1.15,amount),MIN_ZOOM,MAX_ZOOM);
        zoom=next;panX=mouseX-left-x*zoom;panY=mouseY-top-y*zoom;
    }
    public void focus(NpcDialogueGraph.Node n){if(n!=null){panX=(right-left)/2.0-(n.x+NODE_W/2.0)*zoom;panY=(bottom-top)/2.0-(n.y+NODE_H/2.0)*zoom;}}
    public boolean visible(NpcDialogueGraph.Node n){
        double x=screenX(n.x),y=screenY(n.y);
        return x+NODE_W*zoom>=left&&x<=right&&y+NODE_H*zoom>=top&&y<=bottom;
    }
    public int hit(NpcDialogueGraph graph,double mouseX,double mouseY){
        if(!contains(mouseX,mouseY))return -1;
        double x=worldX(mouseX),y=worldY(mouseY);int found=-1;
        for(NpcDialogueGraph.Node n:graph.nodes.values())if(n.x<=x&&x<=n.x+NODE_W&&n.y<=y&&y<=n.y+NODE_H)found=n.id;
        return found;
    }
    public double portScreenX(NpcDialogueGraph.Node n, boolean output) {
        return screenX(n.x + (output ? NODE_W : 0));
    }
    public double portScreenY(NpcDialogueGraph.Node n, boolean output, int portIndex) {
        if (n.type == NpcDialogueGraph.IF) {
            return screenY(n.y + (portIndex == 1 ? NODE_H * 0.70 : NODE_H * 0.35));
        }
        if (n.type == NpcDialogueGraph.RANDOM && output) {
            int ports = Math.max(2, n.randomMax);
            double ratio = (portIndex + 1.0) / (ports + 1.0);
            return screenY(n.y + NODE_H * ratio);
        }
        return screenY(n.y + NODE_H / 2.0);
    }
    public int hitPort(NpcDialogueGraph graph,double mouseX,double mouseY,boolean output){
        if(!contains(mouseX,mouseY))return -1;
        double radius=Math.max(10.0,10.0*zoom);
        double bestDistSq=radius*radius;
        int found=-1;
        hitPortIndex=0;
        for(NpcDialogueGraph.Node n:graph.nodes.values()){
            if(n.special!=NpcDialogueGraph.NORMAL)continue;
            if(output){
                if(n.type==NpcDialogueGraph.IF){
                    double sx0=portScreenX(n,true),sy0=portScreenY(n,true,0);
                    double d0=(mouseX-sx0)*(mouseX-sx0)+(mouseY-sy0)*(mouseY-sy0);
                    if(d0<=bestDistSq){bestDistSq=d0;found=n.id;hitPortIndex=0;}
                    double sx1=portScreenX(n,true),sy1=portScreenY(n,true,1);
                    double d1=(mouseX-sx1)*(mouseX-sx1)+(mouseY-sy1)*(mouseY-sy1);
                    if(d1<=bestDistSq){bestDistSq=d1;found=n.id;hitPortIndex=1;}
                }else if(n.type==NpcDialogueGraph.RANDOM){
                    int ports = Math.max(2, n.randomMax);
                    for(int p = 0; p < ports; p++){
                        double sx=portScreenX(n,true),sy=portScreenY(n,true,p);
                        double d=(mouseX-sx)*(mouseX-sx)+(mouseY-sy)*(mouseY-sy);
                        if(d<=bestDistSq){bestDistSq=d;found=n.id;hitPortIndex=p;}
                    }
                }else{
                    double sx=portScreenX(n,true),sy=portScreenY(n,true,0);
                    double distSq=(mouseX-sx)*(mouseX-sx)+(mouseY-sy)*(mouseY-sy);
                    if(distSq<=bestDistSq){bestDistSq=distSq;found=n.id;hitPortIndex=0;}
                }
            }else{
                if(n.type==NpcDialogueGraph.IF){
                    double sx0=portScreenX(n,false),sy0=portScreenY(n,false,0);
                    double d0=(mouseX-sx0)*(mouseX-sx0)+(mouseY-sy0)*(mouseY-sy0);
                    if(d0<=bestDistSq){bestDistSq=d0;found=n.id;hitPortIndex=0;}
                    double sx1=portScreenX(n,false),sy1=portScreenY(n,false,1);
                    double d1=(mouseX-sx1)*(mouseX-sx1)+(mouseY-sy1)*(mouseY-sy1);
                    if(d1<=bestDistSq){bestDistSq=d1;found=n.id;hitPortIndex=1;}
                }else{
                    double sx=portScreenX(n,false),sy=portScreenY(n,false,0);
                    double distSq=(mouseX-sx)*(mouseX-sx)+(mouseY-sy)*(mouseY-sy);
                    if(distSq<=bestDistSq){bestDistSq=distSq;found=n.id;hitPortIndex=0;}
                }
            }
        }
        return found;
    }
    public boolean hitCable(NpcDialogueGraph.Node a,int fromPort,NpcDialogueGraph.Node c,int toPort,double mx,double my){
        if(c==null)return false;
        double x1=portScreenX(a,true),y1=portScreenY(a,true,fromPort),x2=portScreenX(c,false),y2=portScreenY(c,false,toPort);
        double bend=Math.max(25.0*zoom,Math.abs(x2-x1)*0.45),px=x1,py=y1;
        double hitRadius=Math.max(1.8,Math.min(5.5,4.0*zoom+1.2));
        double maxDistSq=hitRadius*hitRadius;
        for(int i=1;i<=20;i++){
            double t=i/20.0,u=1-t,x=u*u*u*x1+3*u*u*t*(x1+bend)+3*u*t*t*(x2-bend)+t*t*t*x2,y=u*u*u*y1+3*u*u*t*y1+3*u*t*t*y2+t*t*t*y2;
            double dx=x-px,dy=y-py,len=dx*dx+dy*dy,v=len==0?0:Math.max(0,Math.min(1,((mx-px)*dx+(my-py)*dy)/len));
            double ex=mx-(px+dx*v),ey=my-(py+dy*v);if(ex*ex+ey*ey<=maxDistSq)return true;px=x;py=y;
        }return false;
    }
    public void move(NpcDialogueGraph.Node n,double worldX,double worldY){
        n.x=(int)clamp(Math.round(worldX/8)*8,-NpcDialogueGraph.MAX_COORD,NpcDialogueGraph.MAX_COORD);
        n.y=(int)clamp(Math.round(worldY/8)*8,-NpcDialogueGraph.MAX_COORD,NpcDialogueGraph.MAX_COORD);
    }
    public void frameAll(NpcDialogueGraph graph){
        if(graph.nodes.isEmpty())return;int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE;
        for(NpcDialogueGraph.Node n:graph.nodes.values()){minX=Math.min(minX,n.x);minY=Math.min(minY,n.y);maxX=Math.max(maxX,n.x+NODE_W);maxY=Math.max(maxY,n.y+NODE_H);}
        zoom=clamp(Math.min((right-left-60.0)/Math.max(1,maxX-minX),(bottom-top-60.0)/Math.max(1,maxY-minY)),MIN_ZOOM,1);
        panX=(right-left)/2.0-(minX+maxX)/2.0*zoom;panY=(bottom-top)/2.0-(minY+maxY)/2.0*zoom;
    }

    // --- Mobility Points Support (identical coordinate & viewport math) ---
    public double portScreenX(NpcProfile.MobilityPoint n, boolean output) {
        return screenX(n.canvasX + (output ? NODE_W : 0));
    }
    public double portScreenY(NpcProfile.MobilityPoint n, boolean output, int portIndex) {
        if (n.type == NpcProfile.MobilityPoint.TYPE_IF) {
            return screenY(n.canvasY + (portIndex == 1 ? NODE_H * 0.70 : NODE_H * 0.35));
        }
        if (n.type == NpcProfile.MobilityPoint.TYPE_RANDOM && output) {
            int ports = Math.max(2, n.randomMax);
            double ratio = (portIndex + 1.0) / (ports + 1.0);
            return screenY(n.canvasY + NODE_H * ratio);
        }
        return screenY(n.canvasY + NODE_H / 2.0);
    }
    public boolean visible(NpcProfile.MobilityPoint n) {
        double x = screenX(n.canvasX), y = screenY(n.canvasY);
        return x + NODE_W * zoom >= left && x <= right && y + NODE_H * zoom >= top && y <= bottom;
    }
    public int hitPoint(List<NpcProfile.MobilityPoint> points, double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) return -1;
        double x = worldX(mouseX), y = worldY(mouseY);
        int found = -1;
        for (int i = 0; i < points.size(); i++) {
            NpcProfile.MobilityPoint n = points.get(i);
            if (n.canvasX <= x && x <= n.canvasX + NODE_W && n.canvasY <= y && y <= n.canvasY + NODE_H) {
                found = i;
            }
        }
        return found;
    }
    public int hitPointPort(List<NpcProfile.MobilityPoint> points, double mouseX, double mouseY, boolean output) {
        if (!contains(mouseX, mouseY)) return -1;
        double radius = Math.max(14.0, 14.0 * zoom);
        double bestDistSq = radius * radius;
        int found = -1;
        hitPortIndex = 0;
        for (int i = 0; i < points.size(); i++) {
            NpcProfile.MobilityPoint n = points.get(i);
            if (output) {
                if (n.type == NpcProfile.MobilityPoint.TYPE_IF) {
                    double sx0 = portScreenX(n, true), sy0 = portScreenY(n, true, 0);
                    double d0 = (mouseX - sx0) * (mouseX - sx0) + (mouseY - sy0) * (mouseY - sy0);
                    if (d0 <= bestDistSq) { bestDistSq = d0; found = i; hitPortIndex = 0; }
                    double sx1 = portScreenX(n, true), sy1 = portScreenY(n, true, 1);
                    double d1 = (mouseX - sx1) * (mouseX - sx1) + (mouseY - sy1) * (mouseY - sy1);
                    if (d1 <= bestDistSq) { bestDistSq = d1; found = i; hitPortIndex = 1; }
                } else if (n.type == NpcProfile.MobilityPoint.TYPE_RANDOM) {
                    int ports = Math.max(2, n.randomMax);
                    for (int p = 0; p < ports; p++) {
                        double sx = portScreenX(n, true), sy = portScreenY(n, true, p);
                        double d = (mouseX - sx) * (mouseX - sx) + (mouseY - sy) * (mouseY - sy);
                        if (d <= bestDistSq) { bestDistSq = d; found = i; hitPortIndex = p; }
                    }
                } else {
                    double sx = portScreenX(n, true), sy = portScreenY(n, true, 0);
                    double distSq = (mouseX - sx) * (mouseX - sx) + (mouseY - sy) * (mouseY - sy);
                    if (distSq <= bestDistSq) { bestDistSq = distSq; found = i; hitPortIndex = 0; }
                }
            } else {
                double sx = portScreenX(n, false), sy = portScreenY(n, false, 0);
                double distSq = (mouseX - sx) * (mouseX - sx) + (mouseY - sy) * (mouseY - sy);
                if (distSq <= bestDistSq) { bestDistSq = distSq; found = i; hitPortIndex = 0; }
            }
        }
        return found;
    }
    public boolean hitPointCable(NpcProfile.MobilityPoint a, int fromPort, NpcProfile.MobilityPoint c, int toPort, double mx, double my) {
        if (c == null) return false;
        double x1 = portScreenX(a, true), y1 = portScreenY(a, true, fromPort), x2 = portScreenX(c, false), y2 = portScreenY(c, false, toPort);
        double bend = Math.max(25.0 * zoom, Math.abs(x2 - x1) * 0.45), px = x1, py = y1;
        double hitRadius = Math.max(2.0, Math.min(6.0, 4.0 * zoom + 1.2));
        double maxDistSq = hitRadius * hitRadius;
        for (int i = 1; i <= 20; i++) {
            double t = i / 20.0, u = 1 - t, x = u * u * u * x1 + 3 * u * u * t * (x1 + bend) + 3 * u * t * t * (x2 - bend) + t * t * t * x2, y = u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y2;
            double dx = x - px, dy = y - py, len = dx * dx + dy * dy, v = len == 0 ? 0 : Math.max(0, Math.min(1, ((mx - px) * dx + (my - py) * dy) / len));
            double ex = mx - (px + dx * v), ey = my - (py + dy * v);
            if (ex * ex + ey * ey <= maxDistSq) return true;
            px = x; py = y;
        }
        return false;
    }
    public void move(NpcProfile.MobilityPoint n, double worldX, double worldY) {
        n.canvasX = (int) clamp(Math.round(worldX / 8) * 8, -50000, 50000);
        n.canvasY = (int) clamp(Math.round(worldY / 8) * 8, -50000, 50000);
    }
    public void frameAllPoints(List<NpcProfile.MobilityPoint> points) {
        if (points.isEmpty()) return;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (NpcProfile.MobilityPoint n : points) {
            minX = Math.min(minX, n.canvasX);
            minY = Math.min(minY, n.canvasY);
            maxX = Math.max(maxX, n.canvasX + NODE_W);
            maxY = Math.max(maxY, n.canvasY + NODE_H);
        }
        zoom = clamp(Math.min((right - left - 60.0) / Math.max(1, maxX - minX), (bottom - top - 60.0) / Math.max(1, maxY - minY)), MIN_ZOOM, 1);
        panX = (right - left) / 2.0 - (minX + maxX) / 2.0 * zoom;
        panY = (bottom - top) / 2.0 - (minY + maxY) / 2.0 * zoom;
    }

    private static double clamp(double x,double a,double b){return Math.max(a,Math.min(b,x));}
}
