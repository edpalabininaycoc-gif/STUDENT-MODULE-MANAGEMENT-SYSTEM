package moduledistributionsystem.ui.student;

import moduledistributionsystem.ui.common.UIKit;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.model.AppointmentStatus;
import moduledistributionsystem.model.Module;
import moduledistributionsystem.model.QueryStatus;
import moduledistributionsystem.model.Student;
import moduledistributionsystem.model.StudentQuery;

/**
 * Overview dashboard: date, greeting, program / year, and four "At a glance" cards (Available Modules, Upcoming
 * Appointments, Notifications, Student Queries) that jump to their section. Out-of-stock modules the student still
 * needs are shown in an alert under the cards.
 */
public class OverviewPanel extends JPanel {
    /** Program name printed under the greeting. Change it here if the course changes. */
    private static final String PROGRAM = "BSc. Information Technology";
    private static final Color CARD_FILL = new Color(242, 242, 242);
    private static final Color CARD_LINE = new Color(25, 25, 25);
    private static final Color LINK_GREEN = new Color(12, 70, 40);

    /** The shared data store. */
    private final DemoDataStore store;
    /** The student window, used by the cards to jump to a page. */
    private final StudentMainFrame parent;
    private final JLabel dateLabel = new JLabel();
    private final JLabel greetingLabel = new JLabel();
    private final JLabel programLabel = new JLabel();
    private final JPanel cardsRow = new JPanel(new GridLayout(1, 4, 12, 0));
    private final JPanel alertHolder = new JPanel(new BorderLayout());

    /** Builds the page: date, greeting, program, "At a glance" and the (empty) card row. */
    public OverviewPanel(DemoDataStore store, StudentMainFrame parent) {
        this.store = store;
        this.parent = parent;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(28, 32, 28, 32));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        dateLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        dateLabel.setForeground(UIKit.TEXT_DARK);
        greetingLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        greetingLabel.setForeground(UIKit.TEXT_DARK);
        greetingLabel.setBorder(new EmptyBorder(6, 0, 4, 0));
        programLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        programLabel.setForeground(UIKit.TEXT_DARK);
        JLabel glance = new JLabel("At a glance");
        glance.setFont(new Font("SansSerif", Font.BOLD, 13));
        glance.setForeground(UIKit.TEXT_DARK);
        glance.setBorder(new EmptyBorder(22, 0, 12, 0));

        for (JComponent c : new JComponent[]{dateLabel, greetingLabel, programLabel, glance, cardsRow, alertHolder}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        cardsRow.setOpaque(false);
        alertHolder.setOpaque(false);
        alertHolder.setBorder(new EmptyBorder(14, 0, 0, 0));

        content.add(dateLabel);
        content.add(greetingLabel);
        content.add(programLabel);
        content.add(glance);
        content.add(cardsRow);
        content.add(alertHolder);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(content, BorderLayout.NORTH);
        add(UIKit.scroll(top), BorderLayout.CENTER);
        refresh();
    }

    /** One outlined card: icon + badge on top, big number, title, subtitle, divider, link with an arrow. */
    private JPanel glanceCard(String glyph, Color iconBg, Color iconFg, String badge, Color badgeBg, Color badgeFg,
                              String big, String title, String sub, String linkText, int sectionIndex) {
        JPanel card = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_FILL);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.setColor(CARD_LINE);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(14, 14, 12, 14));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        UIKit.IconTile icon = new UIKit.IconTile(glyph, iconBg, iconFg);
        icon.setPreferredSize(new Dimension(30, 30));
        topRow.add(UIKit.westWrap(icon), BorderLayout.WEST);
        JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        badgeWrap.setOpaque(false);
        badgeWrap.add(new UIKit.Pill(badge, badgeBg, badgeFg));
        topRow.add(badgeWrap, BorderLayout.EAST);

        JPanel mid = new JPanel();
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        mid.setOpaque(false);
        mid.setBorder(new EmptyBorder(16, 0, 10, 0));
        JLabel bigLabel = new JLabel(big);
        bigLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        bigLabel.setForeground(UIKit.TEXT_DARK);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        titleLabel.setForeground(UIKit.TEXT_DARK);
        titleLabel.setBorder(new EmptyBorder(4, 0, 2, 0));
        JLabel subLabel = new JLabel(sub);
        subLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        subLabel.setForeground(UIKit.TEXT_MUTED);
        for (JComponent c : new JComponent[]{bigLabel, titleLabel, subLabel}) { c.setAlignmentX(Component.LEFT_ALIGNMENT); mid.add(c); }

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(214, 214, 214)), new EmptyBorder(9, 0, 0, 0)));
        JLabel link = new JLabel(linkText);
        link.setFont(new Font("SansSerif", Font.BOLD, 10));
        link.setForeground(LINK_GREEN);
        bottom.add(link, BorderLayout.WEST);
        bottom.add(new ArrowIcon(), BorderLayout.EAST);

        card.add(topRow, BorderLayout.NORTH);
        card.add(mid, BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);

        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter go = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (parent != null) parent.showSection(sectionIndex); }
        };
        card.addMouseListener(go);
        for (Component c : new Component[]{topRow, mid, bottom, link, bigLabel, titleLabel, subLabel}) c.addMouseListener(go);
        return card;
    }

    /** The small up-right arrow of the card link, drawn by hand so it looks the same on every computer's fonts. */
    private static final class ArrowIcon extends JComponent {
        ArrowIcon() { setPreferredSize(new Dimension(12, 12)); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(LINK_GREEN);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int w = getWidth(), h = getHeight();
            g2.drawLine(2, h - 2, w - 2, 2);
            g2.drawLine(3, 2, w - 2, 2);
            g2.drawLine(w - 2, 2, w - 2, h - 3);
            g2.dispose();
        }
    }

    /**
     * Re-counts available modules, upcoming appointments, unread notifications and answered queries and
     * rebuilds the four cards and the out-of-stock alert.
     */
    public void refresh() {
        Student s = store.getCurrentStudent();
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)));
        String first = s.getName().trim().split("\\s+")[0];
        greetingLabel.setText("Hello, " + first);
        String year = s.getYearLevel() == null ? "" : ", Year " + (s.getYearLevel().ordinal() + 1);
        programLabel.setText(PROGRAM + year);

        List<Module> mineNow = store.getCurrentModulesFor(s);
        long available = mineNow.stream().filter(Module::isBookable).count();
        List<String> outOfStock = new ArrayList<>();
        for (Module m : mineNow) {
            boolean collected = store.getAppointmentsForStudent(s.getId()).stream().anyMatch(a -> a.getModule() != null
                    && a.getModule().getId().equals(m.getId()) && a.getStatus() == AppointmentStatus.COMPLETED);
            if (m.isAvailable() && m.getStocks() <= 0 && !collected) outOfStock.add(m.getDisplayCode() + " " + m.getTitle());
        }
        long upcoming = store.getAppointmentsForStudent(s.getId()).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED || a.getStatus() == AppointmentStatus.PENDING).count();
        long unread = store.getNotificationsForStudent(s.getId()).stream().filter(n -> !n.isRead()).count();
        List<StudentQuery> myQueries = new ArrayList<>();
        for (StudentQuery q : store.getQueries()) if (s.getId().equals(q.getStudentId())) myQueries.add(q);
        long answered = myQueries.stream().filter(q -> q.getStatus() == QueryStatus.ANSWERED).count();

        Color greenBg = new Color(214, 238, 224), greenFg = new Color(20, 110, 60);
        Color amberBg = new Color(253, 236, 200), amberFg = new Color(150, 90, 10);

        cardsRow.removeAll();
        cardsRow.add(glanceCard("\uD83D\uDCE6", greenBg, greenFg, "Due", UIKit.GREEN_PILL_BG, new Color(20, 120, 60),
                String.valueOf(available), "Available Modules", "ready to pickup", "Open modules", 1));
        cardsRow.add(glanceCard("\uD83D\uDCC5", greenBg, greenFg, "Due", UIKit.GREEN_PILL_BG, new Color(20, 120, 60),
                String.valueOf(upcoming), "Upcoming Appointments", nextApptSummary(), "View appointments", 2));
        cardsRow.add(glanceCard("\uD83D\uDD14", amberBg, amberFg, unread > 0 ? "New" : "Read", UIKit.AMBER_PILL_BG, UIKit.AMBER_PILL_FG,
                String.valueOf(unread), "Notifications", unread + " unread", "Read notifications", 3));
        cardsRow.add(glanceCard("\uD83D\uDCAC", greenBg, greenFg, "Due", UIKit.GREEN_PILL_BG, new Color(20, 120, 60),
                myQueries.isEmpty() ? "0" : Math.round(answered * 100.0 / myQueries.size()) + "%", "Student Queries",
                myQueries.isEmpty() ? "no queries yet" : answered + " of " + myQueries.size() + " answered", "View queries", 4));
        cardsRow.revalidate();
        cardsRow.repaint();

        alertHolder.removeAll();
        if (!outOfStock.isEmpty()) {
            UIKit.RoundPanel alert = new UIKit.RoundPanel(14, UIKit.RED_PILL_BG);
            alert.setLayout(new BorderLayout());
            alert.setBorder(new EmptyBorder(12, 16, 12, 16));
            JLabel head = new JLabel("\u26A0 Out of stock right now");
            head.setFont(new Font("SansSerif", Font.BOLD, 12));
            head.setForeground(UIKit.RED_PILL_FG);
            JLabel body = new JLabel("<html><div style='width:640px'>" + String.join(", ", outOfStock).replace("&", "&amp;")
                    + "<br>Booking and rescheduling are paused for these until staff restock them. You'll get a notification when they're back.</div></html>");
            body.setFont(new Font("SansSerif", Font.PLAIN, 11));
            body.setForeground(UIKit.RED_PILL_FG);
            alert.add(head, BorderLayout.NORTH);
            alert.add(body, BorderLayout.CENTER);
            alertHolder.add(alert, BorderLayout.CENTER);
        }
        alertHolder.revalidate();
        alertHolder.repaint();
    }

    /** Text under "Upcoming Appointments", e.g. "confirmed - Oct 7". */
    private String nextApptSummary() {
        return store.getAppointmentsForStudent(store.getCurrentStudent().getId()).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED)
                .map(a -> "confirmed \u00B7 " + a.getDateTime().format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)))
                .findFirst().orElse("no confirmed appointment");
    }
}
