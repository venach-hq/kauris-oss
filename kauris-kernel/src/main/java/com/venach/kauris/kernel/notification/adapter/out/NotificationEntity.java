package com.venach.kauris.kernel.notification.adapter.out;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kernel_notification")
public class NotificationEntity {

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

    @Column(name = "titre", length = 120, nullable = false)
    String titre;

    @Column(name = "message", length = 500, nullable = false)
    String message;

    @Column(name = "lien", length = 255)
    String lien;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "cree_le", nullable = false, updatable = false)
    Instant creeLe;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "lue_le")
    Instant lueLe;

    protected NotificationEntity() {
    }
}
