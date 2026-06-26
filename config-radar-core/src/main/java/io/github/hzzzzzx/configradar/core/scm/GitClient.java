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

    /**
     * Extracts a commit's working tree into {@code path} via {@code git archive}, without creating a
     * git worktree. Used as a fallback when {@link #addWorktree} fails (e.g. worktree lock, NTFS
     * slowness): archive only materializes files (no .git links, hooks, or index), so it is lighter
     * and avoids the common worktree failure modes. Scanning does not need git metadata.
     *
     * <p>Uses zip format (not tar) so the extraction is done with the JDK's built-in {@code java.util.zip},
     * with no dependency on a shell pipe or external tar — works on Windows too.
     *
     * @param repo path inside the repository
     * @param ref commit-ish to extract
     * @param path absolute target directory (created if absent; contents extracted at root)
     */
    public void archive(Path repo, String ref, Path path) throws Exception {
        Files.createDirectories(path);
        var zipFile = Files.createTempFile("cr-archive-", ".zip");
        try {
            run(repo, WORKTREE_TIMEOUT_SECONDS, "git", "-C", repo.toString(),
                "archive", "--format=zip", "-o", zipFile.toString(), ref);
            extractZip(zipFile, path);
        } finally {
            Files.deleteIfExists(zipFile);
        }
    }

    /** Extracts a zip archive into a directory. */
    private static void extractZip(Path zip, Path dest) throws IOException {
        try (var zipFs = java.nio.file.FileSystems.newFileSystem(zip, (ClassLoader) null)) {
            var root = zipFs.getPath("/");
            try (var walk = Files.walk(root)) {
                for (var source : walk.toList()) {
                    var relative = root.relativize(source).toString();
                    if (relative.isEmpty()) {
                        continue;
                    }
                    var target = dest.resolve(relative);
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
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
