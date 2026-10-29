package dev.settledlands.npc;

import dev.settledlands.npc.*;
import dev.settledlands.npc.client.PlayerBodyRenderer;
import dev.settledlands.model.BbModelManager;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Real patched login method + actual payload codec/client handler + actual
 * PlayerBodyRenderer, with explicit fake network, persistence and game API. */
public final class LoginModelScenario {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static UUID uuid(int i) { return new UUID(0, i); }
    static ServerPlayer player(MinecraftServer s, int i, boolean possessed, String model) {
        ServerPlayer p = new ServerPlayer(s, uuid(i));
        p.getPersistentData().putBoolean("settledlands_possess", possessed);
        p.getPersistentData().putString("settledlands_possess_model", model);
        p.getPersistentData().putString("settledlands_possess_name", "Name" + i);
        return p;
    }
    static void login(Player p) { PossessionSystem.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(p)); }
    static void callPrivate(String name, Class<?>[] types, Object... args) {
        try {
            var method = PossessionSystem.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(null, args);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    static void clearInboxes(MinecraftServer s) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) p.inbox.clear();
        PacketDistributor.attempts = 0;
        PacketDistributor.failOnAttempt = -1;
    }
    static void deliver(ServerPlayer receiver) {
        PossessionClientSync.CLIENT_STATES.clear();
        PossessionClientSync.CLIENT_STATES.putAll(receiver.clientStates);
        for (var raw : receiver.inbox) {
            PossessionClientSync packet = (PossessionClientSync) raw;
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf();
            PossessionClientSync.CODEC.encode(buf, packet);
            PossessionClientSync decoded = PossessionClientSync.CODEC.decode(buf);
            check(decoded.player().equals(packet.player()) && decoded.model().equals(packet.model())
                  && decoded.name().equals(packet.name()), "actual codec roundtrip");
            PossessionClientSync.handleClient(decoded, work -> {
                work.run();
                return CompletableFuture.completedFuture(null);
            });
        }
        receiver.clientStates.clear();
        receiver.clientStates.putAll(PossessionClientSync.CLIENT_STATES);
        receiver.inbox.clear();
    }
    static String state(ServerPlayer receiver, UUID id) {
        var value = receiver.clientStates.get(id);
        return value == null ? "" : value.model;
    }
    static boolean render(ServerPlayer receiver, UUID id, String expectedModel) {
        PossessionClientSync.CLIENT_STATES.clear();
        PossessionClientSync.CLIENT_STATES.putAll(receiver.clientStates);
        AbstractClientPlayer player = new AbstractClientPlayer(id);
        Minecraft.getInstance().player = null; // third-person / remote view
        RenderPlayerEvent.Pre event = new RenderPlayerEvent.Pre(player);
        BbModelManager.lastRendered = "";
        PlayerBodyRenderer.onRenderPlayerPre(event);
        if (event.canceled) check(BbModelManager.lastRendered.equals(expectedModel), "wrong real renderer model");
        return event.canceled;
    }
    static void fromJar(Class<?> type) {
        try {
            var source = java.nio.file.Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
            check(source.equals(java.nio.file.Path.of(System.getProperty("fixtureJar")).toAbsolutePath()), type.getName()+" not loaded from candidate JAR");
        } catch (java.net.URISyntaxException error) { throw new AssertionError(error); }
    }
    public static void main(String[] args) {
        boolean old = args.length > 0 && args[0].equals("base");
        fromJar(PossessionClientSync.class); fromJar(PossessionClientSync.State.class); fromJar(PlayerBodyRenderer.class);
        if (!old) {
            try { fromJar(Class.forName("dev.settledlands.npc.PossessionLoginSync251")); }
            catch (ClassNotFoundException error) { throw new AssertionError(error); }
        }
        MinecraftServer server = new MinecraftServer();
        PacketDistributor.server = server;
        // Simulate serialization/reload by copying saved NBT to a new server
        // player object and starting with an empty client cache.
        ServerPlayer previousSession = player(server, 1, true, "ivy_oleander");
        Map<String,Object> persisted = new HashMap<>(previousSession.getPersistentData().values);
        ServerPlayer ivy = new ServerPlayer(server, previousSession.getUUID());
        ivy.getPersistentData().values.putAll(persisted);
        ServerPlayer other = player(server, 2, true, "sage");
        ServerPlayer plain = player(server, 3, false, "stale_model_must_not_render");
        server.getPlayerList().getPlayers().addAll(List.of(ivy, other, plain));
        ivy.clientStates.put(plain.getUUID(), new PossessionClientSync.State("old_ivy", "old"));
        ivy.clientStates.put(other.getUUID(), new PossessionClientSync.State("old_sage", "old"));
        other.clientStates.put(ivy.getUUID(), new PossessionClientSync.State("old_ivy", "old"));
        Map<String,Object> saved = new HashMap<>(ivy.getPersistentData().values);
        login(ivy);
        if (old) {
            check(ivy.inbox.isEmpty() && other.inbox.isEmpty(), "old baseline unexpectedly synchronized possessed login");
            deliver(ivy);
            check(!render(ivy, ivy.getUUID(), "ivy_oleander"), "baseline not missing body");
            System.out.println("PASS baseline 250 reproduces missing possessed model after fresh login");
            return;
        }
        check(ivy.getPersistentData().values.equals(saved), "login changed saved possession data");
        check(BodyWear.logins == 1 && NpcBodyStats.logins == 1, "old login hooks skipped");
        check(ivy.inbox.size()==3 && other.inbox.size()==1 && plain.inbox.size()==1, "complete multiplayer snapshots");
        deliver(ivy); deliver(other); deliver(plain);
        check(state(ivy,ivy.getUUID()).equals("ivy_oleander"), "own body lost on relog");
        check(state(ivy,other.getUUID()).equals("sage"), "existing possessed player missing");
        check(state(ivy,plain.getUUID()).isEmpty(), "stale unpossessed player retained");
        check(state(other,ivy.getUUID()).equals("ivy_oleander") && state(plain,ivy.getUUID()).equals("ivy_oleander"), "observers not updated");
        check(render(ivy,ivy.getUUID(),"ivy_oleander") && render(ivy,other.getUUID(),"sage"), "actual renderer did not draw synced models");
        check(render(other,ivy.getUUID(),"ivy_oleander"), "observer actual renderer did not draw rejoined model");
        check(!render(ivy,plain.getUUID(),""), "unpossessed stale skin incorrectly rendered");

        // Existing bodiless: old method also returned before its old sync block.
        clearInboxes(server); plain.bodiless=true;
        plain.getPersistentData().putBoolean("settledlands_bodiless_given", true);
        plain.clientStates.put(plain.getUUID(),new PossessionClientSync.State("stale_model_must_not_render","old"));
        ivy.clientStates.put(plain.getUUID(),new PossessionClientSync.State("stale_model_must_not_render","old"));
        login(plain); check(plain.inbox.size()==3 && ivy.inbox.size()==1 && other.inbox.size()==1, "bodiless login was skipped");
        deliver(plain); deliver(ivy); deliver(other);
        check(state(plain,plain.getUUID()).isEmpty() && state(ivy,plain.getUUID()).isEmpty(), "unpossessed state not cleared");
        check(state(plain,ivy.getUUID()).equals("ivy_oleander") && render(plain,ivy.getUUID(),"ivy_oleander"), "bodiless client misses other model");
        check(plain.bodiless && plain.addEffectCount==0, "bodiless effect changed");

        // Fresh login still gets its previous bodiless behavior exactly once.
        clearInboxes(server); ServerPlayer fresh = player(server,4,false,""); server.getPlayerList().getPlayers().add(fresh);
        login(fresh);
        check(fresh.bodiless && fresh.addEffectCount==1 && fresh.getPersistentData().getBoolean("settledlands_bodiless_given"), "initial bodiless behavior changed");
        check(!PossessionSystem.isPossessed(fresh) && fresh.inbox.size()==4, "fresh login duplicate/missing snapshot");
        deliver(fresh);check(render(fresh,ivy.getUUID(),"ivy_oleander"), "fresh client misses existing player");

        // The ordinary possession / unpossession broadcasts remain unchanged.
        clearInboxes(server); callPrivate("setPossessed",new Class<?>[]{ServerPlayer.class,String.class,String.class},fresh,"ivy_oleander","New Ivy");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) { deliver(p); check(state(p,fresh.getUUID()).equals("ivy_oleander"), "normal enter broadcast"); }
        callPrivate("clearPossessed",new Class<?>[]{ServerPlayer.class},fresh);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) { deliver(p); check(state(p,fresh.getUUID()).isEmpty(), "normal leave broadcast"); }
        check(!PossessionSystem.isPossessed(fresh) && !fresh.getPersistentData().values.containsKey("settledlands_possess_model"), "normal unpossess state");

        // Failure to send one snapshot to the joiner cannot stop observer updates.
        clearInboxes(server); PacketDistributor.failOnAttempt=2; login(ivy);
        check(other.inbox.size()==1 && plain.inbox.size()==1 && fresh.inbox.size()==1, "one failed direction suppressed observers");
        deliver(other);check(state(other,ivy.getUUID()).equals("ivy_oleander"), "observer lost after send failure");
        PacketDistributor.failOnAttempt=-1;

        // The server's list usually already contains the joined player; test the
        // conservative fallback for an event arriving just before insertion too.
        clearInboxes(server);server.getPlayerList().getPlayers().remove(ivy);login(ivy);
        check(ivy.inbox.size()==4, "self snapshot missing when not in online list");
        deliver(ivy);check(state(ivy,ivy.getUUID()).equals("ivy_oleander"), "pre-insertion self snapshot");
        server.getPlayerList().getPlayers().add(0,ivy);

        clearInboxes(server);int before=PacketDistributor.attempts;login(new Player(uuid(99)));
        check(PacketDistributor.attempts==before, "client-side player was synchronized by server hook");
        ServerPlayer noServer = player(null,7,true,"ivy_oleander");login(noServer);
        check(noServer.inbox.isEmpty(), "null server attempted networking");
        System.out.println("PASS relog model, real payload codec/client handler/renderer, multiplayer, stale state, first login and send failure");
        System.out.println("Persistence, transport, event bus, Minecraft and visual output are synthetic; NOT a game reload");
    }
}
