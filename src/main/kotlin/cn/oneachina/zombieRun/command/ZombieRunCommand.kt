package cn.oneachina.zombieRun.command

import cn.oneachina.zombieRun.ZombieRun
import cn.oneachina.zombieRun.manager.GameManager
import cn.oneachina.zombieRun.model.Button
import cn.oneachina.zombieRun.model.Door
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ZombieRunCommand(private val plugin: ZombieRun) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "start", "spawn", "doors", "buttons", "reload", "open", "close", "reset-all" -> {
                if (!sender.hasPermission("zombie.run.admin")) {
                    sender.sendMessage("§c你没有权限使用此命令！")
                    return true
                }
                when (args[0].lowercase()) {
                    "start" -> handleStart(sender)
                    "spawn" -> handleSpawn(sender, args.drop(1).toTypedArray())
                    "doors" -> handleDoors(sender, args.drop(1).toTypedArray())
                    "buttons" -> handleButtons(sender, args.drop(1).toTypedArray())
                    "reload" -> handleReload(sender)
                    "open" -> handleOpen()
                    "close" -> handleClose()
                    "reset-all" -> handleResetAll(sender)
                }
            }
            "shop", "lobby", "transfer", "coins", "reset-select" -> {
                when (args[0].lowercase()) {
                    "shop" -> handleShop(sender)
                    "lobby" -> handleLobby(sender)
                    "transfer" -> handleTransfer(sender, args.drop(1).toTypedArray())
                    "coins" -> handleCoins(sender, args.drop(1).toTypedArray())
                    "reset-select" -> handleResetSelect(sender)
                }
            }
            else -> sendHelp(sender)
        }
        return true
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("§a===== 僵尸快跑命令 =====")
        sender.sendMessage("§e玩家命令:")
        sender.sendMessage("§a/zr shop - 打开枪械商店 (选择你喜欢的枪械)")
        sender.sendMessage("§a/zr coins - 查看你的硬币余额")
        sender.sendMessage("§a/zr coins <玩家> - 查看他人硬币余额")
        sender.sendMessage("§a/zr transfer <玩家> <金额> - 转账硬币给其他玩家")
        sender.sendMessage("§a/zr reset-select - 清除你的枪械选择")
        sender.sendMessage("§a/zr lobby - 返回大厅")
        sender.sendMessage("")
        sender.sendMessage("§6管理员命令:")
        sender.sendMessage("§a/zr start - 开始游戏")
        sender.sendMessage("§a/zr spawn add <名称> <类型> - 添加重生点")
        sender.sendMessage("§a/zr spawn remove <名称> - 删除重生点")
        sender.sendMessage("§a/zr spawn list - 列出重生点")
        sender.sendMessage("§a/zr doors add ... - 添加门")
        sender.sendMessage("§a/zr doors remove <名称> - 删除门")
        sender.sendMessage("§a/zr doors list - 列出所有门")
        sender.sendMessage("§a/zr buttons add ... - 添加按钮")
        sender.sendMessage("§a/zr buttons remove <名称> - 删除按钮")
        sender.sendMessage("§a/zr buttons list - 列出所有按钮")
        sender.sendMessage("§a/zr reload - 重载配置")
        sender.sendMessage("§a/zr open - 开始游戏")
        sender.sendMessage("§a/zr close - 结束游戏")
        sender.sendMessage("§a/zr coins set <玩家> <金额> - 设置玩家硬币")
        sender.sendMessage("§a/zr coins add <玩家> <金额> - 给玩家添加硬币")
        sender.sendMessage("§a/zr coins take <玩家> <金额> - 扣除玩家硬币")
        sender.sendMessage("§a/zr reset-all - 清除所有玩家身份与游戏数据(保留硬币)")
    }

    private fun handleShop(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage("§c此命令只能由玩家执行！")
            return
        }
        plugin.gunShopGUI.openShop(sender)
    }

    private fun handleLobby(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage("§c此命令只能由玩家执行！")
            return
        }
        plugin.miscManager.teleportToLobby(sender)
    }

    private fun handleTransfer(sender: CommandSender, args: Array<String>) {
        if (sender !is Player) {
            sender.sendMessage("§c此命令只能由玩家执行！")
            return
        }
        if (args.size < 2) {
            sender.sendMessage("§c用法: /zr transfer <玩家> <金额>")
            return
        }
        val target = Bukkit.getPlayer(args[0])
        if (target == null) {
            sender.sendMessage("§c玩家不在线！")
            return
        }
        val amount = args[1].toIntOrNull()
        if (amount == null || amount <= 0) {
            sender.sendMessage("§c金额必须是正整数！")
            return
        }
        val current = plugin.playerDataManager.getCoins(sender)
        if (current < amount) {
            sender.sendMessage("§c你的硬币不足！当前: $current")
            return
        }
        plugin.playerDataManager.removeCoins(sender, amount)
        plugin.playerDataManager.addCoins(target, amount)
        sender.sendMessage("§a成功转账 $amount 硬币给 ${target.name}")
        target.sendMessage("§a你收到了来自 ${sender.name} 的 $amount 硬币")
    }

    private fun handleCoins(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            if (sender !is Player) {
                sender.sendMessage("§c用法: /zr coins <set|add|take> <玩家> <金额>")
                return
            }
            val coins = plugin.playerDataManager.getCoins(sender)
            sender.sendMessage("§6你的硬币余额: $coins")
            return
        }

        when (args[0].lowercase()) {
            "set" -> {
                if (!sender.hasPermission("zombie.run.admin")) {
                    sender.sendMessage("§c你没有权限使用此命令！")
                    return
                }
                if (args.size < 3) {
                    sender.sendMessage("§c用法: /zr coins set <玩家> <金额>")
                    return
                }
                val target = Bukkit.getPlayer(args[1])
                if (target == null) {
                    sender.sendMessage("§c玩家 ${args[1]} 不在线！")
                    return
                }
                val amount = args[2].toIntOrNull()
                if (amount == null || amount < 0) {
                    sender.sendMessage("§c金额必须是非负整数！")
                    return
                }
                plugin.playerDataManager.setCoins(target, amount)
                sender.sendMessage("§a已将 ${target.name} 的硬币设置为 $amount")
                target.sendMessage("§6你的硬币余额已被设置为: $amount")
            }
            "add" -> {
                if (!sender.hasPermission("zombie.run.admin")) {
                    sender.sendMessage("§c你没有权限使用此命令！")
                    return
                }
                if (args.size < 3) {
                    sender.sendMessage("§c用法: /zr coins add <玩家> <金额>")
                    return
                }
                val target = Bukkit.getPlayer(args[1])
                if (target == null) {
                    sender.sendMessage("§c玩家 ${args[1]} 不在线！")
                    return
                }
                val amount = args[2].toIntOrNull()
                if (amount == null || amount <= 0) {
                    sender.sendMessage("§c金额必须是正整数！")
                    return
                }
                plugin.playerDataManager.addCoins(target, amount)
                sender.sendMessage("§a已给 ${target.name} 添加 $amount 硬币")
                target.sendMessage("§6+ $amount 硬币！当前余额: ${plugin.playerDataManager.getCoins(target)}")
            }
            "take" -> {
                if (!sender.hasPermission("zombie.run.admin")) {
                    sender.sendMessage("§c你没有权限使用此命令！")
                    return
                }
                if (args.size < 3) {
                    sender.sendMessage("§c用法: /zr coins take <玩家> <金额>")
                    return
                }
                val target = Bukkit.getPlayer(args[1])
                if (target == null) {
                    sender.sendMessage("§c玩家 ${args[1]} 不在线！")
                    return
                }
                val amount = args[2].toIntOrNull()
                if (amount == null || amount <= 0) {
                    sender.sendMessage("§c金额必须是正整数！")
                    return
                }
                if (!plugin.playerDataManager.removeCoins(target, amount)) {
                    sender.sendMessage("§c玩家硬币不足！当前: ${plugin.playerDataManager.getCoins(target)}")
                    return
                }
                sender.sendMessage("§a已从 ${target.name} 扣除 $amount 硬币")
                target.sendMessage("§6- $amount 硬币！当前余额: ${plugin.playerDataManager.getCoins(target)}")
            }
            else -> {
                val target = Bukkit.getPlayer(args[0])
                if (target == null) {
                    sender.sendMessage("§c玩家 ${args[0]} 不在线！")
                    return
                }
                val coins = plugin.playerDataManager.getCoins(target)
                sender.sendMessage("§6${target.name} 的硬币余额: $coins")
            }
        }
    }

    private fun handleResetSelect(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage("§c此命令只能由玩家执行！")
            return
        }
        plugin.playerDataManager.resetSelectedGun(sender)
        sender.sendMessage("§a已重置你的枪械选择")
    }

    private fun handleStart(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage("§c此命令只能由玩家执行！")
            return
        }
        plugin.gameManager.forceStartGame()
        sender.sendMessage("§a游戏开始！")
    }

    private fun handleSpawn(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            sender.sendMessage("§c用法: /zr spawn <add|remove|list>")
            return
        }
        when (args[0].lowercase()) {
            "add" -> handleSpawnAdd(sender, args.drop(1).toTypedArray())
            "remove" -> handleSpawnRemove(sender, args.drop(1).toTypedArray())
            "list" -> handleSpawnList(sender)
            else -> sender.sendMessage("§c未知子命令，可用: add, remove, list")
        }
    }

    private fun handleSpawnAdd(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("§c此命令只能由玩家执行！")
            return
        }
        if (args.size < 2) {
            sender.sendMessage("§c用法: /zr spawn add <名称> <类型>")
            return
        }
        val name = args[0]
        val typeStr = args[1].uppercase()
        val type = try {
            cn.oneachina.zombieRun.model.Respawn.RespawnType.valueOf(typeStr)
        } catch (_: IllegalArgumentException) {
            sender.sendMessage("§c无效的类型！可用: ${cn.oneachina.zombieRun.model.Respawn.RespawnType.entries.joinToString(", ")}")
            return
        }
        val respawn = cn.oneachina.zombieRun.model.Respawn(
            name = name,
            x = sender.location.blockX,
            y = sender.location.blockY,
            z = sender.location.blockZ,
            yaw = sender.location.yaw.toDouble(),
            pitch = sender.location.pitch.toDouble(),
            type = type,
            doorNumber = null,
            roomNumber = null
        )
        plugin.configManager.addRespawn(respawn)
        plugin.respawnManager.addRespawn(respawn)
        sender.sendMessage("§a重生点 '$name' 添加成功！")
    }

    private fun handleSpawnRemove(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            sender.sendMessage("§c用法: /zr spawn remove <名称>")
            return
        }
        val name = args[0]
        plugin.configManager.removeRespawn(name)
        plugin.respawnManager.removeRespawn(name)
        sender.sendMessage("§a重生点 '$name' 删除成功！")
    }

    private fun handleSpawnList(sender: CommandSender) {
        val respawns = plugin.respawnManager.getAllRespawns()
        if (respawns.isEmpty()) {
            sender.sendMessage("§c当前没有重生点。")
            return
        }
        sender.sendMessage("§a===== 重生点列表 =====")
        respawns.forEach { sender.sendMessage("§a- ${it.name} (${it.type})") }
    }

    private fun handleDoors(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            sender.sendMessage("§c用法: /zr doors <add|remove|list>")
            return
        }
        when (args[0].lowercase()) {
            "add" -> handleDoorsAdd(sender, args.drop(1).toTypedArray())
            "remove" -> handleDoorsRemove(sender, args.drop(1).toTypedArray())
            "list" -> handleDoorsList(sender)
            else -> sender.sendMessage("§c未知子命令，可用: add, remove, list")
        }
    }

    private fun handleDoorsAdd(sender: CommandSender, args: Array<out String>) {
        if (args.size < 7) {
            sender.sendMessage("§c用法: /zr doors add <x1> <y1> <z1> <x2> <y2> <z2> [mode] [门号] [delay] [材质]")
            sender.sendMessage("§cmode: normal, player, zombie, start (默认为 normal)")
            return
        }
        try {
            val x1 = args[0].toInt()
            val y1 = args[1].toInt()
            val z1 = args[2].toInt()
            val x2 = args[3].toInt()
            val y2 = args[4].toInt()
            val z2 = args[5].toInt()
            val mode = args[6].lowercase()
            val validModes = setOf("normal", "player", "zombie", "start")
            if (mode !in validModes) {
                sender.sendMessage("§c模式必须是 normal, player, zombie, start 之一")
                return
            }
            val doorNumber = if (args.size > 7) args[7].toIntOrNull() ?: 0 else 0
            val delay = if (args.size > 8) args[8].toIntOrNull() ?: 30 else 30
            val material = if (args.size > 9) args[9] else "STONE"

            val minX = minOf(x1, x2)
            val minY = minOf(y1, y2)
            val minZ = minOf(z1, z2)
            val maxX = maxOf(x1, x2)
            val maxY = maxOf(y1, y2)
            val maxZ = maxOf(z1, z2)

            val doorName = "door_${System.currentTimeMillis()}"

            val door = Door(
                name = doorName,
                minX = minX,
                minY = minY,
                minZ = minZ,
                maxX = maxX,
                maxY = maxY,
                maxZ = maxZ,
                doorNumber = doorNumber,
                delay = delay,
                material = material,
                mode = Door.DoorMode.fromString(mode),
                useScanData = false,
                blocks = emptyMap()
            )
            plugin.configManager.addDoorFull(door)
            plugin.doorManager.addDoor(door)
            sender.sendMessage("§a门 '$doorName' 添加成功！模式: $mode")
        } catch (_: NumberFormatException) {
            sender.sendMessage("§c坐标必须是整数！")
        }
    }

    private fun handleDoorsRemove(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            sender.sendMessage("§c用法: /zr doors remove <名称>")
            return
        }
        val name = args[0]
        plugin.configManager.removeDoor(name)
        plugin.doorManager.removeDoor(name)
        sender.sendMessage("§a门 '$name' 删除成功！")
    }

    private fun handleDoorsList(sender: CommandSender) {
        val doors = plugin.doorManager.getAllDoors()
        if (doors.isEmpty()) {
            sender.sendMessage("§c当前没有门。")
            return
        }
        sender.sendMessage("§a===== 门列表 =====")
        doors.forEach { sender.sendMessage("§a- ${it.name} (#${it.doorNumber}, ${it.mode})") }
    }

    private fun handleButtons(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            sender.sendMessage("§c用法: /zr buttons <add|remove|list>")
            return
        }
        when (args[0].lowercase()) {
            "add" -> handleButtonsAdd(sender, args.drop(1).toTypedArray())
            "remove" -> handleButtonsRemove(sender, args.drop(1).toTypedArray())
            "list" -> handleButtonsList(sender)
            else -> sender.sendMessage("§c未知子命令，可用: add, remove, list")
        }
    }

    private fun handleButtonsAdd(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("§c此命令只能由玩家执行！")
            return
        }
        if (args.size < 4) {
            sender.sendMessage("§c用法: /zr buttons add <x> <y> <z> <mode> [参数...]")
            return
        }
        try {
            val x = args[0].toInt()
            val y = args[1].toInt()
            val z = args[2].toInt()
            val mode = args[3].lowercase()

            val button = when (mode) {
                "normal" -> {
                    if (args.size < 5) {
                        sender.sendMessage("§cnormal模式需要指定门号！")
                        return
                    }
                    val doorNumber = args[4].toIntOrNull()
                    if (doorNumber == null) {
                        sender.sendMessage("§c门号必须是整数！")
                        return
                    }
                    val name = "button_${x}_${y}_${z}_normal"
                    Button(name, x, y, z, mode, doorNumber = doorNumber)
                }
                "escape" -> {
                    val name = "button_${x}_${y}_${z}_escape"
                    Button(name, x, y, z, mode)
                }
                else -> {
                    sender.sendMessage("§c无效的模式！可用: normal, escape")
                    return
                }
            }
            plugin.buttonManager.addButton(button)
            plugin.configManager.addButton(button)
            sender.sendMessage("§a按钮添加成功！名称: ${button.name}, 模式: ${button.mode}")
        } catch (_: NumberFormatException) {
            sender.sendMessage("§c坐标和数字参数必须是整数！")
        }
    }

    private fun handleButtonsRemove(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            sender.sendMessage("§c用法: /zr buttons remove <名称>")
            return
        }
        val name = args[0]
        plugin.buttonManager.removeButton(name)
        plugin.configManager.removeButton(name)
        sender.sendMessage("§a按钮 '$name' 删除成功！")
    }

    private fun handleButtonsList(sender: CommandSender) {
        val buttons = plugin.buttonManager.getAllButtons()
        if (buttons.isEmpty()) {
            sender.sendMessage("§c当前没有按钮。")
            return
        }
        sender.sendMessage("§a===== 按钮列表 =====")
        buttons.forEach {
            val info = when {
                it.isNormal() -> "门号: ${it.doorNumber}"
                it.isEscape() -> "撤离按钮"
                else -> ""
            }
            sender.sendMessage("§a- ${it.name} 模式: ${it.mode} 坐标: ${it.x},${it.y},${it.z} $info")
        }
    }

    private fun handleReload(sender: CommandSender) {
        plugin.configManager.reloadConfig()
        plugin.doorManager.loadDoors()
        plugin.respawnManager.loadRespawns()
        plugin.buttonManager.loadButtons()
        plugin.startEffectManager.loadEffects()
        plugin.gunManager.loadConfig()
        plugin.gunShopGUI.reloadSettings()
        val resetAfter = plugin.configManager.getConfig()
            .getConfigurationSection("player-data")
            ?.getBoolean("reset-selection-after-game", false) ?: false
        plugin.gameManager.setResetSelectionsAfterGame(resetAfter)
        sender.sendMessage("§a配置重载成功！")
    }

    private fun handleOpen() {
        if (plugin.gameManager.getGameStatus() != GameManager.GameStatus.RUNNING) {
            plugin.gameManager.beginGame()
        } else {
            plugin.logger.warning("游戏已在运行中！")
        }
    }

    private fun handleClose() {
        plugin.gameManager.endGame(GameManager.Team.SPECTATOR)
    }

    private fun handleResetAll(sender: CommandSender) {
        val count = plugin.gameManager.forceClearAllData()
        sender.sendMessage("§a已清空游戏数据！清除了 $count 名玩家的身份信息。")
        sender.sendMessage("§a玩家硬币余额已保留。")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String>? {
        return when (args.size) {
            1 -> {
                if (sender.hasPermission("zombie.run.admin")) {
                    listOf("start", "spawn", "doors", "buttons", "reload", "open", "close", "reset-all",
                        "shop", "lobby", "transfer", "coins", "reset-select")
                } else {
                    listOf("shop", "lobby", "transfer", "coins", "reset-select")
                }.filter { it.startsWith(args[0].lowercase()) }.toMutableList()
            }
            2 -> {
                when (args[0].lowercase()) {
                    "spawn" -> listOf("add", "remove", "list").filter { it.startsWith(args[1].lowercase()) }.toMutableList()
                    "doors" -> listOf("add", "remove", "list").filter { it.startsWith(args[1].lowercase()) }.toMutableList()
                    "buttons" -> listOf("add", "remove", "list").filter { it.startsWith(args[1].lowercase()) }.toMutableList()
                    "transfer" -> Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }.toMutableList()
                    "coins" -> {
                        if (sender.hasPermission("zombie.run.admin")) {
                            listOf("set", "add", "take").filter { it.startsWith(args[1].lowercase()) }.toMutableList()
                        } else {
                            Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }.toMutableList()
                        }
                    }
                    else -> mutableListOf()
                }
            }
            3 -> {
                when (args[0].lowercase()) {
                    "spawn" -> {
                        if (args[1].lowercase() == "add") {
                            cn.oneachina.zombieRun.model.Respawn.RespawnType.entries.map { it.name.lowercase() }
                                .filter { it.startsWith(args[2].lowercase()) }.toMutableList()
                        } else {
                            mutableListOf()
                        }
                    }
                    "doors" -> {
                        if (args[1].lowercase() == "add") {
                            mutableListOf("~")
                        } else {
                            mutableListOf()
                        }
                    }
                    "buttons" -> {
                        if (args[1].lowercase() == "add") {
                            mutableListOf("~")
                        } else {
                            mutableListOf()
                        }
                    }
                    "coins" -> {
                        when (args[1].lowercase()) {
                            "set", "add", "take" -> Bukkit.getOnlinePlayers().map { it.name }
                                .filter { it.startsWith(args[2], ignoreCase = true) }.toMutableList()
                            else -> mutableListOf()
                        }
                    }
                    else -> mutableListOf()
                }
            }
            4 -> {
                when (args[0].lowercase()) {
                    "doors" -> {
                        if (args[1].lowercase() == "add") {
                            mutableListOf("~")
                        } else {
                            mutableListOf()
                        }
                    }
                    "buttons" -> {
                        if (args[1].lowercase() == "add") {
                            mutableListOf("~")
                        } else {
                            mutableListOf()
                        }
                    }
                    else -> mutableListOf()
                }
            }
            5 -> {
                when (args[0].lowercase()) {
                    "buttons" -> {
                        if (args[1].lowercase() == "add") {
                            mutableListOf("~")
                        } else {
                            mutableListOf()
                        }
                    }
                    "doors" -> {
                        if (args[1].lowercase() == "add") {
                            mutableListOf("~")
                        } else {
                            mutableListOf()
                        }
                    }
                    else -> mutableListOf()
                }
            }
            6 -> {
                when (args[0].lowercase()) {
                    "buttons" -> {
                        if (args[1].lowercase() == "add") {
                            listOf("normal", "escape").filter { it.startsWith(args[5].lowercase()) }.toMutableList()
                        } else {
                            mutableListOf()
                        }
                    }
                    "doors" -> {
                        if (args[1].lowercase() == "add") {
                            mutableListOf("~")
                        } else {
                            mutableListOf()
                        }
                    }
                    else -> mutableListOf()
                }
            }
            7 -> {
                when (args[0].lowercase()) {
                    "doors" -> {
                        if (args[1].lowercase() == "add") {
                            mutableListOf("~")
                        } else {
                            mutableListOf()
                        }
                    }
                    else -> mutableListOf()
                }
            }
            8 -> {
                when (args[0].lowercase()) {
                    "doors" -> {
                        if (args[1].lowercase() == "add") {
                            mutableListOf("~")
                        } else {
                            mutableListOf()
                        }
                    }
                    else -> mutableListOf()
                }
            }
            9 -> {
                when (args[0].lowercase()) {
                    "doors" -> {
                        if (args[1].lowercase() == "add") {
                            listOf("normal", "player", "zombie", "start")
                                .filter { it.startsWith(args[8].lowercase()) }.toMutableList()
                        } else {
                            mutableListOf()
                        }
                    }
                    else -> mutableListOf()
                }
            }
            else -> mutableListOf()
        }
    }
}
