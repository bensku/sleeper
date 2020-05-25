package io.github.bensku.sleeper.filter;

import org.bukkit.scheduler.BukkitRunnable;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerAction;

import io.github.bensku.sleeper.SleeperApi;
import io.github.bensku.sleeper.SleeperPlugin;
import io.github.bensku.sleeper.wrappers.WrapperPlayClientEntityAction;

public class LeaveBedPacketFilter {
	
	private final SleeperApi sleeperApi;

	private final ProtocolManager protocolManager;
	private final PacketListener packetListener;

	public LeaveBedPacketFilter(SleeperPlugin plugin, SleeperApi api) {
		this.sleeperApi = api;
		this.protocolManager = ProtocolLibrary.getProtocolManager();
		this.packetListener = new PacketAdapter(plugin, ListenerPriority.HIGHEST, WrapperPlayClientEntityAction.TYPE) {
			@Override
			public void onPacketReceiving(PacketEvent event) {
				LeaveBedPacketFilter.this.onReceivePacket(event);
			}
		};
	}
	
	public void enable() {
		protocolManager.addPacketListener(packetListener);
	}
	
	private void onReceivePacket(PacketEvent event) {
		WrapperPlayClientEntityAction packet = new WrapperPlayClientEntityAction(event.getPacket());
		
		// When player wants to stop sleeping, wake them up
		if (packet.getAction() == PlayerAction.STOP_SLEEPING) {
			// We're not in server thread, not safe to do pretty much anything here
			SleeperPlugin.runTask(new BukkitRunnable() {
				
				@Override
				public void run() {
					// Trying to wake up might fail, e.g. in case of forced sleep; that's ok
					sleeperApi.attemptWakeUp(event.getPlayer());
				}
			});
		}
	}
}
