package tech.mtch.bonsaitrees

import com.gmail.filoghost.holographicdisplays.api.Hologram
import com.gmail.filoghost.holographicdisplays.api.HologramsAPI
import com.gmail.filoghost.holographicdisplays.api.line.TextLine
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockState
import org.bukkit.block.Container
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random


@SuppressWarnings("unused")
class BonsaiTrees : JavaPlugin(), Listener {

    private val validMaterials = HashMap<Material,Map<Material,Double>>()
    private val flowerPots = ConcurrentHashMap<Block,Pot>()

    // Growth percent per second
    private var growthMaxPerSecond = 20.0
    private var growthMinPerSecond = 10.0
    private var bonemealIncreasePercent = 100.0
    private var useHolograms = true

    private var canUseHolograms = false

    private fun conMat(mat: Material): String {
        return mat.toString()
    }

    override fun onEnable() {
        canUseHolograms = Bukkit.getPluginManager().isPluginEnabled("HolographicDisplays")

        addToDefaultConfig()
        reloadConfig()

        growthMaxPerSecond = config.get("growthMaxPerSecond") as Double
        growthMinPerSecond = config.get("growthMinPerSecond") as Double
        bonemealIncreasePercent = config.get("bonemealIncreasePercent") as Double
        useHolograms = config.get("useHologramPercent") as Boolean

        for(section in config.getConfigurationSection("dropMap")!!.getValues(false)) {
            val newMap = HashMap<Material,Double>()
            for(drop in config.getConfigurationSection("dropMap." + section.key)!!.getValues(false)) {
                newMap[Material.valueOf(drop.key)] = drop.value as Double
            }
            validMaterials[Material.valueOf(section.key)] = newMap
        }

        // Register event listener after parsing
        server.pluginManager.registerEvents(this, this)

        //Create FlowerPot ticks
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, Runnable {
            onFlowerPotTick()
        }, 0L, 20L)

        // Load the chunks on late load or something.
        // This can lag the server searching all blocks in a chunk so we do it with a async task.

        for(world in Bukkit.getWorlds()) {
            for(chunk in world.loadedChunks) {
                findPotsInTileEntities(chunk.tileEntities)
            }
        }
    }

    override fun onDisable() {
        // Not sure if this is needed.
        server.scheduler.cancelTasks(this)

        //Delete old holograms
        if(canUseHolograms) {
            for(hologram in HologramsAPI.getHolograms(this)) {
                hologram.delete()
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPluginEnabled(event: PluginEnableEvent) {
        if(event.plugin.name == "HolographicDisplays") {
            canUseHolograms = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPluginDisabled(event: PluginDisableEvent) {
        if(event.plugin.name == "HolographicDisplays") {
            canUseHolograms = false
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChunkLoaded(event: ChunkLoadEvent) {
        findPotsInTileEntities(event.chunk.tileEntities)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val chunk = event.chunk
        val world = event.world
        for(bl in flowerPots.keys) {
            if(bl.world == world && bl.chunk == chunk) {
                flowerPots[bl]?.hologram?.delete() //Delete hologram if it exists
                flowerPots.remove(bl)
            }
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val bl = event.blockPlaced
        if(validMaterials.containsKey(bl.type)) {
            if(!flowerPots.containsKey(bl)) {
                flowerPots[bl] = Pot()
            }
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val bl = event.block
        if(flowerPots.containsKey(bl)) {
            flowerPots[bl]?.hologram?.delete() //Delete hologram if it exists
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
                    flowerPots[bl] = Pot()
                }
            } else if (blType == Material.BONE_MEAL) {
                val player = event.player
                val item = event.item
                if(item is ItemStack && item.type == Material.BONE_MEAL) {
                    val pot = flowerPots[bl]
                    if(pot is Pot) {
                        //Weird cast check to make sure the return value of the block is a double.
                        flowerPotSetPercent(bl, pot.percent + bonemealIncreasePercent)
                        if(item.amount > 1) {
                            item.amount -= 1
                        } else {
                            player.inventory.remove(item)
                        }
                        event.setUseItemInHand(Event.Result.ALLOW)
                        //event.player.sendMessage(String.format("%s growth: %.0f%%", blType.toString(), percent))
                    }
                }
                event.isCancelled = true
            }
        }
    }

    fun onFlowerPotTick() {
        val removeList = ArrayList<Block>()
        for(entry in flowerPots.entries) {
            val bl = entry.key
            if(validMaterials.containsKey(bl.type)) {
                flowerPotSetPercent(bl, entry.value.percent + Random.nextDouble(growthMinPerSecond, growthMaxPerSecond))
            } else {
                removeList.add(bl)
            }
        }
        if(removeList.size > 0) {
            for(bl in removeList) {
                flowerPots[bl]?.hologram?.delete() //Delete hologram if it exists
                flowerPots.remove(bl)
            }
        }
    }

    private fun flowerPotSetPercent(bl: Block, percent: Double) {
        val pot = flowerPots[bl] as Pot
        val container = bl.getRelative(BlockFace.DOWN).state
        if(container !is Container) {
            pot.hologram?.delete()
            return
        }
        var newPercent: Double = percent
        if(percent >= 100.0) {
            newPercent = 100.0
            val itemMap = validMaterials[bl.type]
            if(itemMap != null) {
                val p = Math.random()
                var cumulativeProbability = 0.0
                for(item in itemMap.entries) {
                    cumulativeProbability += item.value
                    if(p <= cumulativeProbability) {
                        container.inventory.addItem(ItemStack(item.key))
                        newPercent = 0.0
                        break
                    }
                }
            }
        }
        var oldPercent: Double = pot.percent
        pot.percent = newPercent
        if(useHolograms && canUseHolograms) {
            //Holographic display
            var hologram = pot.hologram
            var players = bl.location.getNearbyPlayers(4.0)
            if(players.isEmpty() || oldPercent == newPercent) {
                hologram?.delete()
                return
            }
            if(hologram !is Hologram || hologram.isDeleted) {
                val loc = bl.location.toCenterLocation().add(0.0, 0.75, 0.0)
                hologram = HologramsAPI.createHologram(this, loc) as Hologram
                hologram.visibilityManager.isVisibleByDefault = true
                pot.hologram = hologram
            }
            /*for(player in players) {
                hologram.visibilityManager.showTo(player)
            }
            hologram.visibilityManager.*/
            val chatColor = when (newPercent) {
                in 0.0..25.0 -> ChatColor.RED
                in 25.0..50.0 -> ChatColor.GOLD
                in 50.0..75.0 -> ChatColor.YELLOW
                else -> ChatColor.GREEN
            }
            val formattedText = String.format("%s%.0f%%", chatColor, newPercent)
            if(hologram.size() >= 1) {
                val line = hologram.getLine(0) as TextLine
                line.text = formattedText
            } else {
                hologram.insertTextLine(0, formattedText)
            }
        }
        pot.percent = newPercent
    }

    private fun findPotsInTileEntities(tileEntities: Array<BlockState>) {
        for (tileEntity in tileEntities) {
            //Find containers and check if they have a pot above them. Faster loading.
            if (tileEntity is Container) {
                val bl = tileEntity.block.getRelative(BlockFace.UP)
                if (bl.type != Material.AIR && !flowerPots.containsKey(bl) && validMaterials.containsKey(bl.type)) {
                    //Block is not Air, not already in the map, and is within the config.
                    flowerPots[bl] = Pot()
                }
            }
        }
    }

    //How it was done previously.
    /*private fun findPotsInChunk(chunk: Chunk) {
        val maxY = chunk.world.maxHeight-1
        for(x in 0..15) {
            for(y in 0..maxY) {
                for(z in 0..15) {
                    val bl = chunk.getBlock(x, y, z)
                    if(bl.type != Material.AIR && !flowerPots.containsKey(bl) && validMaterials.containsKey(bl.type)) {
                        //Block is not Air, not already in the map, and is within the config.
                        flowerPots[bl] = Pot()
                    }
                }
            }
        }
    }*/

    private class Pot {
        var percent: Double = 0.0
        var hologram: Hologram? = null
    }

    private fun addToDefaultConfig() {
        val defMap = HashMap<String, HashMap<String, Double>>()
        var newMap = HashMap<String, Double>()

        //Oak Sapling
        //newMap = HashMap()
        newMap[conMat(Material.STICK)] = 0.2
        newMap[conMat(Material.OAK_LOG)] = 0.5
        newMap[conMat(Material.OAK_LEAVES)] = 0.1
        newMap[conMat(Material.OAK_SAPLING)] = 0.1
        defMap[conMat(Material.POTTED_OAK_SAPLING)] = newMap

        // Birch Sapling
        newMap = HashMap()
        newMap[conMat(Material.STICK)] = 0.2
        newMap[conMat(Material.BIRCH_LOG)] = 0.5
        newMap[conMat(Material.BIRCH_LEAVES)] = 0.1
        newMap[conMat(Material.BIRCH_SAPLING)] = 0.1
        defMap[conMat(Material.POTTED_BIRCH_SAPLING)] = newMap

        // Spruce Sapling
        newMap = HashMap()
        newMap[conMat(Material.STICK)] = 0.2
        newMap[conMat(Material.SPRUCE_LOG)] = 0.5
        newMap[conMat(Material.SPRUCE_LEAVES)] = 0.1
        newMap[conMat(Material.SPRUCE_SAPLING)] = 0.1
        defMap[conMat(Material.POTTED_SPRUCE_SAPLING)] = newMap

        // Jungle Sapling, has a better chance at creating logs
        newMap = HashMap()
        newMap[conMat(Material.STICK)] = 0.1
        newMap[conMat(Material.JUNGLE_LOG)] = 0.65
        newMap[conMat(Material.JUNGLE_LEAVES)] = 0.1
        newMap[conMat(Material.JUNGLE_SAPLING)] = 0.1
        defMap[conMat(Material.POTTED_JUNGLE_SAPLING)] = newMap

        //Dark Oak Sapling
        newMap = HashMap()
        newMap[conMat(Material.STICK)] = 0.1
        newMap[conMat(Material.DARK_OAK_LOG)] = 0.65
        newMap[conMat(Material.DARK_OAK_LEAVES)] = 0.1
        newMap[conMat(Material.DARK_OAK_SAPLING)] = 0.1
        defMap[conMat(Material.POTTED_DARK_OAK_SAPLING)] = newMap

        // Acacia Sapling
        newMap = HashMap()
        newMap[conMat(Material.STICK)] = 0.2
        newMap[conMat(Material.ACACIA_LOG)] = 0.5
        newMap[conMat(Material.ACACIA_LEAVES)] = 0.1
        newMap[conMat(Material.ACACIA_SAPLING)] = 0.1
        defMap[conMat(Material.POTTED_ACACIA_SAPLING)] = newMap

        //Dead Bush, creates sticks, sand and more dead bushes.
        newMap = HashMap()
        newMap[conMat(Material.STICK)] = 0.2
        newMap[conMat(Material.SAND)] = 0.4
        newMap[conMat(Material.DEAD_BUSH)] = 0.1
        defMap[conMat(Material.POTTED_DEAD_BUSH)] = newMap

        // Cactus creates more cactus, sand, and cactus green dye.
        newMap = HashMap()
        newMap[conMat(Material.SAND)] = 0.1
        newMap[conMat(Material.CACTUS)] = 0.5
        newMap[conMat(Material.CACTUS_GREEN)] = 0.1
        defMap[conMat(Material.POTTED_CACTUS)] = newMap

        config.addDefault("dropMap", defMap)
        config.addDefault("growthMaxPerSecond", growthMaxPerSecond)
        config.addDefault("growthMinPerSecond", growthMinPerSecond)
        config.addDefault("bonemealIncreasePercent", bonemealIncreasePercent)
        config.addDefault("useHologramPercent", useHolograms)
        config.options().copyDefaults(true)
        saveConfig()
        defMap.clear()
    }
}
