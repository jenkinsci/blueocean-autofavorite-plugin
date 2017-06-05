package io.jenkins.blueocean.autofavorite;

import com.google.common.collect.Iterables;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.model.listeners.SCMListener;
import hudson.plugins.favorite.Favorites;
import hudson.plugins.favorite.Favorites.FavoriteException;
import hudson.plugins.git.GitChangeLogParser;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.BuildData;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import hudson.util.LogTaskListener;
import jenkins.branch.MultiBranchProject;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.errors.MissingObjectException;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.CheckForNull;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class FavoritingScmListener extends SCMListener {

    private static final Logger LOGGER = Logger.getLogger(FavoritingScmListener.class.getName());

    @Override
    public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener, @CheckForNull File changelogFile, @CheckForNull SCMRevisionState pollingBaseline) throws Exception {
        // Look for the first build of a multibranch job
        if (!(build instanceof WorkflowRun
                && ((WorkflowRun) build).getParent().getParent() instanceof MultiBranchProject
                && build.getNumber() == 1
                && scm instanceof GitSCM)) {
            return;
        }

        // Bail out if the workspace does not exist or is not a directory
        if (!workspace.exists() || !workspace.isDirectory()) {
            LOGGER.fine("Workspace '" + workspace.getRemote() + "' does not exist or is a directory. Favoriting cannot be run.");
            return;
        }

        BuildData buildData = build.getAction(BuildData.class);
        Revision lastBuiltRevision = buildData.getLastBuiltRevision();
        if (lastBuiltRevision == null) {
            return;
        }

        GitSCM gitSCM = (GitSCM)scm;

        // Sometimes the Git repository isn't consistent so we need to retry (JENKINS-39704)
        GitChangeSet first;
        try {
            first = getChangeSet(gitSCM, workspace, lastBuiltRevision, listener);
        } catch (GitException e) {
            if (e.getCause() instanceof MissingObjectException) {
                // Wait before we retry...
                Thread.sleep(TimeUnit.SECONDS.toMillis(2));
                try {
                    first = getChangeSet(gitSCM, workspace, lastBuiltRevision, listener);
                } catch (GitException ex) {
                    LOGGER.log(Level.SEVERE, "Git repository is not consistent. Can't get the changeset that was just checked out.", ex);
                    first = null;
                }
            } else {
                LOGGER.log(Level.SEVERE, "Unexpected error when retrieving changeset", e);
                first = null;
            }
        }
        if (first == null) {
            return;
        }

        Job<?, ?> job = build.getParent();
        User author = first.getAuthor();

        // User does not exist or is unknown
        if (User.getById(author.getId(), false) == null || User.getUnknown().equals(author)) {
            return;
        }

        // If the user has already favorited then unfavorited it we should not favorite it again
        if (Favorites.hasFavorite(author, job) && !Favorites.isFavorite(author, job)) {
            return;
        }

        // Do not try to favorite if its already a favorite
        // As shown in JENKINS-39803 with docker-workflow, there are 2 checkouts: one to get the Jenknsfile
        // and another to checkout the source within the container, of which this listener will get run twice.
        if (Favorites.isFavorite(author, job)) {
            return;
        }

        // If the user favourites the Job before we get a chance to then an exception could be thrown, failing the run.
        try {
            Favorites.addFavorite(author, job);
            LOGGER.log(Level.INFO, "Automatically favorited " + job.getFullName() + " for " + author);
        } catch (FavoriteException e) {
            LOGGER.log(Level.SEVERE, "Couldn't favourite " + job.getFullName() + " for " + author, e);
        }
    }

    private GitChangeSet getChangeSet(GitSCM scm, FilePath workspace, Revision lastBuiltRevision, TaskListener listener) throws IOException, InterruptedException {
        Git gitBuilder = Git.with(listener, new EnvVars())
                .in(workspace);

        GitTool tool = scm.resolveGitTool(new LogTaskListener(LOGGER, Level.FINE));
        if (tool != null) {
            LOGGER.log(Level.FINE, "Using Git executable for autofavorite");
            gitBuilder = gitBuilder.using(tool.getGitExe());
        } else {
            LOGGER.log(Level.INFO, "Using JGit for autofavorite. This is less reliable than using the Git executable. You should define a Git tool, see https://jenkins.io/doc/book/managing/tools/");
        }

        GitClient git = gitBuilder.getClient();

        List<GitChangeSet> changeSets;
        try (StringWriter writer = new StringWriter()) {
            // Fetch the first commit
            git.changelog()
                    .includes(lastBuiltRevision.getSha1())
                    .to(writer)
                    .max(1)
                    .execute();

            // Parse the changelog
            GitChangeLogParser parser = new GitChangeLogParser(true);
            try (StringReader input = new StringReader(writer.toString())) {
                List<String> lines = IOUtils.readLines(input);
                changeSets = parser.parse(lines);
            }
        }
        return Iterables.getOnlyElement(changeSets, null);
    }
}
