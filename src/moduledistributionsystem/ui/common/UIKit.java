package moduledistributionsystem.ui.common;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Shared look-and-feel helpers used by every screen: the color palette, rounded panels / buttons /
 * chips / pills, the thin scrollbar, the school logo and a few small formatters.
 *
 * How to customize: change a color constant in the palette below and every screen that uses it follows
 * (SAGE is the student sidebar, STEEL the staff sidebar). The page design shared by all portals
 * (header, stat cards, tables) lives in InventoryPanel (Page, RowPanel, SearchBox, Dropdown...).
 */
public final class UIKit {
    /** Utility class: only static members, so it is never instantiated. */
    private UIKit() { }

    /**
     * Makes typed text safe to put inside an HTML label (for example {@code new JLabel("<html>" + text + "</html>")}).
     * Without this, a typed "<b>" or "<img ...>" would be rendered as HTML instead of shown as text. The same
     * idea as PHP's htmlspecialchars(): &amp; &lt; &gt; and the quotes are replaced by their safe forms.
     */
    public static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    // ---------- Palette ----------
    public static final Color SAGE          = new Color(140, 181, 148); // sidebar background
    /** Active menu item / profile card of the student sidebar. */
    public static final Color SAGE_DARK     = new Color(110, 150, 118); // active menu item / profile card
    /** Page background (white). */
    public static final Color PAGE_BG       = Color.WHITE;
    /** Default gray card fill. */
    public static final Color CARD_BG       = new Color(228, 229, 230); // default gray card fill
    /** Slightly darker gray for chips nested inside a card. */
    public static final Color CARD_BG_2     = new Color(214, 215, 216); // nested chip fill
    /** Main text color. */
    public static final Color TEXT_DARK     = new Color(25, 28, 25);
    /** Secondary (gray) text color. */
    public static final Color TEXT_MUTED    = new Color(120, 122, 118);
    /** Black used for primary buttons and selected tabs. */
    public static final Color BLACK_PILL    = new Color(20, 20, 20);
    /** Light green background (success / available pills). */
    public static final Color GREEN_PILL_BG = new Color(224, 245, 232);
    /** Light amber background (pending / warning pills). */
    public static final Color AMBER_PILL_BG = new Color(255, 243, 205);
    /** Amber text color for pills. */
    public static final Color AMBER_PILL_FG = new Color(153, 105, 0);
    /** Light red background (error / out-of-stock pills and buttons). */
    public static final Color RED_PILL_BG   = new Color(255, 226, 226);
    /** Red text color for pills and error text. */
    public static final Color RED_PILL_FG   = new Color(180, 40, 40);
    /** Light blue background (info / picked-up pills). */
    public static final Color BLUE_PILL_BG  = new Color(219, 234, 254);
    /** Blue text color for pills. */
    public static final Color BLUE_PILL_FG  = new Color(29, 78, 216);
    /** Sidebar background of the staff portal (steel blue-gray). */
    public static final Color STEEL         = new Color(148, 168, 189); // staff sidebar
    /** Active menu item / profile card of the staff sidebar. */
    public static final Color STEEL_DARK    = new Color(96, 120, 145);  // active menu item / profile card

    /** Plain rounded-rect background panel — the workhorse card container. */
    public static class RoundPanel extends JPanel {
        private int radius;
        private Color bg;

        /** Creates a card with the given corner radius and fill color. */
        public RoundPanel(int radius, Color bg) {
            this.radius = radius;
            this.bg = bg;
            setOpaque(false);
        }

        /** Changes the fill color and repaints. */
        public void setBg(Color c) {
            this.bg = c;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }

        // Stretch full width inside a BoxLayout list, but never taller than the
        // content actually needs -- prevents a short list (e.g. 3 rows) from being
        // stretched to fill a tall scroll viewport with dead space.
        @Override
        public Dimension getMaximumSize() {
            Dimension pref = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, pref.height);
        }
    }

    /**
     * Wraps a fixed-size component (e.g. a circular badge) so that placing it in a
     * BorderLayout.WEST/EAST slot doesn't stretch it vertically to fill the row --
     * BorderLayout always stretches WEST/EAST children to the full row height, which
     * turns a circle into an oval. GridBagLayout here keeps the child at its own
     * preferred size, centered.
     */
    public static JPanel westWrap(JComponent inner) {
        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setOpaque(false);
        wrap.add(inner);
        return wrap;
    }

    /**
     * Wraps a table cell's component in a panel whose preferred width never drops below
     * minWidth. Pair this with a GridBagConstraints where fill=HORIZONTAL and
     * weightx=<that column's min pixel width> -- GridBagLayout then stretches this
     * wrapper to fill its share of any extra table width, and the FlowLayout alignment
     * inside positions the actual content (left/center) within that stretched width.
     * That's what keeps a value centered under its header instead of hugging the left
     * edge, and spreads leftover table width evenly across every column instead of
     * piling it all up after the last one.
     */
    public static JComponent cell(JComponent inner, int minWidth) {
        return cell(inner, minWidth, FlowLayout.LEFT);
    }

    /**
     * Wraps a component in a transparent cell that is at least minWidth wide, so table columns line up.
     * flowAlign is FlowLayout.LEFT / CENTER / RIGHT.
     */
    public static JComponent cell(JComponent inner, int minWidth, int flowAlign) {
        JPanel wrap = new JPanel(new FlowLayout(flowAlign, 0, 0)) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(Math.max(d.width, minWidth), d.height);
            }
        };
        wrap.setOpaque(false);
        wrap.add(inner);
        return wrap;
    }

    // ---------- Sidebar logo (school seal) ----------
    private static Image cachedLogo;
    /** Makes sure the logo file is only looked for once. */
    private static boolean logoLoadAttempted = false;

    /** The school's student-ID format: 00-0000-000000 (2 digits, 4 digits, 6 digits). Used both
     *  to validate new IDs Admin types in (Add Account) and to check what a student types on
     *  the login screen before bothering to look it up. */
    public static boolean isValidSchoolId(String id) {
        return id != null && id.matches("\\d{2}-\\d{4}-\\d{6}");
    }

    /** The registrar's subject codes read with a space ("ITE 031"); the stored code stays
     *  "ITE031" (no space) everywhere in the data layer, search, and DB. This is purely a
     *  display formatter -- used by Staff's Module Inventory, the student Modules page, and
     *  Admin's Enrollment dialog, so a code looks the same everywhere it's shown. */
    public static String displayCode(String code) {
        if (code == null) return "";
        return code.replaceFirst("^([A-Za-z]+)\\s*([0-9].*)$", "$1 $2");
    }

    /**
     * Loads the college seal (src/moduledistributionsystem/resources/coc_logo.png) once
     * and caches it. Returns null (and logs once) if the file isn't on the classpath yet,
     * so callers can fall back gracefully instead of throwing.
     */
    public static Image loadLogoImage() {
        if (!logoLoadAttempted) {
            logoLoadAttempted = true;
            java.net.URL url = UIKit.class.getResource("/moduledistributionsystem/resources/coc_logo.png");
            if (url != null) {
                cachedLogo = new ImageIcon(url).getImage();
            } else {
                System.err.println("Logo not found on classpath: /moduledistributionsystem/resources/coc_logo.png");
            }
        }
        return cachedLogo;
    }

    /** Circular white badge with the school seal centered inside -- used in every sidebar header. */
    public static class LogoBadge extends JComponent {
        /** The school seal image (may be null if the file is missing). */
        private final Image img;
        /** Diameter of the badge in pixels. */
        private final int size;

        /** Creates a round badge of the given size (pixels). */
        public LogoBadge(int size) {
            this.img = loadLogoImage();
            this.size = size;
            setOpaque(false);
            setPreferredSize(new Dimension(size, size));
        }

        /** The badge is always a square of its size. */
        @Override
        public Dimension getPreferredSize() { return new Dimension(size, size); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setColor(Color.WHITE);
            g2.fillOval(0, 0, size, size);
            if (img != null) {
                // Small inset so the seal doesn't touch the white circle's edge.
                int inset = Math.max(1, size / 14);
                g2.drawImage(img, inset, inset, size - inset * 2, size - inset * 2, this);
            }
            g2.dispose();
        }
    }

    /** Small colored square holding a glyph/emoji, used as a card or list-item icon. */
    public static class IconTile extends JPanel {
        private Color bg;

        /** Creates a small rounded tile with a symbol / emoji (glyph) in the given colors. */
        public IconTile(String glyph, Color bg, Color fg) {
            this.bg = bg;
            setLayout(new GridBagLayout());
            setOpaque(false);
            setPreferredSize(new Dimension(40, 40));
            JLabel l = new JLabel(glyph);
            l.setFont(new Font("SansSerif", Font.BOLD, 15));
            l.setForeground(fg);
            add(l);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Rounded pill/badge label, e.g. status tags ("Confirmed", "Pending", "live"). Sizes itself to its text. */
    public static class Pill extends JLabel {
        private Color bg;
        private int minWidth = 0;

        /** Creates a small rounded label (status pill) with the given text and colors. */
        public Pill(String text, Color bg, Color fg) {
            super(text);
            this.bg = bg;
            setForeground(fg);
            setFont(new Font("SansSerif", Font.BOLD, 11));
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorder(new EmptyBorder(4, 10, 4, 10));
        }

        /** Forces every pill using the same minWidth to render at the same size,
         *  regardless of how long its text is (e.g. "Ready" vs "Pending"). */
        public Pill withMinWidth(int w) {
            this.minWidth = w;
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            if (d.width < minWidth) d.width = minWidth;
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Flat rounded button with a hover tint. Sizes itself from its own text metrics
     * (via an EmptyBorder that contributes real padding to getPreferredSize()) instead
     * of a guessed pixel width, so it never truncates regardless of platform/font/DPI.
     */
    public static class RoundButton extends JButton {
        private Color base, hover, fg;
        private int radius;
        private boolean hovering = false;

        /**
         * Rounded button with default padding. base = normal color, hover = color under the mouse, fg =
         * text color.
         */
        public RoundButton(String text, Color base, Color hover, Color fg, int radius) {
            this(text, base, hover, fg, radius, 9, 18);
        }

        /** Rounded button with custom vertical and horizontal padding (smaller buttons for table rows). */
        public RoundButton(String text, Color base, Color hover, Color fg, int radius, int vPad, int hPad) {
            super(text);
            this.base = base;
            this.hover = hover;
            this.fg = fg;
            this.radius = radius;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setForeground(fg);
            setFont(new Font("SansSerif", Font.BOLD, 13));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            // Padding-only border: not painted, but its insets are still added to
            // getPreferredSize() by JButton, so the pill always fits its own text.
            setBorder(new EmptyBorder(vPad, hPad, vPad, hPad));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { hovering = true; repaint(); }
                @Override
                public void mouseExited(MouseEvent e) { hovering = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hovering ? hover : base);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Toggle-style segmented button used for tab bars and filter chips. Also self-sizing. */
    public static class ToggleChip extends JToggleButton {
        private int radius;

        /**
         * Creates a chip that can be selected (selected = black, unselected = gray). Used for tabs and
         * filters.
         */
        public ToggleChip(String text, int radius) {
            super(text);
            this.radius = radius;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFont(new Font("SansSerif", Font.PLAIN, 12));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(8, 18, 8, 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isSelected()) {
                g2.setColor(BLACK_PILL);
                setForeground(Color.WHITE);
            } else {
                g2.setColor(new Color(235, 235, 235));
                setForeground(TEXT_DARK);
            }
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Thin, rounded, arrow-less scrollbar thumb — replaces the clunky default OS scrollbar. */
    public static class ModernScrollBarUI extends BasicScrollBarUI {
        /** No arrow button at the top / left: the thin scrollbar has none. */
        @Override
        protected JButton createDecreaseButton(int orientation) { return zeroButton(); }

        /** No arrow button at the bottom / right. */
        @Override
        protected JButton createIncreaseButton(int orientation) { return zeroButton(); }

        /** An invisible zero-size button used in place of the scrollbar arrows. */
        private JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        /** Draws nothing: the track is transparent. */
        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            // transparent track — no boxy background
        }

        /** Draws the rounded scrollbar thumb. */
        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !c.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(190, 192, 190));
            int pad = 4;
            g2.fillRoundRect(thumbBounds.x + pad, thumbBounds.y + 2,
                    Math.max(4, thumbBounds.width - pad * 2), Math.max(4, thumbBounds.height - 4), 8, 8);
            g2.dispose();
        }

        /** Smallest size of the scrollbar thumb. */
        @Override
        protected Dimension getMinimumThumbSize() {
            return new Dimension(8, 40);
        }
    }

    /**
     * Standard scroll wrapper used everywhere in the dashboard: transparent, borderless,
     * with the thin modern scrollbar instead of the platform's default one.
     */
    public static JScrollPane scroll(JComponent view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(null);
        // Nimbus (the app's look-and-feel) paints its own viewport border by default,
        // separately from the scroll pane's own border above -- clearing setBorder(null)
        // alone leaves this thin inset line visible as a rectangle framing the whole
        // scroll area, which is what card content was visually butting up against.
        sp.setViewportBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JScrollBar vbar = sp.getVerticalScrollBar();
        vbar.setUnitIncrement(16);
        vbar.setUI(new ModernScrollBarUI());
        vbar.setOpaque(false);
        vbar.setPreferredSize(new Dimension(10, Integer.MAX_VALUE));
        return sp;
    }

    /**
     * Vertical list container for use inside UIKit.scroll(...). Implementing Scrollable
     * with tracksViewportWidth=true forces this panel's width to always match the visible
     * scroll area exactly -- without this, a plain JPanel can end up very slightly wider
     * than the viewport, and since these scroll panes have no horizontal scrollbar, that
     * extra width silently renders past the right edge instead of being reachable.
     * tracksViewportHeight=false is what lets the list grow taller than the viewport and
     * actually scroll, instead of being stretched to fill it.
     */
    public static class ScrollableBoxPanel extends JPanel implements Scrollable {
        /**
         * Creates a vertical column of components that scrolls inside a JScrollPane and follows its
         * width.
         */
        public ScrollableBoxPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 100; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Same as ScrollableBoxPanel but for the shared-GridBagLayout tables (Staff pages). */
    public static class ScrollableGridPanel extends JPanel implements Scrollable {
        /** Creates a grid-layout panel that scrolls inside a JScrollPane and follows its width. */
        public ScrollableGridPanel() {
            setLayout(new GridBagLayout());
            setOpaque(false);
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 100; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
