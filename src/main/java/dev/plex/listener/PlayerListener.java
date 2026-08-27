package dev.plex.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.registry.TypedKey;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockType;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

@SuppressWarnings("UnstableApiUsage")
public class PlayerListener implements Listener
{
    private final PacketListenerCommon packetListener;

    public PlayerListener()
    {
        packetListener = PacketEvents.getAPI().getEventManager().registerListener(new PacketListener()
        {
            @Override
            public void onPacketSend(PacketSendEvent event)
            {
                if (event.getPacketType() != PacketType.Play.Server.ENTITY_STATUS)
                {
                    return;
                }

                WrapperPlayServerEntityStatus packet = new WrapperPlayServerEntityStatus(event);
                Player player = event.getPlayer();
                if (player == null)
                {
                    return;
                }

                int status = packet.getStatus();
                if (packet.getEntityId() == player.getEntityId() && status >= 24 && status <= 27)
                {
                    packet.setStatus(28);
                    event.markForReEncode(true);
                }
            }
        }, PacketListenerPriority.NORMAL);
    }

    public void cleanUp()
    {
        PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
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
                if (item.hasData(DataComponentTypes.CAN_PLACE_ON))
                {
                    canPlace = item.getData(DataComponentTypes.CAN_PLACE_ON).predicates().stream().anyMatch(blockPredicate ->
                    {
                        for (TypedKey<BlockType> key : blockPredicate.blocks())
                        {
                            if (key.key().equals(clicked.getType().asBlockType().key()))
                            {
                                return true;
                            }
                        }
                        return false;
                    });
                }

                if (item.hasData(DataComponentTypes.CAN_BREAK))
                {
                    canBreak = item.getData(DataComponentTypes.CAN_BREAK).predicates().stream().anyMatch(blockPredicate ->
                    {
                        for (TypedKey<BlockType> key : blockPredicate.blocks())
                        {
                            if (key.key().equals(clicked.getType().asBlockType().key()))
                            {
                                return true;
                            }
                        }
                        return false;
                    });
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
            Bukkit.getPluginManager().callEvent(placeEvent);
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
            if (event.getItem() != null && (Tag.ITEMS_SWORDS.isTagged(event.getItem().getType()) || event.getItem().getType() == Material.DEBUG_STICK || event.getItem().getType() == Material.TRIDENT))
            {
                return;
            }
            BlockBreakEvent breakEvent = new BlockBreakEvent(clicked, player);
            Bukkit.getPluginManager().callEvent(breakEvent);
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
            case BREWING_STAND, CAKE, CHEST, HOPPER, TRAPPED_CHEST, ENDER_CHEST, CAULDRON, COMMAND_BLOCK,
                 REPEATING_COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, BEACON, REPEATER, COMPARATOR, BARREL, DISPENSER, DROPPER,
                 LEVER, CRAFTING_TABLE, CARTOGRAPHY_TABLE, SMITHING_TABLE, ENCHANTING_TABLE, FLETCHING_TABLE,
                 BLAST_FURNACE, LOOM, GRINDSTONE, FURNACE, STONECUTTER, BELL, DAYLIGHT_DETECTOR, JIGSAW,
                 STRUCTURE_BLOCK -> true;
            default ->
                    Tag.SIGNS.isTagged(material) || Tag.BEDS.isTagged(material) || Tag.BUTTONS.isTagged(material) || Tag.TRAPDOORS.isTagged(material) || Tag.WOODEN_DOORS.isTagged(material) || Tag.SHULKER_BOXES.isTagged(material) || Tag.ANVIL.isTagged(material) || Tag.FENCE_GATES.isTagged(material);
        };
    }
}
