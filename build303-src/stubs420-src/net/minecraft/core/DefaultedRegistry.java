package net.minecraft.core;

/**
 * Build 420: the static type the game gives the built-in registries.
 *
 * <p><b>It must be an interface.</b> Build 420 shipped this as a class, and the author's log caught
 * {@code java.lang.IncompatibleClassChangeError: Found interface net.minecraft.core.DefaultedRegistry, but
 * class was expected} on the first tick the anchor tried to place a marker: the base jar calls
 * {@code Registry#getKey}/{@code get} through {@code invokeinterface} (see {@code NpcItems#idOf} and
 * {@code WorldEvents#lambda$eligible$0}), and a class mirror turned the same call into
 * {@code invokevirtual}. The descriptor was right, the <i>kind</i> was wrong - and the descriptor-only
 * audit of 420 could not see it. {@code tools/invoke_kind_check.py} now compares the kind of every call
 * against the base jar, so this cannot happen silently again.</p>
 */
public interface DefaultedRegistry<T> extends Registry<T> {
}
