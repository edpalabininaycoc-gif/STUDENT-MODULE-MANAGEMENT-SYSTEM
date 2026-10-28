package moduledistributionsystem.ui.common;

import moduledistributionsystem.data.DemoDataStore;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * "Forgot password": step 1 asks for the School ID / e-mail and e-mails a 6-digit code (through SMTP, off the
 * screen thread); step 2 takes the code plus a new password. The same neutral wording is shown whether or not
 * an account matched, so nobody can use this screen to find out who has an account.
 */
public class ForgotPasswordDialog extends JDialog {
    private final DemoDataStore store;
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);

    private final JTextField idField = new JTextField();
    private final JButton sendBtn = new JButton("Send code");
    private final JLabel step1Msg = new JLabel(" ");

    private final JTextField codeField = new JTextField();
    private final JPasswordField newPass = new JPasswordField();
    private final JPasswordField confirmPass = new JPasswordField();
    private final JButton resetBtn = new JButton("Reset password");
    private final JLabel step2Msg = new JLabel(" ");
    private String identifier = "";
    private boolean done = false;

    public ForgotPasswordDialog(Window owner, DemoDataStore store, String prefillId) {
        super(owner, "Forgot Password", ModalityType.APPLICATION_MODAL);
        this.store = store;
        setSize(500, 430);          // wide enough that the 360px description never gets clipped on the right
        setMinimumSize(new Dimension(500, 430));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        body.setBorder(new EmptyBorder(18, 22, 12, 22));
        body.add(buildStep1(), "1");
        body.add(buildStep2(), "2");
        add(body, BorderLayout.CENTER);
        if (prefillId != null) idField.setText(prefillId.trim());

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        bottom.add(close);
        add(bottom, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(sendBtn);
    }

    public boolean isPasswordChanged() { return done; }

    private static JLabel heading(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("SansSerif", Font.BOLD, 16));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel small(String html) {
        JLabel l = new JLabel("<html><div style='width:360px'>" + html + "</div></html>");
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        l.setForeground(new Color(95, 100, 98));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(4, 0, 12, 0));
        return l;
    }

    private static JLabel fieldLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("SansSerif", Font.BOLD, 12));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(8, 0, 4, 0));
        return l;
    }

    private static void style(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        c.setPreferredSize(new Dimension(300, 32));
    }

    private JPanel buildStep1() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(heading("Reset your password"));
        p.add(small("Enter your School ID (students) or your e-mail (staff / admin). We'll e-mail a 6-digit code to the address on your account."));
        p.add(fieldLabel("School ID or e-mail"));
        style(idField);
        p.add(idField);
        p.add(Box.createVerticalStrut(12));
        sendBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        sendBtn.addActionListener(e -> sendCode());
        p.add(sendBtn);
        step1Msg.setFont(new Font("SansSerif", Font.PLAIN, 12));
        step1Msg.setAlignmentX(Component.LEFT_ALIGNMENT);
        step1Msg.setBorder(new EmptyBorder(10, 0, 0, 0));
        p.add(step1Msg);
        p.add(Box.createVerticalStrut(8));
        JButton haveCode = new JButton("I already have a code");
        haveCode.setAlignmentX(Component.LEFT_ALIGNMENT);
        haveCode.addActionListener(e -> {
            identifier = idField.getText().trim();
            if (identifier.isEmpty()) { step1Msg.setForeground(new Color(180, 40, 40)); step1Msg.setText("Enter your School ID or e-mail first."); return; }
            cards.show(body, "2");
        });
        p.add(haveCode);
        return p;
    }

    private JPanel buildStep2() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(heading("Enter the code"));
        p.add(small("If an active account matches, a 6-digit code was sent to its e-mail address. It works for 15 minutes and only once. "
                + "Check your spam folder too."));
        p.add(fieldLabel("6-digit code"));
        style(codeField);
        p.add(codeField);
        p.add(fieldLabel("New password (at least " + DemoDataStore.MIN_PASSWORD_LENGTH + " characters)"));
        style(newPass);
        p.add(newPass);
        p.add(fieldLabel("Confirm new password"));
        style(confirmPass);
        p.add(confirmPass);
        p.add(Box.createVerticalStrut(12));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetBtn.addActionListener(e -> doReset());
        JButton back = new JButton("Send a new code");
        back.addActionListener(e -> { step1Msg.setText(" "); cards.show(body, "1"); });
        row.add(resetBtn);
        row.add(back);
        p.add(row);
        step2Msg.setFont(new Font("SansSerif", Font.PLAIN, 12));
        step2Msg.setAlignmentX(Component.LEFT_ALIGNMENT);
        step2Msg.setBorder(new EmptyBorder(10, 0, 0, 0));
        p.add(step2Msg);
        return p;
    }

    private void sendCode() {
        String id = idField.getText().trim();
        if (id.isEmpty()) { step1Msg.setForeground(new Color(180, 40, 40)); step1Msg.setText("Enter your School ID or e-mail."); return; }
        DemoDataStore.PendingReset pending = store.prepareResetRequest(id);
        if (pending.error != null) {
            step1Msg.setForeground(new Color(180, 40, 40));
            step1Msg.setText("<html><div style='width:360px'>" + pending.error.replace("\n", "<br>") + "</div></html>");
            return;
        }
        identifier = id;
        if (!pending.mustSend) { goToStep2(); return; }       // no matching account: look exactly the same
        sendBtn.setEnabled(false);
        step1Msg.setForeground(new Color(95, 100, 98));
        step1Msg.setText("Sending e-mail...");
        new SwingWorker<Exception, Void>() {
            @Override protected Exception doInBackground() {
                try { pending.deliver(store.getMailSender()); return null; } catch (Exception ex) { return ex; }
            }
            @Override protected void done() {
                sendBtn.setEnabled(true);
                Exception err;
                try { err = get(); } catch (Exception ex) { err = ex; }
                String problem = store.finishResetRequest(pending, err);
                if (problem != null) {
                    step1Msg.setForeground(new Color(180, 40, 40));
                    step1Msg.setText("<html><div style='width:360px'>" + problem.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>") + "</div></html>");
                } else {
                    goToStep2();
                }
            }
        }.execute();
    }

    private void goToStep2() {
        step1Msg.setText(" ");
        step2Msg.setText(" ");
        cards.show(body, "2");
        getRootPane().setDefaultButton(resetBtn);
        codeField.requestFocusInWindow();
    }

    private void doReset() {
        String pw = new String(newPass.getPassword());
        if (!pw.equals(new String(confirmPass.getPassword()))) {
            step2Msg.setForeground(new Color(180, 40, 40));
            step2Msg.setText("The two passwords don't match.");
            return;
        }
        String problem = store.completePasswordReset(identifier, codeField.getText(), pw);
        if (problem != null) {
            step2Msg.setForeground(new Color(180, 40, 40));
            step2Msg.setText("<html><div style='width:360px'>" + problem + "</div></html>");
            return;
        }
        done = true;
        JOptionPane.showMessageDialog(this, "Your password was changed. You can sign in with it now.", "Password reset", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }
}