package com.example.fitapp.social;

import com.example.fitapp.auth.CurrentUser;
import com.example.fitapp.social.SocialDtos.BlockBody;
import com.example.fitapp.social.SocialDtos.DuelBody;
import com.example.fitapp.social.SocialDtos.DuelDto;
import com.example.fitapp.social.SocialDtos.FeedPageDto;
import com.example.fitapp.social.SocialDtos.FriendDto;
import com.example.fitapp.social.SocialDtos.FriendRequestBody;
import com.example.fitapp.social.SocialDtos.InviteCodeDto;
import com.example.fitapp.social.SocialDtos.LeaderboardDto;
import com.example.fitapp.social.SocialDtos.PrivacyDto;
import com.example.fitapp.social.SocialDtos.RequestDto;
import com.example.fitapp.social.SocialDtos.SearchHit;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Friends, requests, blocks, invite codes and search. */
@RestController
@RequestMapping("/api")
@Tag(name = "Friends")
class FriendController {

    private final FriendService service;

    FriendController(FriendService service) {
        this.service = service;
    }

    /** Usernames starting with the text (at least 2 characters). Rate limited. */
    @GetMapping("/users/search")
    List<SearchHit> search(@RequestParam String username) {
        return service.search(CurrentUser.id(), username);
    }

    @PostMapping("/friends/requests")
    @ResponseStatus(HttpStatus.CREATED)
    RequestDto send(@Valid @RequestBody FriendRequestBody body) {
        return service.sendRequest(CurrentUser.id(), body);
    }

    /** Open requests: direction is incoming (default) or outgoing. */
    @GetMapping("/friends/requests")
    List<RequestDto> requests(@RequestParam(defaultValue = "incoming") String direction) {
        return "outgoing".equalsIgnoreCase(direction) ? service.outgoing(CurrentUser.id()) : service.incoming(CurrentUser.id());
    }

    @PostMapping("/friends/requests/{id}/accept")
    RequestDto accept(@PathVariable UUID id) {
        return service.accept(CurrentUser.id(), id);
    }

    @PostMapping("/friends/requests/{id}/decline")
    RequestDto decline(@PathVariable UUID id) {
        return service.decline(CurrentUser.id(), id);
    }

    @PostMapping("/friends/requests/{id}/cancel")
    RequestDto cancel(@PathVariable UUID id) {
        return service.cancel(CurrentUser.id(), id);
    }

    @GetMapping("/friends")
    List<FriendDto> friends() {
        return service.friends(CurrentUser.id());
    }

    @DeleteMapping("/friends/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID userId) {
        service.removeFriend(CurrentUser.id(), userId);
    }

    @PostMapping("/blocks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void block(@Valid @RequestBody BlockBody body) {
        service.block(CurrentUser.id(), body.userId());
    }

    @GetMapping("/blocks")
    List<FriendDto> blocks() {
        return service.blocked(CurrentUser.id());
    }

    @DeleteMapping("/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unblock(@PathVariable UUID userId) {
        service.unblock(CurrentUser.id(), userId);
    }

    /** Your current invite code (made if you have none). Share it as text or a QR code. */
    @GetMapping("/invite-codes")
    InviteCodeDto code() {
        return service.inviteCode(CurrentUser.id());
    }

    /** A new code: the old one stops working. */
    @PostMapping("/invite-codes")
    @ResponseStatus(HttpStatus.CREATED)
    InviteCodeDto regenerate() {
        return service.regenerateInviteCode(CurrentUser.id());
    }

    /** Using a friend's code makes you friends at once. Rate limited. */
    @PostMapping("/invite-codes/{code}/redeem")
    FriendDto redeem(@PathVariable String code) {
        return service.redeem(CurrentUser.id(), code);
    }
}

@RestController
@RequestMapping("/api/me/privacy")
@Tag(name = "Friends")
class PrivacyController {

    private final PrivacyService service;

    PrivacyController(PrivacyService service) {
        this.service = service;
    }

    @GetMapping
    PrivacyDto get() {
        return service.get(CurrentUser.id());
    }

    @PutMapping
    PrivacyDto put(@Valid @RequestBody PrivacyDto body) {
        return service.update(CurrentUser.id(), body);
    }
}

@RestController
@RequestMapping("/api/leaderboard")
@Tag(name = "Leaderboard")
class LeaderboardController {

    private final LeaderboardService service;

    LeaderboardController(LeaderboardService service) {
        this.service = service;
    }

    /** metric: xp, workouts, streak, consistency or improvement. week: an ISO week such as 2026-W10 (default this week). */
    @GetMapping
    LeaderboardDto board(@RequestParam(defaultValue = "xp") String metric, @RequestParam(required = false) String week) {
        return service.board(CurrentUser.id(), metric, week);
    }
}

@RestController
@RequestMapping("/api/duels")
@Tag(name = "Duels")
class DuelController {

    private final DuelService service;

    DuelController(DuelService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DuelDto create(@Valid @RequestBody DuelBody body) {
        return service.create(CurrentUser.id(), body.opponentId(), body.metric());
    }

    @GetMapping
    List<DuelDto> list(@RequestParam(required = false) String status) {
        return service.list(CurrentUser.id(), status);
    }

    @PostMapping("/{id}/accept")
    DuelDto accept(@PathVariable UUID id) {
        return service.accept(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/decline")
    DuelDto decline(@PathVariable UUID id) {
        return service.decline(CurrentUser.id(), id);
    }

    /** Withdraws a challenge that has not been accepted yet. */
    @PostMapping("/{id}/cancel")
    DuelDto cancel(@PathVariable UUID id) {
        return service.cancel(CurrentUser.id(), id);
    }
}

@RestController
@RequestMapping("/api/feed")
@Tag(name = "Feed")
class FeedController {

    private final FeedService service;

    FeedController(FeedService service) {
        this.service = service;
    }

    @GetMapping
    FeedPageDto feed(@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return service.feed(CurrentUser.id(), cursor, limit);
    }
}
