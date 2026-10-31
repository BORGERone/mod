package dev.settledlands.npc;

/**
 * Build 292: the "the command is only coordinates" shortcut must not fire on a target that
 * was substituted a moment earlier.
 *
 * <p>{@code NpcActions#command} first runs {@code NpcActions#fill}, which turns the mod's own
 * {@code @npc} token into the NPC's uuid, and only then checks whether the command starts
 * with {@code tp}/{@code teleport} followed by a coordinate-looking character
 * ({@code ~ ^ - 0-9}) - if so it inserts {@code @s}, which is what makes a bare
 * {@code tp ~ ~5 ~} work.</p>
 *
 * <p>A uuid starts with a hex digit, so {@code tp @npc ~ ~5 ~} became {@code tp <uuid> ~ ~5 ~}
 * and then {@code tp @s <uuid> ~ ~5 ~}; the game rejected that with "Неверный аргумент для
 * команды". The substitution itself is right - {@code @npc} is the NPC and a bare uuid is a
 * valid target - so the shortcut is what needs the extra look: the token after the verb has
 * to consist of coordinate characters only.</p>
 */
public final class NpcSelfTarget292 {

    private NpcSelfTarget292() {}

    /** The redirect: run the stock {@code replaceFirst} only when the shortcut applies. */
    public static String apply(String command, String regex, String replacement) {
        if (command == null) return null;
        return needsSelfTarget(command) ? command.replaceFirst(regex, replacement) : command;
    }

    /** True when the command gives only coordinates and no target of its own. */
    public static boolean needsSelfTarget(String command) {
        if (command == null) return false;
        int n = command.length();
        int i = 0;
        while (i < n && (command.charAt(i) == '/' || Character.isWhitespace(command.charAt(i)))) i++;
        int verbStart = i;
        while (i < n && !Character.isWhitespace(command.charAt(i))) i++;
        String verb = command.substring(verbStart, i);
        if (!verb.equals("tp") && !verb.equals("teleport")) return false;
        while (i < n && Character.isWhitespace(command.charAt(i))) i++;
        int start = i;
        while (i < n && !Character.isWhitespace(command.charAt(i))) i++;
        if (i == start) return false;
        for (int k = start; k < i; k++) {
            char c = command.charAt(k);
            if (c != '~' && c != '^' && c != '-' && c != '.' && (c < '0' || c > '9')) return false;
        }
        return true;
    }
}
