package com.example.fitapp.exercise;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Global rows (userId null) are seeded and read-only; custom rows belong to one user. */
@Entity
@Table(name = "exercises")
public class Exercise {

    @Id private UUID id;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String instructions = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_muscle", nullable = false)
    private Muscle primaryMuscle;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "secondary_muscles", nullable = false, columnDefinition = "text[]")
    private List<String> secondaryMuscles = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Equipment equipment;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "joint_tags", nullable = false, columnDefinition = "text[]")
    private List<String> jointTags = new ArrayList<>();

    @Column(nullable = false)
    private boolean compound;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Server clock, set on every write: the cursor clients pull by. */
    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Transient private boolean clientTimestamp;

    protected Exercise() {}

    public Exercise(UUID id, UUID userId) {
        this.id = id;
        this.userId = userId;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        if (updatedAt == null) {
            updatedAt = now;
        }
        syncedAt = now;
        clientTimestamp = false;
    }

    @PreUpdate
    void onUpdate() {
        Instant now = Instant.now();
        if (!clientTimestamp) {
            updatedAt = now;
        }
        clientTimestamp = false;
        syncedAt = now;
    }

    /** Applies a client-supplied edit time (used by sync) instead of stamping the server clock. */
    public void setClientUpdatedAt(Instant when) {
        this.updatedAt = when;
        this.clientTimestamp = true;
    }

    public boolean isCustom() {
        return userId != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public Muscle getPrimaryMuscle() {
        return primaryMuscle;
    }

    public void setPrimaryMuscle(Muscle primaryMuscle) {
        this.primaryMuscle = primaryMuscle;
    }

    public List<String> getSecondaryMuscles() {
        return secondaryMuscles;
    }

    public void setSecondaryMuscles(List<String> secondaryMuscles) {
        this.secondaryMuscles = secondaryMuscles;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public void setEquipment(Equipment equipment) {
        this.equipment = equipment;
    }

    public List<String> getJointTags() {
        return jointTags;
    }

    public void setJointTags(List<String> jointTags) {
        this.jointTags = jointTags;
    }

    public boolean isCompound() {
        return compound;
    }

    public void setCompound(boolean compound) {
        this.compound = compound;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
