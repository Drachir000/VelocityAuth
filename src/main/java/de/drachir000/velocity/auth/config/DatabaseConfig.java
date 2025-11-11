package de.drachir000.velocity.auth.config;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Represents the structure of the database.yml file.
 * SnakeYAML will automatically populate this object.
 * Note: Field names MUST match the YAML keys.
 */
@Setter
@SuppressWarnings("unused") // Fields are populated by SnakeYAML
public class DatabaseConfig {
	
	@Getter
	private String driver;
	private Map<String, String> tables;
	@Getter
	private String address;
	@Getter
	private String database;
	@Getter
	private String username;
	@Getter
	private String password;
	private String properties;
	@Getter
	private String sqlite_file;
	
	@Getter
	private PoolConfig pool;
	
	/**
	 * Retrieves the mapped table name for the given key from the tables map.
	 * If no mapping is found, the method returns the key itself.
	 *
	 * @param key the key representing the logical name of the table
	 * @return the corresponding table name if a mapping exists, otherwise the original key
	 */
	public String getTableName(String key) {
		return tables.getOrDefault(key, key);
	}
	
	/**
	 * Retrieves the properties string.
	 * If the properties field is null, an empty string is returned.
	 *
	 * @return the properties string, or an empty string if it is null
	 */
	public String getProperties() {
		return properties != null ? properties : "";
	}
	
	/**
	 * Nested configuration class for HikariCP connection pool settings.
	 */
	@Getter
	@Setter
	@SuppressWarnings("unused") // Fields are populated by SnakeYAML
	public static class PoolConfig {
		private int maximum_pool_size;
		private int minimum_idle;
		private long max_lifetime;
		private long connection_timeout;
		private long idle_timeout;
	}
	
}
