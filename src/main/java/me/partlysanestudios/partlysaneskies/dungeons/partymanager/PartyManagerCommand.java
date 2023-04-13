//
// Written by Su386.
// See LICENSE for copright and license notices.
//

package me.partlysanestudios.partlysaneskies.dungeons.partymanager;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;

public class PartyManagerCommand implements ICommand {

    @Override
    public int compareTo(ICommand o) {
        return 0;
    }

    @Override
    public String getCommandName() {
        return "partymanager";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "Party Manager";
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("pm", "partym");
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        PartyManager.startPartyManager();
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

}
