package cn.oneachina.zombieRun.gui

import cn.oneachina.zombieRun.manager.GunConfig
import cn.oneachina.zombieRun.manager.GunManager
import cn.oneachina.zombieRun.manager.PlayerDataManager
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.io.File
import java.util.UUID

data class GUIButtonConfig(
    val material: Material,
    val displayName: String,
    val lore: List<String>
)

data class GUISettings(
    val title: String,
    val size: Int,
    val borderMaterial: Material,
    val borderName: String,
    val fillerMaterial: Material?,
    val buttons: Map<String, GUIButtonConfig>,
    val shopItem: ShopItemConfig
)

data class ShopItemConfig(
    val material: Material,
    val displayName: String,
    val lore: List<String>
)

class GunShopGUI(
    private val pluginFolder: File,
    private val gunManager: GunManager,
    private val dataManager: PlayerDataManager
) : Listener {

    private val pageSize: Int
    private val gunSlotStart: Int
    private val gunSlotEnd: Int
    private val totalRows: Int

    private val playerPages = mutableMapOf<UUID, Int>()
    private val openInventories = mutableMapOf<UUID, Inventory>()

    private var settings: GUISettings

    init {
        settings = loadSettings()
        totalRows = settings.size / 9
        pageSize = calculatePageSize()
        gunSlotStart = 9
        gunSlotEnd = settings.size - 9 - 1
    }

    private fun calculatePageSize(): Int {
        var count = 0
        for (row in 1 until totalRows - 1) {
            for (col in 1..7) {
                count++
            }
        }
        return count
    }

    private fun loadSettings(): GUISettings {
        val file = File(pluginFolder, "config/gunandgui.yml")
        val config = YamlConfiguration.loadConfiguration(file)
        val guiSection = config.getConfigurationSection("gui") ?: return defaultSettings()

        val title = ChatColor.translateAlternateColorCodes(
            '&', guiSection.getString("title", "&6&l枪械商店") ?: "&6&l枪械商店"
        )
        val size = guiSection.getInt("size", 54).coerceIn(9, 54).let {
            var s = it
            while (s % 9 != 0) s++
            s
        }

        val borderMat = parseMaterial(guiSection.getString("border-material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE)
        val borderName = guiSection.getString("border-name", "")?.let {
            ChatColor.translateAlternateColorCodes('&', it)
        } ?: ""

        val fillerMat = guiSection.getString("filler-material")?.let {
            if (it.isBlank()) null else parseMaterial(it, null)
        }

        val buttons = mutableMapOf<String, GUIButtonConfig>()
        val btnSection = guiSection.getConfigurationSection("buttons")
        if (btnSection != null) {
            for (key in btnSection.getKeys(false)) {
                val sec = btnSection.getConfigurationSection(key) ?: continue
                val mat = parseMaterial(sec.getString("material", "STONE"), Material.STONE) ?: Material.STONE
                val name = ChatColor.translateAlternateColorCodes('&', sec.getString("display-name", key) ?: key)
                val lore = sec.getStringList("lore").map { ChatColor.translateAlternateColorCodes('&', it) }
                buttons[key] = GUIButtonConfig(mat, name, lore)
            }
        }

        val shopSection = config.getConfigurationSection("shop-item")
        val shopItem = if (shopSection != null) {
            val mat = parseMaterial(shopSection.getString("material", "NETHER_STAR"), Material.NETHER_STAR) ?: Material.NETHER_STAR
            val name = ChatColor.translateAlternateColorCodes('&', shopSection.getString("display-name", "&6&l枪械商店") ?: "&6&l枪械商店")
            val lore = shopSection.getStringList("lore").map { ChatColor.translateAlternateColorCodes('&', it) }
            ShopItemConfig(mat, name, lore)
        } else {
            ShopItemConfig(Material.NETHER_STAR, "&6&l枪械商店", listOf("&7右键打开枪械商店"))
        }

        val safeBorderMat = borderMat ?: Material.BLACK_STAINED_GLASS_PANE
        return GUISettings(title, size, safeBorderMat, borderName, fillerMat, buttons, shopItem)
    }

    private fun defaultSettings(): GUISettings {
        return GUISettings(
            title = "&6&l枪械商店",
            size = 54,
            borderMaterial = Material.BLACK_STAINED_GLASS_PANE,
            borderName = "",
            fillerMaterial = null,
            buttons = emptyMap(),
            shopItem = ShopItemConfig(Material.NETHER_STAR, "&6&l枪械商店", listOf("&7右键打开枪械商店"))
        )
    }

    private fun parseMaterial(name: String?, default: Material?): Material? {
        if (name == null || name.isBlank()) return default
        return try {
            Material.valueOf(name.uppercase())
        } catch (e: IllegalArgumentException) {
            default
        }
    }

    fun reloadSettings() {
        settings = loadSettings()
    }

    fun getShopItem(): ItemStack {
        val item = ItemStack(settings.shopItem.material)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(settings.shopItem.displayName)
        meta.lore = settings.shopItem.lore
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
        item.itemMeta = meta
        return item
    }

    fun isShopItem(item: ItemStack?): Boolean {
        if (item == null || item.type != settings.shopItem.material) return false
        val meta = item.itemMeta ?: return false
        return meta.displayName == settings.shopItem.displayName
    }

    fun openShop(player: Player) {
        val page = playerPages.getOrDefault(player.uniqueId, 0)
        val inventory = buildInventory(player, page)
        openInventories[player.uniqueId] = inventory
        player.openInventory(inventory)
    }

    private fun buildInventory(player: Player, page: Int): Inventory {
        val inv = Bukkit.createInventory(null, settings.size, settings.title)

        for (slot in 0 until settings.size) {
            val row = slot / 9
            val col = slot % 9

            if (row == 0 || row == totalRows - 1 || col == 0 || col == 8) {
                inv.setItem(slot, buildBorderItem())
            } else if (settings.fillerMaterial != null) {
                inv.setItem(slot, buildFillerItem())
            }
        }

        val gunList = gunManager.guns.values.toList()
        val totalPages = maxOf(1, (gunList.size + pageSize - 1) / pageSize)
        val currentPage = page.coerceIn(0, totalPages - 1)
        playerPages[player.uniqueId] = currentPage

        val startIdx = currentPage * pageSize
        val endIdx = minOf(startIdx + pageSize, gunList.size)

        var slotIdx = 0
        for (i in startIdx until endIdx) {
            val gun = gunList[i]
            val slot = findGunSlot(slotIdx)
            if (slot >= 0) {
                inv.setItem(slot, buildGunItem(player, gun))
            }
            slotIdx++
        }

        val bottomRow = totalRows - 1
        val shownOnPage = endIdx - startIdx

        val prevBtn = settings.buttons["previous-page"]
        if (prevBtn != null && bottomRow * 9 + 0 < settings.size) {
            inv.setItem(bottomRow * 9 + 0, buildButtonItem(prevBtn, mapOf()))
        }

        val pageBtn = settings.buttons["page-info"]
        if (pageBtn != null && bottomRow * 9 + 4 < settings.size) {
            val placeholders = mapOf(
                "%current%" to (currentPage + 1).toString(),
                "%total%" to totalPages.toString(),
                "%shown%" to shownOnPage.toString(),
                "%total_guns%" to gunList.size.toString()
            )
            inv.setItem(bottomRow * 9 + 4, buildButtonItem(pageBtn, placeholders))
        }

        val nextBtn = settings.buttons["next-page"]
        if (nextBtn != null && bottomRow * 9 + 8 < settings.size) {
            inv.setItem(bottomRow * 9 + 8, buildButtonItem(nextBtn, mapOf()))
        }

        val closeBtn = settings.buttons["close"]
        if (closeBtn != null && bottomRow * 9 + 5 < settings.size) {
            inv.setItem(bottomRow * 9 + 5, buildButtonItem(closeBtn, mapOf()))
        }

        val resetBtn = settings.buttons["reset-selection"]
        if (resetBtn != null && bottomRow * 9 + 3 < settings.size) {
            inv.setItem(bottomRow * 9 + 3, buildButtonItem(resetBtn, mapOf()))
        }

        val coinBtn = settings.buttons["coin-info"]
        if (coinBtn != null && bottomRow * 9 + 1 < settings.size) {
            val placeholders = mapOf("%coins%" to dataManager.getCoins(player).toString())
            inv.setItem(bottomRow * 9 + 1, buildButtonItem(coinBtn, placeholders))
        }

        return inv
    }

    private fun findGunSlot(index: Int): Int {
        val colsPerRow = 7
        val row = (index / colsPerRow) + 1
        val col = (index % colsPerRow) + 1
        if (row >= totalRows - 1) return -1
        return row * 9 + col
    }

    private fun buildBorderItem(): ItemStack {
        val item = ItemStack(settings.borderMaterial)
        val meta = item.itemMeta ?: return item
        if (settings.borderName.isNotBlank()) {
            meta.setDisplayName(settings.borderName)
        } else {
            meta.setDisplayName(" ")
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
        item.itemMeta = meta
        return item
    }

    private fun buildFillerItem(): ItemStack {
        val mat = settings.fillerMaterial ?: return buildBorderItem()
        val item = ItemStack(mat)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(" ")
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
        item.itemMeta = meta
        return item
    }

    private fun buildGunItem(player: Player, gun: GunConfig): ItemStack {
        val item = ItemStack(gun.material)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(gun.displayName)

        val lore = mutableListOf<String>()
        lore.addAll(gun.description)
        lore.add("")
        lore.add("${ChatColor.GRAY}价格: ${ChatColor.YELLOW}${gun.price} 硬币")

        val isSelected = gunManager.isSelected(player, gun.key)
        if (isSelected) {
            lore.add("${ChatColor.GREEN}✓ 已选此枪械")
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true)
        } else {
            lore.add("${ChatColor.GRAY}点击选择此枪械")
        }

        meta.lore = lore
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE)
        item.itemMeta = meta
        return item
    }

    private fun buildButtonItem(cfg: GUIButtonConfig, placeholders: Map<String, String>): ItemStack {
        val item = ItemStack(cfg.material)
        val meta = item.itemMeta ?: return item

        var processedName = cfg.displayName
        for ((k, v) in placeholders) {
            processedName = processedName.replace(k, v)
        }
        meta.setDisplayName(processedName)

        val processedLore = cfg.lore.map { line ->
            var result = line
            for ((k, v) in placeholders) {
                result = result.replace(k, v)
            }
            result
        }
        meta.lore = processedLore
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
        item.itemMeta = meta
        return item
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inv = openInventories[player.uniqueId] ?: return
        if (event.inventory != inv) return

        event.isCancelled = true

        val clicked = event.currentItem ?: return
        if (!clicked.hasItemMeta()) return

        val slot = event.slot
        if (slot < 0 || slot >= settings.size) return

        val row = slot / 9
        val col = slot % 9

        if (row == 0 || row == totalRows - 1 || col == 0 || col == 8) {
            handleBottomClick(player, slot, row, col)
            return
        }

        val gunList = gunManager.guns.values.toList()
        val currentPage = playerPages.getOrDefault(player.uniqueId, 0)

        val colsPerRow = 7
        val localRow = row - 1
        val localCol = col - 1
        val localIndex = localRow * colsPerRow + localCol

        val globalIndex = currentPage * pageSize + localIndex
        if (globalIndex < gunList.size) {
            val gun = gunList[globalIndex]
            gunManager.selectGun(player, gun.key)
            refreshInventory(player)
        }
    }

    private fun handleBottomClick(player: Player, slot: Int, row: Int, col: Int) {
        val bottomRow = totalRows - 1
        val currentPage = playerPages.getOrDefault(player.uniqueId, 0)
        val totalPages = maxOf(1, (gunManager.guns.size + pageSize - 1) / pageSize)

        if (row == bottomRow) {
            when (col) {
                0 -> {
                    if (currentPage > 0) {
                        playerPages[player.uniqueId] = currentPage - 1
                        refreshInventory(player)
                    }
                }
                8 -> {
                    if (currentPage < totalPages - 1) {
                        playerPages[player.uniqueId] = currentPage + 1
                        refreshInventory(player)
                    }
                }
                3 -> {
                    gunManager.resetSelection(player)
                    refreshInventory(player)
                }
                5 -> {
                    player.closeInventory()
                }
            }
        }
    }

    private fun refreshInventory(player: Player) {
        val page = playerPages.getOrDefault(player.uniqueId, 0)
        val newInv = buildInventory(player, page)
        openInventories[player.uniqueId] = newInv
        player.openInventory(newInv)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        if (openInventories[player.uniqueId] == event.inventory) {
            openInventories.remove(player.uniqueId)
        }
    }
}
