package dev.plex;

import dev.plex.listener.PlayerListener;
import dev.plex.module.PlexModule;
import dev.plex.util.PlexLog;
import org.bukkit.Bukkit;

public class FalseOp extends PlexModule
{
    @Override
    public void enable()
    {
        if (!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib"))
        {
            PlexLog.error("The Plex-FalseOp module requires the ProtocolLib plugin to work.");
            return;
        }
        registerListener(new PlayerListener());
    }

    @Override
    public void disable()
    {
    }
}