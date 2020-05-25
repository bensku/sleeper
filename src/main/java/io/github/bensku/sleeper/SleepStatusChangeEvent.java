package io.github.bensku.sleeper;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class SleepStatusChangeEvent extends PlayerEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();
	
	private final SleepStatus oldStatus;
	private final SleepStatus newStatus;
	private boolean cancel;
		
	public SleepStatusChangeEvent(Player who, SleepStatus oldStatus, SleepStatus newStatus) {
		super(who);
		this.cancel = false;
		this.oldStatus = oldStatus;
		this.newStatus = newStatus;
	}
	
	public SleepStatus getOldStatus() {
		return oldStatus;
	}
	
	private SleepStatus getNewStatus() {
		return newStatus;
	}

	@Override
	public boolean isCancelled() {
		return cancel;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancel = cancel;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}
	
    public static HandlerList getHandlerList() {
        return handlers;
    }

}
