package com.kunirank.api.member.model;

/** Read-only projection of Django auth_user; primary keys remain unchanged. */
public record MemberAccount(long id, String username, String passwordHash, boolean active,
                            boolean staff, boolean superuser) {
    public boolean admin() { return active && (staff || superuser); }
    public record PublicProfile(long id, String username, boolean admin) {}
    public PublicProfile publicProfile() { return new PublicProfile(id, username, admin()); }
}
