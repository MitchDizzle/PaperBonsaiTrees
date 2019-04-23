# PaperBonsaiTrees
A Minecraft server plugin port of the BonsaiTrees mod

![](/.github/preview.gif)

## Config:
```yml
# A map containing the pot type as the key and the percent chance of a certain drop happening.
# This uses 'Material.valueOf()' to convert to an item stack.
# The Random picker only goes up to 1.0, anything after that will be ignored, if the total of drops does not add up to 1.0 it will treat the left over as no drop.
# This could technically be used on any block, not just flower pots... Not fully tested however.
dropMap:
  # Oak Sapling, the most basic of BonsaiTrees
  POTTED_OAK_SAPLING:
    STICK: 0.2
    OAK_LOG: 0.5
    OAK_LEAVES: 0.1
    OAK_SAPLING: 0.1
  # Birch Sapling
  POTTED_BIRCH_SAPLING:
    STICK: 0.2
    BIRCH_LOG: 0.5
    BIRCH_LEAVES: 0.1
    BIRCH_SAPLING: 0.1
  # Spruce Sapling
  POTTED_SPRUCE_SAPLING:
    STICK: 0.2
    SPRUCE_LOG: 0.5
    SPRUCE_LEAVES: 0.1
    SPRUCE_SAPLING: 0.1
  # Jungle Sapling, has a better chance of creating logs more than anything else.
  POTTED_JUNGLE_SAPLING:
    STICK: 0.1
    JUNGLE_LOG: 0.65
    JUNGLE_LEAVES: 0.1
    JUNGLE_SAPLING: 0.1
  # Acacia Sapling
  POTTED_ACACIA_SAPLING:
    STICK: 0.2
    ACACIA_LOG: 0.5
    ACACIA_LEAVES: 0.1
    ACACIA_SAPLING: 0.1
  # Dark Oak Sapling, similar to the jungle sapling this creates more logs than anything else.
  POTTED_DARK_OAK_SAPLING:
    STICK: 0.1
    DARK_OAK_LOG: 0.65
    DARK_OAK_LEAVES: 0.1
    DARK_OAK_SAPLING: 0.1
  # Dead Bush, mainly used to create sand, it's coarse and rough and irritating and it gets everywhere
  POTTED_DEAD_BUSH:
    STICK: 0.2
    SAND: 0.4
    DEAD_BUSH: 0.1
  # Cactus, has the chance to create sand and cactus green dye (also generates cacti)
  POTTED_CACTUS:
    CACTUS_GREEN: 0.1
    CACTUS: 0.5
    SAND: 0.1

# growth max percent per second
growthMaxPerSecond: 20.0
# growth min percent per second
growthMinPerSecond: 10.0

# growth percent when a player uses bonemeal on the pot.
bonemealIncreasePercent: 100.0

# If holograms (Invisibile Armor stands) should be shown above the flower pot when a player is nearby.
useHologramPercent: true
```