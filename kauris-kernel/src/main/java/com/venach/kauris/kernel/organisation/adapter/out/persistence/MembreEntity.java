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
@Table(name = "kernel_membre")
public class MembreEntity {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
    UUID tenantId;

    @Column(name = "fournisseur", length = 20)
    String fournisseur;

    @Column(name = "sujet", length = 190)
    String sujet;

    @Column(name = "email", length = 190, nullable = false)
    String email;

    @Column(name = "nom", length = 120, nullable = false)
    String nom;

    @Column(name = "statut", length = 20, nullable = false)
    String statut;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "cree_le", nullable = false, updatable = false)
    Instant creeLe;

    protected MembreEntity() {
    }
}
