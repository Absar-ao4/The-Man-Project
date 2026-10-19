package com.example.fitapp.nutrition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Cached external food (USDA or Open Food Facts), per 100 g. Shared by all users. */
@Entity
@Table(name = "foods")
public class Food {

    @Id private UUID id;

    @Column(nullable = false, updatable = false)
    private String source;

    @Column(name = "external_id", nullable = false, updatable = false)
    private String externalId;

    @Column(nullable = false)
    private String name;

    private String brand;

    @Column(name = "kcal_100g", nullable = false)
    private double kcalPer100g;

    @Column(name = "protein_100g", nullable = false)
    private double proteinPer100g;

    @Column(name = "carbs_100g", nullable = false)
    private double carbsPer100g;

    @Column(name = "fat_100g", nullable = false)
    private double fatPer100g;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "serving_units", nullable = false, columnDefinition = "jsonb")
    private List<ServingUnit> servingUnits = new ArrayList<>();

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected Food() {}

    public Food(UUID id, String source, String externalId) {
        this.id = id;
        this.source = source;
        this.externalId = externalId;
    }

    public void apply(FoodData data) {
        this.name = data.name();
        this.brand = data.brand();
        this.kcalPer100g = data.kcalPer100g();
        this.proteinPer100g = data.proteinPer100g();
        this.carbsPer100g = data.carbsPer100g();
        this.fatPer100g = data.fatPer100g();
        this.servingUnits = new ArrayList<>(data.servingUnits());
        this.fetchedAt = Instant.now();
    }

    public FoodData toData() {
        return new FoodData(
                source,
                externalId,
                name,
                brand,
                kcalPer100g,
                proteinPer100g,
                carbsPer100g,
                fatPer100g,
                List.copyOf(servingUnits));
    }

    public UUID getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getName() {
        return name;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
