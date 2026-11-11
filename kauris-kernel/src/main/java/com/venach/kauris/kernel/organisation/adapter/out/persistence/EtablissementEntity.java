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
@Table(name = "kernel_etablissement")
public class EtablissementEntity {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
    UUID tenantId;

    @Column(name = "nom", length = 120, nullable = false)
    String nom;

    @Column(name = "principal", nullable = false)
    boolean principal;

    @Column(name = "ouvert", nullable = false)
    boolean ouvert;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "cree_le", nullable = false, updatable = false)
    Instant creeLe;

    protected EtablissementEntity() {
    }
}
