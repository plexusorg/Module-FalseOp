package dev.plex;

import dev.plex.listener.PlayerListener;
import dev.plex.module.PlexModule;
import dev.plex.util.PlexLog;
import org.bukkit.Bukkit;

public class FalseOp extends PlexModule
{
    private PlayerListener playerListener;

    @Override
    public void enable()
    {
        if (!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib"))
        {
            PlexLog.error("The Plex-FalseOp module requires the ProtocolLib plugin to work.");
            return;
        }
        playerListener = new PlayerListener();
        registerListener(playerListener);
    }

    @Override
    public void disable()
    {
        if (playerListener != null)
        {
            playerListener.cleanUp();
            playerListener = null;
        }
    }
}