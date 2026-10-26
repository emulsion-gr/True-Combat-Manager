package com.muzlik.pvpcombat.tracking;

/**
 * Data class representing server information to be sent in heartbeat requests.
 * Contains all the stats that the tracking website will display.
 */
public class ServerInfo {

    private final String serverId;
    private final String serverName;
    private final String host;
    private final int port;
    private final String version;
    private final String bukkitVersion;
    private final String minecraftVersion;
    private final int onlinePlayers;
    private final int maxPlayers;
    private final boolean online;
    private final long uptimeMillis;
    private final String pluginVersion;
    private final long lastHeartbeat;

    public ServerInfo(String serverId, String serverName, String host, int port,
                      String version, String bukkitVersion, String minecraftVersion,
                      int onlinePlayers, int maxPlayers, boolean online,
                      long uptimeMillis, String pluginVersion) {
        this.serverId = serverId;
        this.serverName = serverName;
        this.host = host;
        this.port = port;
        this.version = version;
        this.bukkitVersion = bukkitVersion;
        this.minecraftVersion = minecraftVersion;
        this.onlinePlayers = onlinePlayers;
        this.maxPlayers = maxPlayers;
        this.online = online;
        this.uptimeMillis = uptimeMillis;
        this.pluginVersion = pluginVersion;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public String getServerId() { return serverId; }
    public String getServerName() { return serverName; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getVersion() { return version; }
    public String getBukkitVersion() { return bukkitVersion; }
    public String getMinecraftVersion() { return minecraftVersion; }
    public int getOnlinePlayers() { return onlinePlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public boolean isOnline() { return online; }
    public long getUptimeMillis() { return uptimeMillis; }
    public String getPluginVersion() { return pluginVersion; }
    public long getLastHeartbeat() { return lastHeartbeat; }

    /**
     * Converts this ServerInfo to a JSON string for HTTP POST.
     */
    public String toJson() {
        return "{" +
            "\"serverId\":\"" + escapeJson(serverId) + "\"," +
            "\"serverName\":\"" + escapeJson(serverName) + "\"," +
            "\"host\":\"" + escapeJson(host) + "\"," +
            "\"port\":" + port + "," +
            "\"version\":\"" + escapeJson(version) + "\"," +
            "\"bukkitVersion\":\"" + escapeJson(bukkitVersion) + "\"," +
            "\"minecraftVersion\":\"" + escapeJson(minecraftVersion) + "\"," +
            "\"onlinePlayers\":" + onlinePlayers + "," +
            "\"maxPlayers\":" + maxPlayers + "," +
            "\"online\":" + online + "," +
            "\"uptimeMillis\":" + uptimeMillis + "," +
            "\"pluginVersion\":\"" + escapeJson(pluginVersion) + "\"," +
            "\"lastHeartbeat\":" + lastHeartbeat +
            "}";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
