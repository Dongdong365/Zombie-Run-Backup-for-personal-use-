package cn.oneachina.zombieRun.manager

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.Random

data class GunConfig(
    val key: String,
    val material: Material,
    val displayName: String,
    val price: Int,
    val description: List<String>,
    val giveCommands: List<String>,
    val golden: Boolean
)

class GunManager(
    private val pluginFolder: File,
    private val dataManager: PlayerDataManager
) {

    private val random = Random()

    var guns: Map<String, GunConfig> = emptyMap()
        private set

    fun loadConfig() {
        val file = File(pluginFolder, "config/gunandgui.yml")
        if (!file.exists()) {
            pluginFolder.resolve("config").mkdirs()
            file.createNewFile()
        }

        val config = YamlConfiguration.loadConfiguration(file)
        val gunSection = config.getConfigurationSection("guns") ?: return

        val loaded = mutableMapOf<String, GunConfig>()

        for (key in gunSection.getKeys(false)) {
            val section = gunSection.getConfigurationSection(key) ?: continue

            val materialName = section.getString("material", "DIAMOND_SWORD") ?: "DIAMOND_SWORD"
            val material = try {
                Material.valueOf(materialName.uppercase())
            } catch (e: IllegalArgumentException) {
                Bukkit.getLogger().warning("[GunManager] 枪械 $key 的材质 $materialName 无效，使用默认 DIAMOND_SWORD")
                Material.DIAMOND_SWORD
            }

            val displayName = ChatColor.translateAlternateColorCodes(
                '&',
                section.getString("display-name", key) ?: key
            )

            val price = section.getInt("price", 0)

            val description = section.getStringList("description").map {
                ChatColor.translateAlternateColorCodes('&', it)
            }

            val giveCommands = section.getStringList("give-commands")
            val golden = section.getBoolean("golden", false)

            loaded[key] = GunConfig(
                key = key,
                material = material,
                displayName = displayName,
                price = price,
                description = description,
                giveCommands = giveCommands,
                golden = golden
            )
        }

        guns = loaded
        Bukkit.getLogger().info("[GunManager] 已加载 ${loaded.size} 把枪械")
    }

    fun getGun(key: String): GunConfig? = guns[key]

    fun getRandomGun(): GunConfig? {
        if (guns.isEmpty()) return null
        val gunList = guns.values.toList()
        return gunList[random.nextInt(gunList.size)]
    }

    fun selectGun(player: Player, gunKey: String) {
        if (!guns.containsKey(gunKey)) {
            player.sendMessage("${ChatColor.RED}该枪械不存在")
            return
        }
        val current = dataManager.getSelectedGun(player)
        if (current == gunKey) {
            dataManager.resetSelectedGun(player)
            player.sendMessage("${ChatColor.YELLOW}已取消选择 ${guns[gunKey]?.displayName}")
        } else {
            dataManager.setSelectedGun(player, gunKey)
            player.sendMessage("${ChatColor.GREEN}已选择 ${guns[gunKey]?.displayName}${ChatColor.GREEN}，游戏开始时发放")
        }
    }

    fun resetSelection(player: Player) {
        dataManager.resetSelectedGun(player)
        player.sendMessage("${ChatColor.YELLOW}已重置你的枪械选择")
    }

    fun giveSelectedGun(player: Player): Boolean {
        val key = dataManager.getSelectedGun(player)
        val gun = key?.let { guns[it] }

        if (gun != null) {
            if (dataManager.hasEnoughCoins(player, gun.price)) {
                if (gun.price > 0) {
                    dataManager.removeCoins(player, gun.price)
                    player.sendMessage("${ChatColor.GREEN}已扣除 ${gun.price} 硬币，获得 ${gun.displayName}")
                }
                executeGiveCommands(player, gun)
                return true
            } else {
                player.sendMessage("${ChatColor.RED}硬币不足（需要 ${gun.price}，你有 ${dataManager.getCoins(player)}），随机发放一把")
            }
        }

        val randomGun = getRandomGun()
        if (randomGun != null) {
            player.sendMessage("${ChatColor.YELLOW}随机获得: ${randomGun.displayName}")
            executeGiveCommands(player, randomGun)
            return true
        }

        return false
    }

    private fun executeGiveCommands(player: Player, gun: GunConfig) {
        val console = Bukkit.getConsoleSender()
        for (cmd in gun.giveCommands) {
            val processed = cmd.replace("%player%", player.name)
            Bukkit.dispatchCommand(console, processed)
        }
    }

    fun isSelected(player: Player, gunKey: String): Boolean {
        return dataManager.getSelectedGun(player) == gunKey
    }

    fun getSelectedGunName(player: Player): String? {
        val gunKey = dataManager.getSelectedGun(player) ?: return null
        return guns[gunKey]?.displayName ?: gunKey
    }

    fun resetAllSelections() {
        dataManager.resetAllSelections()
    }
}
