package com.venach.kauris.kernel.organisation.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** Un rôle d'un membre ; {@code etablissementId} nul = toute l'organisation. */
@Entity
@Table(name = "kernel_membre_role")
public class MembreRoleEntity {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
    UUID tenantId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "membre_id", length = 36, nullable = false, updatable = false)
    UUID membreId;

    @Column(name = "code_role", length = 30, nullable = false)
    String codeRole;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "etablissement_id", length = 36)
    UUID etablissementId;

    /** Renseigné seulement si {@code codeRole} vaut PERSONNALISE. */
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role_personnalise_id", length = 36)
    UUID rolePersonnaliseId;

    protected MembreRoleEntity() {
    }
}
