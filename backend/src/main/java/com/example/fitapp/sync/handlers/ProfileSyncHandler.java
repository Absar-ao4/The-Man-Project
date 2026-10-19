package com.example.fitapp.sync.handlers;

import com.example.fitapp.profile.Profile;
import com.example.fitapp.profile.ProfileRepository;
import com.example.fitapp.profile.ProfileService;
import com.example.fitapp.sync.BaseSyncHandler;
import com.example.fitapp.sync.RowRejected;
import com.example.fitapp.sync.SyncRows.ProfileRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** One profile row per user, created at registration; devices only ever update it. */
@Component
public class ProfileSyncHandler extends BaseSyncHandler {

    private final ProfileRepository profiles;

    public ProfileSyncHandler(ObjectMapper mapper, Validator validator, ProfileRepository profiles) {
        super(mapper, validator);
        this.profiles = profiles;
    }

    @Override
    public String entity() {
        return "profiles";
    }

    @Override
    public RowOutcome push(UUID userId, ObjectNode node) {
        ProfileRow r = parse(node, ProfileRow.class);
        requireOwner(r.userId(), userId);

        Profile p =
                profiles.findByUserIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new RowRejected("no profile exists for this account"));
        if (!p.getId().equals(r.id())) {
            throw new RowRejected("profile id does not match this account's profile");
        }
        if (isStale(p.getUpdatedAt(), r.updatedAt())) {
            return stale(r.id());
        }

        p.setClientUpdatedAt(r.updatedAt());
        p.setName(r.name().trim());
        // A device that has not finished onboarding must not blank out what another device filled in.
        if (r.age() != null) {
            p.setAge(r.age());
        }
        if (r.sex() != null) {
            p.setSex(r.sex());
        }
        if (r.heightCm() != null) {
            p.setHeightCm(r.heightCm());
        }
        if (r.weightKg() != null) {
            p.setWeightKg(r.weightKg());
        }
        p.setFitnessLevel(r.fitnessLevel());
        p.setActivityLevel(r.activityLevel());
        p.setGoal(r.goal());
        p.setTimezone(ProfileService.validTimezone(r.timezone()));
        p.setCurrency(ProfileService.validCurrency(r.currency()));
        p.setUnits(r.units());
        profiles.saveAndFlush(p);
        return accepted(r.id());
    }

    @Override
    public List<PulledRow> pull(UUID userId, Instant since, int limit) {
        return profiles
                .findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
                        userId, since, PageRequest.of(0, limit))
                .stream()
                .map(
                        p ->
                                new PulledRow(
                                        p.getSyncedAt(),
                                        toNode(
                                                new ProfileRow(
                                                        p.getId(),
                                                        p.getUserId(),
                                                        p.getUpdatedAt(),
                                                        p.getDeletedAt(),
                                                        p.getName(),
                                                        p.getAge(),
                                                        p.getSex(),
                                                        p.getHeightCm(),
                                                        p.getWeightKg(),
                                                        p.getFitnessLevel(),
                                                        p.getActivityLevel(),
                                                        p.getGoal(),
                                                        p.getTimezone(),
                                                        p.getCurrency(),
                                                        p.getUnits()))))
                .toList();
    }
}
