package cn.oneachina.zombieRun

import cn.oneachina.zombieRun.command.DoorPerformanceCommand
import cn.oneachina.zombieRun.command.ZombieRunCommand
import cn.oneachina.zombieRun.gui.GunShopGUI
import cn.oneachina.zombieRun.listener.GameListener
import cn.oneachina.zombieRun.manager.*
import cn.oneachina.zombieRun.papi.ZombieRunExpansion
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class ZombieRun : JavaPlugin() {
    lateinit var configManager: ConfigManager
    lateinit var doorManager: DoorManager
    lateinit var doorZoneManager: DoorZoneManager
    lateinit var buttonManager: ButtonManager
    lateinit var respawnManager: RespawnManager
    lateinit var gameManager: GameManager
    lateinit var staminaManager: StaminaManager
    lateinit var miscManager: MiscManager
    lateinit var startEffectManager: StartEffectManager
    lateinit var playerDataManager: PlayerDataManager
    lateinit var gunManager: GunManager
    lateinit var gunShopGUI: GunShopGUI
    lateinit var gameListener: GameListener

    override fun onEnable() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            ZombieRunExpansion(this).register()
        }

        val configDir = File(dataFolder, "config")
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        val configYml = File(configDir, "config.yml")
        val gunandguiYml = File(configDir, "gunandgui.yml")
        if (!configYml.exists()) {
            saveResource("config/config.yml", false)
            logger.info("已复制默认配置 config.yml")
        }
        if (!gunandguiYml.exists()) {
            saveResource("config/gunandgui.yml", false)
            logger.info("已复制默认配置 gunandgui.yml")
        }

        configManager = ConfigManager(this).apply { loadConfig() }
        doorZoneManager = DoorZoneManager()
        doorManager = DoorManager(this).apply { loadDoors() }
        buttonManager = ButtonManager(this).apply { loadButtons() }
        respawnManager = RespawnManager(this).apply { loadRespawns() }
        gameManager = GameManager(this)
        staminaManager = StaminaManager(this).apply { init() }
        miscManager = MiscManager(this)
        startEffectManager = StartEffectManager(this).apply { loadEffects() }
        val gunConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(gunandguiYml)
        val defaultCoins = gunConfig.getInt("player-data.default-coins", 1000)
        val resetAfter = gunConfig.getBoolean("player-data.reset-selection-after-game", false)
        val dbPath = File(dataFolder, "playerdata.db").absolutePath
        playerDataManager = PlayerDataManager(dbPath, defaultCoins).apply { connect() }
        gunManager = GunManager(dataFolder, playerDataManager).apply { loadConfig() }
        gunShopGUI = GunShopGUI(dataFolder, gunManager, playerDataManager).apply {
            reloadSettings()
        }
        gameManager.setResetSelectionsAfterGame(resetAfter)

        Bukkit.getScheduler().runTaskLater(this, Runnable {
            doorManager.reset()
        }, 20L)

        gameListener = GameListener(this)
        val pm = Bukkit.getPluginManager()
        pm.registerEvents(gameListener, this)
        pm.registerEvents(gunShopGUI, this)
        pm.registerEvents(miscManager, this)

        val zrCommand = ZombieRunCommand(this)
        getCommand("zr")?.setExecutor(zrCommand)
        getCommand("zr")?.tabCompleter = zrCommand
        getCommand("doorperf")?.setExecutor(DoorPerformanceCommand(this))

        logger.info("ZombieRun 核心已启用")
    }

    override fun onDisable() {
        doorManager.reset()
        respawnManager.clear()
        gameManager.clear()
        buttonManager.clear()
        playerDataManager.close()
    }
}
