package com.kunirank.api.deployment;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

final class DeploymentDataSource extends AbstractRoutingDataSource {
    enum Role { QUERY, MEMBER, VOTING }

    private static final ThreadLocal<Role> currentRole = new ThreadLocal<>();

    static void select(Role role) {
        currentRole.set(role);
    }

    static void clear() {
        currentRole.remove();
    }

    @Override
    protected Role determineCurrentLookupKey() {
        Role role = currentRole.get();
        return role == null ? Role.QUERY : role;
    }
}
