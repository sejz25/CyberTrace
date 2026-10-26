package edu.cybertrace;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Local-only one-shot recovery. It never exposes a password reset API to the public web. */
@Component
class LocalAccountRecovery implements ApplicationRunner {
    private static final String REQUIRED_PHRASE = "RESET_ADMIN_ACCESS";
    private final Users users;
    private final SystemSettingRepo settings;
    private final ForensicsService forensics;
    private final PasswordEncoder encoder;
    private final ConfigurableApplicationContext context;

    @Value("${cybertrace.local-reset.confirm:}") private String confirmation;
    @Value("${CYBERTRACE_RECOVERY_USERNAME:}") private String recoveryUsername;
    @Value("${CYBERTRACE_RECOVERY_PASSWORD:}") private String recoveryPassword;

    LocalAccountRecovery(Users users, SystemSettingRepo settings, ForensicsService forensics,
                         PasswordEncoder encoder, ConfigurableApplicationContext context) {
        this.users = users;
        this.settings = settings;
        this.forensics = forensics;
        this.encoder = encoder;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (confirmation == null || confirmation.isBlank()) return;
        if (!REQUIRED_PHRASE.equals(confirmation)) {
            throw new IllegalStateException("Recovery cancelled: confirmation phrase did not match.");
        }
        if (recoveryUsername == null || recoveryUsername.length() < 3
                || recoveryPassword == null || recoveryPassword.length() < 10) {
            throw new IllegalStateException("Set CYBERTRACE_RECOVERY_USERNAME (3+ characters) and CYBERTRACE_RECOVERY_PASSWORD (10+ characters) before local recovery.");
        }

        for (UserRecord user : users.findAll()) {
            if (!user.enabled) continue;
            user.enabled = false;
            users.save(user);
            forensics.audit("LOCAL_ACCOUNT_RECOVERY", "ACCOUNT_DEACTIVATE", user.username,
                    "SUCCESS", "Local administrator recovery; evidence and history retained");
        }

        UserRecord administrator = users.findByUsername(recoveryUsername).orElseGet(UserRecord::new);
        administrator.username = recoveryUsername;
        administrator.password = encoder.encode(recoveryPassword);
        administrator.role = RolePolicy.ADMIN;
        administrator.enabled = true;
        users.saveAndFlush(administrator);
        settings.save(new SystemSetting("ADMIN_BOOTSTRAP_COMPLETE", "true"));
        forensics.audit("LOCAL_ACCOUNT_RECOVERY", "ADMIN_ACCESS_RESTORED", "user:" + recoveryUsername,
                "SUCCESS", "Administrator restored by explicit local one-shot recovery");

        System.out.println("Administrator access restored for " + recoveryUsername + ". Other accounts were deactivated; evidence, cases, and history were preserved.");
        System.out.println("Stop this one-shot recovery process and restart the application normally.");
        SpringApplication.exit(context, () -> 0);
    }
}
