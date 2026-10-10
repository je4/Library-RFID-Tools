package org.objectspace.rfid.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.commons.configuration2.AbstractConfiguration;
import org.objectspace.rfid.FinnishDataModel;
import org.objectspace.rfid.webservice.WebserviceConfig;
import org.objectspace.rfid.webservice.WebserviceDispatcher;
import org.objectspace.rfid.webservice.WebserviceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.feig.fedm.utility.HexConvert;

/**
 * Encapsulates common RFID tag processing logic including database persistence
 * and REST webservice dispatching.
 */
public class TagScanProcessor implements AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(TagScanProcessor.class);

	private final AbstractConfiguration config;
	private Connection conn;
	private PreparedStatement insertStmt;
	private PreparedStatement selectSigStmt;
	private String dbError;

	private WebserviceConfig webserviceConfig;
	private WebserviceDispatcher webserviceDispatcher;

	public TagScanProcessor(AbstractConfiguration config) {
		this.config = config;
		initDatabase();
		initWebservice();
	}

	private void initDatabase() {
		if (config == null) {
			return;
		}
		if (config.getBoolean("database.active", false)) {
			String driver = config.getString("database.driver");
			String dsn = config.getString("database.dsn");

			if (driver != null && dsn != null) {
				try {
					Class.forName(driver).getDeclaredConstructor().newInstance();
					conn = DriverManager.getConnection(dsn);
					conn.setAutoCommit(true);

					String insertSQL = "REPLACE INTO `rfid`.`inventory` "
							+ "(`uid`, `version`, `usagetype`, `parts`, `partno`, `itemid`, `country`, `isil`, `inventorytime`"
							+ ", `marker`, `sessionname`, `raw`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, ?, ?)";
					insertStmt = conn.prepareStatement(insertSQL);

					String selectSQL = "SELECT signatur FROM code_sig WHERE barcode=?";
					selectSigStmt = conn.prepareStatement(selectSQL);

					logger.info("[Datenbank] Verbunden mit: {}", dsn);
				} catch (Exception e) {
					dbError = e.getMessage();
					logger.error("Database connection failed: {}", dbError, e);
				}
			}
		} else {
			logger.info("[Datenbank] Inaktiv");
		}
	}

	private void initWebservice() {
		this.webserviceConfig = WebserviceConfig.fromConfiguration(config);
		if (webserviceConfig != null && webserviceConfig.isActive()) {
			this.webserviceDispatcher = new WebserviceDispatcher(webserviceConfig);
			int keyLen = (webserviceConfig.getJwtKey() != null) ? webserviceConfig.getJwtKey().length() : 0;
			logger.info("[Webservice] Aktiv: {} {} (JWT-Key-Länge: {} Zeichen)",
					webserviceConfig.getHttpMethod(), webserviceConfig.getTargetUrl(),
					keyLen > 0 ? String.valueOf(keyLen) : "0 (kein Key)");
		} else {
			logger.info("[Webservice] Inaktiv");
		}
	}

	public boolean isDatabaseConfigured() {
		return config != null && config.getBoolean("database.active", false);
	}

	public boolean isDatabaseConnected() {
		return conn != null;
	}

	public String getDatabaseError() {
		return dbError;
	}

	public Connection getConnection() {
		return conn;
	}

	public boolean isWebserviceActive() {
		return webserviceConfig != null && webserviceConfig.isActive();
	}

	public WebserviceConfig getWebserviceConfig() {
		return webserviceConfig;
	}

	public WebserviceDispatcher getWebserviceDispatcher() {
		return webserviceDispatcher;
	}

	/**
	 * Stores the scanned tag metadata in the database if configured.
	 */
	public boolean storeInDatabase(String uid, FinnishDataModel metadata, String marker, String sessionName)
			throws SQLException {
		if (insertStmt == null) {
			return false;
		}
		synchronized (insertStmt) {
			insertStmt.setString(1, uid);
			insertStmt.setInt(2, metadata != null ? metadata.getVersion() : 0);
			insertStmt.setInt(3, metadata != null ? metadata.getTypeOfUsage() : 0);
			insertStmt.setInt(4, metadata != null ? metadata.getPartsInItem() : 0);
			insertStmt.setInt(5, metadata != null ? metadata.getPartNumber() : 0);
			insertStmt.setString(6, metadata != null ? metadata.getPrimaryItemId() : "");
			insertStmt.setString(7, metadata != null ? metadata.getCountryOfOwnerLib() : "");
			insertStmt.setString(8, metadata != null ? metadata.getISIL() : "");
			insertStmt.setString(9, marker != null ? marker : "");
			insertStmt.setString(10, sessionName != null ? sessionName : "");
			insertStmt.setBytes(11, metadata != null ? metadata.getData() : null);

			insertStmt.executeUpdate();
			return true;
		}
	}

	/**
	 * Queries signature for a given primary item ID / barcode.
	 */
	public String lookupSignature(String primaryItemId) throws SQLException {
		if (selectSigStmt == null || primaryItemId == null) {
			return "";
		}
		synchronized (selectSigStmt) {
			selectSigStmt.setString(1, primaryItemId);
			StringBuilder sig = new StringBuilder();
			try (ResultSet rs = selectSigStmt.executeQuery()) {
				while (rs.next()) {
					sig.append(rs.getString(1)).append("    ");
				}
			}
			return sig.toString().trim();
		}
	}

	/**
	 * Dispatches scan event to webservice endpoint if active.
	 */
	public WebserviceResponse dispatchWebservice(String marker, String itemId, String rawHex, String uid,
			FinnishDataModel metadata, String sessionName, String extra) throws Exception {
		if (webserviceDispatcher == null || !webserviceConfig.isActive()) {
			return null;
		}
		if (rawHex == null && metadata != null && metadata.getData() != null) {
			rawHex = HexConvert.toHexString(metadata.getData());
		}
		String targetMarker = (marker != null) ? marker : "";
		String targetItemId = (itemId != null) ? itemId : ((metadata != null && !metadata.isEmpty()) ? metadata.getPrimaryItemId() : "empty");

		return webserviceDispatcher.dispatchScan(
				targetMarker,
				targetMarker,
				targetItemId,
				rawHex,
				uid,
				metadata,
				sessionName,
				extra
		);
	}

	@Override
	public void close() {
		try {
			if (insertStmt != null) {
				insertStmt.close();
				insertStmt = null;
			}
		} catch (SQLException e) {
			logger.warn("Error closing insert statement", e);
		}
		try {
			if (selectSigStmt != null) {
				selectSigStmt.close();
				selectSigStmt = null;
			}
		} catch (SQLException e) {
			logger.warn("Error closing select statement", e);
		}
		try {
			if (conn != null && !conn.isClosed()) {
				conn.close();
				conn = null;
				logger.info("[Datenbank] Verbindung geschlossen");
			}
		} catch (SQLException e) {
			logger.warn("Error closing database connection", e);
		}
	}
}
