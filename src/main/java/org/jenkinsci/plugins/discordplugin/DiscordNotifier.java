package org.jenkinsci.plugins.discordplugin;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.*;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.HostnameRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
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
        String jenkinsURL = Jenkins.getInstance().getRootUrl();
        try {
            JDA jda = new JDABuilder(AccountType.BOT).setToken(token).buildBlocking();

            List<Guild> guilds = jda.getGuildsByName(server, true);
            Guild guild = guilds.get(0);

            List<TextChannel> channels = guild.getTextChannelsByName(channel, true);
            TextChannel outputChannel = channels.get(0);

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

    public boolean isConnected() throws InterruptedException, LoginException, RateLimitedException {
        boolean result = false;
        JDA jda = new JDABuilder(AccountType.BOT).setToken(token).buildBlocking();
        if (JDA.Status.CONNECTED == jda.getStatus() ) {
            result = true;
        }
        jda.shutdownNow();
        return result;
    }

    public boolean isGuildAvailable() throws InterruptedException, LoginException, RateLimitedException {
        boolean result = false;
        JDA jda = new JDABuilder(AccountType.BOT).setToken(token).buildBlocking();
        List<Guild> guilds = jda.getGuildsByName(server, true);

        if (guilds.size() != 0) {
            Guild guild = guilds.get(0);
            if (JDA.Status.CONNECTED == jda.getStatus() && guild.isAvailable()) {
                result = true;
            }
        }

        jda.shutdownNow();
        return result;
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
        return new DescriptorImpl();
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private String discordServer;
        private String discordTokenCredentialId;

        public DescriptorImpl() {
            load();
        }

        public String getDiscordServer() {
            return discordServer;
        }

        public String getDiscordTokenCredentialId() {
            return discordTokenCredentialId;
        }

        public ListBoxModel doFillDiscordTokenCredentialIdItems(
                @QueryParameter String tokenCredentialId
                ) {

            if (!Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER)) {
                return new StandardUsernameListBoxModel().includeCurrentValue(tokenCredentialId);
            }

            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(ACL.SYSTEM,
                        Jenkins.getInstance(),
                        StringCredentials.class,
                        Collections.<DomainRequirement>emptyList(),
                        CredentialsMatchers.instanceOf(StringCredentials.class))
                    .includeCurrentValue(tokenCredentialId);
        }

        @Override
        public boolean configure(StaplerRequest sr, JSONObject formData) throws FormException {
            discordServer = formData.getJSONObject("discord").getString("discordServer");
            discordTokenCredentialId = formData.getJSONObject("discord").getString("discordTokenCredentialId");
            save();
            return super.configure(sr, formData);
        }

        @Override
        public DiscordNotifier newInstance(StaplerRequest sr, JSONObject json) throws FormException {
            System.out.println("New Instance...");
            return new DiscordNotifier("test", "test", "test");
        }

        public FormValidation doTestConnection(@QueryParameter("discordServer") String discordServer,
                                               @QueryParameter("discordTokenCredentialId") String discordTokenCredentialId) throws FormException {
            if (StringUtils.isEmpty(discordServer)) {
                return FormValidation.error("Discord Server must not be empty");
            }

            Credentials credentials = CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(
                            StringCredentials.class,
                            Jenkins.getInstance(),
                            ACL.SYSTEM,
                            Collections.<DomainRequirement>emptyList()),
                    CredentialsMatchers.withId(discordTokenCredentialId)
            );

            if (null == credentials) {
                return FormValidation.error("No token provided or cannot find currently selected credentials");
            }

            DiscordNotifier notifier = new DiscordNotifier(discordServer, "testbot", ((StringCredentials) credentials).getSecret().getPlainText());

            try {
                if ( !notifier.isConnected()) {
                    return FormValidation.error("Could not connect to Discord. Check your connectivity or credentials ?");
                }
                if (!notifier.isGuildAvailable()) {
                    return FormValidation.error("Connection to Discord is OK but could not join Server " + discordServer);
                } else {
                    return FormValidation.ok("Connection successful");
                }
            } catch (LoginException e) {
                e.printStackTrace();
                return FormValidation.error("Failure: \n" + e.getMessage());
            } catch (InterruptedException e) {
                e.printStackTrace();
                return FormValidation.error("Failure: \n" + e.getMessage());
            } catch (RateLimitedException e) {
                e.printStackTrace();
                return FormValidation.error("Failure: \n" + e.getMessage());
            }
        }

        @Override
        public String getDisplayName() {
            return "Discord Notifications";
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }
    }

}

