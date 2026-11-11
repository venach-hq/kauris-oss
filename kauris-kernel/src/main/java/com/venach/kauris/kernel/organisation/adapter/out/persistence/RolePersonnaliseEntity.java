package com.venach.kauris.kernel.organisation.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kernel_role_personnalise")
public class RolePersonnaliseEntity {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
    UUID tenantId;

    @Column(name = "nom", length = 60, nullable = false)
    String nom;

    @Column(name = "description", length = 200, nullable = false)
    String description;

    /** Codes de permission séparés par des virgules. */
    @Column(name = "permissions", length = 4000, nullable = false)
    String permissions;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "cree_le", nullable = false, updatable = false)
    Instant creeLe;

    protected RolePersonnaliseEntity() {
    }
}
