package moduledistributionsystem.ui.common;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import moduledistributionsystem.ui.admin.AdminMainFrame;
import moduledistributionsystem.model.AuthResult;
import moduledistributionsystem.data.DemoDataStore;
import moduledistributionsystem.model.Role;
import moduledistributionsystem.ui.staff.StaffMainFrame;
import moduledistributionsystem.ui.student.StudentMainFrame;

/**
 * The login screen: school background, a card with School ID / e-mail + password fields, a "Forgot
 * password?" link and the Sign In button, with small fade / focus animations.
 *
 * What it does on Sign In: asks DemoDataStore.authenticate(...) and, when it succeeds, opens the main
 * window that matches the role (StudentMainFrame, StaffMainFrame or AdminMainFrame). On failure it
 * shows the reason (wrong password, deactivated, archived or locked).
 *
 * How to customize: card size and corner radius are the CARD_* constants below; the background image is
 * src/.../resources/coc1_1.jpg; texts are set in the constructor.
 */
public class LoginPanel extends JPanel {
    /** The shared data store used to check the login. */
    private final DemoDataStore store;
    /** The login window that holds this panel (closed after a successful login). */
    private final JFrame parent;

    // ---------- Palette ----------
    // Kept a single accent hue running through the border, focus glow, button
    // and demo box so the card doesn't feel like unrelated pieces stitched together.
    private static final Color CARD_BG      = new Color(255, 255, 255);
    private static final Color CARD_BORDER  = new Color(214, 232, 218);       // faint green edge so the white card doesn't float
    private static final Color FIELD_BG     = new Color(244, 248, 245);
    private static final Color TEXT_DARK    = new Color(35, 40, 37);
    private static final Color TEXT_MUTED   = new Color(105, 115, 108);
    private static final Color ACCENT       = new Color(96, 191, 110);        // core green accent
    private static final Color ACCENT_DARK  = new Color(58, 138, 74);         // hover text

    /** Width of the white login card (pixels). */
    private static final int CARD_W = 600;
    /** Height of the white login card (pixels). */
    private static final int CARD_H = 492;
    /** Corner radius of the login card. */
    private static final int CARD_RADIUS = 25;

    /** The full-window background (school photo with a fade). */
    private BackgroundPanel outerBackground;
    /** The login card. */
    private RoundedPanel card;

    // ---------- Transparent background layer, draws a soft drop shadow ----------
    static class BackgroundPanel extends JPanel {
        /** 0 to 1: how far the background has faded in. */
        private float fade = 0f;
        /** Size and corner radius of the login card (used to paint its shadow). */
        private final int cardW, cardH, radius;
        /** The school photo (null until it is loaded). */
        private Image bgImage;

        BackgroundPanel(int cardW, int cardH, int radius) {
            super(new GridBagLayout());
            this.cardW = cardW;
            this.cardH = cardH;
            this.radius = radius;
            setOpaque(false);
        }

        /** Sets the school photo and repaints. */
        void setBackgroundImage(Image image) {
            this.bgImage = image;
            repaint();
        }

        /** Sets how far the background has faded in (0 to 1) and repaints. */
        void setFade(float f) {
            this.fade = f;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (bgImage != null) {
                Graphics2D gImg = (Graphics2D) g.create();
                gImg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                int panelW = getWidth();
                int panelH = getHeight();
                int imgW = bgImage.getWidth(this);
                int imgH = bgImage.getHeight(this);
                if (imgW > 0 && imgH > 0) {
                    double scale = Math.max((double) panelW / imgW, (double) panelH / imgH);
                    int drawW = (int) Math.ceil(imgW * scale);
                    int drawH = (int) Math.ceil(imgH * scale);
                    int x = (panelW - drawW) / 2;
                    int y = (panelH - drawH) / 2;
                    gImg.drawImage(bgImage, x, y, drawW, drawH, this);
                }
                gImg.dispose();
            }

            if (fade <= 0f) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int x = (getWidth() - cardW) / 2;
            int y = (getHeight() - cardH) / 2;

            // Smoother falloff: more layers, smaller alpha steps, tighter growth
            // so the shadow reads as a soft blur instead of visible rings.
            int layers = 24;
            for (int i = layers; i > 0; i--) {
                float t = (float) i / layers;
                int alpha = (int) (10 * t * t * fade); // t*t = faster falloff near the edge
                if (alpha <= 0) continue;
                g2.setColor(new Color(20, 30, 22, alpha));
                int grow = (int) (i * 0.9);
                g2.fillRoundRect(x - grow / 2, y - grow / 2 + 6, cardW + grow, cardH + grow, radius + 6, radius + 6);
            }
            g2.dispose();
        }
    }

    // ---------- Rounded card panel with a fade-in entrance + faint border ----------
    static class RoundedPanel extends JPanel {
        /** Corner radius. */
        private final int radius;
        /** Fill color of the card. */
        private final Color backgroundColor;
        /** Border color (null = no border). */
        private Color borderColor = null;
        /** Opacity from 0 (invisible) to 1; used by the fade-in. */
        private float alpha = 0f;

        RoundedPanel(int radius, Color bgColor) {
            super(new GridBagLayout());
            this.radius = radius;
            this.backgroundColor = bgColor;
            setOpaque(false);
        }

        /** Sets the border color and repaints. */
        void setBorderColor(Color c) {
            this.borderColor = c;
            repaint();
        }

        /** Sets the opacity (0 to 1) and repaints. */
        void setAlpha(float a) {
            this.alpha = a;
            repaint();
        }

        /** The current opacity. */
        float getAlphaValue() {
            return alpha;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setColor(backgroundColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            if (borderColor != null) {
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(borderColor);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g2.dispose();
        }

        @Override
        protected void paintChildren(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            super.paintChildren(g2);
            g2.dispose();
        }
    }

    // ---------- Rounded text field with animated focus glow ----------
    static class RoundedTextField extends JTextField {
        private final int radius;
        /** 0 to 1: how far the focus animation of the border has run. */
        private float focusProgress = 0f;
        /** Drives the focus animation. */
        private Timer focusTimer;

        RoundedTextField(int columns, int radius) {
            super(columns);
            this.radius = radius;
            setOpaque(false);
            setForeground(Color.BLACK);
            setCaretColor(Color.BLACK);
            setBorder(new EmptyBorder(8, 12, 8, 12));
            setFont(getFont().deriveFont(Font.PLAIN, 14f));

            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) { animateFocus(true); }
                @Override
                public void focusLost(FocusEvent e) { animateFocus(false); }
            });
        }

        /** Smoothly highlights (or un-highlights) the border when the field gains or loses focus. */
        private void animateFocus(boolean gaining) {
            if (focusTimer != null && focusTimer.isRunning()) focusTimer.stop();
            focusTimer = new Timer(15, null);
            focusTimer.addActionListener(e -> {
                focusProgress += gaining ? 0.1f : -0.1f;
                if (gaining && focusProgress >= 1f) { focusProgress = 1f; focusTimer.stop(); }
                if (!gaining && focusProgress <= 0f) { focusProgress = 0f; focusTimer.stop(); }
                repaint();
            });
            focusTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(FIELD_BG);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            // Faint resting border so fields read as inputs even before focus
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(0, 0, 0, 18));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            if (focusProgress > 0f) {
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), (int) (220 * focusProgress)));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, radius, radius);
            }
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        protected void paintBorder(Graphics g) { }
    }

    // ---------- Rounded password field, same treatment ----------
    static class RoundedPasswordField extends JPasswordField {
        private final int radius;
        /** 0 to 1: how far the focus animation of the border has run. */
        private float focusProgress = 0f;
        /** Drives the focus animation. */
        private Timer focusTimer;

        RoundedPasswordField(int columns, int radius) {
            super(columns);
            this.radius = radius;
            setOpaque(false);
            setForeground(Color.BLACK);
            setCaretColor(Color.BLACK);
            setBorder(new EmptyBorder(8, 12, 8, 12));
            setFont(getFont().deriveFont(Font.PLAIN, 14f));

            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) { animateFocus(true); }
                @Override
                public void focusLost(FocusEvent e) { animateFocus(false); }
            });
        }

        /** Smoothly highlights (or un-highlights) the border when the field gains or loses focus. */
        private void animateFocus(boolean gaining) {
            if (focusTimer != null && focusTimer.isRunning()) focusTimer.stop();
            focusTimer = new Timer(15, null);
            focusTimer.addActionListener(e -> {
                focusProgress += gaining ? 0.1f : -0.1f;
                if (gaining && focusProgress >= 1f) { focusProgress = 1f; focusTimer.stop(); }
                if (!gaining && focusProgress <= 0f) { focusProgress = 0f; focusTimer.stop(); }
                repaint();
            });
            focusTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(FIELD_BG);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(0, 0, 0, 18));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            if (focusProgress > 0f) {
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), (int) (220 * focusProgress)));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, radius, radius);
            }
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        protected void paintBorder(Graphics g) { }
    }

    // ---------- Rounded button with animated hover + press feedback ----------
    static class RoundedButton extends JButton {
        private final int radius;
        /** 0 to 1: how far the hover animation has run. */
        private float hoverProgress = 0f;
        /** Drives the hover animation. */
        private Timer hoverTimer;
        /** True while the mouse button is held down on the button. */
        private boolean pressed = false;

        RoundedButton(String text, int radius) {
            super(text);
            this.radius = radius;
            setOpaque(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setForeground(Color.WHITE);
            setFont(getFont().deriveFont(Font.BOLD, 15f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { animateHover(true); }
                @Override
                public void mouseExited(MouseEvent e) { animateHover(false); }
                @Override
                public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
                @Override
                public void mouseReleased(MouseEvent e) { pressed = false; repaint(); }
            });
        }

        /** Smoothly fades the button color when the mouse enters or leaves. */
        private void animateHover(boolean entering) {
            if (hoverTimer != null && hoverTimer.isRunning()) hoverTimer.stop();
            hoverTimer = new Timer(12, null);
            hoverTimer.addActionListener(e -> {
                hoverProgress += entering ? 0.15f : -0.15f;
                if (entering && hoverProgress >= 1f) { hoverProgress = 1f; hoverTimer.stop(); }
                if (!entering && hoverProgress <= 0f) { hoverProgress = 0f; hoverTimer.stop(); }
                repaint();
            });
            hoverTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Solid accent green base, darkening slightly on hover -- reads as a
            // real primary action button instead of a plain white pill.
            int r  = (int) (ACCENT.getRed()   + (ACCENT_DARK.getRed()   - ACCENT.getRed())   * hoverProgress);
            int gg = (int) (ACCENT.getGreen() + (ACCENT_DARK.getGreen() - ACCENT.getGreen()) * hoverProgress);
            int b  = (int) (ACCENT.getBlue()  + (ACCENT_DARK.getBlue()  - ACCENT.getBlue())  * hoverProgress);

            int shrink = pressed ? 2 : 0;
            g2.setColor(new Color(r, gg, b));
            g2.fillRoundRect(shrink, shrink, getWidth() - shrink * 2, getHeight() - shrink * 2, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------- Link-style label with underline-on-hover ----------
    static class LinkLabel extends JLabel {
        LinkLabel(String text, int alignment) {
            super(text, alignment);
            setForeground(TEXT_MUTED);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    setForeground(ACCENT_DARK);
                    setText("<html><u>" + text + "</u></html>");
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    setForeground(TEXT_MUTED);
                    setText(text);
                }
            });
        }
    }

    /**
     * Builds the whole login screen: fields, link, button and the actions that run on Sign In and on
     * "Forgot password?".
     */
    public LoginPanel(DemoDataStore store, JFrame parent) {
        this.store = store;
        this.parent = parent;

        setLayout(new BorderLayout());
        setOpaque(false);

        outerBackground = new BackgroundPanel(CARD_W, CARD_H, CARD_RADIUS);
        add(outerBackground, BorderLayout.CENTER);

        java.net.URL bgUrl = getClass().getResource("/moduledistributionsystem/resources/coc1_1.jpg");
        if (bgUrl != null) {
            outerBackground.setBackgroundImage(new ImageIcon(bgUrl).getImage());
        } else {
            System.err.println("Background image not found on classpath: /moduledistributionsystem/resources/coc1_1.jpg");
        }

        card = new RoundedPanel(CARD_RADIUS, CARD_BG);
        card.setBorderColor(CARD_BORDER);
        card.setPreferredSize(new Dimension(CARD_W, CARD_H));
        card.setBorder(new EmptyBorder(35, 45, 35, 45));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 0, 6, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;

        // ---------- School seal (matches the seal centered above the title in the Figma) ----------
        Image logoImg = UIKit.loadLogoImage();
        if (logoImg != null) {
            Image scaledLogo = logoImg.getScaledInstance(92, 92, Image.SCALE_SMOOTH);
            JLabel logoLabel = new JLabel(new ImageIcon(scaledLogo));
            logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
            c.insets = new Insets(0, 0, 12, 0);
            c.anchor = GridBagConstraints.CENTER;
            card.add(logoLabel, c);
            c.gridy++;
        }

        JLabel title = new JLabel("Student Module Management System", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(TEXT_DARK);
        c.insets = new Insets(0, 0, 4, 0);
        c.anchor = GridBagConstraints.CENTER;
        card.add(title, c);

        c.gridy++;
        JLabel subtitle = new JLabel("Sign in with your account", SwingConstants.CENTER);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(TEXT_MUTED);
        c.insets = new Insets(0, 0, 26, 0);
        c.anchor = GridBagConstraints.CENTER;
        card.add(subtitle, c);

        c.gridy++;
        c.anchor = GridBagConstraints.WEST;
        JLabel emailLabel = new JLabel("Student ID / Email");
        emailLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        emailLabel.setForeground(TEXT_DARK);
        c.insets = new Insets(0, 0, 6, 0);
        card.add(emailLabel, c);

        c.gridy++;
        RoundedTextField emailField = new RoundedTextField(25, 10);
        emailField.setPreferredSize(new Dimension(0, 40));
        c.insets = new Insets(0, 0, 4, 0);
        card.add(emailField, c);

        c.gridy++;
        JLabel idHint = new JLabel("Students: your School ID, e.g. 02-2526-015176. Staff/Admin: your email.");
        idHint.setFont(new Font("SansSerif", Font.PLAIN, 10));
        idHint.setForeground(new Color(140, 140, 140));
        c.insets = new Insets(0, 0, 16, 0);
        card.add(idHint, c);

        c.gridy++;
        JLabel passLabel = new JLabel("Password");
        passLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        passLabel.setForeground(TEXT_DARK);
        c.insets = new Insets(0, 0, 6, 0);
        card.add(passLabel, c);

        c.gridy++;
        RoundedPasswordField passField = new RoundedPasswordField(20, 10);
        passField.setPreferredSize(new Dimension(0, 40));
        c.insets = new Insets(0, 0, 8, 0);
        card.add(passField, c);

        c.gridy++;
        LinkLabel forgot = new LinkLabel("Forgot Password?", SwingConstants.RIGHT);
        forgot.setFont(new Font("SansSerif", Font.PLAIN, 12));
        forgot.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                new ForgotPasswordDialog(SwingUtilities.getWindowAncestor(LoginPanel.this), store, emailField.getText()).setVisible(true);
            }
        });
        c.insets = new Insets(0, 0, 24, 0);
        card.add(forgot, c);

        c.gridy++;
        c.anchor = GridBagConstraints.CENTER;
        c.fill = GridBagConstraints.NONE;
        RoundedButton signIn = new RoundedButton("Sign In", 12);
        signIn.setPreferredSize(new Dimension(190, 42));
        c.insets = new Insets(0, 0, 0, 0);
        card.add(signIn, c);

        signIn.addActionListener(e -> {
            String email = emailField.getText().trim();
            String password = new String(passField.getPassword());
            if (email.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter both your School ID / email and your password.", "Login", JOptionPane.WARNING_MESSAGE);
                return;
            }
            AuthResult result = store.authenticate(email, password);
            if (result == null) {
                String msg;
                switch (store.getLastAuthFailure()) {
                    case INACTIVE: msg = "This account has been deactivated. Please contact the Registrar Office or the system administrator."; break;
                    case ARCHIVED: msg = "This account has been archived. Please contact the system administrator."; break;
                    case LOCKED: msg = "Too many wrong passwords. Try again in " + store.getLastLockSecondsLeft() + " seconds, or use Forgot Password."; break;
                    default: msg = "Invalid ID/email or password.";
                }
                JOptionPane.showMessageDialog(this, msg, "Login Failed", JOptionPane.ERROR_MESSAGE);
                passField.setText("");
                return;
            }
            SwingUtilities.invokeLater(() -> {
                parent.dispose();
                if (result.getRole() == Role.STAFF) {
                    new StaffMainFrame(store).setVisible(true);
                } else if (result.getRole() == Role.ADMIN) {
                    new AdminMainFrame(store).setVisible(true);
                } else {
                    new StudentMainFrame(store).setVisible(true);
                }
            });
        });

        emailField.addActionListener(e -> signIn.doClick());
        passField.addActionListener(e -> signIn.doClick());

        GridBagConstraints outerC = new GridBagConstraints();
        outerC.gridx = 0;
        outerC.gridy = 0;
        outerC.anchor = GridBagConstraints.CENTER;
        outerBackground.add(card, outerC);

        addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                startEntranceAnimation();
            }
            @Override
            public void ancestorRemoved(AncestorEvent event) { }
            @Override
            public void ancestorMoved(AncestorEvent event) { }
        });
    }

    /** Fades the background and card in when the window first appears. */
    private void startEntranceAnimation() {
        card.setAlpha(0f);
        outerBackground.setFade(0f);
        Timer entrance = new Timer(15, null);
        entrance.addActionListener(e -> {
            float next = card.getAlphaValue() + 0.07f;
            if (next >= 1f) {
                next = 1f;
                ((Timer) e.getSource()).stop();
            }
            card.setAlpha(next);
            outerBackground.setFade(next);
        });
        entrance.start();
    }
}
