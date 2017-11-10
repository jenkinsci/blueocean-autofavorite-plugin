package io.jenkins.blueocean.autofavorite;

import hudson.model.Result;
import hudson.model.User;
import hudson.plugins.favorite.Favorites;
import java.util.concurrent.TimeUnit;
import jenkins.branch.BranchSource;
import jenkins.branch.MultiBranchProject.BranchIndexing;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;

public class FavoritingScmListenerTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testAutoFavoriteForRegisteredUser() throws Exception {
        User.getById("jdumay", true);
        WorkflowJob job = createAndRunPipeline();
        User user = User.getById("jdumay", false);
        assertNotNull(user);
        assertTrue(Favorites.isFavorite(user, job));
    }

//    @Test
    /** Disabled because of https://issues.jenkins-ci.org/browse/JENKINS-39694 **/
    public void testAutoFavoriteForNonRegisteredUser() throws Exception {
        assertNull(User.getById("jdumay", false));
        WorkflowJob job = createAndRunPipeline();
        User user = User.getById("jdumay", false);
        assertNull(User.getById("jdumay", false));
    }

    private WorkflowJob createAndRunPipeline() throws java.io.IOException, InterruptedException {
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
            Thread.sleep(TimeUnit.SECONDS.toMillis(1));
        }

        WorkflowRun run = job.getBuildByNumber(1);
        assertNotNull(run);


        while (run.getResult() == null) {
            Thread.sleep(TimeUnit.SECONDS.toMillis(1));
        }
        return job;
    }
}
