package ca.wacos.nametagedit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.scheduler.BukkitRunnable;

import ca.wacos.nametagedit.NametagChangeEvent.NametagChangeReason;

/**
 * This class loads all group/player data, and applies the tags during
 * reloads/individually
 * 
 * @author sgtcaze
 */
public class NTEHandler {

	private NametagEdit plugin;

	public NTEHandler(NametagEdit plugin) {
		this.plugin = plugin;
	}

	// Stores all group names in order
	public List<String> allGroups = new ArrayList<>();

	// Corresponds permission to group name
	public HashMap<String, String> permissions = new HashMap<>();

	// Stores all group names to permissions/prefix/suffix
	public HashMap<String, List<String>> groupData = new HashMap<>();

	// Stores all player names to prefix/suffix
	public HashMap<String, List<String>> playerData = new HashMap<>();

	// Reloads files, and reapplies tags
	public void reload(CommandSender sender, boolean fromFile) {
		if (plugin.databaseEnabled) {
			loadFromSQL();
		} else {
			if (fromFile) {
				// We load, then save so active edits aren't lost
				plugin.reloadConfig();
				plugin.getFileUtils().loadYamls();
				loadFromFile();
			} else {
				plugin.saveConfig();
				saveFileData();
				loadFromFile();
			}

			applyTags();
		}

		sender.sendMessage("§f[§6NametagEdit§f] §fSuccessfully reloaded files.");
	}

	// Saves all player and group data
	public void saveFileData() {
		plugin.groups.set("Order", allGroups);

		for (String s : playerData.keySet()) {
			List<String> temp = playerData.get(s);
			plugin.players.set("Players." + s + ".Name", temp.get(0)
					.replaceAll("§", "&"));
			plugin.players.set("Players." + s + ".Prefix", temp.get(1)
					.replaceAll("§", "&"));
			plugin.players.set("Players." + s + ".Suffix", temp.get(2)
					.replaceAll("§", "&"));
		}

		for (String s : groupData.keySet()) {
			List<String> temp = groupData.get(s);
			plugin.groups.set("Groups." + s + ".Permission", temp.get(0)
					.replaceAll("§", "&"));
			plugin.groups.set("Groups." + s + ".Prefix", temp.get(1)
					.replaceAll("§", "&"));
			plugin.groups.set("Groups." + s + ".Suffix", temp.get(2)
					.replaceAll("§", "&"));
		}

		plugin.getFileUtils().savePlayersFile();
		plugin.getFileUtils().saveGroupsFile();
	}

	// Loads all player and group data (file)
	public void loadFromFile() {
		groupData.clear();
		playerData.clear();

		allGroups.clear();

		for (String s : plugin.groups.getStringList("Order")) {
			allGroups.add(s);
		}

		for (String s : allGroups) {
			List<String> tempData = new ArrayList<>();
			String perm = plugin.groups
					.getString("Groups." + s + ".Permission");
			String prefix = plugin.groups.getString("Groups." + s + ".Prefix");
			String suffix = plugin.groups.getString("Groups." + s + ".Suffix");

			tempData.add(perm);
			tempData.add(format(prefix));
			tempData.add(format(suffix));

			groupData.put(s, tempData);
			permissions.put(perm, s);
		}

		for (String s : plugin.players.getConfigurationSection("Players")
				.getKeys(false)) {
			List<String> tempData = new ArrayList<>();
			String name = plugin.players.getString("Players." + s + ".Name");
			String prefix = plugin.players
					.getString("Players." + s + ".Prefix");
			String suffix = plugin.players
					.getString("Players." + s + ".Suffix");

			tempData.add(name);
			tempData.add(format(prefix));
			tempData.add(format(suffix));

			playerData.put(s, tempData);
		}
	}

	// Loads all player and group data (SQL)
	public void loadFromSQL() {
		new BukkitRunnable() {
			@Override
			public void run() {
				final HashMap<String, List<String>> groups = plugin.getMySQL()
						.returnGroups();
				final HashMap<String, List<String>> players = plugin.getMySQL()
						.returnPlayers();

				new BukkitRunnable() {
					@Override
					public void run() {
						groupData.clear();
						playerData.clear();

						groupData = groups;
						playerData = players;

						allGroups.clear();

						for (String s : groups.keySet()) {
							allGroups.add(s);
						}

						plugin.getLogger().info(
								"[MySQL] Everything loaded successfully!");

						applyTags();
					}
				}.runTask(plugin);
			}
		}.runTaskAsynchronously(plugin);
	}

	// Workaround for the deprecated getOnlinePlayers(). @Goblom suggested
	public List<Player> getOnline() {
		List<Player> list = new ArrayList<>();

		for (World world : Bukkit.getWorlds()) {
			list.addAll(world.getPlayers());
		}
		return Collections.unmodifiableList(list);
	}

	// Applies tags to online players (for /reload, and /ne reload)
	public void applyTags() {
		for (Player p : getOnline()) {
			if (p != null) {
				NametagManager.clear(p.getName());

				String uuid = p.getUniqueId().toString();

				if (playerData.containsKey(uuid)) {
					List<String> temp = playerData.get(uuid);
					NametagManager.overlap(p.getName(), temp.get(1),
							temp.get(2));
				} else {
					String permission = "";

					Permission perm = null;

					for (String s : allGroups) {
						List<String> temp = groupData.get(s);
						perm = new Permission(temp.get(0),
								PermissionDefault.FALSE);
						if (p.hasPermission(perm)) {
							permission = temp.get(0);
						}
					}

					String group = permissions.get(permission);
					List<String> temp = groupData.get(group);

					if (temp != null) {
						NametagCommand.setNametagSoft(p.getName(), temp.get(1),
								temp.get(2), NametagChangeReason.GROUP_NODE);
					}

					if (plugin.tabListDisabled) {
						String str = "§f" + p.getName();
						String tab = "";
						for (int t = 0; t < str.length() && t < 16; t++) {
							tab += str.charAt(t);
						}
						p.setPlayerListName(tab);
					}
				}
			}
		}
	}

	// Applies tags to a specific player
	public void applyTagToPlayer(Player p) {
		String uuid = p.getUniqueId().toString();

		NametagManager.clear(p.getName());

		if (playerData.containsKey(uuid)) {
			List<String> temp = playerData.get(uuid);
			NametagManager.overlap(p.getName(), temp.get(1), temp.get(2));
		} else {
			String permission = "";

			Permission perm = null;

			for (String s : allGroups) {
				List<String> temp = groupData.get(s);
				perm = new Permission(temp.get(0), PermissionDefault.FALSE);
				if (p.hasPermission(perm)) {
					permission = temp.get(0);
				}
			}

			String group = permissions.get(permission);

			List<String> temp = groupData.get(group);

			if (temp != null) {
				NametagCommand.setNametagSoft(p.getName(), temp.get(1),
						temp.get(2), NametagChangeReason.GROUP_NODE);
			}
		}

		if (plugin.tabListDisabled) {
			String str = "§f" + p.getName();
			String tab = "";
			for (int t = 0; t < str.length() && t < 16; t++) {
				tab += str.charAt(t);
			}
			p.setPlayerListName(tab);
		}
	}

	private String format(String input) {
		return NametagAPI.trim(ChatColor.translateAlternateColorCodes('&',
				input));
	}
}