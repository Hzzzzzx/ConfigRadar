package io.github.hzzzzzx.configradar.core.scm;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests GitClient against a real temporary git repository. Skipped automatically when git is not on
 * PATH (CI without git should not hard-fail).
 */
final class GitClientTest {
    private final GitClient git = new GitClient();

    @TempDir
    Path tempDir;

    private Path initRepo() throws Exception {
        var repo = tempDir.resolve("repo");
        Files.createDirectories(repo);
        run(repo, "git", "init", "-q");
        run(repo, "git", "config", "user.email", "t@t");
        run(repo, "git", "config", "user.name", "t");
        return repo;
    }

    private void commit(Path repo, String msg) throws Exception {
        run(repo, "git", "add", "-A");
        run(repo, "git", "commit", "-q", "-m", msg);
    }

    @Test
    void listsChangedFilesBetweenTwoCommits() throws Exception {
        var repo = initRepo();
        // base commit: one file
        Files.writeString(repo.resolve("application.yml"), "server:\n  port: 8080\n");
        commit(repo, "base");

        // head commit: change one file, add another
        Files.writeString(repo.resolve("application.yml"), "server:\n  port: 9090\n");
        Files.writeString(repo.resolve("new.yml"), "feature:\n  flag: true\n");
        commit(repo, "head");

        var changed = git.changedFiles(repo, "HEAD~1", "HEAD");
        assertTrue(changed.contains("application.yml"), "changed file listed");
        assertTrue(changed.contains("new.yml"), "added file listed");
        assertEquals(2, changed.size());
    }

    @Test
    void materializesCommitIntoWorktree() throws Exception {
        var repo = initRepo();
        Files.writeString(repo.resolve("application.yml"), "server:\n  port: 8080\n");
        commit(repo, "v1");

        var worktree = tempDir.resolve("wt");
        git.addWorktree(repo, "HEAD", worktree);
        assertTrue(Files.exists(worktree.resolve("application.yml")), "worktree has the file");

        git.removeWorktree(repo, worktree);
        assertFalse(Files.exists(worktree.resolve("application.yml")), "worktree cleaned up");
    }

    @Test
    void resolvesRepositoryRoot() throws Exception {
        var repo = initRepo();
        var root = git.repositoryRoot(repo);
        assertNotNull(root, "repository root resolved");
        assertTrue(root.toString().contains("repo"), "root is the repo path");
    }

    private static void run(Path cwd, String... command) throws Exception {
        var p = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start();
        p.waitFor();
        if (p.exitValue() != 0) {
            throw new IllegalStateException("cmd failed: " + new String(p.getInputStream().readAllBytes()));
        }
    }
}
