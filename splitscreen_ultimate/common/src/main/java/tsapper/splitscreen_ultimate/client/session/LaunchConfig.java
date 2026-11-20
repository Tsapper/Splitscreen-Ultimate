package tsapper.splitscreen_ultimate.client.session;

public record LaunchConfig(String session, String instance, int slot, int port, String token, String route) {
    public static final String PREFIX = "splitscreen.";
    public boolean main() { return slot == 1; }
    public static boolean enabled() { return System.getProperty(PREFIX + "session") != null; }
    public static boolean guest() { return enabled() && !"main".equals(System.getProperty(PREFIX + "role")); }
    public static LaunchConfig read() {
        int slot = Integer.parseInt(required("slot"));
        int port = Integer.parseInt(required("port"));
        if (slot < 1 || slot > 4 || port < 1 || port > 65535) throw new IllegalArgumentException("Invalid launch configuration");
        if ((slot == 1) != "main".equals(required("role"))) throw new IllegalArgumentException("Invalid instance role");
        return new LaunchConfig(required("session"), required("instance"), slot, port, required("token"),
                System.getProperty(PREFIX + "controller", ""));
    }
    private static String required(String name) {
        String value = System.getProperty(PREFIX + name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing launch property " + name);
        return value;
    }
}
