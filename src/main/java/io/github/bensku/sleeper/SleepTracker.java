package io.github.bensku.sleeper;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public class SleepTracker {

	private static final SleepStatus[] STATUS_ARRAY = SleepStatus.values();
	private static final NamespacedKey SLEEP_STATUS = SleeperPlugin.createKey("sleep_status");
	
	public SleepStatus getSleepStatus(Player player) {
		Byte status = player.getPersistentDataContainer().get(SLEEP_STATUS, PersistentDataType.BYTE);
		return status != null ? STATUS_ARRAY[status] : SleepStatus.AWAKE;
	}
	
	public void setSleepStatus(Player player, SleepStatus status) {
		player.getPersistentDataContainer().set(SLEEP_STATUS, PersistentDataType.BYTE, (byte) status.ordinal());
	}
}
