package moduledistributionsystem.ui.staff;

import moduledistributionsystem.ui.common.UIKit;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.model.Appointment;
import moduledistributionsystem.model.AppointmentStatus;
import moduledistributionsystem.model.Module;

/**
 * Staff: approve / decline / hand out student appointment requests.
 *
 * The Status dropdown narrows the list to Pending, Approved, Picked Up or Declined, and the toolbar can also narrow it to one
 * module and re-sort it, so staff can decide which subjects to cater to and release first. Staff cannot
 * archive or delete appointments (only an administrator can).
 */
public class StaffAppointmentsPanel extends JPanel {
    private static final String[] LABELS = {"", "STUDENT NAME", "STUDENT ID", "REQUESTED MODULE", "STOCK", "PREFERRED SCHEDULE", "DATE REQ.", "STATUS"};
    private static final int[] MINW   = {40, 150, 124, 200, 56, 136, 76, 104};
    private static final int[] WEIGHT = {1, 6, 4, 10, 2, 5, 3, 4};
    private static final int[] ALIGN  = {InventoryPanel.CENTER, InventoryPanel.LEFT, InventoryPanel.CENTER, InventoryPanel.LEFT,
            InventoryPanel.CENTER, InventoryPanel.CENTER, InventoryPanel.CENTER, InventoryPanel.CENTER};

    static final String TAB_ALL = "All", TAB_PENDING = "Pending", TAB_APPROVED = "Approved", TAB_PICKED = "Picked Up", TAB_DECLINED = "Declined";
    private static final String SORT_NEWEST = "Newest request", SORT_OLDEST = "Oldest request (first come)",
            SORT_PICKUP = "Pickup time", SORT_MODULE = "Module code";

    private final DemoDataStore store;
    private final StaffMainFrame parent;
    private InventoryPanel.Page page;
    private JPanel tableBody;
    private JPanel actionsHolder;
    private InventoryPanel.Dropdown statusFilter;
    private InventoryPanel.SearchBox searchBox;
    private InventoryPanel.Dropdown moduleFilter;
    private InventoryPanel.Dropdown sortFilter;
    private JCheckBox selectAll;
    private final Map<Appointment, JCheckBox> checks = new LinkedHashMap<>();

    public StaffAppointmentsPanel(DemoDataStore store, StaffMainFrame parent) {
        this.store = store;
        this.parent = parent;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(InventoryPanel.PAGE_BG);

        page = new InventoryPanel.Page(InventoryPanel.buildStaffHeader(store, "Student Appointments",
                "Appointment Control & Scheduling Desk", "Registrar Office Controls"), 4);
        add(page.root, BorderLayout.CENTER);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        JComponent toolbar = buildToolbar();
        toolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(toolbar);
        page.card.add(north, BorderLayout.NORTH);

        tableBody = new JPanel();
        tableBody.setLayout(new BoxLayout(tableBody, BoxLayout.Y_AXIS));
        tableBody.setOpaque(false);
        page.card.add(tableBody, BorderLayout.CENTER);

        searchBox.field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        statusFilter.setOnChange(this::refresh);
        moduleFilter.setOnChange(this::refresh);
        sortFilter.setOnChange(this::refresh);

        refresh();
    }

    // ------------------------------------------------------------------ toolbar

    private JComponent buildToolbar() {
        JPanel toolbar = new JPanel(new WrapLayout());   // filters on the left, buttons on the right; buttons drop below when the window is narrow
        toolbar.setOpaque(false);
        toolbar.setBorder(new EmptyBorder(0, 0, 15, 0));

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filters.setOpaque(false);
        searchBox = new InventoryPanel.SearchBox("Search student or module...", 230);
        statusFilter = new InventoryPanel.Dropdown("Status: ");
        Set<String> statuses = new LinkedHashSet<>();
        statuses.add(TAB_ALL);
        statuses.add(TAB_PENDING);
        statuses.add(TAB_APPROVED);
        statuses.add(TAB_PICKED);
        statuses.add(TAB_DECLINED);
        statusFilter.setItems(statuses);
        moduleFilter = new InventoryPanel.Dropdown("Module: ");
        sortFilter = new InventoryPanel.Dropdown("Sort: ");
        Set<String> sorts = new LinkedHashSet<>();
        sorts.add(SORT_NEWEST);
        sorts.add(SORT_OLDEST);
        sorts.add(SORT_PICKUP);
        sorts.add(SORT_MODULE);
        sortFilter.setItems(sorts);
        sortFilter.setSelected(SORT_OLDEST);
        filters.add(searchBox);
        filters.add(Box.createHorizontalStrut(12));
        filters.add(statusFilter);
        filters.add(Box.createHorizontalStrut(12));
        filters.add(moduleFilter);
        filters.add(Box.createHorizontalStrut(12));
        filters.add(sortFilter);
        toolbar.add(filters);

        actionsHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionsHolder.setOpaque(false);
        toolbar.add(actionsHolder);
        return toolbar;
    }

    /** The buttons depend on the tab: nothing to approve in "Approved", nothing to do at all in "Picked Up" / "Declined". */
    private void rebuildActions(String tab) {
        actionsHolder.removeAll();
        boolean all = TAB_ALL.equals(tab);
        if (all || TAB_PENDING.equals(tab)) {
            UIKit.RoundButton approve = InventoryPanel.greenButton("Approve Selected");
            approve.addActionListener(e -> approveSelected());
            actionsHolder.add(approve);
        }
        if (all || TAB_APPROVED.equals(tab)) {
            UIKit.RoundButton pickedUp = InventoryPanel.darkButton("Mark Picked Up");
            pickedUp.addActionListener(e -> markPickedUp());
            actionsHolder.add(pickedUp);
        }
        if (all || TAB_PENDING.equals(tab) || TAB_APPROVED.equals(tab)) {
            UIKit.RoundButton decline = InventoryPanel.redButton("Decline Selected");
            decline.addActionListener(e -> declineSelected());
            actionsHolder.add(decline);
        }
        actionsHolder.revalidate();
        actionsHolder.repaint();
    }

    // ------------------------------------------------------------------ actions

    private List<Appointment> selected() {
        List<Appointment> out = new ArrayList<>();
        for (Map.Entry<Appointment, JCheckBox> en : checks.entrySet()) if (en.getValue().isSelected()) out.add(en.getKey());
        out.sort(Comparator.comparing(Appointment::getRequestedAt)); // first come, first served
        return out;
    }

    private void nothingSelected() {
        JOptionPane.showMessageDialog(this, "Select at least one appointment first.", "Nothing selected", JOptionPane.WARNING_MESSAGE);
    }

    private void done() {
        if (parent != null) SwingUtilities.invokeLater(parent::refreshAll);
        refresh();
    }

    private static String who(Appointment a) {
        return a.getStudentName() + " \u2014 " + a.getModule().getDisplayCode();
    }

    /** Pending -> Approved. A module with no stock left can't be approved (restock it first). */
    private void approveSelected() {
        List<Appointment> sel = selected();
        if (sel.isEmpty()) { nothingSelected(); return; }
        int ok = 0;
        List<String> noStock = new ArrayList<>(), notPending = new ArrayList<>();
        for (Appointment a : sel) {
            if (a.getStatus() != AppointmentStatus.PENDING) { notPending.add(who(a)); continue; }
            if (a.getModule().getStocks() <= 0) { noStock.add(who(a)); continue; }
            if (store.approveAppointment(a, store.getCurrentStaff())) ok++;
        }
        report("Approve", ok, "approved",
                noStock.isEmpty() ? null : "Out of stock \u2014 restock the module in Module Inventory first:\n  " + String.join("\n  ", noStock),
                notPending.isEmpty() ? null : "Only Pending requests can be approved. Skipped:\n  " + String.join("\n  ", notPending));
    }

    private void declineSelected() {
        List<Appointment> sel = selected();
        if (sel.isEmpty()) { nothingSelected(); return; }
        int n = 0;
        List<String> skipped = new ArrayList<>();
        for (Appointment a : sel) {
            if (store.declineAppointment(a)) n++; else skipped.add(who(a));
        }
        report("Decline", n, "declined", skipped.isEmpty() ? null
                : "Only Pending or Approved requests can be declined (a picked-up module can't be). Skipped:\n  " + String.join("\n  ", skipped), null);
    }

    /**
     * Approved -> Picked Up, one copy off the shelf each. Handled in the order the students asked (first come, first
     * served): when fewer copies are left than students selected, the first ones get them and the rest get an
     * "out of stock" error and stay Approved.
     */
    private void markPickedUp() {
        List<Appointment> sel = selected();
        if (sel.isEmpty()) { nothingSelected(); return; }
        int ok = 0;
        List<String> noStock = new ArrayList<>(), wrongState = new ArrayList<>(), failed = new ArrayList<>();
        for (Appointment a : sel) {
            DemoDataStore.PickupResult r = store.markPickedUp(a);
            switch (r) {
                case OK: ok++; break;
                case OUT_OF_STOCK: noStock.add(who(a)); break;
                case NOT_APPROVED: wrongState.add(who(a)); break;
                default: failed.add(who(a)); break;
            }
        }
        report("Mark Picked Up", ok, "marked as picked up",
                noStock.isEmpty() ? null : "OUT OF STOCK \u2014 no copy is left for:\n  " + String.join("\n  ", noStock)
                        + "\nThey stay Approved. Restock the module, then mark them picked up again.",
                wrongState.isEmpty() ? null : "Only Approved appointments can be marked picked up. Skipped:\n  " + String.join("\n  ", wrongState),
                failed.isEmpty() ? null : "Could not save (database problem):\n  " + String.join("\n  ", failed));
    }

    /** One summary dialog: a short line for what worked, and each kind of problem. Errors use the error icon. */
    private void report(String title, int ok, String verb, String... problems) {
        StringBuilder msg = new StringBuilder();
        boolean problem = false;
        if (ok > 0) msg.append(ok).append(" appointment").append(ok == 1 ? " " : "s ").append(verb).append('.');
        for (String p : problems) {
            if (p == null) continue;
            problem = true;
            if (msg.length() > 0) msg.append("\n\n");
            msg.append(p);
        }
        if (problem) {
            JOptionPane.showMessageDialog(this, msg.toString(), title + (ok == 0 ? " \u2014 nothing changed" : " \u2014 some not done"),
                    ok == 0 ? JOptionPane.ERROR_MESSAGE : JOptionPane.WARNING_MESSAGE);
        }
        done();
    }

    // ------------------------------------------------------------------ table

    private JComponent buildHeaderRow() {
        InventoryPanel.RowPanel band = new InventoryPanel.RowPanel(InventoryPanel.BAND_H, false, MINW, WEIGHT, ALIGN, 3);
        band.setOpaque(true);
        band.setBackground(InventoryPanel.BAND);
        for (int i = 0; i < LABELS.length; i++) {
            if (i == 0) {
                selectAll = new JCheckBox();
                selectAll.setOpaque(false);
                selectAll.setToolTipText("Select all shown");
                selectAll.addActionListener(e -> { for (JCheckBox cb : checks.values()) cb.setSelected(selectAll.isSelected()); });
                band.add(selectAll);
                continue;
            }
            JLabel l = new JLabel(LABELS[i]);
            l.setFont(new Font("SansSerif", Font.BOLD, 10));
            l.setForeground(Color.BLACK);
            band.add(l);
        }
        return band;
    }

    private JComponent buildRow(Appointment a) {
        InventoryPanel.RowPanel row = new InventoryPanel.RowPanel(InventoryPanel.ROW_H, true, MINW, WEIGHT, ALIGN, 3);

        JCheckBox cb = new JCheckBox();
        cb.setOpaque(false);
        checks.put(a, cb);
        row.add(cb);

        row.add(InventoryPanel.cellLabel(a.getStudentName(), true, InventoryPanel.INK));
        row.add(InventoryPanel.cellLabel(a.getStudentId(), false, InventoryPanel.SLATE));
        JLabel mod = InventoryPanel.cellLabel(a.getModule().getDisplayCode() + "  " + a.getModule().getTitle(), false, InventoryPanel.INK);
        mod.setToolTipText(a.getModule().getDisplayCode() + "  " + a.getModule().getTitle());
        row.add(mod);

        int stock = a.getModule().getStocks();
        row.add(InventoryPanel.cellLabel(String.valueOf(stock), stock <= 0, stock <= 0 ? InventoryPanel.DELETE_RED : InventoryPanel.INK));
        row.add(InventoryPanel.cellLabel(a.getDateTime().format(DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH)), false, InventoryPanel.INK));
        row.add(InventoryPanel.cellLabel(a.getRequestedAt().format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)), false, InventoryPanel.SEMESTER_TXT));
        row.add(statusBadge(a.getStatus()));
        return row;
    }

    static InventoryPanel.StatusBadge statusBadge(AppointmentStatus s) {
        switch (s) {
            case CONFIRMED:
                return new InventoryPanel.StatusBadge("Approved", new Color(236, 253, 245), new Color(6, 95, 70), new Color(16, 185, 129));
            case COMPLETED:
                return new InventoryPanel.StatusBadge("Picked Up", UIKit.BLUE_PILL_BG, UIKit.BLUE_PILL_FG, new Color(59, 130, 246));
            case CANCELLED:
                return new InventoryPanel.StatusBadge("Declined", new Color(254, 226, 226), new Color(153, 27, 27), new Color(239, 68, 68));
            case PENDING:
            default:
                return new InventoryPanel.StatusBadge("Pending", new Color(254, 243, 199), new Color(133, 50, 10), new Color(245, 158, 11));
        }
    }

    static String tabOf(AppointmentStatus s) {
        switch (s) {
            case CONFIRMED: return TAB_APPROVED;
            case COMPLETED: return TAB_PICKED;
            case CANCELLED: return TAB_DECLINED;
            default: return TAB_PENDING;
        }
    }

    public void refresh() {
        // Nobody can archive or delete appointments any more, so every appointment is listed.
        List<Appointment> live = new ArrayList<>(store.getAppointments());

        int pending = 0, approved = 0, picked = 0, declined = 0;
        Set<String> modules = new LinkedHashSet<>();
        modules.add("All");
        List<Module> mods = new ArrayList<>(store.getModules());
        mods.sort(Comparator.comparing(Module::getCode));
        for (Module m : mods) modules.add(m.getDisplayCode());
        for (Appointment a : live) {
            switch (a.getStatus()) {
                case PENDING: pending++; break;
                case CONFIRMED: approved++; break;
                case COMPLETED: picked++; break;
                default: declined++; break;
            }
            modules.add(a.getModule().getDisplayCode()); // a removed module's old appointments still filter
        }
        moduleFilter.setItems(modules);

        page.statsRow.removeAll();
        page.statsRow.add(InventoryPanel.statCard("Awaiting Review (Pending)", pending + " Pending", InventoryPanel.STAT_PENDING));
        page.statsRow.add(InventoryPanel.statCard("Approved \u2014 Ready for Pickup", approved + " Approved", InventoryPanel.STAT_READY));
        page.statsRow.add(InventoryPanel.statCard("Picked Up", picked + " Picked Up", InventoryPanel.STAT_TOTAL));
        page.statsRow.add(InventoryPanel.statCard("Declined", declined + " Declined", InventoryPanel.DELETE_RED));
        page.statsRow.revalidate();
        page.statsRow.repaint();

        String tab = statusFilter.getSelected();
        rebuildActions(tab);
        String query = searchBox.field.getText().trim().toLowerCase();
        String modSel = moduleFilter.getSelected();

        List<Appointment> shown = new ArrayList<>();
        for (Appointment a : live) {
            if (!TAB_ALL.equals(tab) && !tab.equals(tabOf(a.getStatus()))) continue;
            if (!"All".equals(modSel) && !modSel.equals(a.getModule().getDisplayCode())) continue;
            if (!query.isEmpty()) {
                String hay = (a.getStudentName() + " " + a.getStudentId() + " " + a.getModule().getCode() + " "
                        + a.getModule().getDisplayCode() + " " + a.getModule().getTitle()).toLowerCase();
                if (!hay.contains(query)) continue;
            }
            shown.add(a);
        }
        switch (sortFilter.getSelected()) {
            case SORT_NEWEST: shown.sort((x, y) -> y.getRequestedAt().compareTo(x.getRequestedAt())); break;
            case SORT_PICKUP: shown.sort(Comparator.comparing(Appointment::getDateTime)); break;
            case SORT_MODULE: shown.sort(Comparator.comparing((Appointment a) -> a.getModule().getCode())
                    .thenComparing(Appointment::getRequestedAt)); break;
            default: shown.sort(Comparator.comparing(Appointment::getRequestedAt)); break;
        }

        checks.clear();
        tableBody.removeAll();
        tableBody.add(buildHeaderRow());
        selectAll.setSelected(false);
        for (Appointment a : shown) tableBody.add(buildRow(a));
        if (shown.isEmpty()) {
            tableBody.add(InventoryPanel.emptyLine(live.isEmpty() ? "No appointment requests yet." : "No appointments match your filters."));
        }
        tableBody.revalidate();
        tableBody.repaint();
    }

    /**
     * Two-child toolbar layout: child 0 (filters) on the left and child 1 (action buttons) on the right
     * when both fit on one line. In a narrower window the buttons move to a second line, right-aligned,
     * instead of being drawn on top of the filters.
     */
    private static class WrapLayout implements LayoutManager {
        private static final int GAP = 12;     // space between the two groups (same line)
        private static final int ROW_GAP = 8;  // space between the two lines (wrapped)
        private boolean wrapped = false;

        @Override public void addLayoutComponent(String name, Component comp) { }
        @Override public void removeLayoutComponent(Component comp) { }

        private boolean needsWrap(Container p, Dimension l, Dimension r) {
            Insets in = p.getInsets();
            int avail = p.getWidth() - in.left - in.right;
            return avail > 0 && l.width + GAP + r.width > avail;
        }

        @Override
        public Dimension preferredLayoutSize(Container p) {
            Insets in = p.getInsets();
            if (p.getComponentCount() < 2) return new Dimension(in.left + in.right, in.top + in.bottom);
            Dimension l = p.getComponent(0).getPreferredSize(), r = p.getComponent(1).getPreferredSize();
            boolean wrap = needsWrap(p, l, r);
            int w = wrap ? Math.max(l.width, r.width) : l.width + GAP + r.width;
            int h = wrap ? l.height + ROW_GAP + r.height : Math.max(l.height, r.height);
            return new Dimension(w + in.left + in.right, h + in.top + in.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container p) {
            Insets in = p.getInsets();
            if (p.getComponentCount() < 2) return new Dimension(in.left + in.right, in.top + in.bottom);
            Dimension l = p.getComponent(0).getPreferredSize(), r = p.getComponent(1).getPreferredSize();
            return new Dimension(Math.max(l.width, r.width) + in.left + in.right,
                    l.height + ROW_GAP + r.height + in.top + in.bottom);
        }

        @Override
        public void layoutContainer(Container p) {
            if (p.getComponentCount() < 2) return;
            Insets in = p.getInsets();
            Component left = p.getComponent(0), right = p.getComponent(1);
            Dimension l = left.getPreferredSize(), r = right.getPreferredSize();
            int avail = p.getWidth() - in.left - in.right;
            boolean wrap = needsWrap(p, l, r);
            if (wrap) {
                left.setBounds(in.left, in.top, Math.min(l.width, avail), l.height);
                int rw = Math.min(r.width, avail);
                right.setBounds(in.left + avail - rw, in.top + l.height + ROW_GAP, rw, r.height);
            } else {
                int h = Math.max(l.height, r.height);
                left.setBounds(in.left, in.top, l.width, h);
                right.setBounds(in.left + avail - r.width, in.top, r.width, h);
            }
            if (wrap != wrapped) {   // number of lines changed -> the parent must re-measure this toolbar's height
                wrapped = wrap;
                SwingUtilities.invokeLater(p::revalidate);
            }
        }
    }
}