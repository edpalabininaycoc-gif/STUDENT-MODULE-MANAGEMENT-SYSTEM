package moduledistributionsystem.data;

import java.util.Properties;

/**
 * E-mail (SMTP) settings for the "Forgot password" message, read from the same db.properties file:
 *
 *   smtp.host=smtp.gmail.com
 *   smtp.port=587
 *   smtp.security=starttls   (starttls | ssl | none)
 *   smtp.user=yourname@gmail.com
 *   smtp.password=your-app-password
 *   smtp.from=yourname@gmail.com
 *   smtp.fromName=Student Module Management System
 *
 * How to customize: edit db.properties, not this class. See EMAIL_SETUP.md for a step-by-step guide.
 */
public final class MailConfig {
    /** Mail server address, e.g. smtp.gmail.com. Empty = e-mail is not set up. */
    public final String host;
    /** Mail server port (587 for STARTTLS, 465 for SSL). */
    public final int port;
    public final String security;   // "starttls", "ssl" or "none"
    /** Login name for the mail server (usually the full e-mail address). */
    public final String user;
    /** Mail password. For Gmail this must be an App Password, not the normal password. */
    public final String password;
    /** Sender address shown on the e-mail. Defaults to the login name. */
    public final String from;
    /** Sender name shown on the e-mail. */
    public final String fromName;

    /** Reads the smtp.* settings from the given properties and fills in sensible defaults. */
    public MailConfig(Properties p) {
        this.host = p.getProperty("smtp.host", "").trim();
        this.security = normalizeSecurity(p.getProperty("smtp.security", "starttls"));
        this.port = parsePort(p.getProperty("smtp.port", ""), "ssl".equals(security) ? 465 : 587);
        this.user = p.getProperty("smtp.user", "").trim();
        this.password = p.getProperty("smtp.password", "");
        String f = p.getProperty("smtp.from", "").trim();
        this.from = f.isEmpty() ? user : f;
        String fn = p.getProperty("smtp.fromName", "").trim();
        this.fromName = fn.isEmpty() ? "Student Module Management System" : fn;
    }

    /** Reads the smtp.* settings from db.properties. */
    public static MailConfig load() { return new MailConfig(DbConfig.readProperties()); }

    /** A host and a sender address are the minimum; user/password are only needed when the server asks for a login. */
    public boolean isConfigured() { return !host.isEmpty() && from.contains("@"); }

    /** Turns whatever was typed for smtp.security into "starttls", "ssl" or "none". */
    private static String normalizeSecurity(String s) {
        String v = s == null ? "" : s.trim().toLowerCase();
        if (v.equals("ssl") || v.equals("tls") || v.equals("smtps")) return "ssl";
        if (v.equals("none") || v.equals("plain") || v.equals("false")) return "none";
        return "starttls";
    }

    /** Turns the port text into a number; falls back to the given default. */
    private static int parsePort(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }
}
