package moduledistributionsystem.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * A small SMTP client built only on the JDK (sockets + TLS), so no extra .jar has to be added to the
 * NetBeans project.
 *
 * Supports STARTTLS (port 587, e.g. Gmail / Outlook), implicit SSL (port 465) and no encryption (a
 * local test server), with AUTH PLAIN / AUTH LOGIN. The server certificate and host name are always
 * verified when TLS is used.
 *
 * Sending blocks for a moment (network), so call it from a background thread, never the Swing event
 * thread (ForgotPasswordDialog already does this).
 */
public final class SmtpMailer implements MailSender {
    /** How long to wait for the connection to the mail server. */
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    /** How long to wait for each reply from the mail server. */
    private static final int READ_TIMEOUT_MS = 20_000;

    /** The mail settings (host, port, login...). */
    private final MailConfig cfg;

    /** Creates a mailer that uses the given settings. */
    public SmtpMailer(MailConfig cfg) { this.cfg = cfg; }

    /** True when a mail host and a sender address are set. */
    @Override
    public boolean isConfigured() { return cfg.isConfigured(); }

    /**
     * Sends one plain-text e-mail: connect, (optional) STARTTLS, login, MAIL FROM / RCPT TO / DATA,
     * quit.
     *
     * @throws IOException with a readable message when the mail could not be handed to the mail server
     */
    @Override
    public void send(String toAddress, String subject, String body) throws IOException {
        if (!cfg.isConfigured()) {
            throw new IOException("E-mail is not set up yet. Add the smtp.* settings to db.properties (see EMAIL_SETUP.md).");
        }
        String to = clean(toAddress);
        if (!to.contains("@")) throw new IOException("The recipient address \"" + to + "\" is not a valid e-mail address.");

        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(cfg.host, cfg.port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            if ("ssl".equals(cfg.security)) socket = wrapTls(socket);

            Session s = new Session(socket);
            s.expect(220);
            List<String> caps = s.ehlo();
            if ("starttls".equals(cfg.security)) {
                if (!hasCap(caps, "STARTTLS")) {
                    throw new IOException("The mail server does not offer STARTTLS. Try smtp.security=ssl (port 465) or check smtp.port.");
                }
                s.command("STARTTLS", 220);
                socket = wrapTls(socket);
                s = new Session(socket);
                caps = s.ehlo();
            }
            if (!cfg.user.isEmpty()) authenticate(s, caps);

            s.command("MAIL FROM:<" + clean(cfg.from) + ">", 250);
            s.command("RCPT TO:<" + to + ">", 250, 251);
            s.command("DATA", 354);
            s.raw(buildMessage(to, subject, body));
            s.raw("\r\n.\r\n");
            s.expect(250);
            try { s.command("QUIT", 221); } catch (IOException ignored) { /* the mail is already accepted */ }
        } finally {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    // ------------------------------------------------------------------ pieces

    /** Upgrades a plain connection to TLS and checks that the certificate belongs to smtp.host. */
    private Socket wrapTls(Socket plain) throws IOException {
        SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(plain, cfg.host, cfg.port, true);
        SSLParameters params = ssl.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS"); // verify the certificate matches smtp.host
        ssl.setSSLParameters(params);
        ssl.startHandshake();
        return ssl;
    }

    /** Logs in with AUTH PLAIN, or AUTH LOGIN when only that is offered. */
    private void authenticate(Session s, List<String> caps) throws IOException {
        String authLine = "";
        for (String c : caps) if (c.toUpperCase(Locale.ROOT).startsWith("AUTH")) authLine += " " + c.toUpperCase(Locale.ROOT);
        if (authLine.isEmpty()) {
            throw new IOException("The mail server does not allow logging in on this connection"
                    + ("none".equals(cfg.security) ? " (turn on smtp.security=starttls or ssl)." : "."));
        }
        if (authLine.contains("PLAIN")) {
            String token = Base64.getEncoder().encodeToString(("\0" + cfg.user + "\0" + cfg.password).getBytes(StandardCharsets.UTF_8));
            s.command("AUTH PLAIN " + token, 235);
        } else if (authLine.contains("LOGIN")) {
            s.command("AUTH LOGIN", 334);
            s.command(Base64.getEncoder().encodeToString(cfg.user.getBytes(StandardCharsets.UTF_8)), 334);
            s.command(Base64.getEncoder().encodeToString(cfg.password.getBytes(StandardCharsets.UTF_8)), 235);
        } else {
            throw new IOException("The mail server only offers login methods this app does not support:" + authLine);
        }
    }

    /** True when the server listed the given capability (e.g. STARTTLS) in its EHLO reply. */
    private static boolean hasCap(List<String> caps, String name) {
        for (String c : caps) if (c.equalsIgnoreCase(name) || c.toUpperCase(Locale.ROOT).startsWith(name + " ")) return true;
        return false;
    }

    /** Headers + base64 body. Base64 keeps the text 7-bit safe and means a line that starts with "." can never end the message early. */
    private String buildMessage(String to, String subject, String body) {
        StringBuilder m = new StringBuilder();
        String from = clean(cfg.from);
        m.append("From: ").append(encodeWord(cfg.fromName)).append(" <").append(from).append(">\r\n");
        m.append("To: <").append(to).append(">\r\n");
        m.append("Subject: ").append(encodeWord(subject)).append("\r\n");
        m.append("Date: ").append(DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneId.systemDefault()))).append("\r\n");
        String domain = from.contains("@") ? from.substring(from.indexOf('@') + 1) : "localhost";
        m.append("Message-ID: <").append(UUID.randomUUID()).append('@').append(domain).append(">\r\n");
        m.append("MIME-Version: 1.0\r\n");
        m.append("Content-Type: text/plain; charset=UTF-8\r\n");
        m.append("Content-Transfer-Encoding: base64\r\n\r\n");
        String normalized = body.replace("\r\n", "\n").replace("\n", "\r\n");
        m.append(Base64.getMimeEncoder(76, new byte[]{'\r', '\n'}).encodeToString(normalized.getBytes(StandardCharsets.UTF_8)));
        return m.toString();
    }

    /** RFC 2047 encoded-word for non-ASCII header text; plain text otherwise. Never lets a line break into a header. */
    private static String encodeWord(String text) {
        String t = clean(text);
        boolean ascii = true;
        for (int i = 0; i < t.length(); i++) if (t.charAt(i) > 126 || t.charAt(i) < 32) { ascii = false; break; }
        if (ascii) return t;
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(t.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    /** Removes line breaks, so a value can never inject extra mail headers. */
    private static String clean(String s) {
        return s == null ? "" : s.replace("\r", "").replace("\n", "").trim();
    }

    /** One connection's reader/writer with SMTP reply parsing. */
    private static final class Session {
        /** Reads the server's replies. */
        private final BufferedReader in;
        /** Sends our commands. */
        private final OutputStream out;

        Session(Socket socket) throws IOException {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            this.out = socket.getOutputStream();
        }

        void raw(String text) throws IOException {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        String command(String line, int... okCodes) throws IOException {
            raw(line + "\r\n");
            return expect(okCodes);
        }

        /** Reads one (possibly multi-line) reply and checks its code. Returns the full reply text. */
        String expect(int... okCodes) throws IOException {
            StringBuilder all = new StringBuilder();
            int code = -1;
            String line;
            while ((line = in.readLine()) != null) {
                all.append(line).append('\n');
                if (line.length() < 3) continue;
                try { code = Integer.parseInt(line.substring(0, 3)); } catch (NumberFormatException e) { code = -1; }
                if (line.length() == 3 || line.charAt(3) == ' ') break; // last line of the reply
            }
            if (line == null && code < 0) throw new IOException("The mail server closed the connection unexpectedly.");
            for (int ok : okCodes) if (code == ok) return all.toString();
            throw new IOException("Mail server said: " + all.toString().trim());
        }

        List<String> ehlo() throws IOException {
            String reply = command("EHLO localhost", 250);
            List<String> caps = new ArrayList<>();
            boolean first = true;
            for (String line : reply.split("\n")) {
                if (line.length() <= 4) continue;
                if (first) { first = false; continue; } // the greeting line
                caps.add(line.substring(4).trim());
            }
            return caps;
        }
    }
}
