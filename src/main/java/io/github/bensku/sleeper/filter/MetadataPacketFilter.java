package io.github.bensku.sleeper.filter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.Registry;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.WrappedDataWatcherObject;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;

import io.github.bensku.sleeper.SleepStatus;
import io.github.bensku.sleeper.SleepTracker;
import io.github.bensku.sleeper.SleeperPlugin;
import io.github.bensku.sleeper.wrappers.WrapperPlayServerEntityMetadata;

/**
 * Filters entity metadata of players to make them appear sleeping.
 *
 */
public class MetadataPacketFilter {

	private static final int POSE_INDEX = 6;
	
	/**
	 * Bed position index. Changed from 1.14 to 1.15.
	 */
	private static final int BED_POS_INDEX = Bukkit.getVersion().contains("1.14") ? 12 : 13;

	private final SleepTracker sleepTracker;

	private final ProtocolManager protocolManager;
	private final PacketListener packetListener;
	
	private Class<?> blockPosClass;
	private Constructor<?> blockPosConstructor;
	
	private Class<?> poseEnum;
	private Object poseSleeping;

	public MetadataPacketFilter(SleeperPlugin plugin) {
		this.sleepTracker = new SleepTracker();
		this.protocolManager = ProtocolLibrary.getProtocolManager();
		this.packetListener = new PacketAdapter(plugin, ListenerPriority.HIGHEST, WrapperPlayServerEntityMetadata.TYPE) {
			@Override
			public void onPacketSending(PacketEvent event) {
				MetadataPacketFilter.this.onSendPacket(event);
			}
		};
	}
	
	public void enable() {
		protocolManager.addPacketListener(packetListener);
	}

	/**
	 * Forces an entity to be updated to all connected players. Use this after
	 * changing sleep status to show changes.
	 * @param entity Entity to update to players.
	 */
	public void forceUpdate(Entity entity) {
		// Get entity flags (on fire, etc.)
		Byte flags = WrappedDataWatcher.getEntityWatcher(entity).getByte(0);
		if (flags == null) { // If not set, all flags off by default
			flags = 0;
		}

		// Prepare packet that sets flags to their previous values
		// (i.e. doesn't change anything)
		WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata();
		WrappedDataWatcher watcher = new WrappedDataWatcher();
		WrappedDataWatcherObject object = new WrappedDataWatcherObject(0, Registry.get(Byte.class));
		watcher.setObject(object, flags);

		packet.setEntityID(entity.getEntityId());
		packet.setMetadata(watcher.getWatchableObjects());

		// Send to ALL players
		// Sending to only nearby players seems to not be fully reliable
		// e.g. sometimes the sleeping player fails to receive packet
		for (Player observer : Bukkit.getOnlinePlayers()) {
			try {
				protocolManager.sendServerPacket(observer, packet.getHandle());
			} catch (InvocationTargetException e) {
				throw new AssertionError(e);
			}
		}
	}
	
	/**
	 * Late initialization that we can't do before we have access to a
	 * {@link Player}. Mostly deep reflection for NMS access.
	 */
	private void lateInit(Player player) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException {
		Method getHandleMethod = player.getClass().getMethod("getHandle");
		Object nmsPlayer = getHandleMethod.invoke(player);
		
		blockPosClass = Class.forName(nmsPlayer.getClass().getPackage().getName() + ".BlockPosition");
		blockPosConstructor = blockPosClass.getConstructor(int.class, int.class, int.class);
		
		poseEnum = Class.forName(nmsPlayer.getClass().getPackage().getName() + ".EntityPose");
		Method valueOf = poseEnum.getMethod("valueOf", String.class);
		
		poseSleeping = valueOf.invoke(null, "SLEEPING");
	}

	/**
	 * Send packet handler used for injecting desired sleep status to entity
	 * metadata packets.
	 * @param event Packet event.
	 */
	private void onSendPacket(PacketEvent event) {
		// Using PacketWrapper here (no need to look at Minecraft source)
		WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event.getPacket());
		Entity entity = packet.getEntity(event);
		if (!(entity instanceof Player)) {
			return; // Only processing players here
		}
		
		// Now that we have a player, do some late initialization (one time only)
		Player player = (Player) entity;
		if (poseSleeping == null) {
			try {
				lateInit(player);
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | ClassNotFoundException e) {
				throw new AssertionError("late init failure", e);
			}
		}

		SleepStatus status = sleepTracker.getSleepStatus(player);
		WrappedWatchableObject poseObj = null;
		WrappedWatchableObject bedPosObj;
		
		if (status == SleepStatus.AWAKE) {
			// Remove bed position to actually wake up
			bedPosObj = new WrappedWatchableObject(new WrappedDataWatcherObject(BED_POS_INDEX, Registry.getBlockPositionSerializer(true)), Optional.empty());
		} else {
			// Force pose to sleeping, no matter what it might've been before
			poseObj = new WrappedWatchableObject(new WrappedDataWatcherObject(POSE_INDEX, Registry.get(poseEnum)), poseSleeping);
			
			// Set bed position to where player is (no real bed needed)
			Location loc = player.getLocation();
			try {
				bedPosObj = new WrappedWatchableObject(new WrappedDataWatcherObject(BED_POS_INDEX, Registry.getBlockPositionSerializer(true)),
						Optional.of(blockPosConstructor.newInstance(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())));
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				throw new AssertionError(e);
			}
		}
		
		// Replace existing watchables for pose and bed position
		List<WrappedWatchableObject> watchables = packet.getMetadata();
		for (int i = 0; i < watchables.size(); i++) {
			WrappedWatchableObject obj = watchables.get(i);
			if (poseObj != null && obj.getIndex() == POSE_INDEX) {
				watchables.set(i, poseObj);
				poseObj = null;
			} else if (bedPosObj != null && obj.getIndex() == BED_POS_INDEX) {
				watchables.set(i, bedPosObj);
				bedPosObj = null;
			}
		}
		// Or if we didn't replace, add them to end
		if (poseObj != null) {
			watchables.add(poseObj);
		}
		if (bedPosObj != null) {
			watchables.add(bedPosObj);
		}
		
		// Replace watchables in packet with our modified list
		packet.getHandle().getWatchableCollectionModifier().write(0, watchables);
	}
}
