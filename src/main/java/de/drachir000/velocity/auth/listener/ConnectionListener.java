package de.drachir000.velocity.auth.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import de.drachir000.velocity.auth.VelocityAuthPlugin;
import de.drachir000.velocity.auth.config.MainConfig;
import de.drachir000.velocity.auth.data.DatabaseManager;
import de.drachir000.velocity.auth.data.PlayerAccount;

import javax.annotation.Nullable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

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
	
	public static void initialize() {
		new ConnectionListener();
	}
	
	@Subscribe(priority = -1)
	public void onServerPreConnect(ServerPreConnectEvent event) {
		
		Player player = event.getPlayer();
		
		RegisteredServer targetServer = event.getResult().getServer().orElse(null);
		if (targetServer == null) {
			targetServer = event.getOriginalServer();
		}
		
		PlayerAccount account;
		try {
			account = databaseManager.getAccount(player.getUniqueId()).get();
		} catch (InterruptedException | ExecutionException | CancellationException e) {
			plugin.getLogger().error("Failed to get account for player {} ({}):", player.getUsername(), player.getUniqueId(), e);
			event.setResult(ServerPreConnectEvent.ServerResult.denied());
			return;
		}
		
		boolean allowed = account != null || config.isServerPublic(targetServer.getServerInfo().getName());
		
		if (!allowed) {
			
			RegisteredServer authServer = getAuthServer();
			
			if (authServer != null) {
				event.setResult(ServerPreConnectEvent.ServerResult.allowed(authServer));
			} else {
				event.setResult(ServerPreConnectEvent.ServerResult.denied());
			}
			
		}
		
	}
	
	/**
	 * Retrieves the authentication server as a registered server if it is configured and available.
	 *
	 * @return the registered authentication server if configured and found, or {@code null} otherwise
	 */
	private @Nullable RegisteredServer getAuthServer() {
		if (config.getAuth_server() == null) return null;
		return proxy.getServer(config.getAuth_server()).orElse(null);
	}
	
}
