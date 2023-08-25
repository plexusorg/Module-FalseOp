package dev.plex.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.*;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

public class PlayerListener extends PlexListener
{
    private final ProtocolManager protocolManager;

    public PlayerListener()
    {
        protocolManager = ProtocolLibrary.getProtocolManager();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event)
    {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_STATUS);
        packet.getIntegers().write(0, event.getPlayer().getEntityId());
        packet.getBytes().write(0, (byte) 28);
        protocolManager.sendServerPacket(event.getPlayer(), packet);
    }

    @EventHandler
    private void onBlock(PlayerInteractEvent event)
    {
        if (event.useInteractedBlock() == Event.Result.DENY)
        {
            return;
        }
        var type = event.getMaterial();
        var player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (clicked == null)
        {
            return;
        }
        boolean canPlace = player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SURVIVAL;
        boolean canBreak = player.getGameMode() == GameMode.CREATIVE;
        if (player.getGameMode() == GameMode.ADVENTURE)
        {
            ItemStack item = event.getItem();
            if (item != null)
            {
                ItemMeta meta = item.getItemMeta();
                if (meta != null)
                {
                    canPlace = meta.getPlaceableKeys().contains(clicked.getType().getKey());
                    canBreak = meta.getDestroyableKeys().contains(clicked.getType().getKey());
                }
            }
        }
        boolean clickedTargetBlock = clicked.getType() == Material.COMMAND_BLOCK || clicked.getType() == Material.CHAIN_COMMAND_BLOCK || clicked.getType() == Material.REPEATING_COMMAND_BLOCK || clicked.getType() == Material.STRUCTURE_BLOCK || clicked.getType() == Material.JIGSAW;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && (type == Material.COMMAND_BLOCK || type == Material.CHAIN_COMMAND_BLOCK || type == Material.REPEATING_COMMAND_BLOCK || type == Material.STRUCTURE_BLOCK || type == Material.JIGSAW) && (!clickedTargetBlock || player.isSneaking()))
        {
            if (!canPlace)
            {
                return;
            }
            if (isInteractable(clicked.getType()) && !player.isSneaking())
            {
                return;
            }
            Location loc = clicked.isReplaceable() ? clicked.getLocation() : clicked.getLocation().add(event.getBlockFace().getDirection());
            Block block = loc.getBlock();
            if (!block.isReplaceable())
            {
                return;
            }
            if (!block.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5).isEmpty())
            {
                return;
            }
            Material oldType = block.getType();
            BlockData oldData = block.getBlockData();
            block.setType(type);
            BlockFace face = calcVecBlockFace(player.getLocation().getDirection());
            if (block.getBlockData() instanceof Directional directional)
            {
                directional.setFacing(face.getOppositeFace());
                block.setBlockData(directional);
            }
            BlockPlaceEvent placeEvent = new BlockPlaceEvent(block, block.getState(), clicked, event.getItem(), player, true, player.getHandRaised());
            plugin.getServer().getPluginManager().callEvent(placeEvent);
            if (placeEvent.isCancelled())
            {
                block.setType(oldType);
                block.setBlockData(oldData);
                return;
            }
            if (player.getGameMode() != GameMode.CREATIVE && event.getItem() != null)
            {
                event.getItem().setAmount(event.getItem().getAmount() - 1);
            }
            player.closeInventory();
        }
        else if (event.getAction() == Action.LEFT_CLICK_BLOCK && clickedTargetBlock)
        {
            if (!canBreak)
            {
                return;
            }
            if (event.getItem() != null && (EnchantmentTarget.WEAPON.includes(event.getItem().getType()) || event.getItem().getType() == Material.DEBUG_STICK || event.getItem().getType() == Material.TRIDENT))
            {
                return;
            }
            BlockBreakEvent breakEvent = new BlockBreakEvent(clicked, player);
            plugin.getServer().getPluginManager().callEvent(breakEvent);
            if (breakEvent.isCancelled())
            {
                return;
            }
            clicked.breakNaturally(event.getItem());
        }
    }

    private static BlockFace calcVecBlockFace(Vector vector)
    {
        double x = Math.abs(vector.getX());
        double y = Math.abs(vector.getY());
        double z = Math.abs(vector.getZ());
        if (x > z)
        {
            if (x > y)
            {
                return calcFacing(vector.getX(), BlockFace.EAST, BlockFace.WEST);
            }
            else
            {
                return calcFacing(vector.getY(), BlockFace.UP, BlockFace.DOWN);
            }
        }
        else
        {
            if (z > y)
            {
                return calcFacing(vector.getZ(), BlockFace.SOUTH, BlockFace.NORTH);
            }
            else
            {
                return calcFacing(vector.getY(), BlockFace.UP, BlockFace.DOWN);
            }
        }
    }

    private static BlockFace calcFacing(double value, BlockFace positive, BlockFace negative)
    {
        return value > 0 ? positive : negative;
    }

    private boolean isInteractable(Material material)
    {
        return switch (material)
        {
            case BREWING_STAND, CAKE, CHEST, HOPPER, TRAPPED_CHEST, ENDER_CHEST, CAULDRON, COMMAND_BLOCK, REPEATING_COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, BEACON, REPEATER, COMPARATOR, BARREL, DISPENSER, DROPPER, LEVER, CRAFTING_TABLE, CARTOGRAPHY_TABLE, SMITHING_TABLE, ENCHANTING_TABLE, FLETCHING_TABLE, BLAST_FURNACE, LOOM, GRINDSTONE, FURNACE, STONECUTTER, BELL, DAYLIGHT_DETECTOR, JIGSAW, STRUCTURE_BLOCK ->
                    true;
            default ->
                    Tag.SIGNS.isTagged(material) || Tag.BEDS.isTagged(material) || Tag.BUTTONS.isTagged(material) || Tag.TRAPDOORS.isTagged(material) || Tag.WOODEN_DOORS.isTagged(material) || Tag.SHULKER_BOXES.isTagged(material) || Tag.ANVIL.isTagged(material) || Tag.FENCE_GATES.isTagged(material);
        };
    }
}
