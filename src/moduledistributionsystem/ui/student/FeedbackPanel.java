package moduledistributionsystem.ui.student;

import moduledistributionsystem.ui.student.StudentMainFrame;
import moduledistributionsystem.ui.common.UIKit;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.model.Feedback;

/**
 * Feedback form: choose what the feedback is about, give 1-5 stars, write an optional comment, and see
 * your past feedback.
 *
 * NOTE: this page is not in the student menu at the moment (the Student Queries page took its place).
 * It still compiles and works, so it can be added back by adding it to StudentMainFrame (MENU_LABELS,
 * MENU_GLYPHS and the card layout).
 */
public class FeedbackPanel extends JPanel {
    /** The shared data store. */
    private DemoDataStore store;
    /** The student window. */
    private StudentMainFrame parent;

    /** What the feedback can be about. Add or rename here. */
    private static final String[] CATEGORIES = {"Distribution Process", "Staff & Service", "Booking System", "Module Materials", "Other"};
    /** One chip per category. */
    private UIKit.ToggleChip[] categoryChips;
    private ButtonGroup categoryGroup = new ButtonGroup();

    /** The five rating stars. */
    private JLabel[] stars = new JLabel[5];
    /** The chosen rating (0 = not chosen yet). */
    private int selectedRating = 0;

    /** The optional comment. */
    private JTextArea commentArea;
    /** Character counter under the comment box. */
    private JLabel charHint;

    /** Holds the student's past feedback rows. */
    private JPanel pastHolder;
    /** True while the past feedback list is open. */
    private boolean pastExpanded = false;
    /** The "show / hide past feedback" link. */
    private JLabel pastToggleLabel;

    /** Builds the page. */
    public FeedbackPanel(DemoDataStore store, StudentMainFrame parent) {
        this.store = store;
        this.parent = parent;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(28, 32, 28, 32));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel title = new JLabel("Feedback");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("Help us improve the module distribution experience");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(UIKit.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        header.add(Box.createVerticalStrut(18));
        add(header, BorderLayout.NORTH);

        JPanel content = new UIKit.ScrollableBoxPanel();
        content.setBorder(new EmptyBorder(0, 0, 0, 6));

        content.add(buildCategoryCard());
        content.add(Box.createVerticalStrut(14));
        content.add(buildRatingCard());
        content.add(Box.createVerticalStrut(14));
        content.add(buildCommentCard());
        content.add(Box.createVerticalStrut(16));

        UIKit.RoundButton submitButton = new UIKit.RoundButton("Submit Feedback", UIKit.BLACK_PILL, new Color(40, 40, 40), Color.WHITE, 10);
        submitButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        submitButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        submitButton.addActionListener(e -> submitFeedback());
        content.add(submitButton);
        content.add(Box.createVerticalStrut(14));

        content.add(buildPastSection());

        add(UIKit.scroll(content), BorderLayout.CENTER);

        refreshPast();
    }

    /** Builds the category chips card. */
    private JPanel buildCategoryCard() {
        UIKit.RoundPanel card = new UIKit.RoundPanel(14, UIKit.CARD_BG);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 20, 18, 20));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel q = new JLabel("What are you giving feedback about?");
        q.setFont(new Font("SansSerif", Font.BOLD, 14));
        q.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(q);
        card.add(Box.createVerticalStrut(12));

        JPanel chipsWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        chipsWrap.setOpaque(false);
        chipsWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        categoryChips = new UIKit.ToggleChip[CATEGORIES.length];
        for (int i = 0; i < CATEGORIES.length; i++) {
            UIKit.ToggleChip chip = new UIKit.ToggleChip(CATEGORIES[i], 8);
            categoryGroup.add(chip);
            categoryChips[i] = chip;
            chipsWrap.add(chip);
        }
        card.add(chipsWrap);
        return card;
    }

    /** Builds the star rating card. */
    private JPanel buildRatingCard() {
        UIKit.RoundPanel card = new UIKit.RoundPanel(14, UIKit.CARD_BG);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 20, 18, 20));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel q = new JLabel("Overall experience rating");
        q.setFont(new Font("SansSerif", Font.BOLD, 14));
        q.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(q);
        card.add(Box.createVerticalStrut(10));

        JPanel starsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        starsRow.setOpaque(false);
        starsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < 5; i++) {
            int idx = i + 1;
            JLabel star = new JLabel("\u2606");
            star.setFont(new Font("SansSerif", Font.PLAIN, 26));
            star.setForeground(new Color(180, 180, 180));
            star.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            star.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) { selectedRating = idx; paintStars(); }
                @Override
                public void mouseEntered(MouseEvent e) { previewStars(idx); }
                @Override
                public void mouseExited(MouseEvent e) { paintStars(); }
            });
            stars[i] = star;
            starsRow.add(star);
        }
        card.add(starsRow);
        return card;
    }

    /** Fills the first "count" stars (also used for the hover preview). */
    private void previewStars(int count) {
        for (int i = 0; i < 5; i++) {
            stars[i].setText(i < count ? "\u2605" : "\u2606");
            stars[i].setForeground(i < count ? new Color(240, 180, 30) : new Color(180, 180, 180));
        }
    }

    /** Shows the chosen rating. */
    private void paintStars() { previewStars(selectedRating); }

    /** Builds the comment card. */
    private JPanel buildCommentCard() {
        UIKit.RoundPanel card = new UIKit.RoundPanel(14, UIKit.CARD_BG);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 20, 18, 20));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel q = new JLabel("Your comments");
        q.setFont(new Font("SansSerif", Font.BOLD, 14));
        q.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(q);
        card.add(Box.createVerticalStrut(10));

        commentArea = new JTextArea(5, 40);
        commentArea.setLineWrap(true);
        commentArea.setWrapStyleWord(true);
        commentArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
        commentArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        JScrollPane scroll = new JScrollPane(commentArea);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        card.add(scroll);
        card.add(Box.createVerticalStrut(6));

        charHint = new JLabel("Minimum 20 characters");
        charHint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        charHint.setForeground(new Color(60, 110, 220));
        charHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(charHint);

        commentArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateHint(); }
            public void removeUpdate(DocumentEvent e) { updateHint(); }
            public void changedUpdate(DocumentEvent e) { updateHint(); }
        });

        return card;
    }

    /** Updates the character counter. */
    private void updateHint() {
        int len = commentArea.getText().trim().length();
        if (len >= 20) {
            charHint.setText(len + " characters");
            charHint.setForeground(new Color(30, 150, 80));
        } else {
            charHint.setText("Minimum 20 characters (" + len + "/20)");
            charHint.setForeground(new Color(60, 110, 220));
        }
    }

    /** Builds the collapsible past-feedback section. */
    private JPanel buildPastSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        pastToggleLabel = new JLabel();
        pastToggleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        pastToggleLabel.setForeground(UIKit.TEXT_MUTED);
        pastToggleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        pastToggleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pastToggleLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                pastExpanded = !pastExpanded;
                refreshPast();
            }
        });
        section.add(pastToggleLabel);
        section.add(Box.createVerticalStrut(8));

        pastHolder = new JPanel();
        pastHolder.setLayout(new BoxLayout(pastHolder, BoxLayout.Y_AXIS));
        pastHolder.setOpaque(false);
        pastHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(pastHolder);

        return section;
    }

    /** Builds one row of past feedback. */
    private JPanel pastFeedbackRow(Feedback f) {
        UIKit.RoundPanel row = new UIKit.RoundPanel(12, UIKit.CARD_BG);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBorder(new EmptyBorder(12, 14, 12, 14));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.setOpaque(false);
        JLabel cat = new JLabel(f.getCategory());
        cat.setFont(new Font("SansSerif", Font.BOLD, 12));
        JLabel starsLabel = new JLabel(repeatStar(f.getRating()));
        starsLabel.setForeground(new Color(240, 180, 30));
        top.add(cat);
        top.add(starsLabel);
        row.add(top);

        JLabel comment = new JLabel("<html><div style='width:500px'>" + (f.getComment().isEmpty() ? "(no comment)" : UIKit.escapeHtml(f.getComment())) + "</div></html>");
        comment.setFont(new Font("SansSerif", Font.PLAIN, 12));
        comment.setForeground(new Color(70, 70, 70));
        comment.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(comment);

        JLabel date = new JLabel(f.getSubmittedAt().format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")));
        date.setFont(new Font("SansSerif", Font.PLAIN, 10));
        date.setForeground(UIKit.TEXT_MUTED);
        date.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(date);

        return row;
    }

    /** Returns n filled star characters. */
    private String repeatStar(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append("\u2605");
        return sb.toString();
    }

    /** Checks the form and saves the feedback through the store. */
    private void submitFeedback() {
        String category = null;
        for (int i = 0; i < categoryChips.length; i++) {
            if (categoryChips[i].isSelected()) { category = CATEGORIES[i]; break; }
        }
        if (category == null) {
            JOptionPane.showMessageDialog(this, "Please choose what your feedback is about.", "Feedback", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (selectedRating == 0) {
            JOptionPane.showMessageDialog(this, "Please give an overall rating.", "Feedback", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String comment = commentArea.getText().trim();
        if (comment.length() < 20) {
            int ok = JOptionPane.showConfirmDialog(this, "Your comment is under 20 characters. Submit anyway?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
        }
        store.addFeedback(new Feedback(store.getCurrentStudent().getId(), category, selectedRating, comment));
        JOptionPane.showMessageDialog(this, "Thank you for your feedback.", "Submitted", JOptionPane.INFORMATION_MESSAGE);

        categoryGroup.clearSelection();
        selectedRating = 0;
        paintStars();
        commentArea.setText("");
        updateHint();
        refreshPast();
        if (parent != null) SwingUtilities.invokeLater(() -> parent.refreshAll());
    }

    /** Rebuilds the past feedback list. */
    private void refreshPast() {
        List<Feedback> list = store.getFeedbacksForStudent(store.getCurrentStudent().getId());
        pastToggleLabel.setText((pastExpanded ? "\u25BC " : "\u25B6 ") + "Past feedback submissions (" + list.size() + ")");
        pastHolder.removeAll();
        if (pastExpanded) {
            pastHolder.add(Box.createVerticalStrut(4));
            boolean any = false;
            List<Feedback> reversed = new ArrayList<>(list);
            Collections.reverse(reversed);
            for (Feedback f : reversed) {
                if (any) pastHolder.add(Box.createVerticalStrut(8));
                pastHolder.add(pastFeedbackRow(f));
                any = true;
            }
        }
        pastHolder.revalidate();
        pastHolder.repaint();
    }

    /** Re-reads this student's feedback. */
    public void refresh() {
        refreshPast();
    }
}
