package edu.cybertrace;

import java.util.Set;

/** The one approved role list used when accounts are created or roles change. */
final class RolePolicy {
    static final String ADMIN = "ADMIN";
    static final String LAWYER = "LAWYER";
    static final String INVESTIGATOR = "INVESTIGATOR"; // Existing role name retained for compatibility.
    static final String COURT = "COURT";
    static final String AUDITOR = "AUDITOR";
    static final String RECIPIENT = "RECIPIENT"; // Existing read-only external reviewer role.
    static final Set<String> APPROVED = Set.of(ADMIN, LAWYER, INVESTIGATOR, COURT, AUDITOR, RECIPIENT);

    private RolePolicy() {}

    static boolean is(UserRecord user, String... roles) {
        if (user == null || !user.enabled) return false;
        for (String role : roles) if (role.equals(user.role)) return true;
        return false;
    }
}
