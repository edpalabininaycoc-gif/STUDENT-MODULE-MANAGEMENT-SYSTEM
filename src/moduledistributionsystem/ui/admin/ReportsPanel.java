package moduledistributionsystem.ui.admin;

import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.data.Report;
import moduledistributionsystem.data.ReportKind;
import moduledistributionsystem.model.Appointment;
import moduledistributionsystem.model.AppointmentStatus;
import moduledistributionsystem.model.LogStatus;
import moduledistributionsystem.ui.common.CsvUtil;
import moduledistributionsystem.ui.common.UIKit;
import moduledistributionsystem.ui.staff.InventoryPanel;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Admin page "Reports": summary reports built from the database with multi-table SQL queries
 * (JOIN and GROUP BY), with a text filter, "Show SQL", "Export CSV" and "Print".
 *
 * <p>The four reports are defined in {@link ReportKind} (label, columns and the exact SQL text); the numbers come
 * from {@link DemoDataStore#runReport}. The page looks like the other portal pages: header, stat cards and one gray
 * card with the toolbar and the table.
 *
 * <p>How to customize: add a constant to ReportKind (plus its case in Database.runReport and
 * DemoDataStore.buildReportInMemory) and it shows up in the "Report" drop-down by itself.
 */
public class ReportsPanel extends JPanel {

    /** The shared data store (runs the reports and keeps the activity log). */
    private final DemoDataStore store;
    /** The admin window. */
    private final AdminMainFrame parent;
    /** The shared page skeleton: header, stat cards and the gray card. */
    private final InventoryPanel.Page page;

    /** Holds the gray column-title band and one row per result line. */
    private final JPanel tableBody = new JPanel();

    /** Chooses which report to run. */
    private InventoryPanel.Dropdown reportChoice;
    /** Filters the rows that are shown (every column is searched). */
    private InventoryPanel.SearchBox searchBox;

    /** Explains what the chosen report shows. */
    private final JLabel descriptionLabel = new JLabel(" ");

    /** "n rows" under the table. */
    private final JLabel rowCountLabel = new JLabel(" ");

    /** The report that was last run (all rows, before the text filter is applied). */
    private Report current;

    /**
     * Builds the page: header, stat cards, toolbar (report, filter, Show SQL, Export CSV, Print) and
     * the table.
     */
    public ReportsPanel(DemoDataStore store, AdminMainFrame parent) {
        this.store = store;
        this.parent = parent;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(InventoryPanel.PAGE_BG);

        page = new InventoryPanel.Page(AdminMainFrame.header(store, "Reports",
                "Summary reports built from the database with JOIN and GROUP BY queries", "Administrative Reports"), 4);
        add(page.root, BorderLayout.CENTER);

        // ---- toolbar: report chooser + filter on the left, the three actions on the right ----
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.setBorder(new EmptyBorder(0, 0, 8, 0));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        reportChoice = new InventoryPanel.Dropdown("Report: ");
        Set<String> labels = new LinkedHashSet<>();
        for (ReportKind k : ReportKind.values()) labels.add(k.label());
        reportChoice.setItems(labels);
        reportChoice.setSelected(ReportKind.values()[0].label());
        searchBox = new InventoryPanel.SearchBox("Filter rows...", 220);
        left.add(reportChoice);
        left.add(Box.createHorizontalStrut(12));
        left.add(searchBox);
        toolbar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        UIKit.RoundButton sqlBtn = InventoryPanel.grayButton("Show SQL");
        sqlBtn.addActionListener(e -> showSql());
        UIKit.RoundButton csvBtn = InventoryPanel.greenButton("Export CSV");
        csvBtn.addActionListener(e -> exportCsv());
        UIKit.RoundButton printBtn = InventoryPanel.darkButton("Print");
        printBtn.addActionListener(e -> printReport());
        right.add(sqlBtn);
        right.add(csvBtn);
        right.add(printBtn);
        toolbar.add(right, BorderLayout.EAST);

        descriptionLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        descriptionLabel.setForeground(InventoryPanel.SLATE);
        descriptionLabel.setBorder(new EmptyBorder(0, 2, 12, 0));

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        toolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(toolbar);
        north.add(descriptionLabel);
        page.card.add(north, BorderLayout.NORTH);

        tableBody.setLayout(new BoxLayout(tableBody, BoxLayout.Y_AXIS));
        tableBody.setOpaque(false);
        page.card.add(tableBody, BorderLayout.CENTER);

        rowCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        rowCountLabel.setForeground(InventoryPanel.SLATE);
        rowCountLabel.setBorder(new EmptyBorder(10, 4, 0, 0));
        page.card.add(rowCountLabel, BorderLayout.SOUTH);

        // Choosing another report runs its query; typing in the filter only re-filters the rows already loaded.
        reportChoice.setOnChange(this::refresh);
        searchBox.field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { showRows(); }
            public void removeUpdate(DocumentEvent e) { showRows(); }
            public void changedUpdate(DocumentEvent e) { showRows(); }
        });
        refresh();
    }

    /** The report chosen in the drop-down. */
    private ReportKind chosenKind() {
        return ReportKind.byLabel(reportChoice.getSelected());
    }

    /** Runs the chosen report's query again, updates the stat cards and shows the rows. */
    public void refresh() {
        List<Appointment> all = store.getAppointments();
        long pending = all.stream().filter(a -> a.getStatus() == AppointmentStatus.PENDING).count();
        long pickedUp = all.stream().filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count();
        long outOfStock = store.getModules().stream().filter(m -> m.isAvailable() && m.getStocks() <= 0).count();

        page.statsRow.removeAll();
        page.statsRow.add(InventoryPanel.statCard("Total Appointments", all.size() + " Appointments", InventoryPanel.STAT_TOTAL));
        page.statsRow.add(InventoryPanel.statCard("Picked Up", pickedUp + " Picked Up", InventoryPanel.STAT_READY));
        page.statsRow.add(InventoryPanel.statCard("Waiting for Approval", pending + " Pending", InventoryPanel.STAT_PENDING));
        page.statsRow.add(InventoryPanel.statCard("Open Modules Out of Stock", outOfStock + " Modules", InventoryPanel.DELETE_RED));
        page.statsRow.revalidate();
        page.statsRow.repaint();

        ReportKind kind = chosenKind();
        descriptionLabel.setText("<html>" + UIKit.escapeHtml(kind.description()) + "</html>");
        current = store.runReport(kind);
        showRows();
    }

    /** The rows of the current report that contain the filter text (every cell is searched). */
    private List<String[]> filteredRows() {
        String q = searchBox.field.getText().trim().toLowerCase(Locale.ROOT);
        List<String[]> out = new ArrayList<>();
        for (String[] row : current.getRows()) {
            if (q.isEmpty()) { out.add(row); continue; }
            for (String cell : row) {
                if (cell != null && cell.toLowerCase(Locale.ROOT).contains(q)) { out.add(row); break; }
            }
        }
        return out;
    }

    /** True for columns that hold numbers (they are centered and narrower). */
    private static boolean isNumberColumn(String title) {
        switch (title) {
            case "In Stock": case "Total": case "Pending": case "Approved": case "Picked Up": case "Declined":
            case "Bookings": case "Different Students": case "Students": case "Appointments":
                return true;
            default:
                return false;
        }
    }

    /** Rebuilds the table from the current report and the filter text. */
    private void showRows() {
        if (current == null) return;
        String[] cols = current.getColumns();
        int n = cols.length;
        int[] minW = new int[n], weight = new int[n], align = new int[n];
        int grow = 0;
        for (int i = 0; i < n; i++) {
            boolean num = isNumberColumn(cols[i]);
            minW[i] = num ? 84 : 120;
            weight[i] = num ? 2 : 5;
            align[i] = num ? InventoryPanel.CENTER : InventoryPanel.LEFT;
            if (cols[i].equals("Subject") || cols[i].equals("Student")) { grow = i; weight[i] = 9; minW[i] = 180; }
        }
        List<String[]> rows = filteredRows();
        tableBody.removeAll();
        String[] heads = new String[n];                       // column titles in capitals, like the other pages
        for (int i = 0; i < n; i++) heads[i] = cols[i].toUpperCase(Locale.ROOT);
        tableBody.add(InventoryPanel.headerBand(heads, minW, weight, align, grow));
        for (String[] row : rows) {
            InventoryPanel.RowPanel line = new InventoryPanel.RowPanel(InventoryPanel.ROW_H, true, minW, weight, align, grow);
            for (int i = 0; i < n; i++) {
                JLabel cell = InventoryPanel.cellLabel(row[i], i == 0, InventoryPanel.INK);
                cell.setToolTipText(row[i]);
                line.add(cell);
            }
            tableBody.add(line);
        }
        if (rows.isEmpty()) tableBody.add(InventoryPanel.emptyLine("No rows to show for this report yet."));
        tableBody.revalidate();
        tableBody.repaint();
        rowCountLabel.setText(rows.size() + " row" + (rows.size() == 1 ? "" : "s")
                + (rows.size() != current.getRows().size() ? " (filtered from " + current.getRows().size() + ")" : "")
                + (current.isFromDatabase() ? "  \u00B7  from the database" : "  \u00B7  computed in memory (demo mode, no database)"));
    }

    // ------------------------------------------------------------------ actions

    /** Shows the exact SQL query behind the chosen report (the "relational JOIN query view"). */
    private void showSql() {
        ReportKind kind = chosenKind();
        JTextArea area = new JTextArea(kind.sql() + (store.isUsingDatabase() ? ""
                : "\n\n-- Demo mode: no database is connected, so the same result is computed in memory."));
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setBorder(new EmptyBorder(8, 10, 8, 10));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(640, 260));
        JOptionPane.showMessageDialog(this, scroll, "SQL: " + kind.label(), JOptionPane.PLAIN_MESSAGE);
    }

    /** Saves the rows currently shown (after the filter) to a CSV file the user chooses. */
    private void exportCsv() {
        List<String[]> rows = filteredRows();
        if (rows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "There are no rows to export.", "Export CSV", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        String safeName = chosenKind().label().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        chooser.setSelectedFile(new File("report_" + safeName + "_" + LocalDate.now() + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) file = new File(file.getPath() + ".csv");
        try {
            CsvUtil.write(file, current.getColumns(), rows);
            store.addLog(adminLabel(), "Exported Report: " + chosenKind().label(), LogStatus.SUCCESS);
            JOptionPane.showMessageDialog(this, rows.size() + " row(s) saved to\n" + file.getAbsolutePath(), "Export CSV", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "The file could not be saved. Choose another folder, or close the file if it is open in Excel.",
                    "Export CSV", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Prints the rows currently shown. A plain table is built for the printer (no sidebar, no buttons - only the
     * title, the table and page numbers), then the normal Print window of the computer opens, where "Microsoft
     * Print to PDF" can be chosen to get a PDF.
     */
    private void printReport() {
        List<String[]> rows = filteredRows();
        if (rows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "There are no rows to print.", "Print", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String[] cols = current.getColumns();
        DefaultTableModel model = new DefaultTableModel(rows.toArray(new String[0][]), cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setFont(new Font("SansSerif", Font.PLAIN, 10));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 10));
        table.setRowHeight(18);
        try {
            table.print(JTable.PrintMode.FIT_WIDTH,
                    new MessageFormat("Student Module Management System - " + chosenKind().label() + " - " + LocalDate.now()),
                    new MessageFormat("Page {0}"));
        } catch (java.awt.print.PrinterException | HeadlessException ex) {
            JOptionPane.showMessageDialog(this, "The report could not be printed on this computer. Check the printer, or use Export CSV instead.",
                    "Print", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** "Name (administrator)" text used in the log lines. */
    private String adminLabel() {
        return (store.getCurrentAdmin() != null ? store.getCurrentAdmin().getName() : "Admin") + " (administrator)";
    }
}
