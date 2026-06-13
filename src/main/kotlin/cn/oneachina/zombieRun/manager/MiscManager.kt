package cn.oneachina.zombieRun.manager

import cn.oneachina.zombieRun.ZombieRun
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.entity.Player

class MiscManager(private val plugin: ZombieRun) : Listener {

    private val playerKills = mutableMapOf<Player, Int>()
    private val playerInfections = mutableMapOf<Player, Int>()

    fun addCoins(player: Player, amount: Int) {
        plugin.playerDataManager.addCoins(player, amount)
    }

    fun setCoins(player: Player, amount: Int) {
        plugin.playerDataManager.setCoins(player, amount)
    }

    fun getCoins(player: Player): Int = plugin.playerDataManager.getCoins(player)

    fun takeCoins(player: Player, amount: Int): Boolean {
        return plugin.playerDataManager.removeCoins(player, amount)
    }

    fun addKill(player: Player) {
        playerKills[player] = playerKills.getOrDefault(player, 0) + 1
    }

    fun addInfection(player: Player) {
        playerInfections[player] = playerInfections.getOrDefault(player, 0) + 1
    }

    fun getAllKills(): Map<Player, Int> = playerKills.toMap()
    fun getAllInfections(): Map<Player, Int> = playerInfections.toMap()
    fun getKills(player: Player): Int = playerKills.getOrDefault(player, 0)
    fun getInfections(player: Player): Int = playerInfections.getOrDefault(player, 0)
    fun getSelectedWeapon(player: Player): String? = plugin.gunManager.getSelectedGunName(player)

    fun giveStarterKit(player: Player) {
        plugin.gunManager.giveSelectedGun(player)
    }

    fun teleportToLobby(player: Player) {
        plugin.respawnManager.teleportToWaitRespawn(player)
        plugin.gameManager.setPlayerTeam(player, GameManager.Team.SPECTATOR)
        player.sendMessage("§a你已传送回大厅")
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        val victim = event.entity as? Player ?: return
        if (plugin.gameManager.getPlayerTeam(victim) != GameManager.Team.HUMAN) return
        if (event.cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            event.damage = 0.05
        }
    }

    @EventHandler
    fun onCombat(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        if (plugin.gameManager.getGameStatus() != GameManager.GameStatus.RUNNING) return

        val attackerTeam = plugin.gameManager.getPlayerTeam(attacker)
        val victimTeam = plugin.gameManager.getPlayerTeam(victim)

        if (attackerTeam == GameManager.Team.HUMAN &&
            (victimTeam == GameManager.Team.ZOMBIE || victimTeam == GameManager.Team.ZOMBIE_MAIN)) {

            victim.velocity = victim.velocity.add(attacker.location.direction.setY(-1.0).normalize().multiply(0.3))

            val damage = event.finalDamage * 2
            attacker.sendActionBar(Component.text("造成伤害: ${String.format("%.1f", damage)}").color(NamedTextColor.RED))
        }
    }

    fun clear() {
        playerKills.clear()
        playerInfections.clear()
    }
}
