package com.redfalcons.ledger.model;

import jakarta.persistence.*;

@Entity
@Table(name = "settings")
public class Setting {

    @Id
    @Column(name = "setting_key", length = 64)
    private String key;

    @Column(name = "setting_value", nullable = false)
    private String value;

    protected Setting() {
    }

    public Setting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
}
