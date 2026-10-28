package moduledistributionsystem.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Database settings, read from db.properties (next to the project / the jar).
 *
 * Defaults match a fresh XAMPP install: MySQL on localhost:3306, user "root", empty password. The same
 * file also holds the e-mail (SMTP) settings - see MailConfig.
 *
 * How to customize: edit db.properties, not this class. Useful keys: db.enabled (false = run with in-
 * memory demo data only), db.type (mysql or mariadb), db.host, db.port, db.name, db.user, db.password.
 */
public final class DbConfig {
    /** False means "do not use a database": the app runs with in-memory demo data and nothing is saved. */
    public final boolean enabled;
    /** "mysql" or "mariadb": decides which JDBC driver and URL style is used. */
    public final String type;        // "mysql" or "mariadb" (decides the JDBC driver and URL scheme)
    /** Database server address, usually "localhost". */
    public final String host;
    /** Database server port, 3306 by default. */
    public final int port;
    /** Database name. It is created automatically when it does not exist yet. */
    public final String name;        // database name (created automatically if missing)
    /** Database user. XAMPP's default is "root". */
    public final String user;
    /** Database password. XAMPP's default is empty. */
    public final String password;
    /** Optional: the driver class name, only needed for unusual drivers. */
    public final String driverClass; // optional override
    /**
     * Optional: extra folder in which to look for the driver .jar (default: a "lib" folder next to
     * db.properties).
     */
    public final String libDir;      // optional extra folder to look for the driver .jar

    /** Reads every setting from the loaded properties, using the XAMPP defaults for missing keys. */
    private DbConfig(Properties p) {
        this.enabled = Boolean.parseBoolean(p.getProperty("db.enabled", "true").trim());
        this.type = p.getProperty("db.type", "mysql").trim().toLowerCase();
        this.host = p.getProperty("db.host", "localhost").trim();
        this.port = parsePort(p.getProperty("db.port", "3306"));
        this.name = p.getProperty("db.name", "module_distribution").trim();
        this.user = p.getProperty("db.user", "root").trim();
        this.password = p.getProperty("db.password", "");
        String dc = p.getProperty("db.driver", "").trim();
        this.driverClass = dc.isEmpty() ? null : dc;
        String ld = p.getProperty("db.libDir", "").trim();
        this.libDir = ld.isEmpty() ? null : ld;
    }

    /** Turns the port text into a number; falls back to 3306 when it is not a number. */
    private static int parsePort(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 3306; }
    }

    /**
     * Loads db.properties from the working folder (NetBeans: the project folder), else from next to the
     * app jar or the folder above it. -Dmds.db.config=path overrides. Falls back to the defaults.
     */
    public static DbConfig load() {
        return new DbConfig(readProperties());
    }

    /** The raw contents of db.properties (empty when the file is missing). Also used for the e-mail (SMTP) settings. */
    public static Properties readProperties() {
        Properties p = new Properties();
        List<File> candidates = new ArrayList<>();
        String custom = System.getProperty("mds.db.config");
        if (custom != null) {
            candidates.add(new File(custom));
        } else {
            candidates.add(new File("db.properties"));
            try {
                File here = new File(DbConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                File base = here.isFile() ? here.getParentFile() : here;
                if (base != null) {
                    candidates.add(new File(base, "db.properties"));
                    if (base.getParentFile() != null) candidates.add(new File(base.getParentFile(), "db.properties"));
                }
            } catch (Exception ignored) { }
        }
        for (File f : candidates) {
            if (!f.isFile()) continue;
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
                System.out.println("Settings read from " + f.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("Could not read " + f.getAbsolutePath() + ": " + e.getMessage() + " (using defaults)");
            }
            break;
        }
        return p;
    }

    /** The start of the JDBC address for the chosen database type. */
    private String scheme() { return "mariadb".equals(type) ? "jdbc:mariadb://" : "jdbc:mysql://"; }

    /** Extra connection options (timeouts, character set) appended to the JDBC address. */
    private String params() {
        return "mariadb".equals(type) ? "?connectTimeout=5000"
                : "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&connectTimeout=5000";
    }

    /** URL of the server itself (no database selected), used to create the database on first run. */
    public String serverUrl() { return scheme() + host + ":" + port + "/" + params(); }

    /** JDBC address of the application database itself. */
    public String databaseUrl() { return scheme() + host + ":" + port + "/" + name + params(); }

    /** Short text for messages, e.g. "localhost:3306 / module_distribution (user root)". */
    public String describe() { return host + ":" + port + " / " + name + " (user " + user + ")"; }
}
