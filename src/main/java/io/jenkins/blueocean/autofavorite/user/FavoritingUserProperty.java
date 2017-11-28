package io.jenkins.blueocean.autofavorite.user;

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import io.jenkins.blueocean.autofavorite.Messages;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

public class FavoritingUserProperty extends UserProperty {

    private boolean autofavoriteEnabled;

    @DataBoundConstructor
    public FavoritingUserProperty(boolean autofavoriteEnabled) {
        this.autofavoriteEnabled = autofavoriteEnabled;
    }

    public boolean isAutofavoriteEnabled() {
        return autofavoriteEnabled;
    }

    @VisibleForTesting
    public void setAutofavoriteEnabled(boolean autofavoriteEnabled) {
        this.autofavoriteEnabled = autofavoriteEnabled;
    }

    @Extension
    public static class DescriptorImpl extends UserPropertyDescriptor {

        public DescriptorImpl() {
            super(FavoritingUserProperty.class);
        }

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
