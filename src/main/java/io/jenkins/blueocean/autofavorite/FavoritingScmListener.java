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
import hudson.plugins.git.GitChangeLogParser;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.BuildData;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import io.jenkins.blueocean.service.embedded.util.FavoriteUtil;
import jenkins.branch.MultiBranchProject;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.CheckForNull;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class FavoritingScmListener extends SCMListener {

    private final Logger logger = Logger.getLogger(FavoritingScmListener.class.getName());

    @Override
    public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener, @CheckForNull File changelogFile, @CheckForNull SCMRevisionState pollingBaseline) throws Exception {
        // Look for the first build of a multibranch job
        if (build instanceof WorkflowRun
                && ((WorkflowRun) build).getParent().getParent() instanceof MultiBranchProject
                && build.getNumber() == 1
                && scm instanceof GitSCM) {

            BuildData buildData = build.getAction(BuildData.class);
            Revision lastBuiltRevision = buildData.getLastBuiltRevision();

            GitClient git = Git.with(TaskListener.NULL, new EnvVars())
                    .in(new File(workspace.getRemote()))
                    .getClient();

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

            GitChangeSet first = Iterables.getOnlyElement(changeSets, null);
            if (first != null) {
                Job<?, ?> job = build.getParent();
                User author = first.getAuthor();
                if (!User.getUnknown().equals(author) && !FavoriteUtil.hasFavourite(author, job)) {
                    FavoriteUtil.favoriteJob(job, author, true);
                    logger.log(Level.INFO, "Automatically favorited " + job.getFullName() + " for " + author);
                }
            }
        }
    }
}
