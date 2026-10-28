package moduledistributionsystem.ui.student;

import moduledistributionsystem.ui.student.StudentMainFrame;
import moduledistributionsystem.ui.common.UIKit;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.model.NotificationItem;

/**
 * Student page "Notifications": a list of cards, newest first, with a colored tag (Appointment, Module
 * Ready, Stock Alert, Reminder, Query...), the time, and Mark read / Dismiss buttons. "Mark all read"
 * is at the top.
 *
 * The notifications themselves are created by DemoDataStore (appointment changes, out-of-stock and
 * available alerts, query answers). The tag of a card is chosen from words in its title (categoryOf).
 */
public class NotificationsPanel extends JPanel {
    /** The shared data store. */
    private DemoDataStore store;
    /** The student window (told to refresh the Overview counts). */
    private StudentMainFrame parent;
    private JLabel subtitleLabel = new JLabel();
    /** Holds the notification cards. */
    private JPanel listHolder;

    /** Builds the page: title, "Mark all read" and the (empty) list. */
    public NotificationsPanel(DemoDataStore store, StudentMainFrame parent) {
        this.store = store;
        this.parent = parent;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(28, 32, 28, 32));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 18, 0));

        JPanel headerLeft = new JPanel();
        headerLeft.setLayout(new BoxLayout(headerLeft, BoxLayout.Y_AXIS));
        headerLeft.setOpaque(false);
        JLabel title = new JLabel("Notifications");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitleLabel.setForeground(UIKit.TEXT_MUTED);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerLeft.add(title);
        headerLeft.add(subtitleLabel);
        header.add(headerLeft, BorderLayout.WEST);

        UIKit.RoundButton markAll = new UIKit.RoundButton("Mark all read", UIKit.BLACK_PILL, new Color(40, 40, 40), Color.WHITE, 8);
        markAll.addActionListener(e -> markAllRead());
        JPanel headerRight = new JPanel(new GridBagLayout());
        headerRight.setOpaque(false);
        headerRight.add(markAll);
        header.add(headerRight, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        listHolder = new UIKit.ScrollableBoxPanel();
        listHolder.setBorder(new EmptyBorder(0, 0, 0, 6));
        add(UIKit.scroll(listHolder), BorderLayout.CENTER);

        refresh();
    }

    /** Marks every notification of this student as read. */
    private void markAllRead() {
        for (NotificationItem n : store.getNotificationsForStudent(store.getCurrentStudent().getId())) store.markNotificationRead(n);
        refresh();
        if (parent != null) SwingUtilities.invokeLater(() -> parent.refreshAll());
    }

    /** Chooses the tag (label + icon) of a notification from words in its title. */
    private String[] categoryOf(String title) {
        String t = title.toLowerCase();
        if (t.contains("out of stock") || t.contains("unavailable")) return new String[]{"Stock Alert", "\u26A0"};
        if (t.contains("module")) return new String[]{"Module Ready", "\uD83D\uDCD8"};
        if (t.contains("confirm") || t.contains("complet") || t.contains("appointment")) return new String[]{"Appointment", "\u2705"};
        if (t.contains("reminder") || t.contains("tomorrow")) return new String[]{"Reminder", "\u23F0"};
        if (t.contains("schedule")) return new String[]{"Schedule", "\uD83D\uDCC5"};
        return new String[]{"Update", "\uD83D\uDD14"};
    }

    /** Text and background colors of a tag. */
    private Color[] categoryColors(String category) {
        switch (category) {
            case "Module Ready": return new Color[]{new Color(20, 30, 60), UIKit.BLUE_PILL_BG, UIKit.BLUE_PILL_FG};
            case "Stock Alert":  return new Color[]{new Color(150, 30, 30), UIKit.RED_PILL_BG, UIKit.RED_PILL_FG};
            case "Appointment":  return new Color[]{new Color(20, 90, 55), UIKit.GREEN_PILL_BG, new Color(20, 120, 60)};
            case "Reminder":     return new Color[]{new Color(150, 70, 20), UIKit.AMBER_PILL_BG, UIKit.AMBER_PILL_FG};
            case "Schedule":     return new Color[]{new Color(20, 30, 60), UIKit.BLUE_PILL_BG, UIKit.BLUE_PILL_FG};
            default:             return new Color[]{new Color(70, 70, 70), new Color(230, 230, 230), UIKit.TEXT_DARK};
        }
    }

    /** Builds one card: tag, title, message, time and the Mark read / Dismiss buttons. */
    private JPanel notificationCard(NotificationItem n) {
        UIKit.RoundPanel card = new UIKit.RoundPanel(14, UIKit.CARD_BG);
        card.setLayout(new BorderLayout(14, 0));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        String[] cat = categoryOf(n.getTitle());
        Color[] colors = categoryColors(cat[0]);
        card.add(new UIKit.IconTile(cat[1], colors[0], Color.WHITE), BorderLayout.WEST);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel titleLabel = new JLabel(n.getTitle());
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleRow.add(titleLabel);
        titleRow.add(new UIKit.Pill(cat[0], colors[1], colors[2]));
        if (!n.isRead()) {
            JLabel dot = new JLabel("\u25CF");
            dot.setForeground(new Color(60, 120, 230));
            dot.setFont(new Font("SansSerif", Font.PLAIN, 10));
            titleRow.add(dot);
        }

        JLabel message = new JLabel("<html><div style='width:420px'>" + n.getMessage().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</div></html>");
        message.setFont(new Font("SansSerif", Font.PLAIN, 12));
        message.setForeground(new Color(70, 70, 70));
        message.setAlignmentX(Component.LEFT_ALIGNMENT);
        message.setBorder(new EmptyBorder(4, 0, 4, 0));

        JLabel timeLabel = new JLabel(relativeTime(n.getTimestamp()));
        timeLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        timeLabel.setForeground(UIKit.TEXT_MUTED);
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        center.add(titleRow);
        center.add(message);
        center.add(timeLabel);
        card.add(center, BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.setOpaque(false);
        actions.add(Box.createVerticalGlue());
        JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actionsRow.setOpaque(false);
        if (!n.isRead()) {
            UIKit.RoundButton markRead = new UIKit.RoundButton("Mark read", UIKit.BLACK_PILL, new Color(40, 40, 40), Color.WHITE, 6, 6, 12);
            markRead.addActionListener(e -> {
                store.markNotificationRead(n);
                refresh();
                if (parent != null) SwingUtilities.invokeLater(() -> parent.refreshAll());
            });
            actionsRow.add(markRead);
        }
        UIKit.RoundButton dismiss = new UIKit.RoundButton("Dismiss", new Color(225, 225, 225), new Color(210, 210, 210), UIKit.TEXT_DARK, 6, 6, 12);
        dismiss.addActionListener(e -> {
            store.removeNotification(n);
            refresh();
            if (parent != null) SwingUtilities.invokeLater(() -> parent.refreshAll());
        });
        actionsRow.add(dismiss);
        actions.add(actionsRow);
        actions.add(Box.createVerticalGlue());
        card.add(actions, BorderLayout.EAST);

        return card;
    }

    /** Friendly time such as "Just now", "5 minutes ago" or "Yesterday". */
    private String relativeTime(LocalDateTime ts) {
        Duration d = Duration.between(ts, LocalDateTime.now());
        long hours = d.toHours();
        if (hours < 1) return "Just now";
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = d.toDays();
        if (days == 1) return "Yesterday";
        if (days < 7) return days + " days ago";
        return ts.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"));
    }

    /** Re-reads the notifications and rebuilds the list. */
    public void refresh() {
        List<NotificationItem> mine = store.getNotificationsForStudent(store.getCurrentStudent().getId());
        long unread = mine.stream().filter(n -> !n.isRead()).count();
        subtitleLabel.setText(unread + " unread " + (unread == 1 ? "alert" : "alerts"));

        listHolder.removeAll();
        boolean any = false;
        for (NotificationItem n : mine) {
            if (any) listHolder.add(Box.createVerticalStrut(12));
            listHolder.add(notificationCard(n));
            any = true;
        }
        if (!any) {
            JLabel empty = new JLabel("You're all caught up.");
            empty.setFont(new Font("SansSerif", Font.PLAIN, 13));
            empty.setForeground(UIKit.TEXT_MUTED);
            listHolder.add(empty);
        }
        listHolder.revalidate();
        listHolder.repaint();
    }
}
