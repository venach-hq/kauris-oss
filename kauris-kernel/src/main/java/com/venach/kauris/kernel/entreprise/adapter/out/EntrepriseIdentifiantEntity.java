package com.venach.kauris.kernel.entreprise.adapter.out;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** Un identifiant légal de l'entreprise : le code du champ (« nif », « rccm ») et son numéro. */
@Entity
@Table(name = "kernel_entreprise_identifiant")
public class EntrepriseIdentifiantEntity {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
    UUID tenantId;

    @Column(name = "code", length = 30, nullable = false, updatable = false)
    String code;

    @Column(name = "valeur", length = 60, nullable = false)
    String valeur;

    protected EntrepriseIdentifiantEntity() {
    }
}
