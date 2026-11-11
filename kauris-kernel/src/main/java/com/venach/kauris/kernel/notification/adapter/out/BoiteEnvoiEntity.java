package com.venach.kauris.kernel.notification.adapter.out;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Une ligne de la boîte d'envoi. {@code contenu} est du JSON ; {@code type} dit comment le lire. */
@Entity
@Table(name = "kernel_boite_envoi")
public class BoiteEnvoiEntity {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
    UUID tenantId;

    @Column(name = "type", length = 40, nullable = false, updatable = false)
    String type;

    @Column(name = "contenu", length = 2000, nullable = false, updatable = false)
    String contenu;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "cree_le", nullable = false, updatable = false)
    Instant creeLe;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "prochain_essai_le", nullable = false)
    Instant prochainEssaiLe;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "envoye_le")
    Instant envoyeLe;

    @Column(name = "tentatives", nullable = false)
    int tentatives;

    @Column(name = "derniere_erreur", length = 500)
    String derniereErreur;

    protected BoiteEnvoiEntity() {
    }
}
