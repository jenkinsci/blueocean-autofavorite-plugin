package io.jenkins.blueocean.autofavorite.user;

import hudson.Extension;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import io.jenkins.blueocean.autofavorite.Messages;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

public class FavoritingUserProperty extends UserProperty {

    private boolean enabled = true;

    @DataBoundConstructor
    public FavoritingUserProperty(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Extension
    public static class DescriptorImpl extends UserPropertyDescriptor {
        @Override
        public UserProperty newInstance(User user) {
            return new FavoritingUserProperty(true);
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.autofavorite_name();
        }
    }

    public static FavoritingUserProperty from(User user) {
        return user.getProperty(FavoritingUserProperty.class);
    }
}
