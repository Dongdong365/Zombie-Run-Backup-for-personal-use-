package cn.oneachina.zombieRun.listener

import cn.oneachina.zombieRun.ZombieRun
import cn.oneachina.zombieRun.manager.GameManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleSprintEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import com.destroystokyo.paper.event.player.PlayerJumpEvent

class GameListener(private val plugin: ZombieRun) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        plugin.gameManager.addPlayer(player)
        plugin.staminaManager.addPlayer(player)

        when (plugin.gameManager.getGameStatus()) {
            GameManager.GameStatus.WAITING, GameManager.GameStatus.ENDED -> {
                plugin.gameManager.setPlayerTeam(player, GameManager.Team.HUMAN)
                plugin.respawnManager.teleportToPlayerInitialRespawn(player)
                player.gameMode = GameMode.ADVENTURE
                player.clearActivePotionEffects()
                player.inventory.clear()
                player.health = 20.0
                giveShopItem(player)
            }
            GameManager.GameStatus.STARTING -> {
                plugin.gameManager.setPlayerTeam(player, GameManager.Team.HUMAN)
                plugin.respawnManager.teleportToPlayerInitialRespawn(player)
                player.gameMode = GameMode.ADVENTURE
                giveShopItem(player)
            }
            GameManager.GameStatus.RUNNING -> {
                plugin.gameManager.setPlayerTeam(player, GameManager.Team.ZOMBIE)
                plugin.respawnManager.teleportToZombieRespawn(player)
                player.gameMode = GameMode.ADVENTURE
                plugin.staminaManager.applyZombieEffects(player)
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        plugin.staminaManager.removePlayer(player)
        plugin.gameManager.removePlayer(player)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
            val item = event.item
            if (item != null && plugin.gunShopGUI.isShopItem(item)) {
                plugin.gunShopGUI.openShop(event.player)
                event.isCancelled = true
                return
            }
        }

        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return

        if (block.type == Material.REDSTONE_LAMP || block.type == Material.LEVER) {
            val button = plugin.buttonManager.getButton(block.x, block.y, block.z)
            if (button != null) {
                val player = event.player
                val team = plugin.gameManager.getPlayerTeam(player)

                when {
                    button.isNormal() -> {
                        if (team != GameManager.Team.HUMAN) {
                            player.sendMessage(Component.text("只有人类可以操作按钮！", NamedTextColor.RED))
                            event.isCancelled = true
                            return
                        }
                        val doorNumber = button.doorNumber
                        if (doorNumber != null) {
                            plugin.doorManager.triggerDoor(doorNumber, player)
                        } else {
                            player.sendMessage(Component.text("此按钮配置错误：未指定门号", NamedTextColor.RED))
                        }
                    }
                    button.isEscape() -> {
                        if (team != GameManager.Team.HUMAN) {
                            player.sendMessage(Component.text("只有人类可以操作按钮！", NamedTextColor.RED))
                            event.isCancelled = true
                            return
                        }
                        if (plugin.doorManager.endtime < 0) {
                            plugin.doorManager.startHelicopterEscape()
                            block.type = Material.AIR
                        }
                    }
                }
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        if (plugin.gameManager.getGameStatus() != GameManager.GameStatus.RUNNING) return

        val attackerTeam = plugin.gameManager.getPlayerTeam(attacker)
        val victimTeam = plugin.gameManager.getPlayerTeam(victim)

        if (attackerTeam == GameManager.Team.HUMAN &&
            (victimTeam == GameManager.Team.ZOMBIE || victimTeam == GameManager.Team.ZOMBIE_MAIN)) {
            victim.velocity = victim.velocity.setY(-0.3)
            return
        }

        if ((attackerTeam == GameManager.Team.ZOMBIE || attackerTeam == GameManager.Team.ZOMBIE_MAIN) &&
            victimTeam == GameManager.Team.HUMAN) {
            event.damage = 3.0
            return
        }

        if (attackerTeam == victimTeam) {
            event.isCancelled = true
        }
    }

    private fun infectPlayer(attacker: Player, victim: Player) {
        plugin.miscManager.addInfection(attacker)
        plugin.miscManager.addCoins(attacker, 50)

        Bukkit.broadcast(Component.text("${attacker.name} §c感染了 §b${victim.name}"))

        victim.inventory.clear()
        plugin.gameManager.setPlayerTeam(victim, GameManager.Team.ZOMBIE)
        victim.gameMode = GameMode.SPECTATOR

        var countdown = 5
        var taskId = -1
        val task = object : org.bukkit.scheduler.BukkitRunnable() {
            override fun run() {
                if (plugin.gameManager.getGameStatus() != GameManager.GameStatus.RUNNING) {
                    cancel()
                    return
                }
                if (countdown > 0) {
                    val title = net.kyori.adventure.text.Component.text("§c$countdown")
                    victim.showTitle(net.kyori.adventure.title.Title.title(
                        title,
                        net.kyori.adventure.text.Component.text("§2你已死亡，等待部署")
                    ))
                    countdown--
                } else {
                    victim.gameMode = GameMode.ADVENTURE
                    plugin.staminaManager.applyZombieEffects(victim)
                    plugin.respawnManager.teleportToZombieRespawn(victim)
                    victim.sendMessage(Component.text("你现在是僵尸！阻止人类前进！", NamedTextColor.DARK_GREEN))
                    cancel()
                }
            }
        }
        taskId = task.runTaskTimer(plugin, 0L, 20L).taskId
    }

    @EventHandler
    fun onPlayerDeath(event: org.bukkit.event.entity.PlayerDeathEvent) {
        event.isCancelled = true
        val victim = event.entity
        val killer = victim.killer

        event.drops.clear()
        event.deathMessage(null)

        when (val victimTeam = plugin.gameManager.getPlayerTeam(victim)) {
            GameManager.Team.HUMAN -> {
                if (killer != null && plugin.gameManager.getPlayerTeam(killer) in setOf(GameManager.Team.ZOMBIE, GameManager.Team.ZOMBIE_MAIN)) {
                    infectPlayer(killer, victim)
                } else {
                    plugin.gameManager.setPlayerTeam(victim, GameManager.Team.ZOMBIE)
                    victim.gameMode = GameMode.SPECTATOR
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        victim.gameMode = GameMode.ADVENTURE
                        plugin.staminaManager.applyZombieEffects(victim)
                        plugin.respawnManager.teleportToZombieRespawn(victim)
                        victim.sendMessage(Component.text("你已死亡并变为僵尸！", NamedTextColor.DARK_GREEN))
                    }, 100L)
                }
            }
            GameManager.Team.ZOMBIE, GameManager.Team.ZOMBIE_MAIN -> {
                if (killer != null && plugin.gameManager.getPlayerTeam(killer) == GameManager.Team.HUMAN) {
                    plugin.miscManager.addKill(killer)
                    val reward = if (victimTeam == GameManager.Team.ZOMBIE_MAIN) 150 else 50
                    plugin.miscManager.addCoins(killer, reward)
                    Bukkit.broadcast(Component.text("§b${killer.name} §f击杀了 ${victim.name}"))
                } else {
                    Bukkit.broadcast(Component.text("§2${victim.name} §f死亡了"))
                }
                victim.gameMode = GameMode.SPECTATOR
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    victim.gameMode = GameMode.ADVENTURE
                    plugin.staminaManager.applyZombieEffects(victim)
                    plugin.respawnManager.teleportToZombieRespawn(victim)
                }, 100L)
            }
            else -> {
                victim.inventory.clear()
                plugin.gameManager.setPlayerTeam(victim, GameManager.Team.ZOMBIE)
                victim.gameMode = GameMode.SPECTATOR
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    victim.gameMode = GameMode.ADVENTURE
                    plugin.staminaManager.applyZombieEffects(victim)
                    plugin.respawnManager.teleportToZombieRespawn(victim)
                }, 100L)
            }
        }
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity
        if (entity !is Player) return

        if (event.cause == EntityDamageEvent.DamageCause.FALL) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.player.gameMode != GameMode.CREATIVE) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (event.player.gameMode != GameMode.CREATIVE) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (event.player.gameMode != GameMode.CREATIVE) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: org.bukkit.event.inventory.InventoryClickEvent) {
        if (event.whoClicked is Player) {
            val player = event.whoClicked as Player
            if (player.gameMode != GameMode.CREATIVE) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        if (event.player.gameMode != GameMode.CREATIVE) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerPickupItem(event: EntityPickupItemEvent) {
        val player = if (event.entity is Player) event.entity as? Player else return
        val team = plugin.gameManager.getPlayerTeam(player)
        if (team == GameManager.Team.ZOMBIE || team == GameManager.Team.ZOMBIE_MAIN) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerToggleSprint(event: PlayerToggleSprintEvent) {
        val player = event.player
        plugin.staminaManager.setSprinting(player, event.isSprinting)
        if (plugin.gameManager.getGameStatus() != GameManager.GameStatus.RUNNING) return
        if (plugin.gameManager.getPlayerTeam(player) != GameManager.Team.HUMAN) return
        if (event.isSprinting && !plugin.staminaManager.canSprintOrJump(player)) {
            event.isCancelled = true
            player.sendMessage("§c体力耗尽！请等待体力恢复到100才能疾跑。")
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerJump(event: PlayerJumpEvent) {
        val player = event.player
        if (plugin.gameManager.getGameStatus() != GameManager.GameStatus.RUNNING) return
        if (plugin.gameManager.getPlayerTeam(player) != GameManager.Team.HUMAN) return
        if (!plugin.staminaManager.canSprintOrJump(player)) {
            event.isCancelled = true
            player.sendMessage("§c体力耗尽！请等待体力恢复到100才能跳跃。")
            return
        }
        plugin.staminaManager.addJumpCount(player)
    }

    @EventHandler
    fun onAsyncChat(event: io.papermc.paper.event.player.AsyncChatEvent) {
        event.isCancelled = true
        val player = event.player
        val team = plugin.gameManager.getPlayerTeam(player)
        val rawMsg = PlainTextComponentSerializer.plainText().serialize(event.message())
        val msg = rawMsg.replace("&", "")

        val prefix = when (team) {
            GameManager.Team.HUMAN -> Component.text("[人类] ", NamedTextColor.AQUA)
            GameManager.Team.ZOMBIE -> Component.text("[僵尸] ", NamedTextColor.DARK_GREEN)
            GameManager.Team.ZOMBIE_MAIN -> Component.text("[母体] ", NamedTextColor.LIGHT_PURPLE)
            else -> Component.text("[等待] ", NamedTextColor.GRAY)
        }

        val messageComponent = Component.text()
            .append(prefix)
            .append(Component.text(player.name, NamedTextColor.WHITE))
            .append(Component.text(" >> ", NamedTextColor.GOLD))
            .append(Component.text(msg, NamedTextColor.WHITE))
            .build()

        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.broadcast(messageComponent)
        })
    }

    fun giveShopItem(player: Player) {
        val item = plugin.gunShopGUI.getShopItem()
        player.inventory.setItem(0, item)
    }

    fun giveShopItemsToAllWaiting() {
        for (player in Bukkit.getOnlinePlayers()) {
            if (plugin.gameManager.getGameStatus() == GameManager.GameStatus.WAITING ||
                plugin.gameManager.getGameStatus() == GameManager.GameStatus.ENDED) {
                giveShopItem(player)
            }
        }
    }
}
