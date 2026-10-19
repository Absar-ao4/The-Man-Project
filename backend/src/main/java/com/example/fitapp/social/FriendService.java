package com.example.fitapp.social;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.social.FriendRepository.InviteRow;
import com.example.fitapp.social.FriendRepository.RequestRow;
import com.example.fitapp.social.SocialDtos.FriendDto;
import com.example.fitapp.social.SocialDtos.FriendRequestBody;
import com.example.fitapp.social.SocialDtos.InviteCodeDto;
import com.example.fitapp.social.SocialDtos.RequestDto;
import com.example.fitapp.social.SocialDtos.SearchHit;
import com.example.fitapp.social.SocialDtos.UserCard;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Friends, requests, blocks and invite codes. The rules that matter for privacy: a blocked pair can not find, ask or
 * answer each other (and gets a plain "not found", so a block is not revealed), and a request or code never reveals more than
 * the {@link UserCard}.
 */
@Service
public class FriendService {

    static final int MAX_FRIENDS = 200;
    static final int MAX_PENDING_OUT = 50;
    static final int SEARCH_LIMIT = 20;
    static final int MIN_SEARCH_LENGTH = 2;
    static final Duration CODE_TTL = Duration.ofDays(14);
    static final int CODE_LENGTH = 8;
    /** No 0, O, 1, I or L: codes are read aloud and typed from a screen. */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final FriendRepository repo;
    private final DuelService duels;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public FriendService(FriendRepository repo, DuelService duels, Clock clock) {
        this.repo = repo;
        this.duels = duels;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ search

    @Transactional(readOnly = true)
    public List<SearchHit> search(UUID viewer, String query) {
        String q = query == null ? "" : query.trim();
        if (q.startsWith("@")) {
            q = q.substring(1);
        }
        if (q.length() < MIN_SEARCH_LENGTH) {
            throw ApiException.validation("username", "Type at least " + MIN_SEARCH_LENGTH + " characters");
        }
        Set<UUID> friends = repo.friends(viewer).keySet();
        Set<UUID> sent = new HashSet<>();
        repo.pendingOutgoing(viewer).forEach(r -> sent.add(r.to()));
        Set<UUID> received = new HashSet<>();
        repo.pendingIncoming(viewer).forEach(r -> received.add(r.from()));
        List<SearchHit> hits = new ArrayList<>();
        for (UserCard card : repo.search(viewer, q, SEARCH_LIMIT)) {
            String relation = friends.contains(card.id()) ? "FRIEND" : sent.contains(card.id()) ? "REQUEST_SENT" : received.contains(card.id()) ? "REQUEST_RECEIVED" : "NONE";
            hits.add(new SearchHit(card, relation));
        }
        return hits;
    }

    // ------------------------------------------------------------------ requests

    @Transactional
    public RequestDto sendRequest(UUID viewer, FriendRequestBody body) {
        UUID target = resolveTarget(body);
        if (target.equals(viewer)) {
            throw ApiException.validation("username", "You cannot add yourself");
        }
        if (repo.blockedEitherWay(viewer, target)) {
            throw ApiException.notFound("User not found");
        }
        if (repo.areFriends(viewer, target)) {
            throw new ApiException(ErrorCode.CONFLICT, "You are already friends");
        }
        RequestRow reverse = repo.pendingBetween(target, viewer).orElse(null);
        if (reverse != null) {
            // They already asked us: asking back means yes.
            return accept(viewer, reverse.id());
        }
        if (repo.pendingBetween(viewer, target).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "You already sent this person a request");
        }
        if (repo.friendCount(viewer) >= MAX_FRIENDS || repo.friendCount(target) >= MAX_FRIENDS) {
            throw new ApiException(ErrorCode.LIMIT_REACHED, "A friend list is full (" + MAX_FRIENDS + " friends)");
        }
        if (repo.pendingOutgoingCount(viewer) >= MAX_PENDING_OUT) {
            throw new ApiException(ErrorCode.LIMIT_REACHED, "Too many open requests. Cancel some first.");
        }
        UUID id = UUID.randomUUID();
        try {
            repo.insertRequest(id, viewer, target);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new ApiException(ErrorCode.CONFLICT, "You already sent this person a request"); // two taps at once
        }
        return toDto(repo.request(id).orElseThrow());
    }

    @Transactional
    public RequestDto accept(UUID viewer, UUID requestId) {
        RequestRow r = mine(requestId, viewer, true);
        if (repo.blockedEitherWay(r.from(), r.to())) {
            throw ApiException.notFound("Request not found");
        }
        if (repo.friendCount(r.from()) >= MAX_FRIENDS || repo.friendCount(r.to()) >= MAX_FRIENDS) {
            throw new ApiException(ErrorCode.LIMIT_REACHED, "A friend list is full (" + MAX_FRIENDS + " friends)");
        }
        if (!repo.resolve(r.id(), "ACCEPTED")) {
            throw new ApiException(ErrorCode.CONFLICT, "This request was already answered");
        }
        repo.addFriendship(r.from(), r.to());
        repo.cancelPendingBetween(r.from(), r.to());
        return toDto(repo.request(r.id()).orElseThrow());
    }

    @Transactional
    public RequestDto decline(UUID viewer, UUID requestId) {
        RequestRow r = mine(requestId, viewer, true);
        if (!repo.resolve(r.id(), "DECLINED")) {
            throw new ApiException(ErrorCode.CONFLICT, "This request was already answered");
        }
        return toDto(repo.request(r.id()).orElseThrow());
    }

    @Transactional
    public RequestDto cancel(UUID viewer, UUID requestId) {
        RequestRow r = mine(requestId, viewer, false);
        if (!repo.resolve(r.id(), "CANCELLED")) {
            throw new ApiException(ErrorCode.CONFLICT, "This request was already answered");
        }
        return toDto(repo.request(r.id()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<RequestDto> incoming(UUID viewer) {
        return toDtos(repo.pendingIncoming(viewer));
    }

    @Transactional(readOnly = true)
    public List<RequestDto> outgoing(UUID viewer) {
        return toDtos(repo.pendingOutgoing(viewer));
    }

    /** The request, if [viewer] is the one who may act on it ([asRecipient]) or withdraw it. Anyone else gets "not found". */
    private RequestRow mine(UUID requestId, UUID viewer, boolean asRecipient) {
        RequestRow r = repo.request(requestId).orElseThrow(() -> ApiException.notFound("Request not found"));
        UUID owner = asRecipient ? r.to() : r.from();
        if (!owner.equals(viewer)) {
            throw ApiException.notFound("Request not found");
        }
        if (!"PENDING".equals(r.status())) {
            throw new ApiException(ErrorCode.CONFLICT, "This request was already answered");
        }
        return r;
    }

    private UUID resolveTarget(FriendRequestBody body) {
        if (body.userId() != null) {
            return repo.card(body.userId()).map(UserCard::id).orElseThrow(() -> ApiException.notFound("User not found"));
        }
        if (body.username() != null && !body.username().isBlank()) {
            String name = body.username().trim();
            return repo.idByUsername(name.startsWith("@") ? name.substring(1) : name).orElseThrow(() -> ApiException.notFound("User not found"));
        }
        throw ApiException.validation("username", "Give a username");
    }

    // ------------------------------------------------------------------ friends

    @Transactional(readOnly = true)
    public List<FriendDto> friends(UUID viewer) {
        Map<UUID, Instant> friends = repo.friends(viewer);
        Map<UUID, UserCard> cards = repo.cards(friends.keySet());
        List<FriendDto> result = new ArrayList<>();
        friends.forEach(
                (id, since) -> {
                    UserCard card = cards.get(id);
                    if (card != null) {
                        result.add(new FriendDto(card, since));
                    }
                });
        result.sort((a, b) -> a.user().username().compareToIgnoreCase(b.user().username()));
        return result;
    }

    @Transactional
    public void removeFriend(UUID viewer, UUID friendId) {
        if (!repo.removeFriendship(viewer, friendId)) {
            throw ApiException.notFound("That person is not your friend");
        }
        duels.cancelOpenBetween(viewer, friendId);
    }

    // ------------------------------------------------------------------ blocks

    @Transactional
    public void block(UUID viewer, UUID target) {
        if (viewer.equals(target)) {
            throw ApiException.validation("userId", "You cannot block yourself");
        }
        if (repo.card(target).isEmpty()) {
            throw ApiException.notFound("User not found");
        }
        repo.block(viewer, target);
        repo.removeFriendship(viewer, target);
        repo.cancelPendingBetween(viewer, target);
        duels.cancelOpenBetween(viewer, target);
    }

    @Transactional
    public void unblock(UUID viewer, UUID target) {
        if (!repo.unblock(viewer, target)) {
            throw ApiException.notFound("That person is not blocked");
        }
    }

    @Transactional(readOnly = true)
    public List<FriendDto> blocked(UUID viewer) {
        Map<UUID, Instant> blocked = repo.blockedBy(viewer);
        Map<UUID, UserCard> cards = repo.cards(blocked.keySet());
        List<FriendDto> result = new ArrayList<>();
        blocked.forEach(
                (id, since) -> {
                    UserCard card = cards.get(id);
                    if (card != null) {
                        result.add(new FriendDto(card, since));
                    }
                });
        return result;
    }

    // ------------------------------------------------------------------ invite codes

    /** The current code, or a new one if there is none (or it ran out). */
    @Transactional
    public InviteCodeDto inviteCode(UUID viewer) {
        Instant now = clock.instant();
        InviteRow active = repo.activeCode(viewer, now).orElse(null);
        return active != null ? new InviteCodeDto(active.code(), active.expiresAt()) : create(viewer, now);
    }

    /** Replaces the code: the old one stops working at once. */
    @Transactional
    public InviteCodeDto regenerateInviteCode(UUID viewer) {
        Instant now = clock.instant();
        repo.expireCodes(viewer, now);
        return create(viewer, now);
    }

    private InviteCodeDto create(UUID viewer, Instant now) {
        Instant expires = now.plus(CODE_TTL);
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = randomCode();
            if (repo.insertCode(viewer, code, expires)) {
                return new InviteCodeDto(code, expires);
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not make an invite code, try again");
    }

    /** Using someone's code makes you friends at once: owning the code is their consent. */
    @Transactional
    public FriendDto redeem(UUID viewer, String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        InviteRow row = repo.codeOwner(code, clock.instant()).orElseThrow(() -> ApiException.notFound("That code is not valid or has expired"));
        UUID owner = row.userId();
        if (owner.equals(viewer)) {
            throw ApiException.validation("code", "That is your own code");
        }
        if (repo.blockedEitherWay(viewer, owner)) {
            throw ApiException.notFound("That code is not valid or has expired");
        }
        if (!repo.areFriends(viewer, owner)) {
            if (repo.friendCount(viewer) >= MAX_FRIENDS || repo.friendCount(owner) >= MAX_FRIENDS) {
                throw new ApiException(ErrorCode.LIMIT_REACHED, "A friend list is full (" + MAX_FRIENDS + " friends)");
            }
            repo.addFriendship(viewer, owner);
            repo.cancelPendingBetween(viewer, owner);
        }
        UserCard card = repo.card(owner).orElseThrow(() -> ApiException.notFound("User not found"));
        return new FriendDto(card, repo.friends(viewer).getOrDefault(owner, clock.instant()));
    }

    // ------------------------------------------------------------------ mapping

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private List<RequestDto> toDtos(List<RequestRow> rows) {
        Set<UUID> ids = new HashSet<>();
        rows.forEach(r -> {
            ids.add(r.from());
            ids.add(r.to());
        });
        Map<UUID, UserCard> cards = repo.cards(ids);
        List<RequestDto> result = new ArrayList<>();
        for (RequestRow r : rows) {
            if (cards.containsKey(r.from()) && cards.containsKey(r.to())) {
                result.add(new RequestDto(r.id(), cards.get(r.from()), cards.get(r.to()), r.status(), r.createdAt()));
            }
        }
        return result;
    }

    private RequestDto toDto(RequestRow r) {
        return toDtos(List.of(r)).stream().findFirst().orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
