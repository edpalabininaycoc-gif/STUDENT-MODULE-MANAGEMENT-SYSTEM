package moduledistributionsystem;

import moduledistributionsystem.data.Database;
import moduledistributionsystem.data.DbConfig;
import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.ui.common.LoginFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * Launcher: run this class to start the Module Distribution System.
 *
 * What happens, in order:
 * 1. Nimbus (a modern Swing look) is switched on.
 * 2. db.properties is read and the app connects to MySQL / MariaDB (XAMPP defaults if the file is
 * missing).
 * 3. If the database can not be reached, the app still starts with in-memory sample data and says so
 * (nothing is saved in that mode).
 * 4. The login window opens. After login, the Student, Staff or Admin main window opens according to
 * the account's role.
 *
 * How to customize: connection settings live in db.properties; the window flow starts in
 * ui/common/LoginFrame.
 */
public class ModuleDistributionSystem {
    /**
     * Program entry point: sets the look, connects to the database, creates the shared DemoDataStore
     * and opens the login window.
     */
    public static void main(String[] args) {
        // Try to set Nimbus for a modern look; fall back to system L&F.
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (UnsupportedLookAndFeelException | ClassNotFoundException | InstantiationException | IllegalAccessException ex) { /* ignore */ }
        }

        SwingUtilities.invokeLater(() -> {
            DemoDataStore store = null;
            String problem = null;
            DbConfig cfg = DbConfig.load();
            if (cfg.enabled) {
                try {
                    Database db = Database.connect(cfg);
                    store = new DemoDataStore(db);
                    Runtime.getRuntime().addShutdownHook(new Thread(db::close));
                    System.out.println("Connected to database " + cfg.describe());
                } catch (Exception e) {
                    e.printStackTrace();
                    problem = "Could not connect to the database (" + cfg.describe() + ").";   // technical details were printed to the console above
                }
            }
            if (store == null) store = new DemoDataStore();

            final long[] lastShown = {0};
            store.setDbErrorListener((message, cause) -> {
                System.err.println(message);
                cause.printStackTrace();
                long now = System.currentTimeMillis();
                if (now - lastShown[0] < 5000) return; // don't stack up dialogs when the database is down
                lastShown[0] = now;
                // Users only see a short, friendly sentence. The technical details (the SQL error text) stay in the
                // console, so a crash message is never shown on screen.
                String headline = message.split("\n", 2)[0];
                if (headline.endsWith(":")) headline = headline.substring(0, headline.length() - 1);
                final String friendly = headline + ".\n\nYour change was NOT saved. Please try again. If it keeps happening, check that "
                        + "MySQL is running in the XAMPP Control Panel or tell the administrator.";
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, friendly, "Database problem", JOptionPane.ERROR_MESSAGE));
            });

            new LoginFrame(store).setVisible(true);

            if (problem != null) {
                JOptionPane.showMessageDialog(null,
                        problem + "\n\nThe app is running with sample data kept only in memory, so nothing you do will be saved.\n"
                                + "Start MySQL in the XAMPP Control Panel, check db.properties, then restart the app.",
                        "Running without a database", JOptionPane.WARNING_MESSAGE);
            }
        });
    }
}
