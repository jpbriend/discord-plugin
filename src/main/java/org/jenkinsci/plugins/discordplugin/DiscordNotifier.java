package org.jenkinsci.plugins.discordplugin;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.discordplugin.service.DiscordService;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.security.auth.login.CredentialNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.logging.Logger;


public class DiscordNotifier extends Notifier {

    private static final Logger logger = Logger.getLogger(DiscordNotifier.class.getName());

    private String channel;
    private DiscordService service = DiscordService.getInstance();

    @DataBoundConstructor
    public DiscordNotifier(String channel) {
        super();
        this.channel = channel;

        DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(DiscordNotifier.DescriptorImpl.class);
        this.service.setServer(descriptor.getDiscordServer());
        this.service.setTokenCredentialId(descriptor.getDiscordTokenCredentialId());
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        if (!this.service.isConnected()) {
            try {
                this.service.connect();
            } catch (CredentialNotFoundException e) {
                e.printStackTrace();
                throw new InterruptedException(e.getMessage());
            }
        }
        this.service.notifyBuild(this.channel, build, listener);
        return true;
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

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
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

        public FormValidation doTestConnection(@QueryParameter("discordServer") String discordServer,
                                               @QueryParameter("discordTokenCredentialId") String discordTokenCredentialId) throws FormException {
            return FormValidation.ok("Connection successful");
        }

        @Nonnull
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

