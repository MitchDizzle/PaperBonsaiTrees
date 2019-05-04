package tech.mtch.bonsaitrees

import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockState
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class BonsaiTrees : JavaPlugin(), Listener {

    private val validMaterials = HashMap<Material,PotConfig>()
    private val flowerPots = ConcurrentHashMap<Block,Double>()

    // Growth percent per second
    private var growthMaxPerSecond = 20.0
    private var growthMinPerSecond = 10.0
    private var bonemealIncreasePercent = 100.0

    private var showGrowthPercent = true

    override fun onEnable() {

        addToDefaultConfig()

        growthMaxPerSecond = config.get("growthMaxPerSecond") as Double
        growthMinPerSecond = config.get("growthMinPerSecond") as Double
        bonemealIncreasePercent = config.get("bonemealIncreasePercent") as Double



        for(section in config.getConfigurationSection("potConfig")!!.getValues(false)) {
            val potConfig = PotConfig()
            //Other configs values

            //Get Drops:
            for(drop in config.getConfigurationSection("potConfig." + section.key + ".drops")!!.getValues(false)) {
                potConfig.addDrop(Material.valueOf(drop.key), drop.value as Double)
            }
            validMaterials[Material.valueOf(section.key)] = potConfig
        }

        // Register event listener after parsing
        server.pluginManager.registerEvents(this, this)

        //Create FlowerPot ticks
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, Runnable {
            onFlowerPotTick()
        }, 0L, 50L)

        //Late load compatibility, if this is a normal load it should only find one loaded chunk.
        for(world in Bukkit.getWorlds()) {
            for(chunk in world.loadedChunks) {
                findPotsInTileEntities(chunk.tileEntities)
            }
        }
    }

    override fun onDisable() {
        // Not sure if this is needed.
        server.scheduler.cancelTasks(this)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkLoaded(event: ChunkLoadEvent) {
        findPotsInTileEntities(event.chunk.tileEntities)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val chunk = event.chunk
        val world = event.world
        for(bl in flowerPots.keys) {
            if(bl.world == world && bl.chunk == chunk) {
                flowerPots.remove(bl)
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val bl = event.blockPlaced
        if(validMaterials.containsKey(bl.type)) {
            if(!flowerPots.containsKey(bl)) {
                flowerPots[bl] = 0.0
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onBlockBreak(event: BlockBreakEvent) {
        val bl = event.block
        if(flowerPots.containsKey(bl)) {
            flowerPots.remove(bl)
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val bl = event.clickedBlock
        if(bl is Block) {
            val blType = event.material
            if(!flowerPots.containsKey(bl)) {
                if(bl.type == Material.FLOWER_POT || validMaterials.containsKey(bl.type)) {
                    //Adds to the flowerPot map. If the contents is being removed then next tick will remove it anyways.
                    flowerPots[bl] = 0.0
                }
            } else if (blType == Material.BONE_MEAL) {
                val player = event.player
                val item = event.item
                if(item is ItemStack && item.type == Material.BONE_MEAL) {
                    val percent = flowerPots[bl]?.plus(bonemealIncreasePercent) ?: 0.0
                    flowerPotSetPercent(bl, percent)
                    if(item.amount > 1) {
                        item.amount -= 1
                    } else {
                        player.inventory.remove(item)
                    }
                    event.setUseItemInHand(Event.Result.ALLOW)
                }
                event.isCancelled = true
            }
        }
    }

    private fun onFlowerPotTick() {
        val removeList = ArrayList<Block>()
        for(entry in flowerPots.entries) {
            val bl = entry.key
            if(validMaterials.containsKey(bl.type)) {
                flowerPotSetPercent(bl, entry.value.plus(Random.nextDouble(growthMinPerSecond, growthMaxPerSecond)))
            } else {
                removeList.add(bl)
            }
        }
        if(removeList.size > 0) {
            for(bl in removeList) {
                flowerPots.remove(bl)
            }
        }
    }

    private fun flowerPotSetPercent(bl: Block, percent: Double) {
        val container = bl.getRelative(BlockFace.DOWN).state as? Container ?: return
        var newPercent: Double = percent
        if(percent >= 100.0) {
            newPercent -= 100.0 //Instead of setting to zero we clamp.
            val itemMap = validMaterials[bl.type]?.drops
            if(!itemMap.isNullOrEmpty()) {
                val p = Math.random()
                var cumulativeProbability = 0.0
                for(item in itemMap.entries) {
                    cumulativeProbability += item.value
                    if(p <= cumulativeProbability) {
                        container.inventory.addItem(ItemStack(item.key))
                        break
                    }
                }
            }
        }
        flowerPots[bl] = newPercent
        if(showGrowthPercent) {
            //Percent display, need to make this better, instead of getting all nearby players for each pot..
            val players = bl.location.world!!.getNearbyEntities(bl.location, 4.0, 4.0, 4.0).filterIsInstance<Player>()
            if (players.isEmpty()) {
                return
            }
            val chatColor = when (newPercent) {
                in 0.0..25.0 -> ChatColor.RED
                in 25.0..50.0 -> ChatColor.GOLD
                in 50.0..75.0 -> ChatColor.YELLOW
                else -> ChatColor.GREEN
            }
            val formattedText = String.format("%s%s: %2.0f%%", chatColor, validMaterials[bl.type]?.display, newPercent)
            for (player in players) {
                if (player.getTargetBlock(null, 10) == bl) {
                    sendActionMessage(player, formattedText)
                }
            }
        }
    }

    private fun findPotsInTileEntities(tileEntities: Array<BlockState>) {
        for (tileEntity in tileEntities) {
            //Find containers and check if they have a pot above them. Faster loading.
            if (tileEntity is Container) {
                val bl = tileEntity.block.getRelative(BlockFace.UP)
                if (bl.type != Material.AIR && !flowerPots.containsKey(bl) && validMaterials.containsKey(bl.type)) {
                    //Block is not Air, not already in the map, and is within the config.
                    flowerPots[bl] = 0.0
                }
            }
        }
    }

    private fun addToDefaultConfig() {
        val defMap = HashMap<Material, PotConfig>()
        var potConfig = PotConfig()

        //Oak Sapling
        potConfig.display = "Oak Tree"
        potConfig.growthMaxPerIter = 20.0
        potConfig.growthMinPerIter = 10.0
        potConfig.bonemealIncreasePercent = 100.0
        potConfig.addDrop(Material.STICK, 0.1)
        potConfig.addDrop(Material.APPLE, 0.2)
        potConfig.addDrop(Material.OAK_LOG, 0.5)
        potConfig.addDrop(Material.OAK_LEAVES, 0.1)
        potConfig.addDrop(Material.OAK_SAPLING, 0.1)
        defMap[Material.POTTED_OAK_SAPLING] = potConfig

        //Birch Sapling
        potConfig = PotConfig()
        potConfig.display = "Birch Tree"
        potConfig.growthMaxPerIter = 20.0
        potConfig.growthMinPerIter = 10.0
        potConfig.bonemealIncreasePercent = 100.0
        potConfig.addDrop(Material.STICK, 0.1)
        potConfig.addDrop(Material.BIRCH_LOG, 0.5)
        potConfig.addDrop(Material.BIRCH_LEAVES, 0.1)
        potConfig.addDrop(Material.BIRCH_SAPLING, 0.1)
        defMap[Material.POTTED_BIRCH_SAPLING] = potConfig

        //TODO reconfigure the rest... maybe having a default config packaged isn't a bad idea.

        config.addDefault("potConfig", defMap)
        config.addDefault("iterTicks", 50.0) // Every 2.5 seconds
        config.addDefault("showGrowthPercent", showGrowthPercent)
        config.options().copyDefaults(true)
        saveConfig()
        defMap.clear()
        reloadConfig()
    }

    private fun sendActionMessage(player: Player, message: String) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message).component1())
    }
}
