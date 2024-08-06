package io.jenkins.blueocean.autofavorite;

import hudson.Functions;
import hudson.model.Result;
import hudson.model.User;
import hudson.plugins.favorite.Favorites;
import java.util.concurrent.TimeUnit;

import hudson.plugins.git.util.BuildData;
import io.jenkins.blueocean.autofavorite.user.FavoritingUserProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.MultiBranchProject.BranchIndexing;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

public class FavoritingScmListenerTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testAutoFavoriteForRegisteredUser() throws Exception {
        assumeFalse(Functions.isWindows());
        User.getById("jdumay", true);
        WorkflowJob job = createAndRunPipeline();
        User user = User.getById("jdumay", false);
        assertNotNull(user);
        assertTrue(Favorites.isFavorite(user, job));
    }

    @Test
    public void testAutoFavoriteForRegisteredUserWhenDisabled() throws Exception {
        assumeFalse(Functions.isWindows());
        User jdumay = User.getById("jdumay", true);
        assertNotNull(jdumay);

        // Disable autofavorite
        FavoritingUserProperty.from(jdumay).setAutofavoriteEnabled(false);
        jdumay.save();

        WorkflowJob job = createAndRunPipeline();
        User user = User.getById("jdumay", false);
        assertNotNull(user);
        assertFalse(Favorites.isFavorite(user, job));
    }

    @Test
    public void testAutoFavoriteForRegisteredUserWhenGloballyDisabled() throws Exception {
        assumeFalse(Functions.isWindows());
        User jdumay = User.getById("jdumay", true);
        assertNotNull(jdumay);

        // Disable autofavorite
        assertTrue(FavoritingScmListener.isEnabled());
        System.setProperty(FavoritingScmListener.BLUEOCEAN_FEATURE_AUTOFAVORITE_ENABLED_PROPERTY, "false");
        assertFalse(FavoritingScmListener.isEnabled());

        WorkflowJob job = createAndRunPipeline();
        User user = User.getById("jdumay", false);
        assertNotNull(user);
        assertFalse(Favorites.isFavorite(user, job));
    }

    @Test
    public void testAutoFavoriteNullBuildData() throws Exception {
        assumeFalse(Functions.isWindows());
        User.getById("jdumay", true);
        WorkflowJob job = createAndRunPipeline(true);
        WorkflowRun build = job.getBuildByNumber(1);
        assertTrue("Build shouldn't have any data", build.getActions(BuildData.class).isEmpty());
        User user = User.getById("jdumay", false);
        assertNotNull(user);
        assertFalse("User shouldn't have any favorite", Favorites.isFavorite(user, job));
    }

    @After
    public void enableFeature() {
        System.setProperty(FavoritingScmListener.BLUEOCEAN_FEATURE_AUTOFAVORITE_ENABLED_PROPERTY, "true");
    }

    @Ignore("JENKINS-39694")
    @Test
    public void testAutoFavoriteForNonRegisteredUser() throws Exception {
        assumeFalse(Functions.isWindows());
        assertNull(User.getById("jdumay", false));
        WorkflowJob job = createAndRunPipeline();
        User user = User.getById("jdumay", false);
        assertNull(User.getById("jdumay", false));
    }

    private WorkflowJob createAndRunPipeline() throws java.io.IOException, InterruptedException {
        return createAndRunPipeline(false);
    }

    private WorkflowJob createAndRunPipeline(boolean removeBuildData) throws java.io.IOException, InterruptedException {
        WorkflowMultiBranchProject mbp = j.createProject(WorkflowMultiBranchProject.class, "WorkflowMultiBranchProject");
        GitSCMSource gitSCMSource = new GitSCMSource("https://github.com/i386/feedle.git");
        gitSCMSource.setCredentialsId("");
        gitSCMSource.getTraits().add(new BranchDiscoveryTrait());
        mbp.getSourcesList().add(new BranchSource(gitSCMSource));

        BranchIndexing<WorkflowJob, WorkflowRun> indexing = mbp.getIndexing();
        indexing.run();

        while (indexing.getResult() == Result.NOT_BUILT) {
            Thread.sleep(TimeUnit.SECONDS.toMillis(1));
        }

        assertEquals(Result.SUCCESS,  indexing.getResult());

        WorkflowJob job = mbp.getItem("master");
        while (job.getBuilds().isEmpty()) {
            Thread.sleep(5);
        }

        WorkflowRun run = job.getBuildByNumber(1);
        assertNotNull(run);

        while (run.getResult() == null) {
            /* poll faster as long as we still need to removeBuildData */
            if (removeBuildData) {
                run.removeActions(BuildData.class);
                Thread.sleep(5);
            } else {
                Thread.sleep(TimeUnit.SECONDS.toMillis(1));
            }
        }

        return job;
    }
}
