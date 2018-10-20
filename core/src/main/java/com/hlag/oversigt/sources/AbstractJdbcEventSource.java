package com.hlag.oversigt.sources;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hlag.oversigt.core.OversigtEvent;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.core.eventsource.ScheduledEventSource;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.DatabaseConnection;

/**
 * @author Olaf Neumann
 *
 */
public abstract class AbstractJdbcEventSource<T extends OversigtEvent> extends ScheduledEventSource<T> {
	protected static final Logger DB_LOGGER = LoggerFactory.getLogger("db");

	private DatabaseConnection databaseConnection = DatabaseConnection.EMPTY;
	private Credentials credentials = Credentials.EMPTY;

	private Duration databaseQueryInterval = Duration.ofHours(1);

	@Property(name = "Database Connection", description = "The connection details to be used for the SQL connection.")
	public DatabaseConnection getDatabaseConnection() {
		return databaseConnection;
	}

	public void setDatabaseConnection(DatabaseConnection databaseConnection) {
		this.databaseConnection = databaseConnection;
	}

	@Property(name = "Credentials", description = "The credentials to be used to connect to the database.", needsRestart = true)
	public Credentials getCredentials() {
		return credentials;
	}

	public void setCredentials(Credentials credentials) {
		this.credentials = credentials;
	}

	@Property(name = "Query interval", description = "How often should this event source call the database?", needsRestart = false)
	public Duration getDatabaseQueryInterval() {
		return databaseQueryInterval;
	}

	public void setDatabaseQueryInterval(Duration databaseQueryInterval) {
		this.databaseQueryInterval = databaseQueryInterval;
	}

	private Connection wrapConnection(Connection connection) {
		return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { Connection.class },
				new UnpreparedStatementPreventingInvocationHandler(connection));
	}

	private Connection getConnection() {
		if (getDatabaseConnection() != DatabaseConnection.EMPTY) {
			try {
				// Load the driver
				getDatabaseConnection().loadDriverClass();
				getLogger().info("Loaded JDBC driver.");

				// Create the connection using the IBM Data Server Driver for JDBC and SQLJ
				Connection con = DriverManager.getConnection(getDatabaseConnection().getJdbcUrl(),
						getCredentials().getUsername(),
						getCredentials().getPassword());
				// Commit changes manually
				con.setAutoCommit(false);
				con.setReadOnly(true);
				con.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
				getLogger().info("Created JDBC connection to the data source.");
				return wrapConnection(con);
			} catch (ClassNotFoundException e) {
				return failure("Could not load JDBC driver.", e);
			} catch (SQLException e) {
				return failure("Failed connecting to data base.", e);
			}
		} else {
			return failure("Database connection is not configured.");
		}
	}

	private boolean withConnection(DBConnectionConsumer function) throws SQLException {
		Connection connection = null;
		try {
			connection = getConnection();
			if (connection != null) {
				function.apply(connection);
				return true;
			}
		} finally {
			if (connection != null) {
				try {
					connection.rollback();
					connection.close();
				} catch (SQLException e) {
					getLogger().warn("Unable to rollback and close DB connection", e);
				}
			}
		}
		return false;
	}

	private LocalDateTime lastDbAccessDateTime = null;

	@Override
	protected final T produceEvent() {
		if (lastDbAccessDateTime == null
				|| LocalDateTime.now().minus(getDatabaseQueryInterval()).isAfter(lastDbAccessDateTime)) {
			try {
				withConnection(this::gatherDatabaseInfo);
				lastDbAccessDateTime = LocalDateTime.now();
			} catch (SQLException e) {
				return failure("Unable to gather database info", e);
			}
		}
		return produceEventFromData();
	}

	protected abstract void gatherDatabaseInfo(Connection connection) throws SQLException;

	protected abstract T produceEventFromData();

	public static <T> List<T> readFromDatabase(Connection connection,
			ResultSetFunction<T> readOneLine,
			String sql,
			Object... parameters) throws SQLException {

		long time = System.currentTimeMillis();
		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			for (int i = 0; i < parameters.length; ++i) {
				stmt.setObject(i + 1, parameters[i]);
			}
			return readFromDatabase(stmt, readOneLine);
		} catch (SQLException e) {
			DB_LOGGER.error("Query failed", e);
			throw e;
		} finally {
			DB_LOGGER.info("Finished query. Duration: " + (System.currentTimeMillis() - time));
			DB_LOGGER.trace(sql);
		}
	}

	private static <T> List<T> readFromDatabase(PreparedStatement statement, ResultSetFunction<T> readOneLine)
			throws SQLException {
		try (ResultSet rs = statement.executeQuery()) {
			return readFromDatabase(rs, readOneLine);
		}
	}

	private static <T> List<T> readFromDatabase(ResultSet rs, ResultSetFunction<T> readOneLine) throws SQLException {
		List<T> list = new ArrayList<>();
		while (rs.next()) {
			T item = readOneLine.readLine(rs);
			list.add(item);
		}
		return list;
	}

	@FunctionalInterface
	private interface DBConnectionConsumer {
		void apply(Connection connection) throws SQLException;
	}

	@FunctionalInterface
	public interface ResultSetFunction<T> {
		T readLine(ResultSet resultSet) throws SQLException;
	}

	private static class UnpreparedStatementPreventingInvocationHandler implements InvocationHandler {
		private final Connection connection;

		private UnpreparedStatementPreventingInvocationHandler(Connection connection) {
			this.connection = connection;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if (method.getDeclaringClass() == Connection.class && "createStatement".equals(method.getName())) {
				throw new RuntimeException(
						"Oversigt does not allow unprepared statements. Please use #prepareStatement instead.");
			}
			return method.invoke(connection, args);
		}
	}
}
