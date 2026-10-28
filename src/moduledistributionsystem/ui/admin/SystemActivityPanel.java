package moduledistributionsystem.ui.admin;

import moduledistributionsystem.ui.common.CsvUtil;
import moduledistributionsystem.ui.common.UIKit;
import moduledistributionsystem.ui.staff.InventoryPanel;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.model.LogStatus;
import moduledistributionsystem.model.SystemLogEntry;

/** Admin: monitor logs, login sessions and overall system usage. Same page design as the staff pages. */
public class SystemActivityPanel extends JPanel {
    /** The shared data store. */
    private DemoDataStore store;
    /** The admin window. */
    private AdminMainFrame parent;
    /** The shared page skeleton. */
    private final InventoryPanel.Page page;
    private final JPanel tableBody = new JPanel();
    /** Search by user or action. */
    private InventoryPanel.SearchBox searchBox;
    /** Filter by Success / Failed. */
    private InventoryPanel.Dropdown statusFilter;
    private final JLabel countLabel = new JLabel(" ");

    /** Column titles of the log table. */
    private static final String[] LABELS = {"TIMESTAMP", "USER", "ACTION", "IP ADDRESS", "STATUS"};
    /** Minimum width of each column. */
    private static final int[] MINW   = {150, 170, 260, 100, 90};
    /** How the free width is shared between the columns. */
    private static final int[] WEIGHT = {4, 5, 10, 3, 3};
    /** Alignment of each column. */
    private static final int[] ALIGN  = {InventoryPanel.LEFT, InventoryPanel.LEFT, InventoryPanel.LEFT, InventoryPanel.LEFT, InventoryPanel.CENTER};
    /** Rows drawn at once; the export still contains every entry that matches the filters. */
    private static final int MAX_ROWS = 300;

    /** Builds the page: header, stat cards, toolbar (search, filter, export) and table. */
    public SystemActivityPanel(DemoDataStore store, AdminMainFrame parent) {
        this.store = store;
        this.parent = parent;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(InventoryPanel.PAGE_BG);

        page = new InventoryPanel.Page(AdminMainFrame.header(store, "System Activity",
                "Monitor logs, login sessions, and overall system usage", "Audit Trail"), 3);
        add(page.root, BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.setBorder(new EmptyBorder(0, 0, 15, 0));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filters.setOpaque(false);
        searchBox = new InventoryPanel.SearchBox("Search user or action...", 240);
        statusFilter = new InventoryPanel.Dropdown("Status: ");
        Set<String> sts = new LinkedHashSet<>();
        sts.add("All");
        sts.add("Success");
        sts.add("Failed");
        statusFilter.setItems(sts);
        filters.add(searchBox);
        filters.add(Box.createHorizontalStrut(12));
        filters.add(statusFilter);
        toolbar.add(filters, BorderLayout.WEST);
        UIKit.RoundButton exportBtn = InventoryPanel.grayButton("\u2B07 Export Logs");
        exportBtn.addActionListener(e -> exportLogs());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(exportBtn);
        toolbar.add(right, BorderLayout.EAST);
        page.card.add(toolbar, BorderLayout.NORTH);

        tableBody.setLayout(new BoxLayout(tableBody, BoxLayout.Y_AXIS));
        tableBody.setOpaque(false);
        page.card.add(tableBody, BorderLayout.CENTER);

        countLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        countLabel.setForeground(InventoryPanel.SLATE);
        countLabel.setBorder(new EmptyBorder(10, 4, 0, 0));
        page.card.add(countLabel, BorderLayout.SOUTH);

        searchBox.field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        statusFilter.setOnChange(this::refresh);
        refresh();
    }

    /** Builds one log row: time, user, action, address and result. */
    private JComponent logRow(SystemLogEntry log) {
        InventoryPanel.RowPanel row = new InventoryPanel.RowPanel(InventoryPanel.ROW_H, true, MINW, WEIGHT, ALIGN, 2);
        row.add(InventoryPanel.cellLabel(log.getTimestamp().format(DateTimeFormatter.ofPattern("MMM d, yyyy - h:mm a", Locale.ENGLISH)), false, InventoryPanel.SEMESTER_TXT));
        row.add(InventoryPanel.cellLabel(log.getUser(), true, InventoryPanel.INK));
        JLabel action = InventoryPanel.cellLabel(log.getAction(), false, InventoryPanel.INK);
        action.setToolTipText(log.getAction());
        row.add(action);
        row.add(InventoryPanel.cellLabel(log.getIpAddress(), false, InventoryPanel.SLATE));
        row.add(log.getStatus() == LogStatus.SUCCESS
                ? new InventoryPanel.StatusBadge("Success", new Color(236, 253, 245), new Color(6, 95, 70), new Color(16, 185, 129))
                : new InventoryPanel.StatusBadge("Failed", new Color(254, 226, 226), new Color(153, 27, 27), new Color(239, 68, 68)));
        return row;
    }

    /** Saves the logs that match the current filters to a CSV file the user chooses. */
    private void exportLogs() {
        List<SystemLogEntry> filtered = filteredLogs();
        if (filtered.isEmpty()) {
            JOptionPane.showMessageDialog(this, "There are no entries to export with the current filters.", "Export Logs", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("system_activity_log.csv"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;
        try (PrintWriter w = new PrintWriter(new FileWriter(chooser.getSelectedFile()))) {
            w.println("Timestamp,User,Action,IP Address,Status");
            for (SystemLogEntry log : filtered) {
                w.println(String.join(",",
                        csv(log.getTimestamp().format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))),
                        csv(log.getUser()), csv(log.getAction()), csv(log.getIpAddress()), csv(log.getStatus().toString())));
            }
            JOptionPane.showMessageDialog(this, "Exported " + filtered.size() + " entries to " + chooser.getSelectedFile().getName(), "Export Logs", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "The file could not be saved. Choose another folder, or close the file if it is open in Excel.", "Export Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Makes one value safe for a CSV file (quotes it, and stops spreadsheet formulas from running). */
    private String csv(String s) {
        return CsvUtil.cell(s);   // shared helper: quotes the value and stops spreadsheet formulas from running
    }

    /** The log rows that match the search text and the Success / Failed filter. */
    private List<SystemLogEntry> filteredLogs() {
        String query = searchBox.field.getText().trim().toLowerCase();
        String st = statusFilter.getSelected();
        List<SystemLogEntry> out = new java.util.ArrayList<>();
        for (SystemLogEntry log : store.getLogs()) {
            if ("Success".equals(st) && log.getStatus() != LogStatus.SUCCESS) continue;
            if ("Failed".equals(st) && log.getStatus() != LogStatus.FAILED) continue;
            if (!query.isEmpty() && !(log.getUser().toLowerCase().contains(query) || log.getAction().toLowerCase().contains(query))) continue;
            out.add(log);
        }
        return out;
    }

    /** Re-reads the log and rebuilds the stat cards and the table. */
    public void refresh() {
        List<SystemLogEntry> all = store.getLogs();
        LocalDate today = LocalDate.now();
        long loginsToday = all.stream().filter(l -> l.getStatus() == LogStatus.SUCCESS
                && l.getAction().toLowerCase().contains("login") && l.getTimestamp().toLocalDate().equals(today)).count();
        long failedToday = all.stream().filter(l -> l.getStatus() == LogStatus.FAILED && l.getTimestamp().toLocalDate().equals(today)).count();
        // There is no real concurrent-session tracking in a desktop app -- shown as the distinct users who logged in today.
        long activeSessions = all.stream().filter(l -> l.getStatus() == LogStatus.SUCCESS
                && l.getAction().toLowerCase().contains("login") && l.getTimestamp().toLocalDate().equals(today))
                .map(SystemLogEntry::getUser).distinct().count();

        page.statsRow.removeAll();
        page.statsRow.add(InventoryPanel.statCard("Total Logins Today", loginsToday + " Logins", InventoryPanel.STAT_TOTAL));
        page.statsRow.add(InventoryPanel.statCard("Users Signed In Today", activeSessions + " Users", InventoryPanel.STAT_READY));
        page.statsRow.add(InventoryPanel.statCard("Failed Events Today", failedToday + " Failed", InventoryPanel.DELETE_RED));
        page.statsRow.revalidate();
        page.statsRow.repaint();

        List<SystemLogEntry> filtered = filteredLogs();
        tableBody.removeAll();
        tableBody.add(InventoryPanel.headerBand(LABELS, MINW, WEIGHT, ALIGN, 2));
        int n = 0;
        for (SystemLogEntry log : filtered) {
            if (n++ >= MAX_ROWS) break;
            tableBody.add(logRow(log));
        }
        if (filtered.isEmpty()) tableBody.add(InventoryPanel.emptyLine("No activity matches your filters."));
        tableBody.revalidate();
        tableBody.repaint();
        countLabel.setText("Showing " + Math.min(filtered.size(), MAX_ROWS) + " of " + filtered.size() + " matching entries ("
                + all.size() + " in total)" + (filtered.size() > MAX_ROWS ? " \u2014 export to get all of them" : ""));
    }
}
