package io.github.bensku.sleeper;

import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import io.github.bensku.sleeper.filter.LeaveBedPacketFilter;
import io.github.bensku.sleeper.filter.MetadataPacketFilter;

public class SleeperPlugin extends JavaPlugin {

	private static SleeperPlugin INSTANCE;
	
	public static NamespacedKey createKey(String key) {
		return new NamespacedKey(INSTANCE, key);
	}
	
	public static void runTask(BukkitRunnable task) {
		task.runTask(INSTANCE);
	}
		
	@Override
	public void onEnable() {
		INSTANCE = this;

		//saveDefaultConfig(); // Copy default config to data folder
		SleepTracker sleepTracker = new SleepTracker();
		MetadataPacketFilter metadataFilter = new MetadataPacketFilter(this);
		SleeperApi.instance = new SleeperApi(sleepTracker, metadataFilter);
		LeaveBedPacketFilter leaveBedFilter = new LeaveBedPacketFilter(this, SleeperApi.getInstance());
		
		metadataFilter.enable();
		leaveBedFilter.enable();
	}
	
	@Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		switch (command.getName()) {
		case "sleep":
			Player target = getServer().getPlayer(args[0]);
			SleepStatus current = SleeperApi.getInstance().getSleepStatus(target);
			SleeperApi.getInstance().setSleepStatus(target, current == SleepStatus.SLEEPING ? SleepStatus.AWAKE : SleepStatus.SLEEPING);
		}
        return true;
    }

}
