package cn.oneachina.zombieRun.manager

import org.bukkit.entity.Player

class WeaponHelper {

    companion object {
        @Volatile
        private var instance: WeaponHelper? = null

        fun getInstance(): WeaponHelper {
            return instance ?: synchronized(this) {
                instance ?: WeaponHelper().also { instance = it }
            }
        }
    }
}
