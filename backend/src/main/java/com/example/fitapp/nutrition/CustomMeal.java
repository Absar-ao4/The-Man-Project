package com.example.fitapp.nutrition;

import com.example.fitapp.common.SyncableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "custom_meals")
public class CustomMeal extends SyncableEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean favorite;

    protected CustomMeal() {}

    public CustomMeal(UUID id, UUID userId) {
        super(id, userId);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }
}
