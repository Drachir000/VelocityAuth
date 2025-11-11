package de.drachir000.velocity.auth.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import de.drachir000.velocity.auth.VelocityAuthPlugin;
import de.drachir000.velocity.auth.config.MainConfig;
import de.drachir000.velocity.auth.data.DatabaseManager;

public class ConnectionListener {
	
	private static ConnectionListener instance = null;
	
	private final VelocityAuthPlugin plugin;
	private final ProxyServer proxy;
	private final MainConfig config;
	private final DatabaseManager databaseManager;
	
	{
		
		this.plugin = VelocityAuthPlugin.getInstance();
		
		this.proxy = plugin.getServer();
		this.config = plugin.getMainConfig();
		this.databaseManager = plugin.getDatabaseManager();
		
		if (instance != null) {
			proxy.getEventManager().unregisterListener(plugin, instance);
		}
		instance = this;
		proxy.getEventManager().register(plugin, this);
		
	}
	
	@Subscribe(priority = -1)
	public void onServerPreConnect(ServerPreConnectEvent event) {
		
		Player player = event.getPlayer();
		
		RegisteredServer targetServer = event.getResult().getServer().orElse(null);
		if (targetServer == null) {
			targetServer = event.getOriginalServer();
		}
		
		// TODO Whitelist logic
		
	}
	
}
