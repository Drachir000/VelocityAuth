package de.drachir000.velocity.auth.data;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.DatabaseTableConfig;
import com.j256.ormlite.table.TableUtils;
import de.drachir000.velocity.auth.VelocityAuthPlugin;
import de.drachir000.velocity.auth.config.DatabaseConfig;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages the connection to the database, configuration loading,
 * and initialization of Data Access Objects (DAOs).
 */
public class DatabaseManager {
	
	private final VelocityAuthPlugin plugin;
	private final Path configPath;
	private DatabaseConfig config;
	private ConnectionSource connectionSource;
	
	private final ExecutorService dbExecutor = Executors.newFixedThreadPool(4);
	
	// DAOs (Data Access Objects)
	private Dao<PlayerAccount, UUID> accountDao;
	
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
	}
	
	/**
	 * Loads the database.yml config, builds the JDBC connection,
	 * and initializes all tables and DAOs.
	 *
	 * @throws Exception if loading, connecting, or table creation fails.
	 */
	public void connect() throws Exception {
		
		this.config = loadConfig();
		this.connectionSource = buildConnectionSource();
		
		setupTables();
		
		if (this.connectionSource == null || this.accountDao == null) {
			throw new NullPointerException("Failed to initialize database connection!");
		}
		
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
			
			configPath.getParent().toFile().mkdirs();
			
			try (InputStream in = getClass().getResourceAsStream("/database.yml");
			     OutputStream out = Files.newOutputStream(configPath)) {
				
				if (in == null) {
					throw new IOException("Could not find default database.yml in JAR!");
				}
				
				Files.createDirectories(configPath.getParent());
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
		
		String jdbcUrl;
		String driver = config.getDriver().toLowerCase();
		
		// Load driver class
		switch (driver) {
			
			case "mysql" -> {
				
				try {
					Class.forName("com.mysql.cj.jdbc.Driver");
				} catch (ClassNotFoundException handled) {
					plugin.getLogger().warn("Failed to load MySQL driver class.");
				}
				
				jdbcUrl = String.format("jdbc:mysql://%s/%s%s",
						config.getAddress(),
						config.getDatabase(),
						config.getProperties());
				
			}
			
			case "postgresql" -> {
				
				try {
					Class.forName("org.postgresql.Driver");
				} catch (ClassNotFoundException handled) {
					plugin.getLogger().warn("Failed to load PostgreSQL driver class.");
				}
				
				jdbcUrl = String.format("jdbc:postgresql://%s/%s%s",
						config.getAddress(),
						config.getDatabase(),
						config.getProperties());
				
			}
			
			case "sqlite" -> {
				
				try {
					Class.forName("org.sqlite.JDBC");
				} catch (ClassNotFoundException handled) {
					plugin.getLogger().warn("Failed to load SQLite driver class.");
				}
				
				// Resolve the SQLite file path relative to the data directory
				Path sqlitePath = plugin.getPluginDirectory().resolve(config.getSqlite_file());
				jdbcUrl = "jdbc:sqlite:" + sqlitePath.toAbsolutePath();
				
			}
			
			default -> throw new SQLException("Invalid database driver specified: " + driver);
			
		}
		
		if (driver.equals("sqlite")) {
			return new JdbcConnectionSource(jdbcUrl);
		} else {
			return new JdbcConnectionSource(jdbcUrl, config.getUsername(), config.getPassword());
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
	 * Closes the database connection source.
	 */
	public void close() {
		
		if (this.connectionSource != null) {
			try {
				this.connectionSource.close();
			} catch (Exception e) {
				plugin.getLogger().error("Failed to close database connection:", e);
			}
		}
		
		dbExecutor.close();
		
	}
	
	// --- Public Data Access Methods ---
	// All database calls should be ASYNCHRONOUS to not lag the proxy.
	
	/**
	 * Fetches a player account by their UUID.
	 *
	 * @param uuid The player's UUID.
	 * @return A {@link CompletableFuture} containing the PlayerAccount, or null if not found.
	 */
	public CompletableFuture<PlayerAccount> getAccount(@Nonnull UUID uuid) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return accountDao.queryForId(uuid);
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
				return accountDao.queryBuilder().where().eq(ACCOUNTS_MC_NAME, mcName).queryForFirst();
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
				return accountDao.queryBuilder().where().eq(ACCOUNTS_DISCORD_ID, discordId).queryForFirst();
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
				return accountDao.queryBuilder().where().eq(ACCOUNTS_EMAIL, email).queryForFirst();
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
				accountDao.createOrUpdate(account);
			} catch (SQLException e) {
				plugin.getLogger().error("Failed to save account:", e);
			}
		}, dbExecutor);
	}
	
}
