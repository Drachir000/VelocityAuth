package de.drachir000.velocity.auth.data;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.DataSourceConnectionSource;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.DatabaseTableConfig;
import com.j256.ormlite.table.TableUtils;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.drachir000.velocity.auth.VelocityAuthPlugin;
import de.drachir000.velocity.auth.config.DatabaseConfig;
import org.json.JSONObject;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Manages the connection to the database, configuration loading,
 * and initialization of Data Access Objects (DAOs).
 */
public class DatabaseManager {
	
	private final VelocityAuthPlugin plugin;
	private final Path configPath;
	private DatabaseConfig config;
	private ConnectionSource connectionSource;
	private HikariDataSource hikariDataSource;
	
	private ExecutorService dbExecutor;
	
	private int mojangRateLimit = 0;
	
	private void resetMojangRateLimit() {
		this.mojangRateLimit = 0;
	}
	
	private final ScheduledTask rateLimitResetTask;
	
	// DAOs (Data Access Objects)
	private Dao<PlayerAccount, UUID> accountDao;
	
	/**
	 * A thread-safe cache to hold player accounts after they log in.
	 * This prevents the database being hit on every server switch.
	 */
	private final Map<UUID, PlayerAccount> accountCache = new ConcurrentHashMap<>();
	private final Collection<UUID> noAccounts = new CopyOnWriteArrayList<>();
	
	// Column names
	private static final String ACCOUNTS_UUID = "uuid";
	private static final String ACCOUNTS_MC_NAME = "mcName";
	private static final String ACCOUNTS_DISCORD_ID = "discordId";
	private static final String ACCOUNTS_EMAIL = "email";
	private static final String ACCOUNTS_DATE_ACCEPTED = "dateAccepted";
	private static final String ACCOUNTS_LAST_ONLINE = "lastOnline";
	private static final String ACCOUNTS_BANNED_UNTIL = "bannedUntil";
	
	public DatabaseManager(VelocityAuthPlugin plugin) {
		
		this.plugin = plugin;
		this.configPath = plugin.getPluginDirectory().resolve("database.yml");
		
		this.rateLimitResetTask = this.plugin.getServer().getScheduler().buildTask(plugin, this::resetMojangRateLimit).repeat(10, TimeUnit.MINUTES).schedule();
		
	}
	
	/**
	 * Loads the database.yml config, builds the JDBC connection,
	 * and initializes all tables and DAOs.
	 *
	 * @throws Exception if loading, connecting, or table creation fails.
	 */
	public void connect() throws Exception {
		
		try {
			
			this.config = loadConfig();
			
			this.dbExecutor = new ThreadPoolExecutor(
					config.getCore_threads(), // core pool size
					config.getMax_threads(), // maximum pool size
					config.getKeep_alive_time(), TimeUnit.SECONDS, // keep-alive time
					new LinkedBlockingQueue<>(config.getQueue_size()), // bounded queue
					new ThreadFactoryBuilder().setNameFormat("velocityauth-db-%d").build(),
					new ThreadPoolExecutor.CallerRunsPolicy() // backpressure policy
			);
			
			this.connectionSource = buildConnectionSource();
			
			setupTables();
			
		} catch (Exception e) {
			close();
			throw e;
		}
		
		if (this.connectionSource == null || this.accountDao == null) {
			close();
			throw new NullPointerException("Failed to initialize database connection!");
		}
		
		plugin.getServer().getEventManager().register(plugin, this);
		
	}
	
	/**
	 * Loads the database.yml file from the plugin's data folder.
	 * If it doesn't exist, it's copied from the plugin's resources.
	 *
	 * @return The loaded DatabaseConfig object.
	 * @throws IOException if file I/O fails.
	 */
	private DatabaseConfig loadConfig() throws IOException {
		
		if (Files.notExists(configPath)) {
			
			plugin.getLogger().info("Creating default database.yml...");
			
			Files.createDirectories(configPath.getParent());
			
			try (InputStream in = getClass().getResourceAsStream("/database.yml");
			     OutputStream out = Files.newOutputStream(configPath)) {
				
				if (in == null) {
					throw new IOException("Could not find default database.yml in JAR!");
				}
				
				in.transferTo(out);
				
			}
			
		}
		
		// using "safe" constructor for SnakeYAML
		LoaderOptions options = new LoaderOptions();
		Yaml yaml = new Yaml(new Constructor(DatabaseConfig.class, options));
		
		try (InputStream in = Files.newInputStream(configPath)) {
			return yaml.load(in);
		}
		
	}
	
	/**
	 * Builds the JDBC ConnectionSource based on the loaded config.
	 *
	 * @return A configured ConnectionSource.
	 * @throws SQLException if the database URL or driver is invalid.
	 */
	private ConnectionSource buildConnectionSource() throws SQLException {
		
		String driver = config.getDriver().toLowerCase();
		
		if (driver.equals("sqlite")) {
			
			try {
				Class.forName("org.sqlite.JDBC");
			} catch (ClassNotFoundException handled) {
				plugin.getLogger().warn("Failed to load SQLite driver class.");
			}
			
			// Resolve the SQLite file path relative to the data directory
			Path sqlitePath = plugin.getPluginDirectory().resolve(config.getSqlite_file());
			String jdbcUrl = "jdbc:sqlite:" + sqlitePath.toAbsolutePath();
			
			return new JdbcConnectionSource(jdbcUrl);
			
		} else {
			
			HikariConfig hikariConfig = new HikariConfig();
			
			switch (driver) {
				
				case "mysql" -> {
					
					try {
						Class.forName("com.mysql.cj.jdbc.Driver");
					} catch (ClassNotFoundException handled) {
						plugin.getLogger().warn("Failed to load MySQL driver class.");
					}
					
					String jdbcUrl = String.format("jdbc:mysql://%s/%s%s",
							config.getAddress(),
							config.getDatabase(),
							config.getProperties());
					
					hikariConfig.setJdbcUrl(jdbcUrl);
					hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
					
				}
				
				case "postgresql" -> {
					
					try {
						Class.forName("org.postgresql.Driver");
					} catch (ClassNotFoundException handled) {
						plugin.getLogger().warn("Failed to load PostgreSQL driver class.");
					}
					
					String jdbcUrl = String.format("jdbc:postgresql://%s/%s%s",
							config.getAddress(),
							config.getDatabase(),
							config.getProperties());
					
					hikariConfig.setJdbcUrl(jdbcUrl);
					hikariConfig.setDriverClassName("org.postgresql.Driver");
					
				}
				
				default -> throw new SQLException("Invalid database driver specified: " + driver);
				
			}
			
			// Set connection credentials
			hikariConfig.setUsername(config.getUsername());
			hikariConfig.setPassword(config.getPassword());
			
			// Configure HikariCP pool settings
			DatabaseConfig.PoolConfig poolConfig = config.getPool();
			if (poolConfig != null) {
				hikariConfig.setMaximumPoolSize(poolConfig.getMaximum_pool_size());
				hikariConfig.setMinimumIdle(poolConfig.getMinimum_idle());
				hikariConfig.setMaxLifetime(poolConfig.getMax_lifetime());
				hikariConfig.setConnectionTimeout(poolConfig.getConnection_timeout());
				hikariConfig.setIdleTimeout(poolConfig.getIdle_timeout());
			} else {
				// Default values if pool config is not specified
				hikariConfig.setMaximumPoolSize(10);
				hikariConfig.setMinimumIdle(5);
				hikariConfig.setMaxLifetime(1800000); // 30 minutes
				hikariConfig.setConnectionTimeout(30000); // 30 seconds
				hikariConfig.setIdleTimeout(600000); // 10 minutes
			}
			
			// Additional HikariCP optimizations
			hikariConfig.setPoolName("VelocityAuth-HikariPool");
			hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
			hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
			hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
			hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
			
			// Create HikariCP DataSource
			this.hikariDataSource = new HikariDataSource(hikariConfig);
			
			// Wrap HikariDataSource in ORMLite's DataSourceConnectionSource
			return new DataSourceConnectionSource(hikariDataSource, hikariConfig.getJdbcUrl());
			
		}
		
	}
	
	/**
	 * Creates tables if they don't exist and initializes the DAOs.
	 * This method dynamically sets the table names from the config.
	 */
	private void setupTables() throws SQLException, IllegalArgumentException {
		
		// --- PlayerAccount Table ---
		// 1. Create a config for the table
		DatabaseTableConfig<PlayerAccount> accountTableConfig = DatabaseTableConfig.fromClass(connectionSource.getDatabaseType(), PlayerAccount.class);
		
		// 2. Set the table name from our YAML config
		accountTableConfig.setTableName(validateTableName(config.getTableName("accounts")));
		
		// 3. Create the table if it doesn't exist
		try {
			TableUtils.createTableIfNotExists(connectionSource, accountTableConfig);
		} catch (SQLException e) {
			// MySQL will throw an exception for creating the indices if the table already exists
			if (config.getDriver().equalsIgnoreCase("mysql") && !e.getMessage().startsWith("SQL statement failed: CREATE INDEX"))
				throw e;
			plugin.getLogger().warn("SQLException while setting up Tables: {}. Continuing anyways...", e.getMessage());
		}
		
		// 4. Create the DAO using the custom config
		this.accountDao = DaoManager.createDao(connectionSource, accountTableConfig);
		
	}
	
	/**
	 * Validates a table name to ensure it only contains alphanumeric characters or underscores.
	 * Throws {@link IllegalArgumentException} if the table name is invalid.
	 *
	 * @param tableName The table name to validate.
	 * @return The validated table name if it is valid.
	 * @throws IllegalArgumentException if the table name contains invalid characters.
	 */
	private String validateTableName(String tableName) throws IllegalArgumentException {
		if (!tableName.matches("^[a-zA-Z0-9_]+$")) {
			throw new IllegalArgumentException("Invalid table name: " + tableName);
		}
		return tableName;
	}
	
	/**
	 * Handles the login event for a player. This method fetches the associated
	 * {@link PlayerAccount} for the player based on their UUID and loads it into the cache.
	 * If the player's account is unable to be fetched or does not exist, appropriate actions are
	 * taken. Additionally, it ensures the stored Minecraft username matches the
	 * player's current username, updating if necessary.
	 *
	 * @param event The login event containing details about the player joining.
	 */
	@Subscribe
	public void onLogin(LoginEvent event) {
		
		Player player = event.getPlayer();
		UUID uuid = player.getUniqueId();
		
		PlayerAccount account;
		try {
			account = getAccount(uuid).get();
		} catch (InterruptedException | ExecutionException | CancellationException e) {
			plugin.getLogger().error("Failed to get account for player {}({}):", player.getUsername(), uuid, e);
			return;
		}
		
		if (account == null) {
			noAccounts.add(uuid);
			return;
		}
		
		plugin.getServer().getScheduler().buildTask(plugin, () -> {
			
			String username = player.getUsername();
			String mcName = account.getMcName();
			
			if (!mcName.equals(username)) {
				updateMcName(account, username);
			}
			
		}).schedule();
		
	}
	
	/**
	 * Handles the event triggered when a player disconnects from the server.
	 * This method removes the player's UUID from the cache.
	 *
	 * @param event The disconnect event containing the player details, including their UUID.
	 */
	@Subscribe
	public void onDisconnect(DisconnectEvent event) {
		
		Player player = event.getPlayer();
		UUID uuid = player.getUniqueId();
		
		noAccounts.remove(uuid);
		removeAccountFromCache(uuid);
		
	}
	
	/**
	 * Updates the Minecraft name (mcName) associated with a PlayerAccount.
	 * Ensures that name conflicts are appropriately resolved and recursively updates
	 * the previous owner of the conflicting name if necessary.
	 *
	 * @param account The PlayerAccount to be updated with the new Minecraft name.
	 * @param mcName  The new Minecraft name to assign to the provided PlayerAccount.
	 */
	private void updateMcName(PlayerAccount account, String mcName) {
		updateMcName(account, mcName, 0);
	}
	
	/**
	 * Updates the Minecraft name (mcName) associated with a PlayerAccount.
	 * Ensures that name conflicts are appropriately resolved and recursively updates
	 * the previous owner of the conflicting name if necessary.
	 *
	 * @param account The PlayerAccount to be updated with the new Minecraft name.
	 * @param mcName  The new Minecraft name to assign to the provided PlayerAccount.
	 * @param depth   The current recursion depth. Used for error checking. Should be set to 0 when called from outside this method.
	 */
	private void updateMcName(PlayerAccount account, String mcName, int depth) {
		
		if (depth > 10) {
			throw new IllegalStateException("Recursive update depth exceeded! This should be incredibly rare and should be reported to the developer. Aborting update for this player!");
		}
		
		PlayerAccount previousNameHolder = null;
		try {
			previousNameHolder = findAccountByMcName(mcName).get();
		} catch (InterruptedException | ExecutionException | CancellationException e) {
			plugin.getLogger().warn("Failed to get account for player {}:", mcName, e);
		}
		
		if (previousNameHolder != null && !previousNameHolder.getUuid().equals(account.getUuid())) {
			
			String currentNameOfPreviousHolder = null;
			
			try {
				
				plugin.getLogger().info("Checking current name for UUID {} (previous owner of name {}).",
						previousNameHolder.getUuid(), mcName);
				
				currentNameOfPreviousHolder = fetchCurrentNameFromMojang(previousNameHolder.getUuid()).get();
				
			} catch (InterruptedException | ExecutionException | CancellationException e) {
				plugin.getLogger().error("Failed to fetch current name for UUID {}. Aborting recursive update for this player.",
						previousNameHolder.getUuid(), e);
				return;
			}
			
			if (currentNameOfPreviousHolder != null && !currentNameOfPreviousHolder.equalsIgnoreCase(previousNameHolder.getMcName())) {
				
				plugin.getLogger().info("Name conflict: {} is now used by {}. Recursively updating previous owner (UUID: {}) from {} to {}.",
						mcName, account.getUuid(), previousNameHolder.getUuid(), previousNameHolder.getMcName(), currentNameOfPreviousHolder);
				
				updateMcName(previousNameHolder, currentNameOfPreviousHolder, depth + 1);
				
			} else if (currentNameOfPreviousHolder == null) {
				
				// Mojang API failed to find a name (e.g., 204 No Content)
				plugin.getLogger().warn("Could not resolve a name for UUID {}. This account may be deleted. Saving the first 16 characters of their UUID as their Minecraft name.",
						previousNameHolder.getUuid());
				
				updateMcName(previousNameHolder, previousNameHolder.getUuid().toString().substring(0, 16), depth + 1);
				
			} else if (currentNameOfPreviousHolder.equalsIgnoreCase(mcName)) { // The previous holder still has that name, so the new player ('account') cannot take it. This is an error.
				
				plugin.getLogger().error("CANNOT update {} to {}. That name is still correctly owned by UUID {} (this should never happen!).",
						account.getUuid(), mcName, previousNameHolder.getUuid());
				
				return;
				
			}
			
		}
		
		account.setMcName(mcName);
		
	}
	
	/**
	 * Fetches the current Minecraft username for a given UUID from Mojang's API.
	 * This method is async
	 *
	 * @param uuid The player's UUID.
	 * @return A CompletableFuture holding the current username, or null if not found/error.
	 */
	private CompletableFuture<String> fetchCurrentNameFromMojang(UUID uuid) {
		return CompletableFuture.supplyAsync(() -> {
			
			if (mojangRateLimit >= 600) {
				plugin.getLogger().warn("Mojang API rate limit exceeded. Aborting fetch for UUID {}.", uuid);
				return null;
			}
			
			// Mojang's API requires the UUID without dashes
			String urlString = "https://sessionserver.mojang.com/session/minecraft/profile/"
					+ uuid.toString().replace("-", "");
			
			try {
				
				URL url = new URL(urlString);
				
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("GET");
				connection.setConnectTimeout(5000); // 5 seconds
				connection.setReadTimeout(5000);
				
				int responseCode = connection.getResponseCode();
				
				if (responseCode == HttpURLConnection.HTTP_OK) { // 200
					
					JSONObject json = new JSONObject(new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
					
					if (json.has("name")) {
						return json.getString("name");
					} else {
						plugin.getLogger().warn("Mojang API response for {} did not contain 'name' field.", uuid);
						return null;
					}
					
				} else if (responseCode == 204) { // 204 No Content
					plugin.getLogger().warn("Mojang API returned 204 (No Content) for UUID {}. No profile associated?", uuid);
					return null;
				} else {
					plugin.getLogger().error("Mojang API request for {} failed with code: {}", uuid, responseCode);
					return null;
				}
				
			} catch (Exception e) {
				plugin.getLogger().error("Failed to fetch or parse profile for UUID {}:", uuid, e);
				return null;
			}
			
		});
	}
	
	/**
	 * Synchronously adds a loaded PlayerAccount to the cache.
	 *
	 * @param uuid    The player's UUID.
	 * @param account The PlayerAccount object.
	 */
	private void addAccountToCache(UUID uuid, @Nonnull PlayerAccount account) {
		this.noAccounts.remove(uuid);
		this.accountCache.put(uuid, account);
	}
	
	/**
	 * Synchronously retrieves a PlayerAccount from the cache.
	 *
	 * @param uuid The player's UUID.
	 * @return The cached PlayerAccount, or null if not in the cache.
	 */
	private PlayerAccount getAccountFromCache(UUID uuid) {
		return this.accountCache.get(uuid);
	}
	
	/**
	 * Synchronously removes a PlayerAccount from the cache (e.g., on disconnect).
	 * Saves the PlayerAccount if it has changed since the last save.
	 *
	 * @param uuid The player's UUID.
	 * @return The PlayerAccount that was removed, or null.
	 */
	private PlayerAccount removeAccountFromCache(UUID uuid) {
		
		if (this.accountCache.containsKey(uuid) && this.accountCache.get(uuid).isModified()) {
			saveAccount(this.accountCache.get(uuid));
		}
		
		return this.accountCache.remove(uuid);
		
	}
	
	/**
	 * Closes the database connection source.
	 */
	public void close() {
		
		plugin.getLogger().info("Unregistering event listener for database connection close...");
		plugin.getServer().getEventManager().unregisterListener(plugin, this);
		
		plugin.getLogger().info("Saving cached accounts to database (this may take a moment)...");
		new ArrayList<>(accountCache.keySet()).forEach(this::removeAccountFromCache);
		
		if (this.connectionSource != null) {
			plugin.getLogger().info("Closing database connection...");
			try {
				this.connectionSource.close();
			} catch (Exception e) {
				plugin.getLogger().error("Failed to close database connection:", e);
			}
		}
		
		if (this.hikariDataSource != null && !this.hikariDataSource.isClosed()) {
			plugin.getLogger().info("Closing database pool...");
			this.hikariDataSource.close();
		}
		
		if (this.dbExecutor != null && !this.dbExecutor.isShutdown()) {
			plugin.getLogger().info("Closing database executor...");
			dbExecutor.close(); // Note for Claude Code Review: this method does exist. This method usually calls shutdown and has it's own InterruptedException handling. I am using Java 21 (21 > 19).
		}
		
		if (rateLimitResetTask != null) {
			plugin.getLogger().info("Cancelling rate limit reset task...");
			rateLimitResetTask.cancel();
		}
		
	}
	
	// --- Public Data Access Methods ---
	// All database calls should be ASYNCHRONOUS to not lag the proxy.
	
	/**
	 * Fetches a player account by their UUID.
	 * Uses a cache to speed up lookups.
	 *
	 * @param uuid The player's UUID.
	 * @return A {@link CompletableFuture} containing the PlayerAccount, or null if not found.
	 */
	public CompletableFuture<PlayerAccount> getAccount(@Nonnull UUID uuid) {
		return CompletableFuture.supplyAsync(() -> {
			
			if (noAccounts.contains(uuid)) {
				return null;
			}
			
			if (accountCache.containsKey(uuid)) {
				return getAccountFromCache(uuid);
			}
			
			try {
				
				PlayerAccount account = accountDao.queryForId(uuid);
				addAccountToCache(uuid, account);
				account.setModified(false);
				
				return account;
				
			} catch (SQLException e) {
				plugin.getLogger().error("Failed to query account by UUID:", e);
				return null;
			}
			
		}, dbExecutor);
	}
	
	/**
	 * Fetches a player account by their Minecraft Name.
	 *
	 * @param mcName The user's Minecraft Name (case-sensitive).
	 * @return A {@link CompletableFuture} containing the PlayerAccount, or null if not found.
	 */
	public CompletableFuture<PlayerAccount> findAccountByMcName(@Nonnull String mcName) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				
				// OrmLite query builder for "WHERE mcName = ?"
				PlayerAccount account = accountDao.queryBuilder().where().eq(ACCOUNTS_MC_NAME, mcName).queryForFirst();
				
				if (account == null) {
					
					for (PlayerAccount cachedAccount : accountCache.values()) {
						if (cachedAccount.getMcName().equalsIgnoreCase(mcName)) {
							return cachedAccount;
						}
					}
					
					return null;
					
				}
				
				if (accountCache.containsKey(account.getUuid())) {
					return getAccountFromCache(account.getUuid());
				} else {
					
					addAccountToCache(account.getUuid(), account);
					
					return account;
					
				}
				
			} catch (SQLException e) {
				plugin.getLogger().error("Failed to query account by Minecraft Name:", e);
				return null;
			}
		}, dbExecutor);
	}
	
	/**
	 * Fetches a player account by their Discord ID.
	 *
	 * @param discordId The user's Discord ID.
	 * @return A {@link CompletableFuture} containing the PlayerAccount, or null if not found.
	 */
	public CompletableFuture<PlayerAccount> findAccountByDiscordId(@Nonnull String discordId) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				
				// OrmLite query builder for "WHERE discordId = ?"
				PlayerAccount account = accountDao.queryBuilder().where().eq(ACCOUNTS_DISCORD_ID, discordId).queryForFirst();
				
				if (account == null) {
					
					for (PlayerAccount cachedAccount : accountCache.values()) {
						if (cachedAccount.getDiscordId().equalsIgnoreCase(discordId)) {
							return cachedAccount;
						}
					}
					
					return null;
					
				}
				
				if (accountCache.containsKey(account.getUuid())) {
					return getAccountFromCache(account.getUuid());
				} else {
					
					addAccountToCache(account.getUuid(), account);
					
					return account;
					
				}
				
			} catch (SQLException e) {
				plugin.getLogger().error("Failed to query account by Discord ID:", e);
				return null;
			}
		}, dbExecutor);
	}
	
	/**
	 * Fetches a player account by their Email Address.
	 *
	 * @param email The user's email (case-sensitive).
	 * @return A {@link CompletableFuture} containing the PlayerAccount, or null if not found.
	 */
	public CompletableFuture<PlayerAccount> findAccountByEmail(@Nonnull String email) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				
				// OrmLite query builder for "WHERE email = ?"
				PlayerAccount account = accountDao.queryBuilder().where().eq(ACCOUNTS_EMAIL, email).queryForFirst();
				
				if (account == null) {
					
					for (PlayerAccount cachedAccount : accountCache.values()) {
						if (cachedAccount.getEmail().equalsIgnoreCase(email)) {
							return cachedAccount;
						}
					}
					
					return null;
					
				}
				
				if (accountCache.containsKey(account.getUuid())) {
					return getAccountFromCache(account.getUuid());
				} else {
					
					addAccountToCache(account.getUuid(), account);
					
					return account;
					
				}
				
				
			} catch (SQLException e) {
				plugin.getLogger().error("Failed to query account by email:", e);
				return null;
			}
		}, dbExecutor);
	}
	
	/**
	 * Saves (creates or updates) a player account in the database.
	 *
	 * @param account The account to save.
	 * @return A {@link CompletableFuture} that completes when the operation is done.
	 */
	public CompletableFuture<Void> saveAccount(@Nonnull PlayerAccount account) {
		return CompletableFuture.runAsync(() -> {
			try {
				
				noAccounts.remove(account.getUuid());
				
				accountDao.createOrUpdate(account);
				
				accountCache.put(account.getUuid(), account);
				account.setModified(false);
				
			} catch (SQLException e) {
				plugin.getLogger().error("Failed to save account:", e);
			}
		}, dbExecutor);
	}
	
}
