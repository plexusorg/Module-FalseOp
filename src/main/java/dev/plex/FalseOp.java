package dev.plex;

import dev.plex.listener.PlayerListener;
import dev.plex.module.PlexModule;
import org.bukkit.Bukkit;

public class FalseOp extends PlexModule
{
    private PlayerListener playerListener;

    @Override
    public void enable()
    {
        if (!Bukkit.getPluginManager().isPluginEnabled("packetevents"))
        {
            throw new IllegalStateException("The FalseOp module requires the PacketEvents plugin to work.");
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
