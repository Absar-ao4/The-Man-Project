package com.example.fitapp.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Friends, requests, blocks, invite codes and privacy settings, through the real HTTP API. */
class FriendsIntegrationTest extends AbstractIntegrationTest {

    private record Person(Session session, String username) {}

    private Person person(String prefix) throws Exception {
        // Usernames are 3 to 20 characters: up to 11 of the prefix, an underscore and 8 random characters.
        String base = prefix.length() > 11 ? prefix.substring(0, 11) : prefix;
        String username = base + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return new Person(users().register(username + "@example.com", username, "Name " + prefix), username);
    }

    private JsonNode search(Person viewer, String query) throws Exception {
        return body(get(viewer.session(), "/api/users/search?username=" + query).andExpect(status().isOk()));
    }

    private UUID id(Person p) {
        return p.session().userId();
    }

    private JsonNode sendRequest(Person from, Person to) throws Exception {
        return body(post(from.session(), "/api/friends/requests", Map.of("username", to.username())).andExpect(status().isCreated()));
    }

    private void befriend(Person a, Person b) throws Exception {
        JsonNode request = sendRequest(a, b);
        post(b.session(), "/api/friends/requests/" + request.get("id").asText() + "/accept", Map.of()).andExpect(status().isOk());
    }

    private List<String> friendUsernames(Person p) throws Exception {
        List<String> names = new ArrayList<>();
        body(get(p.session(), "/api/friends").andExpect(status().isOk())).forEach(f -> names.add(f.get("user").get("username").asText()));
        return names;
    }

    // ------------------------------------------------------------ search

    @Test
    void searchFindsUsernamesByPrefixAndShowsHowYouAreRelated() throws Exception {
        String tag = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        Person a = person("srch" + tag + "a");
        Person b = person("srch" + tag + "b");

        JsonNode hits = search(a, "srch" + tag);
        assertThat(hits.size()).isEqualTo(1); // never yourself
        assertThat(hits.get(0).get("user").get("username").asText()).isEqualTo(b.username());
        assertThat(hits.get(0).get("relation").asText()).isEqualTo("NONE");
        assertThat(hits.get(0).get("user").has("email")).as("an email is never shown").isFalse();

        JsonNode request = sendRequest(a, b);
        assertThat(search(a, b.username()).get(0).get("relation").asText()).isEqualTo("REQUEST_SENT");
        assertThat(search(b, a.username()).get(0).get("relation").asText()).isEqualTo("REQUEST_RECEIVED");

        post(b.session(), "/api/friends/requests/" + request.get("id").asText() + "/accept", Map.of()).andExpect(status().isOk());
        assertThat(search(a, b.username()).get(0).get("relation").asText()).isEqualTo("FRIEND");
    }

    @Test
    void searchIsCaseInsensitiveAcceptsAnAtSignAndNeedsTwoCharacters() throws Exception {
        Person a = person("seeker");
        Person b = person("mixed");

        assertThat(search(a, "@" + b.username().toUpperCase()).size()).isEqualTo(1);
        get(a.session(), "/api/users/search?username=m").andExpect(status().isBadRequest());
        get(a.session(), "/api/users/search?username=").andExpect(status().isBadRequest());
        assertThat(search(a, "zzzz_nobody_" + UUID.randomUUID().toString().substring(0, 8)).size()).isZero();
    }

    @Test
    void searchTreatsWildcardsAsPlainText() throws Exception {
        Person a = person("wild");

        assertThat(search(a, "%%").size()).isZero();
        assertThat(search(a, "__").size()).isZero();
    }

    // ------------------------------------------------------------ requests

    @Test
    void aRequestIsSentAnsweredAndMakesFriends() throws Exception {
        Person a = person("asker");
        Person b = person("answerer");

        JsonNode request = sendRequest(a, b);
        assertThat(request.get("status").asText()).isEqualTo("PENDING");
        assertThat(request.get("from").get("username").asText()).isEqualTo(a.username());
        String requestId = request.get("id").asText();

        assertThat(body(get(b.session(), "/api/friends/requests")).size()).isEqualTo(1);
        assertThat(body(get(a.session(), "/api/friends/requests?direction=outgoing")).size()).isEqualTo(1);
        assertThat(body(get(a.session(), "/api/friends/requests")).size()).as("nothing incoming for the sender").isZero();

        JsonNode accepted = body(post(b.session(), "/api/friends/requests/" + requestId + "/accept", Map.of()).andExpect(status().isOk()));
        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(friendUsernames(a)).containsExactly(b.username());
        assertThat(friendUsernames(b)).containsExactly(a.username());
        assertThat(body(get(b.session(), "/api/friends/requests")).size()).isZero();
    }

    @Test
    void onlyTheRecipientCanAcceptOrDeclineAndOnlyTheSenderCanCancel() throws Exception {
        Person a = person("sender");
        Person b = person("recipient");
        Person c = person("bystander");
        String requestId = sendRequest(a, b).get("id").asText();

        post(c.session(), "/api/friends/requests/" + requestId + "/accept", Map.of()).andExpect(status().isNotFound());
        post(a.session(), "/api/friends/requests/" + requestId + "/accept", Map.of()).andExpect(status().isNotFound());
        post(a.session(), "/api/friends/requests/" + requestId + "/decline", Map.of()).andExpect(status().isNotFound());
        post(b.session(), "/api/friends/requests/" + requestId + "/cancel", Map.of()).andExpect(status().isNotFound());
        assertThat(friendUsernames(a)).isEmpty();

        post(a.session(), "/api/friends/requests/" + requestId + "/cancel", Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        post(b.session(), "/api/friends/requests/" + requestId + "/accept", Map.of()).andExpect(status().isConflict());
    }

    @Test
    void aDeclinedRequestCanBeSentAgain() throws Exception {
        Person a = person("again");
        Person b = person("decliner");
        String first = sendRequest(a, b).get("id").asText();

        post(b.session(), "/api/friends/requests/" + first + "/decline", Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DECLINED"));

        sendRequest(a, b);
        assertThat(body(get(b.session(), "/api/friends/requests")).size()).isEqualTo(1);
    }

    @Test
    void requestsThatMakeNoSenseAreRefused() throws Exception {
        Person a = person("refused");
        Person b = person("target");
        sendRequest(a, b);

        post(a.session(), "/api/friends/requests", Map.of("username", b.username())).andExpect(status().isConflict()); // already sent
        post(a.session(), "/api/friends/requests", Map.of("username", a.username())).andExpect(status().isBadRequest()); // yourself
        post(a.session(), "/api/friends/requests", Map.of("username", "nobody_" + UUID.randomUUID().toString().substring(0, 8))).andExpect(status().isNotFound());
        post(a.session(), "/api/friends/requests", Map.of("userId", UUID.randomUUID())).andExpect(status().isNotFound());
        post(a.session(), "/api/friends/requests", Map.of()).andExpect(status().isBadRequest());
    }

    @Test
    void askingSomeoneWhoAlreadyAskedYouMakesYouFriends() throws Exception {
        Person a = person("mutual_a");
        Person b = person("mutual_b");
        sendRequest(a, b);

        JsonNode back = body(post(b.session(), "/api/friends/requests", Map.of("username", a.username())).andExpect(status().isCreated()));

        assertThat(back.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(friendUsernames(a)).containsExactly(b.username());
        post(a.session(), "/api/friends/requests", Map.of("username", b.username())).andExpect(status().isConflict()); // now friends
    }

    @Test
    void friendsCanBeRemovedAndTheOtherSideLosesThemToo() throws Exception {
        Person a = person("remover");
        Person b = person("removed");
        befriend(a, b);

        delete(a.session(), "/api/friends/" + id(b)).andExpect(status().isNoContent());

        assertThat(friendUsernames(a)).isEmpty();
        assertThat(friendUsernames(b)).isEmpty();
        delete(a.session(), "/api/friends/" + id(b)).andExpect(status().isNotFound());
        delete(a.session(), "/api/friends/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ blocks

    @Test
    void blockingEndsTheFriendshipAndHidesBothPeopleFromEachOther() throws Exception {
        Person a = person("blocker");
        Person b = person("blocked");
        befriend(a, b);

        post(a.session(), "/api/blocks", Map.of("userId", id(b))).andExpect(status().isNoContent());

        assertThat(friendUsernames(a)).isEmpty();
        assertThat(friendUsernames(b)).isEmpty();
        assertThat(search(b, a.username()).size()).as("the blocked person can not find the blocker").isZero();
        assertThat(search(a, b.username()).size()).as("and the blocker does not see them either").isZero();
        // Neither can ask the other, and a block is not revealed: it looks like the user does not exist.
        post(b.session(), "/api/friends/requests", Map.of("username", a.username())).andExpect(status().isNotFound());
        post(a.session(), "/api/friends/requests", Map.of("username", b.username())).andExpect(status().isNotFound());
        JsonNode blocks = body(get(a.session(), "/api/blocks").andExpect(status().isOk()));
        assertThat(blocks.size()).isEqualTo(1);
        assertThat(blocks.get(0).get("user").get("username").asText()).isEqualTo(b.username());
        assertThat(body(get(b.session(), "/api/blocks")).size()).as("the blocked person sees no trace").isZero();
    }

    @Test
    void blockingCancelsOpenRequestsAndUnblockingDoesNotRestoreFriendship() throws Exception {
        Person a = person("blk_a");
        Person b = person("blk_b");
        sendRequest(a, b);

        post(b.session(), "/api/blocks", Map.of("userId", id(a))).andExpect(status().isNoContent());
        assertThat(body(get(b.session(), "/api/friends/requests")).size()).isZero();

        delete(b.session(), "/api/blocks/" + id(a)).andExpect(status().isNoContent());
        assertThat(friendUsernames(a)).isEmpty();
        assertThat(search(b, a.username()).size()).isEqualTo(1);
        delete(b.session(), "/api/blocks/" + id(a)).andExpect(status().isNotFound());
    }

    @Test
    void youCanNotBlockYourselfOrSomeoneWhoDoesNotExist() throws Exception {
        Person a = person("blk_self");

        post(a.session(), "/api/blocks", Map.of("userId", id(a))).andExpect(status().isBadRequest());
        post(a.session(), "/api/blocks", Map.of("userId", UUID.randomUUID())).andExpect(status().isNotFound());
        post(a.session(), "/api/blocks", Map.of()).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ invite codes

    @Test
    void anInviteCodeIsStableUntilReplaced() throws Exception {
        Person a = person("coder");

        JsonNode first = body(get(a.session(), "/api/invite-codes").andExpect(status().isOk()));
        JsonNode again = body(get(a.session(), "/api/invite-codes").andExpect(status().isOk()));

        assertThat(first.get("code").asText()).hasSize(8).matches("[A-Z2-9]+").doesNotContain("0", "1", "O", "I", "L");
        assertThat(again.get("code").asText()).isEqualTo(first.get("code").asText());
        assertThat(java.time.Instant.parse(first.get("expiresAt").asText())).isAfter(java.time.Instant.now().plusSeconds(13 * 86_400L));

        JsonNode fresh = body(post(a.session(), "/api/invite-codes", Map.of()).andExpect(status().isCreated()));
        assertThat(fresh.get("code").asText()).isNotEqualTo(first.get("code").asText());
        assertThat(body(get(a.session(), "/api/invite-codes")).get("code").asText()).isEqualTo(fresh.get("code").asText());
    }

    @Test
    void redeemingACodeMakesFriendsAtOnceAndAReplacedCodeStopsWorking() throws Exception {
        Person owner = person("owner");
        Person friend = person("redeemer");
        Person late = person("late");
        String code = body(get(owner.session(), "/api/invite-codes")).get("code").asText();

        JsonNode redeemed = body(post(friend.session(), "/api/invite-codes/" + code.toLowerCase() + "/redeem", Map.of()).andExpect(status().isOk()));
        assertThat(redeemed.get("user").get("username").asText()).isEqualTo(owner.username());
        assertThat(friendUsernames(owner)).containsExactly(friend.username());
        assertThat(friendUsernames(friend)).containsExactly(owner.username());

        // Again is harmless.
        post(friend.session(), "/api/invite-codes/" + code + "/redeem", Map.of()).andExpect(status().isOk());
        assertThat(friendUsernames(owner)).hasSize(1);

        post(owner.session(), "/api/invite-codes", Map.of()).andExpect(status().isCreated());
        post(late.session(), "/api/invite-codes/" + code + "/redeem", Map.of()).andExpect(status().isNotFound());
        assertThat(friendUsernames(late)).isEmpty();
    }

    @Test
    void badCodesAreRefused() throws Exception {
        Person a = person("badcode");
        String own = body(get(a.session(), "/api/invite-codes")).get("code").asText();

        post(a.session(), "/api/invite-codes/" + own + "/redeem", Map.of()).andExpect(status().isBadRequest());
        post(a.session(), "/api/invite-codes/NOSUCHCD/redeem", Map.of()).andExpect(status().isNotFound());
    }

    @Test
    void aBlockedPersonCanNotUseTheOwnersCode() throws Exception {
        Person owner = person("code_owner");
        Person blocked = person("code_blocked");
        String code = body(get(owner.session(), "/api/invite-codes")).get("code").asText();
        post(owner.session(), "/api/blocks", Map.of("userId", id(blocked))).andExpect(status().isNoContent());

        post(blocked.session(), "/api/invite-codes/" + code + "/redeem", Map.of()).andExpect(status().isNotFound());
        assertThat(friendUsernames(owner)).isEmpty();
    }

    @Test
    void anExpiredCodeIsRefused() throws Exception {
        Person owner = person("expired_owner");
        Person user = person("expired_user");
        String code = body(get(owner.session(), "/api/invite-codes")).get("code").asText();
        jdbcExpire(code);

        post(user.session(), "/api/invite-codes/" + code + "/redeem", Map.of()).andExpect(status().isNotFound());
        // And asking for a code again gives a new one.
        assertThat(body(get(owner.session(), "/api/invite-codes")).get("code").asText()).isNotEqualTo(code);
    }

    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private void jdbcExpire(String code) {
        jdbc.update("update invite_codes set expires_at = now() - interval '1 minute' where code = ?", code);
    }

    // ------------------------------------------------------------ privacy

    @Test
    void privacyStartsWithSafeDefaultsAndIsChangedPerUser() throws Exception {
        Person a = person("private_a");
        Person b = person("private_b");

        get(a.session(), "/api/me/privacy")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showFeed").value(true))
                .andExpect(jsonPath("$.showPrWeights").value(false))
                .andExpect(jsonPath("$.showOnLeaderboard").value(true));

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("showFeed", false);
        settings.put("showPrWeights", true);
        settings.put("showOnLeaderboard", false);
        put(a.session(), "/api/me/privacy", settings)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showFeed").value(false))
                .andExpect(jsonPath("$.showPrWeights").value(true))
                .andExpect(jsonPath("$.showOnLeaderboard").value(false));
        get(a.session(), "/api/me/privacy").andExpect(jsonPath("$.showFeed").value(false));
        get(b.session(), "/api/me/privacy").andExpect(jsonPath("$.showFeed").value(true));
    }

    // ------------------------------------------------------------ access

    @Test
    void everythingNeedsASignedInUser() throws Exception {
        for (String path : List.of("/api/friends", "/api/friends/requests", "/api/users/search?username=abc", "/api/invite-codes", "/api/blocks", "/api/me/privacy", "/api/leaderboard", "/api/duels", "/api/feed")) {
            mvc.perform(MockMvcRequestBuilders.get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(MockMvcRequestBuilders.post("/api/friends/requests").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        assertThat(TestUsers.PASSWORD).isNotBlank();
    }
}
