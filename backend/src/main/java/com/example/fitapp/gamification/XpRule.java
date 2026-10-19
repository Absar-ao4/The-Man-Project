package com.example.fitapp.gamification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row of the tunable XP configuration. See {@link XpRules}. */
@Entity
@Table(name = "xp_rules")
public class XpRule {

    @Id
    @Column(name = "rule_key")
    private String key;

    @Column(name = "rule_value", nullable = false)
    private int value;

    protected XpRule() {}

    public String getKey() {
        return key;
    }

    public int getValue() {
        return value;
    }
}
