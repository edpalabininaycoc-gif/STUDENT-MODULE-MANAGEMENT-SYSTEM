# E-mail (SMTP) setup for "Forgot password"

The login screen's **Forgot password?** link e-mails a 6-digit code to the account's e-mail address
(valid 15 minutes, one use, max 5 tries). The app talks to your mail server directly, so no extra .jar is needed.

Add these lines to `db.properties` (the same file that holds the database settings):

```
smtp.host=smtp.gmail.com
smtp.port=587
smtp.security=starttls
smtp.user=yourschoolaccount@gmail.com
smtp.password=your-16-letter-app-password
smtp.from=yourschoolaccount@gmail.com
smtp.fromName=Student Module Management System
```

* `smtp.security` is `starttls` (port 587, most providers), `ssl` (port 465) or `none` (local test server only).
* **Gmail:** turn on 2-Step Verification, then create an *App password* (Google Account > Security > App passwords)
  and use that as `smtp.password`. Your normal Gmail password will not work.
* **Outlook / Microsoft 365:** `smtp.host=smtp.office365.com`, port 587, `starttls`.
* Until `smtp.host` and a from-address are filled in, Forgot password tells the user that e-mail isn't set up
  (nothing breaks, and an admin can still change a password under Manage Users > Manage > Change password).
* Keep `db.properties` private (it now holds the mail password too) and do not commit it to a shared repository.

Every student/staff/admin account needs a real e-mail address, because the code is sent to the e-mail
stored on the account.
