package moduledistributionsystem.data;

/**
 * Anything that can e-mail a plain-text message.
 *
 * This interface is the <i>abstraction</i> the rest of the program depends on: DemoDataStore only knows
 * "a MailSender", never how the mail really travels. The real implementation is SmtpMailer; a test can
 * plug in a fake one. That is <i>polymorphism</i>: the same call send(...) does different things
 * depending on the object behind it.
 */
public interface MailSender {
    /** @throws Exception with a readable message when the mail could not be handed to the mail server */
    void send(String toAddress, String subject, String body) throws Exception;

    /** True when this sender has what it needs to work (for SMTP: a host and a from-address are set). */
    boolean isConfigured();
}
