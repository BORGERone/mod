package dev.settledlands.audio;

import net.minecraft.world.phys.Vec3;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;

import javax.sound.sampled.*;
import java.io.File;
import java.nio.IntBuffer;

public final class AudioStreamPlayer extends Thread {
    private final File file;
    private final Vec3 origin;
    private final float radius;
    private final float baseVolume;
    private final boolean loop;
    private final float fadeSec;
    private final float pitch;
    private final boolean isMusic;
    private final long soundId;

    private final long startTimeMillis;
    private volatile boolean running = true;
    private volatile boolean stopping = false;
    private volatile long stopTimeMillis = 0;
    private volatile float stopFadeSec = 1.0f;

    public AudioStreamPlayer(File file, Vec3 origin, float radius, float volume, boolean loop, float fadeSec, float pitch, boolean isMusic, long soundId) {
        super("SettledLands-" + (isMusic ? "Music-" : "Sound-") + file.getName());
        setDaemon(true);
        this.file = file;
        this.origin = origin;
        this.radius = Math.max(0.0f, radius);
        this.baseVolume = Math.max(0.0f, Math.min(1.0f, volume));
        this.loop = loop;
        this.fadeSec = Math.max(0.0f, fadeSec);
        this.pitch = Math.max(0.1f, Math.min(3.0f, pitch));
        this.isMusic = isMusic;
        this.soundId = soundId;
        this.startTimeMillis = System.currentTimeMillis();
    }

    public AudioStreamPlayer(File file, Vec3 origin, float radius, float volume, boolean loop, float fadeSec, float pitch, boolean isMusic) {
        this(file, origin, radius, volume, loop, fadeSec, pitch, isMusic, 0L);
    }

    public AudioStreamPlayer(File file, Vec3 origin, float radius, float volume, boolean loop, float fadeSec, float pitch) {
        this(file, origin, radius, volume, loop, fadeSec, pitch, AudioStorage.isMusicFile(file), 0L);
    }

    public boolean isMusic() {
        return isMusic;
    }

    public long getSoundId() {
        return soundId;
    }

    public float getRadius() {
        return radius;
    }

    public boolean isActivelyPlaying() {
        return running && isAlive();
    }

    public File getFile() {
        return file;
    }

    public void stopPlaying(float fadeSeconds) {
        this.stopFadeSec = Math.max(0.0f, fadeSeconds);
        this.stopTimeMillis = System.currentTimeMillis();
        this.stopping = true;
        if (stopFadeSec <= 0.0f) {
            this.running = false;
        }
    }

    public float computeEffectiveVolume() {
        float distFactor = 1.0f;
        if (radius > 0.0f && origin != null) {
            Vec3 playerPos = ClientAudioService.getClientPlayerPos();
            if (playerPos != null) {
                double dist = playerPos.distanceTo(origin);
                if (dist > radius) {
                    distFactor = 0.0f;
                } else {
                    distFactor = (float) Math.max(0.0, Math.min(1.0, 1.0 - (dist / radius)));
                }
            }
        }

        if (isMusic && distFactor > 0.0f && !stopping) {
            ClientAudioService.ensureVanillaMusicSuppressed();
        }

        long now = System.currentTimeMillis();
        float fade = 1.0f;
        if (stopping) {
            long stopElapsed = now - stopTimeMillis;
            if (stopFadeSec > 0.0f) {
                fade = Math.max(0.0f, 1.0f - (float) stopElapsed / (stopFadeSec * 1000.0f));
            } else {
                fade = 0.0f;
            }
            if (fade <= 0.001f) {
                running = false;
            }
        } else if (fadeSec > 0.0f) {
            long startElapsed = now - startTimeMillis;
            fade = Math.min(1.0f, (float) startElapsed / (fadeSec * 1000.0f));
        }

        return baseVolume * distFactor * fade;
    }

    @Override
    public void run() {
        String name = file.getName().toLowerCase();
        try {
            if (name.endsWith(".ogg")) {
                playOgg();
            } else if (name.endsWith(".mp3")) {
                playMp3();
            } else {
                playWav();
            }
        } catch (Throwable t) {
            // Graceful handling for missing audio device or headless CI
        } finally {
            running = false;
            ClientAudioService.onPlayerFinished(this);
        }
    }

    private void playMp3() {
        try {
            do {
                try (var fis = new java.io.FileInputStream(file);
                     var bis = new java.io.BufferedInputStream(fis)) {
                    javazoom.jl.decoder.Bitstream bitstream = new javazoom.jl.decoder.Bitstream(bis);
                    javazoom.jl.decoder.Decoder decoder = new javazoom.jl.decoder.Decoder();
                    SourceDataLine line = null;
                    try {
                        javazoom.jl.decoder.Header header;
                        while (running && (header = bitstream.readFrame()) != null) {
                            javazoom.jl.decoder.SampleBuffer output = (javazoom.jl.decoder.SampleBuffer) decoder.decodeFrame(header, bitstream);
                            if (output == null) {
                                bitstream.closeFrame();
                                continue;
                            }
                            int channels = output.getChannelCount();
                            int sampleRate = output.getSampleFrequency();
                            if (line == null) {
                                float targetRate = sampleRate * pitch;
                                AudioFormat format = new AudioFormat(targetRate, 16, channels, true, false);
                                DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);
                                if (!AudioSystem.isLineSupported(lineInfo)) {
                                    bitstream.closeFrame();
                                    break;
                                }
                                line = (SourceDataLine) AudioSystem.getLine(lineInfo);
                                line.open(format, 16384);
                                line.start();
                            }
                            float vol = computeEffectiveVolume();
                            if (vol <= 0.001f && stopping) {
                                bitstream.closeFrame();
                                break;
                            }
                            short[] pcm = output.getBuffer();
                            int len = output.getBufferLength();
                            byte[] byteBuf = new byte[len * 2];
                            for (int i = 0; i < len; i++) {
                                int s = Math.round(pcm[i] * vol);
                                s = Math.max(-32768, Math.min(32767, s));
                                byteBuf[i * 2] = (byte)(s & 0xFF);
                                byteBuf[i * 2 + 1] = (byte)((s >> 8) & 0xFF);
                            }
                            line.write(byteBuf, 0, len * 2);
                            bitstream.closeFrame();
                        }
                    } finally {
                        if (line != null) {
                            if (!stopping) {
                                line.drain();
                            } else {
                                line.flush();
                            }
                            line.stop();
                            line.close();
                        }
                        bitstream.close();
                    }
                }
            } while (loop && running && !stopping);
        } catch (Throwable ignored) {}
    }

    private void playOgg() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            long decoder = STBVorbis.stb_vorbis_open_filename(file.getAbsolutePath(), error, null);
            if (decoder == 0) return;
            try {
                STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                STBVorbis.stb_vorbis_get_info(decoder, info);
                int channels = Math.max(1, info.channels());
                int sampleRate = info.sample_rate();
                float targetRate = sampleRate * pitch;
                AudioFormat format = new AudioFormat(targetRate, 16, channels, true, false);
                DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);
                if (!AudioSystem.isLineSupported(lineInfo)) return;
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
                line.open(format, 16384);
                line.start();
                try {
                    short[] pcm = new short[2048 * channels];
                    byte[] byteBuf = new byte[pcm.length * 2];
                    while (running) {
                        int samples = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);
                        if (samples <= 0) {
                            if (loop && running) {
                                STBVorbis.stb_vorbis_seek_start(decoder);
                                continue;
                            }
                            break;
                        }
                        float vol = computeEffectiveVolume();
                        if (vol <= 0.001f && stopping) break;
                        for (int i = 0; i < samples * channels; i++) {
                            int s = Math.round(pcm[i] * vol);
                            s = Math.max(-32768, Math.min(32767, s));
                            byteBuf[i * 2] = (byte)(s & 0xFF);
                            byteBuf[i * 2 + 1] = (byte)((s >> 8) & 0xFF);
                        }
                        line.write(byteBuf, 0, samples * channels * 2);
                    }
                } finally {
                    if (line != null) {
                        if (!stopping) {
                            line.drain();
                        } else {
                            line.flush();
                        }
                        line.stop();
                        line.close();
                    }
                }
            } finally {
                STBVorbis.stb_vorbis_close(decoder);
            }
        } catch (Throwable ignored) {}
    }

    private void playWav() {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {
            AudioFormat baseFormat = ais.getFormat();
            AudioFormat targetFormat = new AudioFormat(
                baseFormat.getEncoding(),
                baseFormat.getSampleRate() * pitch,
                baseFormat.getSampleSizeInBits(),
                baseFormat.getChannels(),
                baseFormat.getFrameSize(),
                baseFormat.getFrameRate() * pitch,
                baseFormat.isBigEndian()
            );
            DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, targetFormat);
            if (!AudioSystem.isLineSupported(lineInfo)) return;
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
            line.open(targetFormat, 16384);
            line.start();
            try {
                byte[] buffer = new byte[4096];
                int read;
                while (running && (read = ais.read(buffer, 0, buffer.length)) != -1) {
                    float vol = computeEffectiveVolume();
                    if (vol <= 0.001f && stopping) break;
                    if (targetFormat.getSampleSizeInBits() == 16) {
                        for (int i = 0; i < read; i += 2) {
                            short s = (short)((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
                            s = (short)Math.max(-32768, Math.min(32767, Math.round(s * vol)));
                            buffer[i] = (byte)(s & 0xFF);
                            buffer[i + 1] = (byte)((s >> 8) & 0xFF);
                        }
                    }
                    line.write(buffer, 0, read);
                }
            } finally {
                if (line != null) {
                    if (!stopping) {
                        line.drain();
                    } else {
                        line.flush();
                    }
                    line.stop();
                    line.close();
                }
            }
        } catch (Throwable ignored) {}
    }
}
