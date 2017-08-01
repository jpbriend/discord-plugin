package org.jenkinsci.plugins.discordplugin.service;

public class DiscordService {
    private static DiscordService service;
    private DiscordService() {}

    public static synchronized DiscordService getInstance() {
        if (null == service) {
            service = new DiscordService();
        }
        return service;
    }

    private void connect() {

    }
}
