package tsapper.splitscreen_ultimate.client.session;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayersTest {
    @Test void firstControllerClaimsMainAndReservationsAreIdempotent() {
        var players = new Players(); var pad = UUID.randomUUID();
        assertEquals(1, players.claim(pad).slot);
        assertNull(players.claim(pad)); assertEquals(1, players.all().size());
    }
    @Test void maximumFourPlayersIncludingPendingLaunches() {
        var players = new Players();
        for (int i = 1; i <= 4; i++) assertEquals(i, players.claim(UUID.randomUUID()).slot);
        assertNull(players.claim(UUID.randomUUID()));
    }
    @Test void reconnectUsesLowestReservedSlotWithoutLaunching() {
        var players = new Players(); var one = UUID.randomUUID(); var two = UUID.randomUUID();
        players.claim(one); var guest = players.claim(two); guest.launched = true;
        players.disconnected(Set.of(one));
        var replacement = players.claim(UUID.randomUUID());
        assertSame(guest, replacement); assertEquals(2, replacement.slot); assertTrue(replacement.launched);
    }
    @Test void staleLaunchResultsCannotRemoveReplacement() {
        var players = new Players(); players.claim(UUID.randomUUID());
        var guest = players.claim(UUID.randomUUID()); String old = guest.request;
        players.remove(2); var replacement = players.claim(UUID.randomUUID());
        assertFalse(players.launchResult(2, old, false)); assertSame(replacement, players.get(2));
        assertTrue(players.launchResult(2, replacement.request, false)); assertNull(players.get(2));
    }
    @Test void exitedGuestFreesControllerForFreshJoin() {
        var players = new Players(); players.claim(UUID.randomUUID()); var pad = UUID.randomUUID();
        var first = players.claim(pad); players.remove(2); var next = players.claim(pad);
        assertEquals(2, next.slot); assertNotEquals(first.route, next.route);
    }
}
