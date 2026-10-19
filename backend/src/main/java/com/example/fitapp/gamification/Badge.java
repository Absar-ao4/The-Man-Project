package com.example.fitapp.gamification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A badge definition (seeded). Earning one is a {@link UserBadge}. */
@Entity
@Table(name = "badges")
public class Badge {

    @Id private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Badge() {}

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
