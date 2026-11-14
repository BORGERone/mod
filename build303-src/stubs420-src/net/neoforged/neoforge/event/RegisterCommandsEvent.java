package net.neoforged.neoforge.event;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * Build 420: the one moment a mod is allowed to add a command.
 *
 * <p>The base jar already subscribes to this event from {@code NpcEscortCommands},
 * {@code CutsceneCommands}, {@code ModCommands} and others, and calls {@code getDispatcher()} on it - so
 * the shape is the jar's own.</p>
 */
public class RegisterCommandsEvent {

    public CommandDispatcher<CommandSourceStack> getDispatcher() {
        return null;
    }
}
