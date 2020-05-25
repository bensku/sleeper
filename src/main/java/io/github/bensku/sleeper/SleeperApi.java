package io.github.bensku.sleeper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import io.github.bensku.sleeper.filter.MetadataPacketFilter;

public class SleeperApi {

	static SleeperApi instance;
	
	public static SleeperApi getInstance() {
		if (instance == null) {
			throw new IllegalArgumentException("sleeper API is not yet available; check that you depend or soft-depend correctly");
		}
		return instance;
	}
	
	private final SleepTracker sleepTracker;
	private final MetadataPacketFilter metadataFilter;
	
	SleeperApi(SleepTracker sleepTracker, MetadataPacketFilter metadataFilter) {
		this.sleepTracker = sleepTracker;
		this.metadataFilter = metadataFilter;
	}
	
	public SleepStatus getSleepStatus(Player player) {
		return sleepTracker.getSleepStatus(player);
	}
	
	public void setSleepStatus(Player player, SleepStatus status) {
		SleepStatus oldStatus = getSleepStatus(player);
		
		// If status didn't change, do nothing
		if (oldStatus == status) {
			return;
		}
		
		// Call event for other plugins
		SleepStatusChangeEvent event = new SleepStatusChangeEvent(player, oldStatus, status);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			return; // Don't change status if another plugin cancelled
		}
		
		// Store new sleep status
		sleepTracker.setSleepStatus(player, status);
		
		// Force new status to be updated to players
		metadataFilter.forceUpdate(player);
	}
	
	public boolean sleepNaturally(Player player, Location bed) {
		// Guard against waking from forced sleep
		if (getSleepStatus(player) == SleepStatus.FORCED_SLEEP) {
			return false; // Won't transition to natural sleep from forced one
		}
		
		// Teleport player to bed (to keep server state close to what clients are observing)
		player.teleport(bed.add(0, 1, 0), TeleportCause.UNKNOWN);
		
		// Make them sleep there
		setSleepStatus(player, SleepStatus.SLEEPING);
		return true;
	}
	
	public boolean attemptWakeUp(Player player) {
		SleepStatus status = getSleepStatus(player);
		if (status == SleepStatus.AWAKE) {
			return true; // No need to do anything
		} else if (status == SleepStatus.FORCED_SLEEP) {
			return false; // Player cannot wake themself up
		}
		
		// Call event for leaving bed (not cancellable due to Bukkit design)
		// We'll assume that the bed is under the player
		Bukkit.getPluginManager().callEvent(new PlayerBedLeaveEvent(player, player.getLocation().subtract(0, 1, 0).getBlock(), false));
		
		// Wake player up
		setSleepStatus(player, SleepStatus.AWAKE);
		return true;
	}
	
}
