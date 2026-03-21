package com.gedfix.services

import java.io.File

/**
 * Git-based version control for power users.
 * Shells out to the git CLI for repository operations.
 * Requires git to be installed on the system.
 */
object GitSyncService {

    data class GitResult(val success: Boolean, val output: String)

    /**
     * Check if git is available on the system PATH.
     */
    fun isAvailable(): Boolean = try {
        val process = ProcessBuilder("git", "--version")
            .redirectErrorStream(true)
            .start()
        val exitCode = process.waitFor()
        exitCode == 0
    } catch (e: Exception) {
        false
    }

    /**
     * Initialize a git repository at the given path.
     */
    fun init(repoPath: String): GitResult = exec("git", "init", repoPath)

    /**
     * Check if a path is already a git repository.
     */
    fun isRepo(repoPath: String): Boolean {
        val result = exec("git", "-C", repoPath, "rev-parse", "--is-inside-work-tree")
        return result.success && result.output.trim() == "true"
    }

    /**
     * Stage all changes and commit with a message.
     */
    fun commit(repoPath: String, message: String): GitResult {
        val addResult = exec("git", "-C", repoPath, "add", ".")
        if (!addResult.success) return addResult
        return exec("git", "-C", repoPath, "commit", "-m", message)
    }

    /**
     * Push to the configured remote.
     */
    fun push(repoPath: String): GitResult = exec("git", "-C", repoPath, "push")

    /**
     * Get the current status of the repository.
     */
    fun status(repoPath: String): GitResult = exec("git", "-C", repoPath, "status", "--short")

    /**
     * Get the log of recent commits.
     */
    fun log(repoPath: String, maxCount: Int = 20): GitResult =
        exec("git", "-C", repoPath, "log", "--oneline", "-n", maxCount.toString())

    /**
     * Check if a remote is configured.
     */
    fun hasRemote(repoPath: String): Boolean {
        val result = exec("git", "-C", repoPath, "remote")
        return result.success && result.output.trim().isNotEmpty()
    }

    /**
     * Write a .gitignore for GEDCOM projects (exclude media, keep text data).
     */
    fun writeGitignore(repoPath: String) {
        val gitignore = """
            # GedFix Git Sync - auto-generated
            # Exclude large media files
            *.jpg
            *.jpeg
            *.png
            *.gif
            *.bmp
            *.tiff
            *.tif
            *.webp
            *.pdf
            *.mp3
            *.mp4
            *.mov
            *.avi

            # Exclude database files (GEDCOM is the source of truth)
            *.db
            *.db-journal
            *.db-shm
            *.db-wal

            # OS files
            .DS_Store
            Thumbs.db
        """.trimIndent()

        File(repoPath, ".gitignore").writeText(gitignore)
    }

    private fun exec(vararg command: String): GitResult = try {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        GitResult(success = exitCode == 0, output = output.trim())
    } catch (e: Exception) {
        GitResult(success = false, output = "Error: ${e.message}")
    }
}
