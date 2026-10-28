package moduledistributionsystem.ui.staff;

import moduledistributionsystem.ui.common.UIKit;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicTextFieldUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.model.Module;
import moduledistributionsystem.model.ModuleStatus;
import moduledistributionsystem.model.Staff;
import moduledistributionsystem.model.YearLevel;

/**
 * Staff page "Module Inventory": a table of every module with its stock, per-slot student limit and
 * status, plus search, Semester / Department filters, and Add / Edit / Delete.
 *
 * This file ALSO holds the page building blocks shared by the Student and Admin portals, so the whole
 * app looks the same: Page (header + stat cards + one gray card), RowPanel (table row / header band),
 * StatusBadge, SearchBox, Dropdown, TabBar, IconButton, statCard(), buildHeader() and the toolbar
 * button helpers (grayButton, darkButton, greenButton, redButton, rowButton).
 *
 * How to customize:
 * - colors: the constants at the top of this class;
 * - table columns: LABELS / MINW / WEIGHT / ALIGN and buildRow();
 * - the per-slot limit and stock fields: ModuleEditDialog at the bottom.
 */
public class InventoryPanel extends JPanel {
    // ---------- Palette (sampled from the Figma frame) ----------
    public static final Color PAGE_BG      = new Color(248, 250, 252);
    public static final Color CARD         = new Color(221, 223, 224);
    public static final Color BAND         = new Color(192, 194, 195); // table header band + Add button
    public static final Color ROW_LINE     = new Color(225, 229, 237);
    public static final Color HEADER_LINE  = new Color(236, 240, 246);
    public static final Color INK          = new Color(15, 23, 42);
    public static final Color SLATE        = new Color(71, 85, 105);
    private static final Color CRUMB        = new Color(22, 40, 136);
    private static final Color STAT_LABEL   = new Color(20, 38, 78);
    public static final Color STAT_TOTAL   = new Color(20, 40, 135);
    public static final Color STAT_READY   = new Color(10, 184, 120);
    public static final Color STAT_PENDING = new Color(250, 140, 0);
    private static final Color STAT_RELEASE = new Color(24, 50, 80);
    public static final Color SEMESTER_TXT = new Color(20, 40, 80);
    private static final Color INPUT_BORDER = new Color(50, 50, 50);
    private static final Color INPUT_TEXT   = new Color(30, 55, 90);
    private static final Color ICON_SLATE   = new Color(120, 140, 170);
    private static final Color EDIT_BLUE    = new Color(30, 100, 245);
    public static final Color DELETE_RED   = new Color(239, 50, 50);

    /** Column text alignment values used by RowPanel. */
    public static final int LEFT = 0, CENTER = 1, RIGHT = 2;
    /** Column titles of the inventory table. */
    private static final String[] LABELS = {"MODULE CODE", "MODULE NAME", "UNITS / CREDITS", "SEMESTER",
            "AVAILABLE STOCKS", "PER TIME SLOT", "STATUS", "ACTIONS"};
    // Minimum pixel width of each column; anything wider is shared out by WEIGHT (the name column
    // soaks up most of it, like in the Figma).
    private static final int[] MINW   = {84, 140, 108, 84, 116, 104, 96, 68};
    /** How the free width is shared between the columns (bigger = wider). */
    private static final int[] WEIGHT = {2, 12, 2, 3, 3, 3, 3, 1};
    /** Alignment of each column. */
    private static final int[] ALIGN  = {LEFT, LEFT, CENTER, LEFT, CENTER, CENTER, CENTER, RIGHT};
    /** Left / right padding inside a table row. */
    public static final int PAD_LEFT = 12, PAD_RIGHT = 10;
    /** Height of the gray header band and of one data row (pixels). */
    public static final int BAND_H = 28, ROW_H = 44;

    /** The shared data store. */
    private final DemoDataStore store;
    /** The staff window (told to refresh the other pages after a change). */
    private final StaffMainFrame parent;
    /** The row of statistic cards (total, ready, pending...). */
    private JPanel statsRow;
    /** Holds the header band and one row per module. */
    private JPanel tableBody;
    /** Search by code or name. */
    private SearchBox searchBox;
    /** Filter by semester. */
    private Dropdown semesterFilter;
    /** Filter by department. */
    private Dropdown deptFilter;
    /** True until the first refresh (used to pick the default filters). */
    private boolean firstLoad = true;

    /** Builds the page: header, stat cards, toolbar and table. */
    public InventoryPanel(DemoDataStore store, StaffMainFrame parent) {
        this.store = store;
        this.parent = parent;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(PAGE_BG);

        JPanel content = new UIKit.ScrollableBoxPanel();
        content.setBorder(new EmptyBorder(20, 22, 22, 22));

        JComponent header = buildHeader();
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(header);
        content.add(Box.createVerticalStrut(12));

        JPanel divider = new JPanel();
        divider.setBackground(HEADER_LINE);
        divider.setOpaque(true);
        divider.setPreferredSize(new Dimension(10, 1));
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(divider);
        content.add(Box.createVerticalStrut(16));

        statsRow = new JPanel(new GridLayout(1, 4, 14, 0));
        statsRow.setOpaque(false);
        statsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        statsRow.setPreferredSize(new Dimension(10, 66));
        statsRow.setMinimumSize(new Dimension(10, 66));
        statsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
        content.add(statsRow);
        content.add(Box.createVerticalStrut(16));

        UIKit.RoundPanel tableCard = new UIKit.RoundPanel(12, CARD);
        tableCard.setLayout(new BorderLayout());
        tableCard.setBorder(new EmptyBorder(17, 15, 17, 15));
        tableCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        tableCard.add(buildToolbar(), BorderLayout.NORTH);
        tableBody = new JPanel();
        tableBody.setLayout(new BoxLayout(tableBody, BoxLayout.Y_AXIS));
        tableBody.setOpaque(false);
        tableCard.add(tableBody, BorderLayout.CENTER);
        content.add(tableCard);

        add(UIKit.scroll(content), BorderLayout.CENTER);

        searchBox.field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        semesterFilter.setOnChange(this::refresh);
        deptFilter.setOnChange(this::refresh);

        refresh();
    }

    // ------------------------------------------------------------------ header

    /** Builds this page's header ("Module Inventory" with its subtitle and breadcrumb). */
    private JComponent buildHeader() {
        return buildStaffHeader(store, "Module Inventory", "Curriculum Registry Control Console", "Registrar Office Controls");
    }

    /**
     * Header of a staff page: title, subtitle and breadcrumb. (It also knows the signed-in staff
     * member, but the header no longer shows a name - the sidebar already does.)
     */
    public static JComponent buildStaffHeader(DemoDataStore store, String titleText, String subText, String crumbText) {
        Staff st = store.getCurrentStaff();
        return buildHeader(titleText, subText, crumbText, st != null ? st.getName() : "Staff", st != null ? st.getTitle() : "");
    }

    /**
     * The shared page header used by every portal: a bold title with a subtitle and a colored
     * breadcrumb under it.
     */
    public static JComponent buildHeader(String titleText, String subText, String crumbText, String name, String roleTitle) {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        JLabel title = new JLabel(titleText);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(INK);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel subRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        subRow.setOpaque(false);
        subRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sub = new JLabel(subText);
        sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sub.setForeground(SLATE);
        JLabel dot = new JLabel("\u2022");
        dot.setFont(new Font("SansSerif", Font.PLAIN, 10));
        dot.setForeground(new Color(148, 163, 184));
        JLabel crumb = new JLabel(crumbText);
        crumb.setFont(new Font("SansSerif", Font.BOLD, 12));
        crumb.setForeground(CRUMB);
        subRow.add(sub);
        subRow.add(Box.createHorizontalStrut(8));
        subRow.add(dot);
        subRow.add(Box.createHorizontalStrut(8));
        subRow.add(crumb);
        left.add(title);
        left.add(Box.createVerticalStrut(2));
        left.add(subRow);
        header.add(left, BorderLayout.WEST);

        // (No "System Live" pill and no name / role / avatar on the right -- the sidebar already shows who is signed in.)
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
        return header;
    }

    /** First letters of a name, e.g. "Mark Sanchez" -> "MS". */
    public static String initialsOf(String name) {
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0)));
            if (sb.length() >= 2) break;
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ toolbar

    /** Builds the search box, the two filters and the "+ Add New Module" button. */
    private JComponent buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.setBorder(new EmptyBorder(0, 0, 15, 0));

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filters.setOpaque(false);
        searchBox = new SearchBox("Search modules...", 210);
        semesterFilter = new Dropdown("Semester: ");
        deptFilter = new Dropdown("Dept: ");
        filters.add(searchBox);
        filters.add(Box.createHorizontalStrut(12));
        filters.add(semesterFilter);
        filters.add(Box.createHorizontalStrut(12));
        filters.add(deptFilter);
        toolbar.add(filters, BorderLayout.WEST);

        UIKit.RoundButton addBtn = new UIKit.RoundButton("+ Add New Module", BAND, new Color(180, 182, 184), Color.BLACK, 6, 7, 16);
        addBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        addBtn.addActionListener(e -> openEditor(null));
        JPanel addWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        addWrap.setOpaque(false);
        addWrap.add(addBtn);
        toolbar.add(addWrap, BorderLayout.EAST);
        return toolbar;
    }

    // ------------------------------------------------------------------ table

    /** Builds the gray column-title band. */
    private JComponent buildHeaderRow() {
        RowPanel band = new RowPanel(BAND_H, false);
        band.setOpaque(true);
        band.setBackground(BAND);
        for (int i = 0; i < LABELS.length; i++) {
            JLabel l = new JLabel(LABELS[i]);
            l.setFont(new Font("SansSerif", Font.BOLD, 10));
            l.setForeground(Color.BLACK);
            band.add(l);
        }
        return band;
    }

    /**
     * Builds one table row for a module: code, name, credits, semester, stock, per-slot limit, status
     * badge and the edit / delete buttons.
     */
    private JComponent buildRow(Module m) {
        RowPanel row = new RowPanel(ROW_H, true);

        JLabel code = new JLabel(UIKit.displayCode(m.getCode()));
        code.setFont(new Font("SansSerif", Font.BOLD, 12));
        code.setForeground(INK);
        row.add(code);

        JLabel title = new JLabel(m.getTitle());
        title.setFont(new Font("SansSerif", Font.PLAIN, 12));
        title.setForeground(INK);
        row.add(title);

        JLabel credits = new JLabel(String.valueOf(m.getCredits()));
        credits.setFont(new Font("SansSerif", Font.PLAIN, 12));
        credits.setForeground(INK);
        row.add(credits);

        JLabel sem = new JLabel(m.getSemester());
        sem.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sem.setForeground(SEMESTER_TXT);
        row.add(sem);

        // Available stocks: number of physical copies on hand. Red when the shelf is empty.
        JLabel stocks = new JLabel(String.valueOf(m.getStocks()));
        stocks.setFont(new Font("SansSerif", m.getStocks() <= 0 ? Font.BOLD : Font.PLAIN, 12));
        stocks.setForeground(m.getStocks() <= 0 ? DELETE_RED : INK);
        row.add(stocks);

        JLabel slot = new JLabel(m.getSlotLimit() + " students");
        slot.setFont(new Font("SansSerif", Font.PLAIN, 12));
        slot.setForeground(INK);
        row.add(slot);

        row.add(statusBadge(m.getStatus()));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        IconButton edit = new IconButton(IconButton.EDIT, "Edit module");
        edit.addActionListener(e -> openEditor(m));
        IconButton delete = new IconButton(IconButton.DELETE, "Delete module");
        delete.addActionListener(e -> deleteModule(m));
        actions.add(edit);
        actions.add(Box.createHorizontalStrut(9));
        actions.add(delete);
        row.add(actions);
        return row;
    }

    /** The colored Ready / Pending / Released badge. */
    private static StatusBadge statusBadge(ModuleStatus s) {
        switch (s) {
            case READY:
                return new StatusBadge("Ready", new Color(236, 253, 245), new Color(6, 95, 70), new Color(16, 185, 129));
            case RELEASED:
                return new StatusBadge("Released", UIKit.BLUE_PILL_BG, UIKit.BLUE_PILL_FG, new Color(59, 130, 246));
            case PENDING:
            default:
                return new StatusBadge("Pending", new Color(254, 243, 199), new Color(133, 50, 10), new Color(245, 158, 11));
        }
    }

    /**
     * Asks for confirmation, then removes the module. Pending / approved appointments for it are
     * declined and the students are notified.
     */
    private void deleteModule(Module m) {
        long open = store.getAppointments().stream().filter(x -> x.getModule() != null && x.getModule().getId().equals(m.getId())
                && (x.getStatus() == moduledistributionsystem.model.AppointmentStatus.PENDING
                || x.getStatus() == moduledistributionsystem.model.AppointmentStatus.CONFIRMED)).count();
        String warn = open > 0 ? "\n\n" + open + " pending/approved appointment(s) for it will be declined and the students notified." : "";
        int ok = JOptionPane.showConfirmDialog(this, "Remove " + m.getDisplayCode() + " from inventory?" + warn, "Delete Module", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            store.removeModule(m);
            if (parent != null) SwingUtilities.invokeLater(parent::refreshAll);
            refresh();
        }
    }

    /**
     * Opens the Add (existing = null) or Edit dialog and saves the result through the store, which also
     * rejects duplicate codes.
     */
    private void openEditor(Module existing) {
        ModuleEditDialog dlg = new ModuleEditDialog(SwingUtilities.getWindowAncestor(this), existing, store);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            Module target;
            if (existing == null) {
                target = dlg.buildModule();
                String problem = store.addModule(target);
                if (problem != null) { JOptionPane.showMessageDialog(this, problem, "Module not saved", JOptionPane.WARNING_MESSAGE); return; }
            } else {
                boolean wasBookable = existing.isBookable();
                dlg.applyTo(existing);
                String problem = store.saveModuleEdit(existing, wasBookable);
                if (problem != null) { JOptionPane.showMessageDialog(this, problem, "Module not saved", JOptionPane.WARNING_MESSAGE); return; }
                target = existing;
            }
            // Don't let a filter hide the module the staff member just saved.
            String sem = semesterFilter.getSelected();
            if (!"All".equals(sem) && !sem.equals(target.getSemester())) semesterFilter.setSelected("All");
            String dept = deptFilter.getSelected();
            if (!"All".equals(dept) && !dept.equals(target.getDepartment())) deptFilter.setSelected("All");
            if (parent != null) SwingUtilities.invokeLater(parent::refreshAll);
            refresh();
        }
    }

    /**
     * Re-reads the modules and rebuilds the stat cards and the table using the current search and
     * filters.
     */
    public void refresh() {
        List<Module> all = store.getModules();

        long ready = all.stream().filter(m -> m.getStatus() == ModuleStatus.READY).count();
        long pending = all.stream().filter(m -> m.getStatus() == ModuleStatus.PENDING).count();
        long released = all.stream().filter(m -> m.getStatus() == ModuleStatus.RELEASED).count();
        statsRow.removeAll();
        statsRow.add(statCard("Total Registered Modules", all.size() + " Modules", STAT_TOTAL));
        statsRow.add(statCard("Active Catalog", ready + " Ready", STAT_READY));
        statsRow.add(statCard("Awaiting Review", pending + " Pending", STAT_PENDING));
        statsRow.add(statCard("Released", released + " Released", STAT_RELEASE));
        statsRow.revalidate();
        statsRow.repaint();

        Set<String> semesters = new LinkedHashSet<>();
        semesters.add("All");
        Set<String> depts = new LinkedHashSet<>();
        depts.add("All");
        for (Module m : all) {
            semesters.add(m.getSemester());
            depts.add(m.getDepartment());
        }
        semesterFilter.setItems(semesters);
        deptFilter.setItems(depts);
        if (firstLoad) {
            firstLoad = false;
            // The Figma opens on "Dept: CITE".
            if (depts.contains("CITE")) deptFilter.setSelected("CITE");
        }

        String query = searchBox.field.getText().trim().toLowerCase();
        String semSel = semesterFilter.getSelected();
        String deptSel = deptFilter.getSelected();

        tableBody.removeAll();
        tableBody.add(buildHeaderRow());
        boolean any = false;
        for (Module m : all) {
            if (!"All".equals(semSel) && !semSel.equals(m.getSemester())) continue;
            if (!"All".equals(deptSel) && !deptSel.equals(m.getDepartment())) continue;
            if (!query.isEmpty() && !(m.getCode().toLowerCase().contains(query) || m.getTitle().toLowerCase().contains(query))) continue;
            tableBody.add(buildRow(m));
            any = true;
        }
        if (!any) {
            JLabel empty = new JLabel("No modules match your filters.");
            empty.setFont(new Font("SansSerif", Font.PLAIN, 13));
            empty.setForeground(SLATE);
            empty.setBorder(new EmptyBorder(16, PAD_LEFT, 16, 0));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            tableBody.add(empty);
        }
        tableBody.revalidate();
        tableBody.repaint();
    }

    /** One statistic card: small title on top, big colored value below. Shared by every portal. */
    public static JPanel statCard(String label, String value, Color valueColor) {
        UIKit.RoundPanel card = new UIKit.RoundPanel(10, CARD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel l = new JLabel(label.toUpperCase());
        l.setFont(new Font("SansSerif", Font.BOLD, 10));
        l.setForeground(STAT_LABEL);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel v = new JLabel(value);
        v.setFont(new Font("SansSerif", Font.BOLD, 20));
        v.setForeground(valueColor);
        v.setAlignmentX(Component.LEFT_ALIGNMENT);
        v.setBorder(new EmptyBorder(3, 0, 0, 0));
        card.add(l);
        card.add(v);
        return card;
    }

    // ------------------------------------------------------------------ shared helpers for every portal

    /** A row of tab chips ("All  Pending (3)  Approved (2) ...") in the same black/gray style as the rest of the app. */
    public static class TabBar extends JPanel {
        private final java.util.Map<String, UIKit.ToggleChip> chips = new java.util.LinkedHashMap<>();
        private final ButtonGroup group = new ButtonGroup();
        /** Key of the tab that is currently selected. */
        private String selected;
        /** Runs when another tab is chosen. */
        private Runnable onChange;

        /** Creates one chip per key; the first one starts selected. */
        public TabBar(String... keys) {
            super(new FlowLayout(FlowLayout.LEFT, 8, 0));
            setOpaque(false);
            for (String k : keys) {
                UIKit.ToggleChip c = new UIKit.ToggleChip(k, 8);
                c.setBorder(new EmptyBorder(6, 14, 6, 14));
                c.addActionListener(e -> { selected = k; if (onChange != null) onChange.run(); });
                group.add(c);
                chips.put(k, c);
                add(c);
            }
            selected = keys[0];
            chips.get(selected).setSelected(true);
        }

        /** The key of the selected tab. */
        public String getSelected() { return selected; }
        /** Sets what to run when another tab is chosen. */
        public void setOnChange(Runnable r) { this.onChange = r; }

        /** Selects a tab by code (does not call the change listener). */
        public void select(String key) {
            if (!chips.containsKey(key)) return;
            selected = key;
            chips.get(key).setSelected(true);
        }

        /** Show a count next to a tab, e.g. setCount("Pending", 3) -> "Pending (3)". The tab keeps its key. */
        public void setCount(String key, int n) {
            UIKit.ToggleChip c = chips.get(key);
            if (c != null) c.setText(key + " (" + n + ")");
        }
    }

    /** The small rounded button used in the toolbar of every table card. */
    public static UIKit.RoundButton toolButton(String text, Color base, Color hover, Color fg) {
        UIKit.RoundButton b = new UIKit.RoundButton(text, base, hover, fg, 6, 7, 16);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        return b;
    }

    /** Gray toolbar button. */
    public static UIKit.RoundButton grayButton(String text) { return toolButton(text, BAND, new Color(180, 182, 184), Color.BLACK); }
    /** Black toolbar button (main action). */
    public static UIKit.RoundButton darkButton(String text) { return toolButton(text, UIKit.BLACK_PILL, new Color(40, 40, 40), Color.WHITE); }
    /** Green toolbar button (approve / confirm). */
    public static UIKit.RoundButton greenButton(String text) { return toolButton(text, UIKit.GREEN_PILL_BG, new Color(200, 235, 215), new Color(20, 120, 60)); }
    /** Red toolbar button (decline / dangerous). */
    public static UIKit.RoundButton redButton(String text) { return toolButton(text, UIKit.RED_PILL_BG, new Color(250, 210, 210), UIKit.RED_PILL_FG); }

    /** Small button used inside a table row. */
    public static UIKit.RoundButton rowButton(String text, boolean dark) {
        UIKit.RoundButton b = dark
                ? new UIKit.RoundButton(text, UIKit.BLACK_PILL, new Color(40, 40, 40), Color.WHITE, 6, 4, 10)
                : new UIKit.RoundButton(text, new Color(225, 225, 225), new Color(210, 210, 210), UIKit.TEXT_DARK, 6, 4, 10);
        b.setFont(new Font("SansSerif", Font.BOLD, 11));
        return b;
    }

    /** One text cell of a table row. */
    public static JLabel cellLabel(String text, boolean bold, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, 12));
        l.setForeground(color);
        return l;
    }

    /** The grey header band of a table. */
    public static JComponent headerBand(String[] labels, int[] minW, int[] weight, int[] align, int growCol) {
        RowPanel band = new RowPanel(BAND_H, false, minW, weight, align, growCol);
        band.setOpaque(true);
        band.setBackground(BAND);
        for (String t : labels) {
            JLabel l = new JLabel(t);
            l.setFont(new Font("SansSerif", Font.BOLD, 10));
            l.setForeground(Color.BLACK);
            band.add(l);
        }
        return band;
    }

    /** The "nothing here" line under a table band. */
    public static JLabel emptyLine(String msg) {
        JLabel empty = new JLabel(msg);
        empty.setFont(new Font("SansSerif", Font.PLAIN, 13));
        empty.setForeground(SLATE);
        empty.setBorder(new EmptyBorder(16, PAD_LEFT, 16, 0));
        empty.setAlignmentX(Component.LEFT_ALIGNMENT);
        return empty;
    }

    /**
     * The page skeleton every portal page now shares: scrollable column with the header, a divider, a row of stat
     * cards and one gray card that holds the toolbar + table. Returns {content card body holder}.
     */
    public static final class Page {
        /** The whole page: put it in the window. */
        public final JPanel root;
        /** Fill this with statCard(...) panels. */
        public final JPanel statsRow;
        /** The gray card: add the toolbar to NORTH and the table to CENTER. */
        public final UIKit.RoundPanel card;

        /** Builds the scrolling page: header, divider, a row with statCount cards and the gray card. */
        public Page(JComponent header, int statCount) {
            root = new JPanel(new BorderLayout());
            root.setOpaque(true);
            root.setBackground(PAGE_BG);
            JPanel content = new UIKit.ScrollableBoxPanel();
            content.setBorder(new EmptyBorder(20, 22, 22, 22));
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(header);
            content.add(Box.createVerticalStrut(12));
            JPanel divider = new JPanel();
            divider.setBackground(HEADER_LINE);
            divider.setOpaque(true);
            divider.setPreferredSize(new Dimension(10, 1));
            divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            divider.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(divider);
            content.add(Box.createVerticalStrut(16));
            statsRow = new JPanel(new GridLayout(1, statCount, 14, 0));
            statsRow.setOpaque(false);
            statsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            statsRow.setPreferredSize(new Dimension(10, 66));
            statsRow.setMinimumSize(new Dimension(10, 66));
            statsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
            content.add(statsRow);
            content.add(Box.createVerticalStrut(16));
            card = new UIKit.RoundPanel(12, CARD);
            card.setLayout(new BorderLayout());
            card.setBorder(new EmptyBorder(17, 15, 17, 15));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(card);
            root.add(UIKit.scroll(content), BorderLayout.CENTER);
        }
    }

    // ------------------------------------------------------------------ small building blocks

    /**
     * One table row (or the header band). Children are added in column order and this layout
     * puts each one in its column, so the header and every data row line up exactly.
     */
    public static class RowPanel extends JPanel {
        /** Height of the row in pixels. */
        private final int rowHeight;
        /** Draw a thin line under the row? */
        private final boolean divider;
        /** Per column: minimum width, share of free width and alignment. */
        private final int[] minW, weight, align;
        private final int growCol; // column that soaks up the rounding remainder

        /** Row with the inventory table's columns. */
        public RowPanel(int rowHeight, boolean divider) {
            this(rowHeight, divider, MINW, WEIGHT, ALIGN, 1);
        }

        /** Same look with a different set of columns (used by Student Appointments, which follows this page's design). */
        public RowPanel(int rowHeight, boolean divider, int[] minW, int[] weight, int[] align, int growCol) {
            this.rowHeight = rowHeight;
            this.divider = divider;
            this.minW = minW;
            this.weight = weight;
            this.align = align;
            this.growCol = growCol;
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setLayout(new LayoutManager() {
                /** Not needed: children are placed purely by their order. */
                public void addLayoutComponent(String name, Component comp) { }
                /** Not needed. */
                public void removeLayoutComponent(Component comp) { }

                /** Width of all columns at their minimum, one row tall. */
                public Dimension preferredLayoutSize(Container p) {
                    int w = PAD_LEFT + PAD_RIGHT;
                    for (int m : RowPanel.this.minW) w += m;
                    return new Dimension(w, RowPanel.this.rowHeight);
                }

                /** Same as the preferred size. */
                public Dimension minimumLayoutSize(Container p) { return preferredLayoutSize(p); }

                /** Puts child number n in column n, aligned as requested. */
                public void layoutContainer(Container p) {
                    int total = p.getWidth() - PAD_LEFT - PAD_RIGHT;
                    int[] w = columnWidths(total);
                    int x = PAD_LEFT;
                    int n = Math.min(p.getComponentCount(), w.length);
                    for (int i = 0; i < n; i++) {
                        Component c = p.getComponent(i);
                        Dimension pref = c.getPreferredSize();
                        int cw = Math.min(pref.width, w[i]);
                        int ch = Math.min(pref.height, p.getHeight());
                        int al = RowPanel.this.align[i];
                        int cx = al == LEFT ? x : al == RIGHT ? x + w[i] - cw : x + (w[i] - cw) / 2;
                        int cy = (p.getHeight() - ch) / 2;
                        c.setBounds(cx, cy, cw, ch);
                        x += w[i];
                    }
                }
            });
        }

        /** Splits the available width: every column gets its minimum, the rest is shared by weight. */
        private int[] columnWidths(int total) {
            int sumMin = 0, sumWeight = 0;
            for (int i = 0; i < minW.length; i++) { sumMin += minW[i]; sumWeight += weight[i]; }
            int[] w = new int[minW.length];
            if (total >= sumMin) {
                int extra = total - sumMin;
                int used = 0;
                for (int i = 0; i < w.length; i++) {
                    w[i] = minW[i] + extra * weight[i] / sumWeight;
                    used += w[i];
                }
                w[growCol] += total - used; // rounding remainder goes to the main text column
            } else {
                for (int i = 0; i < w.length; i++) w[i] = Math.max(1, minW[i] * Math.max(total, 0) / sumMin);
            }
            return w;
        }

        /** The row may stretch to any width but keeps its height. */
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (divider) {
                g.setColor(ROW_LINE);
                g.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
        }
    }

    /** Status pill from the Figma: soft tinted rounded rectangle, colored dot, bold label. */
    public static class StatusBadge extends JComponent {
        /** Text inside the badge. */
        private final String text;
        /** Background, text and dot colors. */
        private final Color bg, fg, dot;

        /** Creates a rounded badge with a colored dot and a label. */
        public StatusBadge(String text, Color bg, Color fg, Color dot) {
            this.text = text;
            this.bg = bg;
            this.fg = fg;
            this.dot = dot;
            setFont(new Font("SansSerif", Font.BOLD, 11));
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(10 + 6 + 6 + fm.stringWidth(text) + 11, 22);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 9, 9);
            g2.setColor(dot);
            g2.fill(new Ellipse2D.Float(10, getHeight() / 2f - 3f, 6, 6));
            g2.setColor(fg);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, 22, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Square action button with a hand-drawn blue pencil (edit) or red trash can (delete) icon. */
    public static class IconButton extends JButton {
        /** Which icon to draw: pencil or trash can. */
        static final int EDIT = 0, DELETE = 1;
        /** EDIT or DELETE. */
        private final int kind;
        /** True while the mouse is over the button. */
        private boolean hover = false;

        IconButton(int kind, String tooltip) {
            this.kind = kind;
            setToolTipText(tooltip);
            Dimension d = new Dimension(24, 24);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            if (hover) {
                g2.setColor(new Color(236, 238, 239));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            }
            g2.setColor(new Color(208, 210, 211));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (kind == EDIT) {
                AffineTransform old = g2.getTransform();
                g2.translate(cx, cy);
                g2.rotate(Math.toRadians(45));
                g2.setColor(EDIT_BLUE);
                g2.fill(new RoundRectangle2D.Float(-2.2f, -6.5f, 4.4f, 9.5f, 1.5f, 1.5f));
                Path2D tip = new Path2D.Float();
                tip.moveTo(-2.2f, 3.6f);
                tip.lineTo(2.2f, 3.6f);
                tip.lineTo(0f, 7.2f);
                tip.closePath();
                g2.fill(tip);
                g2.setTransform(old);
            } else {
                g2.setColor(DELETE_RED);
                g2.fill(new RoundRectangle2D.Float(cx - 4.5f, cy - 2f, 9f, 8f, 2f, 2f));   // bin
                g2.fill(new RoundRectangle2D.Float(cx - 5.5f, cy - 4.5f, 11f, 2f, 1f, 1f)); // lid
                g2.fill(new RoundRectangle2D.Float(cx - 2f, cy - 6.3f, 4f, 2f, 1f, 1f));   // handle
            }
            g2.dispose();
        }
    }

    /** Outlined search field with a magnifier and a hint that stays visible while empty. */
    public static class SearchBox extends JPanel {
        /** The text field itself (read its text, add a listener to it). */
        public final JTextField field;

        /** Creates a rounded search box with a magnifier icon and a gray hint text. */
        public SearchBox(final String hint, int width) {
            setLayout(new BorderLayout());
            setOpaque(false);
            setPreferredSize(new Dimension(width, 30));
            setBorder(new EmptyBorder(0, 33, 0, 10));
            field = new JTextField() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (getText().isEmpty()) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        g2.setColor(new Color(20, 20, 20));
                        g2.setFont(getFont());
                        FontMetrics fm = g2.getFontMetrics();
                        Insets in = getInsets();
                        g2.drawString(hint, in.left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                        g2.dispose();
                    }
                }
            };
            // Nimbus paints its own white text-field background; the Basic UI honours setOpaque(false).
            field.setUI(new BasicTextFieldUI());
            field.setOpaque(false);
            field.setBorder(null);
            field.setFont(new Font("SansSerif", Font.PLAIN, 12));
            field.setForeground(new Color(20, 20, 20));
            field.setCaretColor(new Color(20, 20, 20));
            add(field, BorderLayout.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(INPUT_BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            // magnifier
            float cy = getHeight() / 2f;
            g2.setColor(ICON_SLATE);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Ellipse2D.Float(11, cy - 6.5f, 9, 9));
            g2.drawLine(19, (int) (cy + 2.5f), 22, (int) (cy + 5.5f));
            g2.dispose();
        }
    }

    /** Outlined "Label: Value  v" filter, e.g. "Semester: All". Click to pick another value. */
    public static class Dropdown extends JComponent {
        /** Text shown before the chosen value, e.g. "Semester: ". */
        private final String prefix;
        /** The choices. */
        private String[] items = {"All"};
        /** The chosen value. */
        private String selected = "All";
        /** Runs when the user picks another value. */
        private Runnable onChange;

        /** Creates a rounded drop-down button like "Semester: All". */
        public Dropdown(String prefix) {
            this.prefix = prefix;
            setFont(new Font("SansSerif", Font.PLAIN, 12));
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { showPopup(); }
            });
        }

        /** Sets what to run when the user picks another value. */
        public void setOnChange(Runnable r) { this.onChange = r; }
        /** The chosen value. */
        public String getSelected() { return selected; }

        /** Chooses a value (does not run the change listener). */
        public void setSelected(String s) {
            selected = s;
            revalidate();
            repaint();
        }

        /** Replace the choices, keeping the current pick if it still exists. Does not fire onChange. */
        public void setItems(Set<String> values) {
            items = values.toArray(new String[0]);
            if (!values.contains(selected)) selected = items.length > 0 ? items[0] : "All";
            revalidate();
            repaint();
        }

        /** Opens the list of choices under the button. */
        private void showPopup() {
            JPopupMenu menu = new JPopupMenu();
            for (final String it : items) {
                JMenuItem mi = new JMenuItem(it);
                mi.setFont(new Font("SansSerif", it.equals(selected) ? Font.BOLD : Font.PLAIN, 12));
                mi.addActionListener(e -> {
                    if (!it.equals(selected)) {
                        setSelected(it);
                        if (onChange != null) onChange.run();
                    }
                });
                menu.add(mi);
            }
            menu.show(this, 0, getHeight() + 2);
        }

        @Override
        public Dimension getPreferredSize() {
            // Sized to what it currently shows (never narrower than "<prefix>All"), like the Figma chips.
            FontMetrics fm = getFontMetrics(getFont());
            int textW = Math.max(fm.stringWidth(prefix + "All"), fm.stringWidth(prefix + selected));
            return new Dimension(12 + textW + 10 + 9 + 11, 30);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(INPUT_BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g2.setColor(INPUT_TEXT);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(prefix + selected, 12, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            // chevron
            float x = getWidth() - 16f, y = getHeight() / 2f;
            g2.setColor(new Color(100, 125, 155));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D chevron = new Path2D.Float();
            chevron.moveTo(x - 3.5f, y - 1.8f);
            chevron.lineTo(x, y + 1.8f);
            chevron.lineTo(x + 3.5f, y - 1.8f);
            g2.draw(chevron);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ add / edit dialog

    /** Small modal for creating/editing a module's fields, including its available stocks. */
    static class ModuleEditDialog extends JDialog {
        /** True after the user clicked Save. */
        private boolean confirmed = false;
        /** Text inputs of the form. */
        private JTextField codeField, titleField, locationField, semesterField, deptField;
        /** Number / date inputs: units, collect-by date, stock and max students per time slot. */
        private JSpinner creditsSpinner, dateSpinner, stocksSpinner, slotSpinner;
        /** The data store, used to reject a duplicate module code while the form is open. */
        private final DemoDataStore store;
        /** The module being edited, or null when adding a new one. */
        private final Module existing;
        /** Status chooser. */
        private JComboBox<ModuleStatus> statusCombo;
        /** Year level chooser. */
        private JComboBox<YearLevel> yearLevelCombo;

        ModuleEditDialog(Window owner, Module existing, DemoDataStore store) {
            super(owner, existing == null ? "Add Module" : "Edit Module", ModalityType.APPLICATION_MODAL);
            this.store = store;
            this.existing = existing;
            setSize(440, 830);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout());

            JPanel content = new JPanel();
            content.setBorder(new EmptyBorder(16, 16, 16, 16));
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

            content.add(new JLabel("Module Code"));
            codeField = new JTextField(existing == null ? "" : existing.getCode());
            content.add(codeField);
            content.add(Box.createVerticalStrut(8));

            content.add(new JLabel("Title"));
            titleField = new JTextField(existing == null ? "" : existing.getTitle());
            content.add(titleField);
            content.add(Box.createVerticalStrut(8));

            content.add(new JLabel("Credits"));
            creditsSpinner = new JSpinner(new SpinnerNumberModel(existing == null ? 3 : existing.getCredits(), 1, 12, 1));
            content.add(creditsSpinner);
            content.add(Box.createVerticalStrut(8));

            content.add(new JLabel("Location"));
            locationField = new JTextField(existing == null ? "Basement" : existing.getLocation());
            content.add(locationField);
            content.add(Box.createVerticalStrut(8));

            content.add(new JLabel("Semester"));
            semesterField = new JTextField(existing == null ? "Semester 1" : existing.getSemester());
            content.add(semesterField);
            content.add(Box.createVerticalStrut(8));

            content.add(new JLabel("Department"));
            deptField = new JTextField(existing == null ? "CITE" : existing.getDepartment());
            content.add(deptField);
            content.add(Box.createVerticalStrut(8));

            content.add(new JLabel("Year Level"));
            yearLevelCombo = new JComboBox<>(YearLevel.values());
            yearLevelCombo.setSelectedItem(existing == null || existing.getYearLevel() == null
                    ? YearLevel.SECOND_YEAR : existing.getYearLevel());
            yearLevelCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, yearLevelCombo.getPreferredSize().height));
            content.add(yearLevelCombo);
            content.add(Box.createVerticalStrut(8));

            content.add(new JLabel("Collect By"));
            Date initialDate = existing == null
                    ? Date.from(LocalDate.now().plusDays(14).atStartOfDay(ZoneId.systemDefault()).toInstant())
                    : Date.from(existing.getCollectBy().atStartOfDay(ZoneId.systemDefault()).toInstant());
            dateSpinner = new JSpinner(new SpinnerDateModel());
            dateSpinner.setValue(initialDate);
            dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, "MM/dd/yy"));
            content.add(dateSpinner);
            content.add(Box.createVerticalStrut(8));

            content.add(new JLabel("Available Stocks"));
            stocksSpinner = new JSpinner(new SpinnerNumberModel(existing == null ? 0 : existing.getStocks(), 0, 100000, 1));
            content.add(stocksSpinner);
            JLabel stocksHint = new JLabel("<html><div style='width:340px'>Count the copies physically on hand at the office and "
                    + "encode the number here. It goes down by one each time a pickup is marked Completed; "
                    + "edit it again when you restock or the count is off.</div></html>");
            stocksHint.setFont(new Font("SansSerif", Font.PLAIN, 11));
            stocksHint.setForeground(new Color(110, 112, 118));
            stocksHint.setBorder(new EmptyBorder(3, 0, 0, 0));
            content.add(stocksHint);
            content.add(Box.createVerticalStrut(8));

            content.add(new JLabel("Max students per time slot"));
            slotSpinner = new JSpinner(new SpinnerNumberModel(existing == null ? Module.DEFAULT_SLOT_LIMIT : existing.getSlotLimit(), 1, 500, 1));
            content.add(slotSpinner);
            JLabel slotHint = new JLabel("<html><div style='width:340px'>To avoid long lines at the pickup desk, only this many students can book "
                    + "the same date and time slot for this module. Full slots are shown as Full to students.</div></html>");
            slotHint.setFont(new Font("SansSerif", Font.PLAIN, 11));
            slotHint.setForeground(new Color(110, 112, 118));
            slotHint.setBorder(new EmptyBorder(3, 0, 0, 0));
            content.add(slotHint);
            content.add(Box.createVerticalStrut(8));

            content.add(new JLabel("Status"));
            statusCombo = new JComboBox<>(ModuleStatus.values());
            statusCombo.setSelectedItem(existing == null ? ModuleStatus.PENDING : existing.getStatus());
            content.add(statusCombo);

            add(content, BorderLayout.CENTER);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton cancel = new JButton("Cancel");
            JButton save = new JButton(existing == null ? "Add" : "Save");
            buttons.add(cancel);
            buttons.add(save);
            add(buttons, BorderLayout.SOUTH);

            cancel.addActionListener(e -> dispose());
            save.addActionListener(e -> {
                if (codeField.getText().trim().isEmpty() || titleField.getText().trim().isEmpty()
                        || semesterField.getText().trim().isEmpty() || deptField.getText().trim().isEmpty()
                        || locationField.getText().trim().isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Code, title, location, semester and department are required.", "Missing info", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                String codeProblem = store.moduleCodeProblem(codeField.getText(), existing);
                if (codeProblem != null) {
                    JOptionPane.showMessageDialog(this, codeProblem, "Module code", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (pickedDate().isBefore(LocalDate.now()) && statusCombo.getSelectedItem() == ModuleStatus.READY) {
                    int ok = JOptionPane.showConfirmDialog(this, "The collect-by date is already in the past, so students can't book any day.\n\nSave anyway?",
                            "Collect-by date", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (ok != JOptionPane.YES_OPTION) return;
                }
                if (statusCombo.getSelectedItem() == ModuleStatus.READY && ((Integer) stocksSpinner.getValue()) == 0) {
                    int ok = JOptionPane.showConfirmDialog(this,
                            "This module is marked Ready but has 0 available stocks.\n"
                                    + "Students will see it as Out of Stock and won't be able to book it.\n\nSave anyway?",
                            "No stock encoded", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (ok != JOptionPane.YES_OPTION) return;
                }
                confirmed = true;
                dispose();
            });
        }

        /** True when the user saved (not cancelled). */
        boolean isConfirmed() { return confirmed; }

        /** The collect-by date chosen in the date spinner. */
        private LocalDate pickedDate() {
            Date d = (Date) dateSpinner.getValue();
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }

        /** Builds a NEW module from the form values. */
        Module buildModule() {
            Module m = new Module(codeField.getText().trim(), titleField.getText().trim(),
                    (Integer) creditsSpinner.getValue(), locationField.getText().trim(),
                    pickedDate(), (ModuleStatus) statusCombo.getSelectedItem(),
                    semesterField.getText().trim(), deptField.getText().trim(),
                    (Integer) stocksSpinner.getValue());
            m.setYearLevel((YearLevel) yearLevelCombo.getSelectedItem());
            m.setSlotLimit((Integer) slotSpinner.getValue());
            return m;
        }

        /** Copies the form values into an existing module (edit). */
        void applyTo(Module m) {
            m.setCode(codeField.getText().trim());
            m.setTitle(titleField.getText().trim());
            m.setCredits((Integer) creditsSpinner.getValue());
            m.setLocation(locationField.getText().trim());
            m.setCollectBy(pickedDate());
            m.setStatus((ModuleStatus) statusCombo.getSelectedItem());
            m.setSemester(semesterField.getText().trim());
            m.setDepartment(deptField.getText().trim());
            m.setStocks((Integer) stocksSpinner.getValue());
            m.setYearLevel((YearLevel) yearLevelCombo.getSelectedItem());
            m.setSlotLimit((Integer) slotSpinner.getValue());
        }
    }
}
