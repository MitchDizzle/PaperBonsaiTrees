package tech.mtch.bonsaitrees

import org.bukkit.Material

class PotConfig {
    var display: String = "Bonsai Pot"
    var growthMaxPerIter: Double = 20.0
    var growthMinPerIter: Double = 10.0
    var bonemealIncreasePercent: Double = 100.0
    val drops = HashMap<Material,Double>()

    fun addDrop(material: Material, chance: Double) {
        drops[material] = chance
    }
}
