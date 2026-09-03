package dev.plex.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.item.ItemAdventurePredicate;
import io.papermc.paper.registry.TypedKey;
import java.util.EnumSet;
import java.util.Set;
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
    private static final Set<Material> TARGET_BLOCKS = EnumSet.of(Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK, Material.STRUCTURE_BLOCK, Material.JIGSAW);

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
        Block clicked = event.getClickedBlock();
        if (clicked == null)
        {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && TARGET_BLOCKS.contains(event.getMaterial()))
        {
            placeBlock(event, clicked);
        }
        else if (event.getAction() == Action.LEFT_CLICK_BLOCK && TARGET_BLOCKS.contains(clicked.getType()))
        {
            breakBlock(event, clicked);
        }
    }

    private void placeBlock(PlayerInteractEvent event, Block clicked)
    {
        Player player = event.getPlayer();
        if (TARGET_BLOCKS.contains(clicked.getType()) && !player.isSneaking())
        {
            return;
        }
        if (!canPlace(player, event.getItem(), clicked))
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
        block.setType(event.getMaterial());
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

    private void breakBlock(PlayerInteractEvent event, Block clicked)
    {
        Player player = event.getPlayer();
        if (!canBreak(player, event.getItem(), clicked))
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

    private boolean canPlace(Player player, ItemStack item, Block clicked)
    {
        return switch (player.getGameMode())
        {
            case CREATIVE, SURVIVAL -> true;
            case ADVENTURE -> item != null && permits(item, clicked, DataComponentTypes.CAN_PLACE_ON);
            default -> false;
        };
    }

    private boolean canBreak(Player player, ItemStack item, Block clicked)
    {
        return player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.ADVENTURE && item != null && permits(item, clicked, DataComponentTypes.CAN_BREAK);
    }

    private boolean permits(ItemStack item, Block clicked, DataComponentType.Valued<ItemAdventurePredicate> componentType)
    {
        if (!item.hasData(componentType))
        {
            return false;
        }
        for (var blockPredicate : item.getData(componentType).predicates())
        {
            for (TypedKey<BlockType> key : blockPredicate.blocks())
            {
                if (key.key().equals(clicked.getType().asBlockType().key()))
                {
                    return true;
                }
            }
        }
        return false;
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
