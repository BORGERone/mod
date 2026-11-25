package dev.settledlands.chest;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 443: C2S/S2C sync for {@code config/settledlands/books/*.txt} custom colored books,
 * integrated with {@link ChestLootNet436#onRegister}.
 */
public final class CustomBookNet443 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/CustomBookNet443");
    public static final int MAX_WIRE_CHARS = 30_000;

    private CustomBookNet443() {}

    /** S2C: summary list of all saved server books (id, colored title, author, pageCount). */
    public record Books(String payload) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Books> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath("settledlands", "custom_books"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Books> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Books::payload, Books::new);

        @Override
        public CustomPacketPayload.Type<Books> type() {
            return TYPE;
        }
    }

    /** C2S: request full pages of one custom book (or a fresh suggested id). */
    public record BookQuery(String bookId) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<BookQuery> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath("settledlands", "custom_book_query"));

        public static final StreamCodec<RegistryFriendlyByteBuf, BookQuery> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, BookQuery::bookId, BookQuery::new);

        @Override
        public CustomPacketPayload.Type<BookQuery> type() {
            return TYPE;
        }
    }

    /** S2C: full book data or save/delete/give result. */
    public record BookData(String payload) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<BookData> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath("settledlands", "custom_book_data"));

        public static final StreamCodec<RegistryFriendlyByteBuf, BookData> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, BookData::payload, BookData::new);

        @Override
        public CustomPacketPayload.Type<BookData> type() {
            return TYPE;
        }
    }

    /** C2S: save, delete, or test-give a custom book on the server. */
    public record BookSave(String payload) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<BookSave> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath("settledlands", "custom_book_save"));

        public static final StreamCodec<RegistryFriendlyByteBuf, BookSave> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, BookSave::payload, BookSave::new);

        @Override
        public CustomPacketPayload.Type<BookSave> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        if (registrar == null) return;
        try {
            registrar.playToClient(Books.TYPE, Books.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() -> onBooks(payload, context)));
        } catch (Throwable failure) {
            LOG.warn("custom_book: failed to register settledlands:custom_books: " + failure);
        }
        try {
            registrar.playToServer(BookQuery.TYPE, BookQuery.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() -> onBookQuery(payload, context)));
        } catch (Throwable failure) {
            LOG.warn("custom_book: failed to register settledlands:custom_book_query: " + failure);
        }
        try {
            registrar.playToClient(BookData.TYPE, BookData.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() -> onBookData(payload, context)));
        } catch (Throwable failure) {
            LOG.warn("custom_book: failed to register settledlands:custom_book_data: " + failure);
        }
        try {
            registrar.playToServer(BookSave.TYPE, BookSave.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() -> onBookSave(payload, context)));
        } catch (Throwable failure) {
            LOG.warn("custom_book: failed to register settledlands:custom_book_save: " + failure);
        }
    }

    // ---- server senders -----------------------------------------------------

    public static void sendBookSummaries(ServerPlayer player) {
        if (player == null) return;
        try {
            String wire = buildSummariesPayload();
            PacketDistributor.sendToPlayer(player, new Books(wire));
        } catch (Throwable failure) {
            LOG.warn("custom_book: failed to send book summaries: " + failure);
        }
    }

    public static void sendBookSummariesToOperators(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) return;
        try {
            String wire = buildSummariesPayload();
            Books pkt = new Books(wire);
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players == null) return;
            for (ServerPlayer sp : players) {
                if (sp != null && sp.hasPermissions(2)) {
                    PacketDistributor.sendToPlayer(sp, pkt);
                }
            }
        } catch (Throwable failure) {
            LOG.warn("custom_book: failed to broadcast book summaries: " + failure);
        }
    }

    private static String buildSummariesPayload() {
        List<CustomBook443.BookDef> books = CustomBookManager443.listBooks();
        String wire = CustomBook443.encodeSummariesWire(books);
        if (wire.length() > MAX_WIRE_CHARS) {
            wire = wire.substring(0, MAX_WIRE_CHARS);
            int lastNl = wire.lastIndexOf('\n');
            wire = lastNl > 0 ? wire.substring(0, lastNl) : "";
        }
        return wire;
    }

    // ---- client senders -----------------------------------------------------

    public static void sendBookQuery(String rawBookId) {
        try {
            String id = CustomBook443.normalizeBookId(rawBookId);
            PacketDistributor.sendToServer(new BookQuery(id));
        } catch (Throwable failure) {
            LOG.warn("custom_book: failed to query book: " + failure);
        }
    }

    public static boolean sendBookAction(String command, CustomBook443.BookDef book) {
        try {
            String cmd = (command == null || command.isEmpty()) ? "SAVE" : command.trim();
            String body = "DELETE".equals(cmd)
                    ? (book == null ? "" : CustomBook443.normalizeBookId(book.id))
                    : CustomBook443.encodeBookWire(book);
            if (body.isEmpty()) return false;
            String wire = cmd + "\n" + body;
            if (wire.length() > MAX_WIRE_CHARS) return false;
            PacketDistributor.sendToServer(new BookSave(wire));
            return true;
        } catch (Throwable failure) {
            LOG.warn("custom_book: failed to send book save action: " + failure);
            return false;
        }
    }

    // ---- handlers -----------------------------------------------------------

    private static void onBooks(Books payload, IPayloadContext context) {
        if (payload == null) return;
        List<CustomBook443.BookDef> list = CustomBook443.decodeSummariesWire(payload.payload());
        CustomBook443.setClientBookSummaries(list);
        CustomBook443.notifyClientBooksUpdated();
    }

    private static void onBookData(BookData payload, IPayloadContext context) {
        if (payload == null || payload.payload() == null) return;
        String raw = payload.payload();
        String[] lines = raw.split("\n", 2);
        String status = lines.length >= 1 ? lines[0].trim() : "";
        String body = lines.length >= 2 ? lines[1] : "";
        if ("OK".equals(status) || "SAVED".equals(status)) {
            CustomBook443.BookDef parsed = CustomBook443.decodeBookWire(body);
            if (parsed != null) {
                CustomBook443.putClientBook(parsed);
            }
        } else if ("DELETED".equals(status)) {
            CustomBook443.removeClientBook(body.trim());
        }
        CustomBook443.notifyClientBookData(raw);
    }

    private static void onBookQuery(BookQuery payload, IPayloadContext context) {
        if (payload == null || context == null) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(2)) {
            PacketDistributor.sendToPlayer(player, new BookData("ERR:noperm\n"));
            return;
        }
        sendBookSummaries(player);
        String requested = CustomBook443.normalizeBookId(payload.bookId());
        if (!requested.isEmpty() && CustomBookManager443.bookExists(requested)) {
            CustomBook443.BookDef def = CustomBookManager443.loadBookQuiet(requested);
            if (def != null) {
                String encoded = CustomBook443.encodeBookWire(def);
                String wire = "OK\n" + encoded;
                if (wire.length() > MAX_WIRE_CHARS) {
                    PacketDistributor.sendToPlayer(player, new BookData("ERR:too_big\n" + requested));
                    return;
                }
                PacketDistributor.sendToPlayer(player, new BookData(wire));
                return;
            }
        }
        String suggested = !requested.isEmpty()
                ? requested
                : CustomBook443.suggestNextBookId(CustomBookManager443.listBookIds());
        PacketDistributor.sendToPlayer(player, new BookData("NEW\n" + suggested));
    }

    private static void onBookSave(BookSave payload, IPayloadContext context) {
        if (payload == null || context == null) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        String raw = payload.payload();
        if (raw == null || raw.isEmpty() || raw.length() > MAX_WIRE_CHARS) return;
        String[] lines = raw.split("\n", 2);
        String cmd = lines.length >= 1 ? lines[0].trim() : "";
        String body = lines.length >= 2 ? lines[1] : "";

        if (!player.hasPermissions(2)) {
            PacketDistributor.sendToPlayer(player, new BookData("ERR:noperm\n"));
            return;
        }

        if ("DELETE".equals(cmd)) {
            String norm = CustomBook443.normalizeBookId(body);
            if (norm.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new BookData("ERR:bad_id\n"));
                return;
            }
            CustomBookManager443.deleteBook(norm);
            sendBookSummariesToOperators(player.getServer());
            PacketDistributor.sendToPlayer(player, new BookData("DELETED\n" + norm));
            return;
        }

        if ("GIVE".equals(cmd)) {
            CustomBook443.BookDef def = CustomBook443.decodeBookWire(body);
            if (def == null || def.id.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new BookData("ERR:bad_id\n"));
                return;
            }
            ItemStack stack = CustomBookManager443.buildBookStack(def, 1);
            if (!stack.isEmpty()) {
                boolean added = player.getInventory().add(stack);
                if (!added || !stack.isEmpty()) {
                    player.drop(stack, false);
                }
            }
            PacketDistributor.sendToPlayer(player, new BookData("GIVEN\n" + def.id));
            return;
        }

        if ("SAVE".equals(cmd)) {
            CustomBook443.BookDef def = CustomBook443.decodeBookWire(body);
            if (def == null || def.id.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new BookData("ERR:bad_id\n"));
                return;
            }
            String savedId = CustomBookManager443.saveBook(def);
            if (savedId.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new BookData("ERR:io\n" + def.id));
                return;
            }
            sendBookSummariesToOperators(player.getServer());
            PacketDistributor.sendToPlayer(player, new BookData("SAVED\n" + CustomBook443.encodeBookWire(def)));
        }
    }
}
