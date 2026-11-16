package net.minecraft.nbt;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * Compile-time mirror (Build 425): {@code NbtIo.readCompressed(InputStream, NbtAccounter)} and
 * {@link #write(CompoundTag, DataOutput)}. Includes real gzipped NBT decoding so JVM probes load
 * the bundled {@code start.slbp} directly.
 */
public class NbtIo {

    public static void write(CompoundTag tag, DataOutput output) throws IOException {}

    public static CompoundTag readCompressed(InputStream stream, NbtAccounter accounter) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(stream)))) {
            int rootType = in.readUnsignedByte();
            if (rootType != 10) {
                throw new IOException("Expected root TAG_Compound (10), got " + rootType);
            }
            readString(in);
            return readCompound(in);
        }
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] buf = in.readNBytes(len);
        if (buf.length != len) {
            throw new IOException("Unexpected EOF in NBT string");
        }
        return new String(buf, StandardCharsets.UTF_8);
    }

    private static CompoundTag readCompound(DataInputStream in) throws IOException {
        CompoundTag out = new CompoundTag();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) {
                return out;
            }
            String key = readString(in);
            readEntryInto(out, key, type, in);
        }
    }

    private static void readEntryInto(CompoundTag out, String key, int type, DataInputStream in)
            throws IOException {
        switch (type) {
            case 1 -> out.putByte(key, in.readByte());
            case 2 -> out.putShort(key, in.readShort());
            case 3 -> out.putInt(key, in.readInt());
            case 4 -> out.putLong(key, in.readLong());
            case 5 -> out.putFloat(key, in.readFloat());
            case 6 -> out.putDouble(key, in.readDouble());
            case 7 -> {
                int len = in.readInt();
                out.putByteArray(key, in.readNBytes(len));
            }
            case 8 -> out.putString(key, readString(in));
            case 9 -> {
                int itemType = in.readUnsignedByte();
                int len = in.readInt();
                ListTag list = new ListTag();
                for (int i = 0; i < len; i++) {
                    if (itemType == 10) {
                        list.add(readCompound(in));
                    } else {
                        skipPayload(itemType, in);
                    }
                }
                out.put(key, list);
            }
            case 10 -> out.put(key, readCompound(in));
            case 11 -> {
                int len = in.readInt();
                int[] arr = new int[len];
                for (int i = 0; i < len; i++) arr[i] = in.readInt();
                out.putIntArray(key, arr);
            }
            case 12 -> {
                int len = in.readInt();
                long[] arr = new long[len];
                for (int i = 0; i < len; i++) arr[i] = in.readLong();
                out.putLongArray(key, arr);
            }
            default -> throw new IOException("Unsupported NBT tag type: " + type);
        }
    }

    private static void skipPayload(int type, DataInputStream in) throws IOException {
        switch (type) {
            case 0 -> {}
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> {
                int len = in.readInt();
                in.readNBytes(len);
            }
            case 8 -> readString(in);
            case 9 -> {
                int itemType = in.readUnsignedByte();
                int len = in.readInt();
                for (int i = 0; i < len; i++) skipPayload(itemType, in);
            }
            case 10 -> readCompound(in);
            case 11 -> {
                int len = in.readInt();
                for (int i = 0; i < len; i++) in.readInt();
            }
            case 12 -> {
                int len = in.readInt();
                for (int i = 0; i < len; i++) in.readLong();
            }
            default -> throw new IOException("Unsupported NBT tag type in list: " + type);
        }
    }
}
