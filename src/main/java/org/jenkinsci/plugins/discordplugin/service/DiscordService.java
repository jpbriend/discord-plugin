package org.jenkinsci.plugins.discordplugin.service;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import org.jenkinsci.plugins.discordplugin.exceptions.DiscordConfigurationException;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import javax.security.auth.login.CredentialNotFoundException;
import javax.security.auth.login.LoginException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class DiscordService {
    private static DiscordService service;

    private static final Logger logger = Logger.getLogger(DiscordService.class.getName());

    private String server;
    private String tokenCredentialId;

    private JDA jda;
    private Guild guild;

    private DiscordService() {}

    public static synchronized DiscordService getInstance() {
        if (null == service) {
            service = new DiscordService();
        }
        return service;
    }

    public void notifyBuild(String channel, AbstractBuild<?,?> build, BuildListener listener) {
        String jenkinsURL = Jenkins.getInstance().getRootUrl();
        TextChannel textChannel = this.getTextChannel(channel);

        EmbedBuilder builder = new EmbedBuilder();
        builder
                .setTitle(((AbstractBuild)build).getProject().getFullDisplayName()+" - "+build.getDisplayName())
                .setColor(build.getResult().color.getBaseColor())
                .setDescription(build.getParent().getDescription())
                .addField("Status", build.getResult().toString(), true);
        if (null != jenkinsURL) {
            builder.addField("", "[Logs](" + Jenkins.getInstance().getRootUrl() + build.getUrl() + "console)", true);
        }
        builder
                .addField("Duration", Util.getTimeSpanString(System.currentTimeMillis()-build.getStartTimeInMillis()), false)
                .setTimestamp(Instant.now());
        MessageEmbed message = builder.build();

        textChannel.sendMessage(message).queue(response->listener.getLogger().println("Notification sent to Discord server on channel " + channel));
    }

    private TextChannel getTextChannel(String channel) {
        List<TextChannel> channels = this.guild.getTextChannelsByName(channel, true);
        return channels.get(0);

    }

    private void checkConfigurationValid() throws DiscordConfigurationException {

        // Check server configuration
        if (this.server == null) {
            throw new DiscordConfigurationException("Missing Discord server configuration");
        }

        // Check token availability
        try {
            String token = this.getDiscordToken();
            if (token == null) {
                throw new DiscordConfigurationException("Credential " + this.tokenCredentialId + " is empty");
            }
        } catch (CredentialNotFoundException e) {
            throw new DiscordConfigurationException(e.getMessage());
        }

        return;
    }

    public boolean isConnected() {
        boolean result = false;
        if (null != this.jda) {
            if (JDA.Status.CONNECTED == this.jda.getStatus()) {
                result = true;
            }
        }
        return result;
    }

    /**
     * Connect to the Discord servers and to the provided guild
     * @return true if connection is ok. Otherwise false
     * @throws CredentialNotFoundException
     */
    public boolean connect() throws CredentialNotFoundException {
        logger.fine("Connecting to Discord server...");
        boolean result = false;

        try {
            this.checkConfigurationValid();
        } catch (DiscordConfigurationException e) {
            e.printStackTrace();
            return false;
        }
        logger.fine("Connection configuration is ok.");

        String token = this.getDiscordToken();

        try {
            this.jda = new JDABuilder(AccountType.BOT).setToken(token).buildBlocking();

            List<Guild> guilds = jda.getGuildsByName(server, true);
            this.guild = guilds.get(0);
            if (guild != null) {
                logger.fine("Connection to Discord is fine. Connected to guild " + guild.getName());
                result = true;
            }
        } catch (InterruptedException|RateLimitedException|LoginException e) {
            e.printStackTrace();
        }
        return result;
    }

    private String getDiscordToken() throws CredentialNotFoundException {
        Credentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StringCredentials.class,
                        Jenkins.getInstance(),
                        ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(this.tokenCredentialId)
        );

        if (null == credentials) {
            throw new CredentialNotFoundException("Can not find token '" + tokenCredentialId);
        } else {
            return ((StringCredentials) credentials).getSecret().getPlainText();
        }
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getTokenCredentialId() {
        return tokenCredentialId;
    }

    public void setTokenCredentialId(String tokenCredentialId) {
        this.tokenCredentialId = tokenCredentialId;
    }
}
