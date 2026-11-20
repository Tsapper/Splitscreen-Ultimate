package tsapper.splitscreen_ultimate.client.session;

import java.util.*;

/** Client-thread session authority. Physical connection ids never leave the main instance. */
public final class Players {
    public static final class Player {
        public final int slot;
        public final String route = UUID.randomUUID().toString();
        public UUID physical;
        public String request = "";
        public String status = "Waiting";
        public boolean launched, ready, world;
        private Player(int slot, UUID physical) { this.slot = slot; this.physical = physical; }
    }
    private final TreeMap<Integer, Player> players = new TreeMap<>();
    public Players() { var main = new Player(1, null); main.launched = main.ready = true; players.put(1, main); }
    public Collection<Player> all() { return Collections.unmodifiableCollection(players.values()); }
    public Player get(int slot) { return players.get(slot); }
    public Player claim(UUID connection) {
        if (players.values().stream().anyMatch(p -> connection.equals(p.physical))) return null;
        Player waiting = players.values().stream().filter(p -> p.physical == null).findFirst().orElse(null);
        if (waiting != null) { waiting.physical = connection; return waiting; }
        if (players.size() >= 4) return null;
        for (int slot = 2; slot <= 4; slot++) if (!players.containsKey(slot)) {
            Player player = new Player(slot, connection);
            player.request = UUID.randomUUID().toString();
            player.status = "Launching";
            players.put(slot, player);
            return player;
        }
        return null;
    }
    public void disconnected(Set<UUID> connected) {
        for (Player p : players.values()) if (p.physical != null && !connected.contains(p.physical)) {
            p.physical = null;
            p.status = "Controller disconnected — press A to reconnect";
        }
    }
    public boolean launchResult(int slot, String request, boolean success) {
        Player player = players.get(slot);
        if (player == null || !player.request.equals(request)) return false;
        if (!success) players.remove(slot); else player.launched = true;
        return true;
    }
    public void remove(int slot) { if (slot != 1) players.remove(slot); }
}
