package io.github.hzzzzzx.configradar.core.scm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Minimal git wrapper using the system {@code git} command via {@link ProcessBuilder}.
 *
 * <p>Used by the {@code config-diff} command to list changed files between two commits and to
 * materialize each commit into a temporary worktree for scanning. Paths reported by git are relative
 * to the repository root, matching ConfigRadar's {@code source.path} convention when the scanned
 * project root equals the repository root.
 *
 * <p>This is not a plugin loader — git must be installed on the host. It follows the same
 * ProcessBuilder pattern as {@code CodegraphCliSemanticProvider}.
 */
public final class GitClient {

    /** Timeout for quick read-only queries (diff, rev-parse). These are normally sub-second. */
    private static final long QUERY_TIMEOUT_SECONDS = 120;

    /**
     * Timeout for worktree operations (add/remove), which check out the full tree. On Windows/NTFS
     * this can take minutes for large repositories, so the budget is generous.
     */
    private static final long WORKTREE_TIMEOUT_SECONDS = 900;

    /**
     * Returns the repository root for the given path, or null if it is not inside a git repository.
     *
     * @param repo a path inside the repository
     * @return absolute repository root, or null
     */
    public Path repositoryRoot(Path repo) {
        try {
            var output = run(repo, QUERY_TIMEOUT_SECONDS, "git", "-C", repo.toString(), "rev-parse", "--show-toplevel");
            return Path.of(output.strip());
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Lists files changed between two commits (relative to the repository root).
     *
     * @param repo path inside the repository
     * @param baseRef base commit-ish (e.g. a tag, branch, or sha)
     * @param headRef head commit-ish
     * @return list of changed file paths, normalized to forward slashes
     */
    public List<String> changedFiles(Path repo, String baseRef, String headRef) throws Exception {
        var output = run(repo, QUERY_TIMEOUT_SECONDS, "git", "-C", repo.toString(), "diff", "--name-only", baseRef, headRef);
        var files = new ArrayList<String>();
        for (var line : output.split("\n")) {
            var trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                files.add(trimmed.replace('\\', '/'));
            }
        }
        return files;
    }

    /**
     * Creates a detached worktree of the given ref at {@code path}.
     *
     * @param repo path inside the repository
     * @param ref commit-ish to check out
     * @param path absolute path for the new worktree (must not exist)
     */
    public void addWorktree(Path repo, String ref, Path path) throws Exception {
        Files.createDirectories(path.getParent());
        run(repo, WORKTREE_TIMEOUT_SECONDS, "git", "-C", repo.toString(), "worktree", "add", "--detach", path.toString(), ref);
    }

    /**
     * Removes a worktree created by {@link #addWorktree}.
     *
     * @param repo path inside the repository
     * @param path the worktree path to remove
     */
    public void removeWorktree(Path repo, Path path) {
        try {
            run(repo, WORKTREE_TIMEOUT_SECONDS, "git", "-C", repo.toString(), "worktree", "remove", "--force", path.toString());
        } catch (Exception ignored) {
            // best-effort cleanup; the worktree may already be gone or removal may fail on some systems
        }
    }

    private static String run(Path cwd, long timeoutSeconds, String... command) throws Exception {
        var process = new ProcessBuilder(command)
            .directory(cwd.toFile())
            .redirectErrorStream(true)
            .start();
        var finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        var output = new String(process.getInputStream().readAllBytes());
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("git timed out after " + timeoutSeconds + "s: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            throw new IOException("git failed: " + output.strip());
        }
        return output;
    }
}
