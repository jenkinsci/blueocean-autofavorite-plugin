package io.jenkins.blueocean.autofavorite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import hudson.model.Result;
import hudson.model.User;
import hudson.plugins.favorite.Favorites;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import hudson.plugins.git.GitSCM;
import jenkins.branch.BranchSource;
import jenkins.branch.MultiBranchProject.BranchIndexing;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.libs.FolderLibraries;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

public class WorkFlowLibrariesTest {

	@Rule
	public TemporaryFolder folder= new TemporaryFolder();

	@Rule
	public JenkinsRule j = new JenkinsRule();

	private Path jobRepository;
	private Path libraryRepository;

	@Before
	public void setUp() throws Exception {

		GitSCM.ALLOW_LOCAL_CHECKOUT = true;

		jobRepository = folder.newFolder().toPath();
		libraryRepository = folder.newFolder().toPath();

		createJobRepository(jobRepository);
		createLibraryRepository(libraryRepository);

	}

	@Test
	public void testGlobal() throws Exception {

		setupGlobalLibraries(libraryRepository);

		assertNull(User.getById("name1", false));
		assertNull(User.getById("name2", false));
		User.getById("name1", true);
		User.getById("name2", true);

		final WorkflowMultiBranchProject project = createWorkflowMultiBranchProject(jobRepository);
		project.addProperty(createFolderLibraries(libraryRepository));
		final WorkflowJob job = runPipeline(project);

		final User name1 = User.getById("name1", false);
		assertNotNull(name1);
		assertTrue(Favorites.isFavorite(name1, job));

		final User name2 = User.getById("name2", false);
		assertNotNull(name2);
		assertFalse(Favorites.isFavorite(name2, job));

	}


	@Test
	public void testFolder() throws Exception {

		assertNull(User.getById("name1", false));
		assertNull(User.getById("name2", false));
		User.getById("name1", true);
		User.getById("name2", true);

		final WorkflowMultiBranchProject project = createWorkflowMultiBranchProject(jobRepository);
		project.addProperty(createFolderLibraries(libraryRepository));
		final WorkflowJob job = runPipeline(project);

		final User name1 = User.getById("name1", false);
		assertNotNull(name1);
		assertTrue(Favorites.isFavorite(name1, job));

		final User name2 = User.getById("name2", false);
		assertNotNull(name2);
		assertFalse(Favorites.isFavorite(name2, job));

	}

	private WorkflowMultiBranchProject createWorkflowMultiBranchProject(final Path jobRepository) throws java.io.IOException, InterruptedException {
		WorkflowMultiBranchProject mbp = j.createProject(WorkflowMultiBranchProject.class, "WorkflowMultiBranchProject");
		GitSCMSource gitSCMSource = new GitSCMSource(jobRepository.toString());
		gitSCMSource.setCredentialsId("");
		gitSCMSource.getTraits().add(new BranchDiscoveryTrait());
		mbp.getSourcesList().add(new BranchSource(gitSCMSource));

		return mbp;
	}

	private WorkflowJob runPipeline(final WorkflowMultiBranchProject mbp) throws java.io.IOException, InterruptedException {
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
			Thread.sleep(TimeUnit.SECONDS.toMillis(1));
		}

		return job;
	}

	private void setupGlobalLibraries(final Path libraryRepository) {
		List<LibraryConfiguration> libraries = new ArrayList<>();
		GitSCMSource scmSource = new GitSCMSource(libraryRepository.toString());
		SCMSourceRetriever scmSourceRetriever = new SCMSourceRetriever(scmSource);
		final LibraryConfiguration libraryConfiguration = new LibraryConfiguration(
			"shared-library",
			scmSourceRetriever);
		libraryConfiguration.setDefaultVersion("master");
		libraries.add(libraryConfiguration);
		GlobalLibraries.get().setLibraries(libraries);
	}

	private FolderLibraries createFolderLibraries(final Path libraryRepository) {
		List<LibraryConfiguration> libraries = new ArrayList<>();
		GitSCMSource scmSource = new GitSCMSource(libraryRepository.toString());
		SCMSourceRetriever scmSourceRetriever = new SCMSourceRetriever(scmSource);
		final LibraryConfiguration libraryConfiguration = new LibraryConfiguration(
				"shared-library",
				scmSourceRetriever);
		libraryConfiguration.setDefaultVersion("master");
		libraries.add(libraryConfiguration);
		return new FolderLibraries(libraries);
	}

	private void createJobRepository(final Path path) throws IOException, GitAPIException {
		Files.copy(getClass().getResourceAsStream("/job-repository/Jenkinsfile"),
				   path.resolve("Jenkinsfile"));

		try (Git git = Git.init().setDirectory(path.toFile()).call()) {

			git.add().addFilepattern("Jenkinsfile").call();

			git.commit()
			   .setMessage("some commit message 1")
			   .setAuthor("name1", "name1@example.com")
			   .call();
		}
	}

	private void createLibraryRepository(final Path path) throws IOException, GitAPIException {
		final Path vars = path.resolve("vars");
		vars.toFile().mkdir();

		Files.copy(getClass().getResourceAsStream("/library-repository/vars/helloWorld.groovy"),
				   vars.resolve("helloWorld.groovy"));

		try (Git git = Git.init().setDirectory(path.toFile()).call()) {

			git.add().addFilepattern("vars/helloWorld.groovy").call();

			git.commit()
			   .setMessage("some commit message 2")
			   .setAuthor("name2", "name2@example.com")
			   .call();
		}
	}
}
