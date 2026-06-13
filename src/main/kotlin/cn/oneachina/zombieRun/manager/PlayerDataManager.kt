package cn.oneachina.zombieRun.manager

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID

class PlayerDataManager(
    private val dbPath: String,
    private val defaultCoins: Int
) {

    private var connection: Connection? = null

    fun connect() {
        try {
            Class.forName("org.sqlite.JDBC")
            connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")

            connection!!.createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode = WAL")
                stmt.execute("PRAGMA synchronous = FULL")
                stmt.execute("PRAGMA foreign_keys = ON")
            }

            createTables()
            Bukkit.getLogger().info("[PlayerData] 数据库连接成功: $dbPath")
        } catch (e: Exception) {
            Bukkit.getLogger().severe("[PlayerData] 数据库连接失败: ${e.message}")
            e.printStackTrace()
        }
    }

    fun close() {
        try {
            connection?.createStatement()?.use { stmt ->
                stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)")
            }
            connection?.close()
            Bukkit.getLogger().info("[PlayerData] 数据库已安全关闭")
        } catch (e: Exception) {
            Bukkit.getLogger().severe("[PlayerData] 关闭数据库时出错: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun ensureConnection(): Boolean {
        try {
            if (connection == null || connection!!.isClosed) {
                connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
                connection!!.createStatement().use { stmt ->
                    stmt.execute("PRAGMA journal_mode = WAL")
                    stmt.execute("PRAGMA synchronous = FULL")
                }
            }
            return connection != null
        } catch (e: Exception) {
            Bukkit.getLogger().severe("[PlayerData] 重新连接数据库失败: ${e.message}")
            return false
        }
    }

    private fun createTables() {
        val sql = """
            CREATE TABLE IF NOT EXISTS players (
                uuid TEXT PRIMARY KEY,
                player_name TEXT NOT NULL,
                coins INTEGER NOT NULL DEFAULT $defaultCoins,
                selected_gun TEXT DEFAULT NULL,
                last_updated INTEGER NOT NULL DEFAULT (strftime('%s','now'))
            )
        """.trimIndent()
        connection?.createStatement()?.use { stmt ->
            stmt.execute(sql)
        }
    }

    private fun ensurePlayerExists(uuid: UUID, playerName: String) {
        if (!ensureConnection()) return
        val checkSql = "SELECT uuid FROM players WHERE uuid = ?"
        connection?.prepareStatement(checkSql)?.use { stmt ->
            stmt.setString(1, uuid.toString())
            val rs = stmt.executeQuery()
            if (!rs.next()) {
                val insertSql = "INSERT INTO players (uuid, player_name, coins, selected_gun) VALUES (?, ?, ?, NULL)"
                connection?.prepareStatement(insertSql)?.use { insertStmt ->
                    insertStmt.setString(1, uuid.toString())
                    insertStmt.setString(2, playerName)
                    insertStmt.setInt(3, defaultCoins)
                    insertStmt.executeUpdate()
                    Bukkit.getLogger().info("[PlayerData] 新玩家记录: $playerName ($uuid) 初始金币 $defaultCoins")
                }
            }
        }
    }

    fun getCoins(player: Player): Int {
        if (!ensureConnection()) return defaultCoins
        ensurePlayerExists(player.uniqueId, player.name)
        val sql = "SELECT coins FROM players WHERE uuid = ?"
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, player.uniqueId.toString())
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return rs.getInt("coins")
            }
        }
        return defaultCoins
    }

    fun setCoins(player: Player, coins: Int) {
        if (!ensureConnection()) return
        ensurePlayerExists(player.uniqueId, player.name)
        val sql = "UPDATE players SET coins = ?, last_updated = strftime('%s','now') WHERE uuid = ?"
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setInt(1, coins.coerceAtLeast(0))
            stmt.setString(2, player.uniqueId.toString())
            stmt.executeUpdate()
        }
    }

    fun addCoins(player: Player, amount: Int) {
        val current = getCoins(player)
        setCoins(player, current + amount)
    }

    fun removeCoins(player: Player, amount: Int): Boolean {
        val current = getCoins(player)
        if (current < amount) return false
        setCoins(player, current - amount)
        return true
    }

    fun hasEnoughCoins(player: Player, amount: Int): Boolean {
        return getCoins(player) >= amount
    }

    fun getSelectedGun(player: Player): String? {
        if (!ensureConnection()) return null
        ensurePlayerExists(player.uniqueId, player.name)
        val sql = "SELECT selected_gun FROM players WHERE uuid = ?"
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, player.uniqueId.toString())
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return rs.getString("selected_gun")
            }
        }
        return null
    }

    fun setSelectedGun(player: Player, gunKey: String?) {
        if (!ensureConnection()) return
        ensurePlayerExists(player.uniqueId, player.name)
        val sql = "UPDATE players SET selected_gun = ?, last_updated = strftime('%s','now') WHERE uuid = ?"
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, gunKey)
            stmt.setString(2, player.uniqueId.toString())
            stmt.executeUpdate()
        }
    }

    fun resetSelectedGun(player: Player) {
        setSelectedGun(player, null)
    }

    fun resetAllSelections() {
        if (!ensureConnection()) return
        val sql = "UPDATE players SET selected_gun = NULL"
        connection?.createStatement()?.use { stmt ->
            stmt.executeUpdate(sql)
        }
    }

    fun disconnect() {
        close()
    }
}
