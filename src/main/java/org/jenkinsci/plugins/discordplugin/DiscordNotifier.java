package org.jenkinsci.plugins.discordplugin;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.model.Jenkins;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;


public class DiscordNotifier extends Notifier {

    private static final Logger logger = Logger.getLogger(DiscordNotifier.class.getName());

    private final String server;
    private final String channel;
    private final String token;

    @DataBoundConstructor
    public DiscordNotifier(String server, String channel, String token) {
        super();
        this.server = server;
        this.channel = channel;
        this.token = token;
    }

    public String getServer() {
        return server;
    }
    public String getChannel() {
        return channel;
    }
    public String getToken() {
        return token;
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        try {
            JDA jda = new JDABuilder(AccountType.BOT).setToken(token).buildBlocking();

            List<Guild> guilds = jda.getGuildsByName(server, true);
            Guild guild = guilds.get(0);

            List<TextChannel> channels = guild.getTextChannelsByName(channel, true);
            TextChannel outputChannel = channels.get(0);

            EmbedBuilder builder = new EmbedBuilder();
            MessageEmbed message = builder
                    .setTitle(((AbstractBuild)build).getProject().getFullDisplayName()+" - "+build.getDisplayName())
                    .setColor(build.getResult().color.getBaseColor())
                    .setDescription(build.getParent().getDescription())
                    .addField("Status", build.getResult().toString(), true)
                    .addField("", "[Logs]("+Jenkins.getInstance().getRootUrl()+build.getUrl()+"/console)", true)
                    .addField("Duration", Util.getTimeSpanString(System.currentTimeMillis()-build.getStartTimeInMillis()), false)
                    .setTimestamp(Instant.now())
                    .build();

            outputChannel.sendMessage(message).queue(response->listener.getLogger().println("Notification sent to Discord server on channel " + channel));

        } catch (LoginException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (RateLimitedException e) {
            e.printStackTrace();
        } finally {
            return true;
        }
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @SuppressWarnings("rawtypes")
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }
    }

}

