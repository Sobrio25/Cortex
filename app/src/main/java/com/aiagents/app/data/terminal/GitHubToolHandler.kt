package com.aiagents.app.data.terminal

import android.util.Log
import com.aiagents.app.data.local.SecurePreferences
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

data class GitHubToolResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class GitHubToolHandler @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val securePreferences: SecurePreferences
) {
    companion object {
        private const val TAG = "GitHubToolHandler"
        private const val API_URL = "https://api.github.com"
        const val TOOL_SEARCH_REPOS = "github_search_repos"
        const val TOOL_LIST_ISSUES = "github_list_issues"
        const val TOOL_CREATE_ISSUE = "github_create_issue"
        const val TOOL_READ_FILE = "github_read_file"
        const val TOOL_LIST_PULLS = "github_list_pulls"
        const val TOOL_GET_REPO = "github_get_repo"
        const val TOOL_CREATE_UPDATE_FILE = "github_create_update_file"
        const val TOOL_DELETE_FILE = "github_delete_file"
        const val TOOL_CREATE_BRANCH = "github_create_branch"
        const val TOOL_CREATE_PULL = "github_create_pull"
        const val TOOL_COMMENT_ISSUE = "github_comment_issue"
        const val TOOL_LIST_BRANCHES = "github_list_branches"
        const val TOOL_GET_ISSUE = "github_get_issue"
        const val TOOL_UPDATE_ISSUE = "github_update_issue"
        const val TOOL_LIST_ISSUE_COMMENTS = "github_list_issue_comments"
        const val TOOL_GET_PULL = "github_get_pull"
        const val TOOL_MERGE_PULL = "github_merge_pull"
        const val TOOL_UPDATE_PULL = "github_update_pull"
        const val TOOL_LIST_PR_FILES = "github_list_pr_files"
        const val TOOL_REQUEST_REVIEW = "github_request_review"
        const val TOOL_LIST_COMMITS = "github_list_commits"
        const val TOOL_GET_COMMIT = "github_get_commit"
        const val TOOL_LIST_RELEASES = "github_list_releases"
        const val TOOL_CREATE_RELEASE = "github_create_release"
        const val TOOL_GET_LATEST_RELEASE = "github_get_latest_release"
        const val TOOL_LIST_REPO_CONTENTS = "github_list_repo_contents"
        const val TOOL_LIST_TAGS = "github_list_tags"
        const val TOOL_LIST_CONTRIBUTORS = "github_list_contributors"
        const val TOOL_CREATE_REPO = "github_create_repo"
        const val TOOL_FORK_REPO = "github_fork_repo"
        const val TOOL_GET_USER = "github_get_user"
        const val TOOL_GET_AUTHENTICATED_USER = "github_get_authenticated_user"
        const val TOOL_LIST_USER_REPOS = "github_list_user_repos"
        const val TOOL_LIST_GISTS = "github_list_gists"
        const val TOOL_CREATE_GIST = "github_create_gist"
        const val TOOL_LIST_WORKFLOWS = "github_list_workflows"
        const val TOOL_TRIGGER_WORKFLOW = "github_trigger_workflow"
        const val TOOL_LIST_WORKFLOW_RUNS = "github_list_workflow_runs"
        const val TOOL_STAR_REPO = "github_star_repo"
        const val TOOL_LIST_STARRED = "github_list_starred"

        val ALL_TOOL_NAMES = setOf(
            TOOL_SEARCH_REPOS, TOOL_LIST_ISSUES, TOOL_CREATE_ISSUE,
            TOOL_READ_FILE, TOOL_LIST_PULLS, TOOL_GET_REPO,
            TOOL_CREATE_UPDATE_FILE, TOOL_DELETE_FILE, TOOL_CREATE_BRANCH,
            TOOL_CREATE_PULL, TOOL_COMMENT_ISSUE, TOOL_LIST_BRANCHES,
            TOOL_GET_ISSUE, TOOL_UPDATE_ISSUE, TOOL_LIST_ISSUE_COMMENTS,
            TOOL_GET_PULL, TOOL_MERGE_PULL, TOOL_UPDATE_PULL,
            TOOL_LIST_PR_FILES, TOOL_REQUEST_REVIEW,
            TOOL_LIST_COMMITS, TOOL_GET_COMMIT,
            TOOL_LIST_RELEASES, TOOL_CREATE_RELEASE, TOOL_GET_LATEST_RELEASE,
            TOOL_LIST_REPO_CONTENTS, TOOL_LIST_TAGS, TOOL_LIST_CONTRIBUTORS,
            TOOL_CREATE_REPO, TOOL_FORK_REPO,
            TOOL_GET_USER, TOOL_GET_AUTHENTICATED_USER, TOOL_LIST_USER_REPOS,
            TOOL_LIST_GISTS, TOOL_CREATE_GIST,
            TOOL_LIST_WORKFLOWS, TOOL_TRIGGER_WORKFLOW, TOOL_LIST_WORKFLOW_RUNS,
            TOOL_STAR_REPO, TOOL_LIST_STARRED
        )

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            toolDef(TOOL_SEARCH_REPOS,
                "Busca repositorios en GitHub por consulta.",
                mapOf("query" to param("string", "Consulta de busqueda. Ej: 'kotlin android mvvm', 'user:octocat'")),
                listOf("query")
            ),
            toolDef(TOOL_GET_REPO,
                "Obtiene informacion detallada de un repositorio de GitHub.",
                mapOf("repo" to param("string", "Repositorio en formato owner/repo. Ej: 'facebook/react'")),
                listOf("repo")
            ),
            toolDef(TOOL_LIST_ISSUES,
                "Lista issues de un repositorio de GitHub. Puede filtrar por estado.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "state" to param("string", "Estado: open, closed, all (default: open)"),
                    "labels" to param("string", "Filtrar por labels separadas por coma")
                ),
                listOf("repo")
            ),
            toolDef(TOOL_CREATE_ISSUE,
                "Crea un nuevo issue en un repositorio de GitHub.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "title" to param("string", "Titulo del issue"),
                    "body" to param("string", "Descripcion del issue en markdown"),
                    "labels" to param("string", "Labels separadas por coma (opcional)")
                ),
                listOf("repo", "title")
            ),
            toolDef(TOOL_READ_FILE,
                "Lee el contenido de un archivo de un repositorio de GitHub.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "path" to param("string", "Ruta del archivo. Ej: 'src/main.kt', 'README.md'"),
                    "ref" to param("string", "Branch o commit (default: main)")
                ),
                listOf("repo", "path")
            ),
            toolDef(TOOL_LIST_PULLS,
                "Lista pull requests de un repositorio de GitHub.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "state" to param("string", "Estado: open, closed, all (default: open)")
                ),
                listOf("repo")
            ),
            toolDef(TOOL_CREATE_UPDATE_FILE,
                "Create or update a file in a GitHub repo. For updates, provide the current file SHA (use github_read_file first to get it).",
                mapOf(
                    "repo" to param("string", "Repository in owner/repo format"),
                    "path" to param("string", "File path. E.g. 'src/main.kt', 'docs/README.md'"),
                    "content" to param("string", "File content (plain text, will be base64-encoded automatically)"),
                    "message" to param("string", "Commit message"),
                    "sha" to param("string", "Current file SHA (required for updates, omit for new files)"),
                    "branch" to param("string", "Target branch (default: repo's default branch)")
                ),
                listOf("repo", "path", "content", "message")
            ),
            toolDef(TOOL_DELETE_FILE,
                "Delete a file from a GitHub repo. Requires the file SHA (use github_read_file first).",
                mapOf(
                    "repo" to param("string", "Repository in owner/repo format"),
                    "path" to param("string", "File path to delete"),
                    "message" to param("string", "Commit message"),
                    "sha" to param("string", "Current file SHA (required)"),
                    "branch" to param("string", "Target branch (optional)")
                ),
                listOf("repo", "path", "message", "sha")
            ),
            toolDef(TOOL_CREATE_BRANCH,
                "Create a new branch in a GitHub repo from an existing branch or commit.",
                mapOf(
                    "repo" to param("string", "Repository in owner/repo format"),
                    "branch" to param("string", "New branch name"),
                    "from" to param("string", "Source branch or commit SHA (default: repo's default branch)")
                ),
                listOf("repo", "branch")
            ),
            toolDef(TOOL_CREATE_PULL,
                "Create a pull request in a GitHub repo.",
                mapOf(
                    "repo" to param("string", "Repository in owner/repo format"),
                    "title" to param("string", "PR title"),
                    "body" to param("string", "PR description in markdown (optional)"),
                    "head" to param("string", "Source branch (the branch with changes)"),
                    "base" to param("string", "Target branch (default: repo's default branch)")
                ),
                listOf("repo", "title", "head")
            ),
            toolDef(TOOL_COMMENT_ISSUE,
                "Add a comment to a GitHub issue or pull request.",
                mapOf(
                    "repo" to param("string", "Repository in owner/repo format"),
                    "number" to param("integer", "Issue or PR number"),
                    "body" to param("string", "Comment body in markdown")
                ),
                listOf("repo", "number", "body")
            ),
            toolDef(TOOL_LIST_BRANCHES,
                "List branches of a GitHub repository.",
                mapOf(
                    "repo" to param("string", "Repository in owner/repo format")
                ),
                listOf("repo")
            ),
            // ── Issues (extra) ──
            toolDef(TOOL_GET_ISSUE,
                "Obtiene los detalles completos de un issue específico de GitHub.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "number" to param("integer", "Número del issue")
                ),
                listOf("repo", "number")
            ),
            toolDef(TOOL_UPDATE_ISSUE,
                "Actualiza un issue de GitHub: título, cuerpo, estado, labels o assignees.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "number" to param("integer", "Número del issue"),
                    "title" to param("string", "Nuevo título (opcional)"),
                    "body" to param("string", "Nuevo cuerpo en markdown (opcional)"),
                    "state" to param("string", "Nuevo estado: 'open' o 'closed' (opcional)"),
                    "labels" to param("string", "Labels separadas por coma (reemplaza las existentes, opcional)"),
                    "assignees" to param("string", "Usernames de asignados separados por coma (opcional)")
                ),
                listOf("repo", "number")
            ),
            toolDef(TOOL_LIST_ISSUE_COMMENTS,
                "Lista los comentarios de un issue o PR de GitHub.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "number" to param("integer", "Número del issue o PR"),
                    "per_page" to param("integer", "Resultados por página (default: 20)")
                ),
                listOf("repo", "number")
            ),
            // ── Pull Requests (extra) ──
            toolDef(TOOL_GET_PULL,
                "Obtiene los detalles completos de un pull request específico.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "number" to param("integer", "Número del PR")
                ),
                listOf("repo", "number")
            ),
            toolDef(TOOL_MERGE_PULL,
                "Mergea un pull request en GitHub.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "number" to param("integer", "Número del PR"),
                    "commit_title" to param("string", "Título del commit de merge (opcional)"),
                    "commit_message" to param("string", "Mensaje del commit de merge (opcional)"),
                    "merge_method" to param("string", "Método: 'merge', 'squash' o 'rebase' (default: merge)")
                ),
                listOf("repo", "number")
            ),
            toolDef(TOOL_UPDATE_PULL,
                "Actualiza un pull request: título, cuerpo o estado.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "number" to param("integer", "Número del PR"),
                    "title" to param("string", "Nuevo título (opcional)"),
                    "body" to param("string", "Nuevo cuerpo en markdown (opcional)"),
                    "state" to param("string", "Nuevo estado: 'open' o 'closed' (opcional)")
                ),
                listOf("repo", "number")
            ),
            toolDef(TOOL_LIST_PR_FILES,
                "Lista los archivos cambiados en un pull request.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "number" to param("integer", "Número del PR")
                ),
                listOf("repo", "number")
            ),
            toolDef(TOOL_REQUEST_REVIEW,
                "Solicita review de uno o más usuarios en un pull request.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "number" to param("integer", "Número del PR"),
                    "reviewers" to param("string", "Usernames de reviewers separados por coma")
                ),
                listOf("repo", "number", "reviewers")
            ),
            // ── Commits ──
            toolDef(TOOL_LIST_COMMITS,
                "Lista los commits recientes de un branch de un repositorio.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "branch" to param("string", "Branch (default: branch principal)"),
                    "per_page" to param("integer", "Resultados por página (default: 15)")
                ),
                listOf("repo")
            ),
            toolDef(TOOL_GET_COMMIT,
                "Obtiene los detalles de un commit específico, incluyendo archivos cambiados.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "sha" to param("string", "SHA del commit")
                ),
                listOf("repo", "sha")
            ),
            // ── Releases ──
            toolDef(TOOL_LIST_RELEASES,
                "Lista los releases de un repositorio de GitHub.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "per_page" to param("integer", "Resultados por página (default: 10)")
                ),
                listOf("repo")
            ),
            toolDef(TOOL_CREATE_RELEASE,
                "Crea un nuevo release en un repositorio de GitHub.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "tag_name" to param("string", "Nombre del tag (ej: 'v1.0.0')"),
                    "name" to param("string", "Nombre del release (opcional)"),
                    "body" to param("string", "Descripción del release en markdown (opcional)"),
                    "target_commitish" to param("string", "Branch o commit SHA (default: branch principal)"),
                    "draft" to param("string", "'true' para crear como borrador (default: false)"),
                    "prerelease" to param("string", "'true' para marcar como pre-release (default: false)")
                ),
                listOf("repo", "tag_name")
            ),
            toolDef(TOOL_GET_LATEST_RELEASE,
                "Obtiene el último release publicado de un repositorio.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo")
                ),
                listOf("repo")
            ),
            // ── Repository (extra) ──
            toolDef(TOOL_LIST_REPO_CONTENTS,
                "Lista el contenido de un directorio en un repositorio de GitHub.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "path" to param("string", "Ruta del directorio (default: raíz)"),
                    "ref" to param("string", "Branch o commit SHA (opcional)")
                ),
                listOf("repo")
            ),
            toolDef(TOOL_LIST_TAGS,
                "Lista los tags de un repositorio de GitHub.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "per_page" to param("integer", "Resultados por página (default: 20)")
                ),
                listOf("repo")
            ),
            toolDef(TOOL_LIST_CONTRIBUTORS,
                "Lista los contribuidores de un repositorio de GitHub.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "per_page" to param("integer", "Resultados por página (default: 20)")
                ),
                listOf("repo")
            ),
            toolDef(TOOL_CREATE_REPO,
                "Crea un nuevo repositorio en GitHub para el usuario autenticado.",
                mapOf(
                    "name" to param("string", "Nombre del repositorio"),
                    "description" to param("string", "Descripción del repositorio (opcional)"),
                    "private" to param("string", "'true' para repositorio privado (default: false)"),
                    "auto_init" to param("string", "'true' para inicializar con README (default: false)")
                ),
                listOf("name")
            ),
            toolDef(TOOL_FORK_REPO,
                "Hace fork de un repositorio de GitHub.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "organization" to param("string", "Organización destino del fork (opcional, default: usuario autenticado)")
                ),
                listOf("repo")
            ),
            // ── Users ──
            toolDef(TOOL_GET_USER,
                "Obtiene el perfil público de un usuario de GitHub.",
                mapOf(
                    "username" to param("string", "Nombre de usuario de GitHub")
                ),
                listOf("username")
            ),
            toolDef(TOOL_GET_AUTHENTICATED_USER,
                "Obtiene el perfil del usuario autenticado (tú).",
                emptyMap(),
                emptyList()
            ),
            toolDef(TOOL_LIST_USER_REPOS,
                "Lista los repositorios de un usuario de GitHub.",
                mapOf(
                    "username" to param("string", "Nombre de usuario (omitir para repos propios)"),
                    "sort" to param("string", "Ordenar por: 'updated', 'created', 'pushed', 'full_name' (default: updated)"),
                    "per_page" to param("integer", "Resultados por página (default: 15)")
                ),
                emptyList()
            ),
            // ── Gists ──
            toolDef(TOOL_LIST_GISTS,
                "Lista los gists del usuario autenticado.",
                mapOf(
                    "per_page" to param("integer", "Resultados por página (default: 10)")
                ),
                emptyList()
            ),
            toolDef(TOOL_CREATE_GIST,
                "Crea un nuevo gist en GitHub.",
                mapOf(
                    "description" to param("string", "Descripción del gist (opcional)"),
                    "filename" to param("string", "Nombre del archivo"),
                    "content" to param("string", "Contenido del archivo"),
                    "public" to param("string", "'true' para gist público (default: false)")
                ),
                listOf("filename", "content")
            ),
            // ── Actions/Workflows ──
            toolDef(TOOL_LIST_WORKFLOWS,
                "Lista los workflows de GitHub Actions de un repositorio.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo")
                ),
                listOf("repo")
            ),
            toolDef(TOOL_TRIGGER_WORKFLOW,
                "Dispara manualmente un workflow de GitHub Actions (workflow_dispatch).",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "workflow_id" to param("string", "ID o nombre del archivo del workflow (ej: 'ci.yml')"),
                    "ref" to param("string", "Branch o tag donde ejecutar (default: branch principal)"),
                    "inputs" to param("string", "Inputs del workflow en formato JSON (opcional)")
                ),
                listOf("repo", "workflow_id")
            ),
            toolDef(TOOL_LIST_WORKFLOW_RUNS,
                "Lista las ejecuciones recientes de un workflow de GitHub Actions.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "workflow_id" to param("string", "ID o nombre del archivo del workflow (opcional, lista todos si se omite)"),
                    "status" to param("string", "Filtrar por estado: 'completed', 'in_progress', 'queued' (opcional)"),
                    "per_page" to param("integer", "Resultados por página (default: 10)")
                ),
                listOf("repo")
            ),
            // ── Stars ──
            toolDef(TOOL_STAR_REPO,
                "Da o quita star a un repositorio de GitHub.",
                mapOf(
                    "repo" to param("string", "Repositorio en formato owner/repo"),
                    "unstar" to param("string", "'true' para quitar star (default: false, da star)")
                ),
                listOf("repo")
            ),
            toolDef(TOOL_LIST_STARRED,
                "Lista los repositorios con star del usuario autenticado.",
                mapOf(
                    "sort" to param("string", "Ordenar por: 'created' o 'updated' (default: created)"),
                    "per_page" to param("integer", "Resultados por página (default: 15)")
                ),
                emptyList()
            )
        )

        private fun param(type: String, desc: String) = mapOf("type" to type, "description" to desc)

        private fun toolDef(name: String, desc: String, props: Map<String, Map<String, String>>, required: List<String>) = mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to name,
                "description" to desc,
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to props,
                    "required" to required
                )
            )
        )
    }

    suspend fun executeTool(toolCallId: String, toolName: String, arguments: String): GitHubToolResult {
        val token = securePreferences.getGitHubToken()
        if (token.isNullOrBlank()) {
            return GitHubToolResult(toolCallId, false,
                "Error: GitHub no esta configurado. Ve a MCP para agregar tu Personal Access Token.")
        }

        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            when (toolName) {
                TOOL_SEARCH_REPOS -> searchRepos(toolCallId, args, token)
                TOOL_GET_REPO -> getRepo(toolCallId, args, token)
                TOOL_LIST_ISSUES -> listIssues(toolCallId, args, token)
                TOOL_CREATE_ISSUE -> createIssue(toolCallId, args, token)
                TOOL_READ_FILE -> readFile(toolCallId, args, token)
                TOOL_LIST_PULLS -> listPulls(toolCallId, args, token)
                TOOL_CREATE_UPDATE_FILE -> createUpdateFile(toolCallId, args, token)
                TOOL_DELETE_FILE -> deleteFile(toolCallId, args, token)
                TOOL_CREATE_BRANCH -> createBranch(toolCallId, args, token)
                TOOL_CREATE_PULL -> createPull(toolCallId, args, token)
                TOOL_COMMENT_ISSUE -> commentIssue(toolCallId, args, token)
                TOOL_LIST_BRANCHES -> listBranches(toolCallId, args, token)
                TOOL_GET_ISSUE -> getIssue(toolCallId, args, token)
                TOOL_UPDATE_ISSUE -> updateIssue(toolCallId, args, token)
                TOOL_LIST_ISSUE_COMMENTS -> listIssueComments(toolCallId, args, token)
                TOOL_GET_PULL -> getPull(toolCallId, args, token)
                TOOL_MERGE_PULL -> mergePull(toolCallId, args, token)
                TOOL_UPDATE_PULL -> updatePull(toolCallId, args, token)
                TOOL_LIST_PR_FILES -> listPrFiles(toolCallId, args, token)
                TOOL_REQUEST_REVIEW -> requestReview(toolCallId, args, token)
                TOOL_LIST_COMMITS -> listCommits(toolCallId, args, token)
                TOOL_GET_COMMIT -> getCommit(toolCallId, args, token)
                TOOL_LIST_RELEASES -> listReleases(toolCallId, args, token)
                TOOL_CREATE_RELEASE -> createRelease(toolCallId, args, token)
                TOOL_GET_LATEST_RELEASE -> getLatestRelease(toolCallId, args, token)
                TOOL_LIST_REPO_CONTENTS -> listRepoContents(toolCallId, args, token)
                TOOL_LIST_TAGS -> listTags(toolCallId, args, token)
                TOOL_LIST_CONTRIBUTORS -> listContributors(toolCallId, args, token)
                TOOL_CREATE_REPO -> createRepo(toolCallId, args, token)
                TOOL_FORK_REPO -> forkRepo(toolCallId, args, token)
                TOOL_GET_USER -> getUser(toolCallId, args, token)
                TOOL_GET_AUTHENTICATED_USER -> getAuthenticatedUser(toolCallId, token)
                TOOL_LIST_USER_REPOS -> listUserRepos(toolCallId, args, token)
                TOOL_LIST_GISTS -> listGists(toolCallId, args, token)
                TOOL_CREATE_GIST -> createGist(toolCallId, args, token)
                TOOL_LIST_WORKFLOWS -> listWorkflows(toolCallId, args, token)
                TOOL_TRIGGER_WORKFLOW -> triggerWorkflow(toolCallId, args, token)
                TOOL_LIST_WORKFLOW_RUNS -> listWorkflowRuns(toolCallId, args, token)
                TOOL_STAR_REPO -> starRepo(toolCallId, args, token)
                TOOL_LIST_STARRED -> listStarred(toolCallId, args, token)
                else -> GitHubToolResult(toolCallId, false, "Herramienta desconocida: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando $toolName", e)
            GitHubToolResult(toolCallId, false, "Error: ${e.message}")
        }
    }

    private suspend fun searchRepos(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val query = args.get("query")?.asString ?: return GitHubToolResult(id, false, "Parametro 'query' requerido")
        val url = "$API_URL/search/repositories?q=${enc(query)}&per_page=10&sort=stars"
        val json = get(url, token) ?: return GitHubToolResult(id, false, "Error al buscar repositorios")

        val items = json.getAsJsonArray("items")
        if (items == null || items.size() == 0) return GitHubToolResult(id, true, "No se encontraron repositorios para: \"$query\"")

        val formatted = buildString {
            appendLine("Repositorios para: \"$query\" (${json.get("total_count")?.asInt ?: 0} total)")
            appendLine()
            items.take(10).forEachIndexed { i, item ->
                val r = item.asJsonObject
                appendLine("${i + 1}. **${r.get("full_name")?.asString}** - ${r.get("stargazers_count")?.asInt ?: 0} stars")
                val desc = r.get("description")?.asString
                if (!desc.isNullOrBlank()) appendLine("   $desc")
                appendLine("   Lang: ${r.get("language")?.asString ?: "N/A"} | Forks: ${r.get("forks_count")?.asInt ?: 0}")
                appendLine("   URL: ${r.get("html_url")?.asString}")
                appendLine()
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    private suspend fun getRepo(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parametro 'repo' requerido")
        val json = get("$API_URL/repos/$repo", token) ?: return GitHubToolResult(id, false, "Repositorio no encontrado: $repo")

        val formatted = buildString {
            appendLine("**${json.get("full_name")?.asString}**")
            appendLine(json.get("description")?.asString ?: "(sin descripcion)")
            appendLine()
            appendLine("Stars: ${json.get("stargazers_count")?.asInt} | Forks: ${json.get("forks_count")?.asInt} | Issues: ${json.get("open_issues_count")?.asInt}")
            appendLine("Lenguaje: ${json.get("language")?.asString ?: "N/A"}")
            appendLine("Default branch: ${json.get("default_branch")?.asString}")
            appendLine("Creado: ${json.get("created_at")?.asString}")
            appendLine("Ultima actualizacion: ${json.get("updated_at")?.asString}")
            val license = json.getAsJsonObject("license")?.get("name")?.asString
            if (license != null) appendLine("Licencia: $license")
            appendLine("URL: ${json.get("html_url")?.asString}")
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    private suspend fun listIssues(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parametro 'repo' requerido")
        val state = args.get("state")?.asString ?: "open"
        val labels = args.get("labels")?.asString
        var url = "$API_URL/repos/$repo/issues?state=$state&per_page=15"
        if (!labels.isNullOrBlank()) url += "&labels=${enc(labels)}"

        val items = getArray(url, token) ?: return GitHubToolResult(id, false, "Error al obtener issues")
        if (items.size() == 0) return GitHubToolResult(id, true, "No hay issues ($state) en $repo")

        val formatted = buildString {
            appendLine("Issues ($state) en $repo:")
            appendLine()
            items.forEachIndexed { i, item ->
                val r = item.asJsonObject
                if (r.has("pull_request")) return@forEachIndexed // skip PRs
                val labelsList = r.getAsJsonArray("labels")?.joinToString(", ") { it.asJsonObject.get("name").asString } ?: ""
                appendLine("#${r.get("number")?.asInt} **${r.get("title")?.asString}** [${r.get("state")?.asString}]")
                if (labelsList.isNotBlank()) appendLine("   Labels: $labelsList")
                appendLine("   Por: ${r.get("user")?.asJsonObject?.get("login")?.asString} | ${r.get("created_at")?.asString}")
                appendLine()
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    private suspend fun createIssue(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parametro 'repo' requerido")
        val title = args.get("title")?.asString ?: return GitHubToolResult(id, false, "Parametro 'title' requerido")
        val body = args.get("body")?.asString ?: ""
        val labels = args.get("labels")?.asString

        val jsonBody = buildString {
            append("{\"title\":${com.google.gson.JsonPrimitive(title)}")
            if (body.isNotBlank()) append(",\"body\":${com.google.gson.JsonPrimitive(body)}")
            if (!labels.isNullOrBlank()) {
                val labelArray = labels.split(",").map { "\"${it.trim()}\"" }.joinToString(",")
                append(",\"labels\":[$labelArray]")
            }
            append("}")
        }

        val result = post("$API_URL/repos/$repo/issues", jsonBody, token)
            ?: return GitHubToolResult(id, false, "Error al crear issue")

        val number = result.get("number")?.asInt
        val url = result.get("html_url")?.asString
        return GitHubToolResult(id, true, "Issue #$number creado exitosamente: $url")
    }

    private suspend fun readFile(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parametro 'repo' requerido")
        val path = args.get("path")?.asString ?: return GitHubToolResult(id, false, "Parametro 'path' requerido")
        val ref = args.get("ref")?.asString

        var url = "$API_URL/repos/$repo/contents/$path"
        if (!ref.isNullOrBlank()) url += "?ref=${enc(ref)}"

        val json = get(url, token) ?: return GitHubToolResult(id, false, "Archivo no encontrado: $path en $repo")
        val content = json.get("content")?.asString?.replace("\n", "")
        if (content == null) return GitHubToolResult(id, false, "No se pudo leer el contenido del archivo")

        val decoded = String(android.util.Base64.decode(content, android.util.Base64.DEFAULT))
        val size = json.get("size")?.asInt ?: decoded.length

        val sha = json.get("sha")?.asString ?: ""

        return GitHubToolResult(id, true, buildString {
            appendLine("Archivo: $path ($size bytes)")
            if (sha.isNotEmpty()) appendLine("SHA: $sha")
            appendLine("---")
            // Truncate very large files
            if (decoded.length > 15000) {
                append(decoded.take(15000))
                appendLine("\n\n... (truncado, archivo muy largo)")
            } else {
                append(decoded)
            }
        })
    }

    private suspend fun listPulls(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parametro 'repo' requerido")
        val state = args.get("state")?.asString ?: "open"

        val items = getArray("$API_URL/repos/$repo/pulls?state=$state&per_page=15", token)
            ?: return GitHubToolResult(id, false, "Error al obtener pull requests")
        if (items.size() == 0) return GitHubToolResult(id, true, "No hay PRs ($state) en $repo")

        val formatted = buildString {
            appendLine("Pull Requests ($state) en $repo:")
            appendLine()
            items.forEachIndexed { i, item ->
                val r = item.asJsonObject
                appendLine("#${r.get("number")?.asInt} **${r.get("title")?.asString}** [${r.get("state")?.asString}]")
                appendLine("   ${r.get("head")?.asJsonObject?.get("ref")?.asString} -> ${r.get("base")?.asJsonObject?.get("ref")?.asString}")
                appendLine("   Por: ${r.get("user")?.asJsonObject?.get("login")?.asString} | ${r.get("created_at")?.asString}")
                appendLine()
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    // HTTP helpers
    private suspend fun get(url: String, token: String): com.google.gson.JsonObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.code !in 200..299) { Log.e(TAG, "GET $url -> ${resp.code}"); return@withContext null }
            JsonParser.parseString(resp.body?.string() ?: "").asJsonObject
        } catch (e: Exception) { Log.e(TAG, "GET error", e); null }
    }

    private suspend fun getArray(url: String, token: String): com.google.gson.JsonArray? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.code !in 200..299) { Log.e(TAG, "GET $url -> ${resp.code}"); return@withContext null }
            JsonParser.parseString(resp.body?.string() ?: "").asJsonArray
        } catch (e: Exception) { Log.e(TAG, "GET error", e); null }
    }

    private suspend fun post(url: String, body: String, token: String): com.google.gson.JsonObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.code !in 200..299) { Log.e(TAG, "POST $url -> ${resp.code}"); return@withContext null }
            JsonParser.parseString(resp.body?.string() ?: "").asJsonObject
        } catch (e: Exception) { Log.e(TAG, "POST error", e); null }
    }

    private suspend fun put(url: String, body: String, token: String): com.google.gson.JsonObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .put(body.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = okHttpClient.newCall(req).execute()
            val respBody = resp.body?.string() ?: ""
            if (resp.code !in 200..299) { Log.e(TAG, "PUT $url -> ${resp.code}: $respBody"); return@withContext null }
            JsonParser.parseString(respBody).asJsonObject
        } catch (e: Exception) { Log.e(TAG, "PUT error", e); null }
    }

    private suspend fun delete(url: String, body: String, token: String): com.google.gson.JsonObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .delete(body.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = okHttpClient.newCall(req).execute()
            val respBody = resp.body?.string() ?: ""
            if (resp.code !in 200..299) { Log.e(TAG, "DELETE $url -> ${resp.code}: $respBody"); return@withContext null }
            JsonParser.parseString(respBody).asJsonObject
        } catch (e: Exception) { Log.e(TAG, "DELETE error", e); null }
    }

    // ── Write operations ──────────────────────────────────────────────────

    private suspend fun createUpdateFile(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parameter 'repo' required")
        val path = args.get("path")?.asString ?: return GitHubToolResult(id, false, "Parameter 'path' required")
        val content = args.get("content")?.asString ?: return GitHubToolResult(id, false, "Parameter 'content' required")
        val message = args.get("message")?.asString ?: return GitHubToolResult(id, false, "Parameter 'message' required")
        val sha = args.get("sha")?.asString
        val branch = args.get("branch")?.asString

        val base64Content = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)

        val jsonBody = buildString {
            append("{\"message\":${com.google.gson.JsonPrimitive(message)}")
            append(",\"content\":${com.google.gson.JsonPrimitive(base64Content)}")
            if (!sha.isNullOrBlank()) append(",\"sha\":${com.google.gson.JsonPrimitive(sha)}")
            if (!branch.isNullOrBlank()) append(",\"branch\":${com.google.gson.JsonPrimitive(branch)}")
            append("}")
        }

        val result = put("$API_URL/repos/$repo/contents/$path", jsonBody, token)
            ?: return GitHubToolResult(id, false, "Error creating/updating file. Check repo permissions and that SHA is correct for updates.")

        val fileUrl = result.getAsJsonObject("content")?.get("html_url")?.asString ?: ""
        val newSha = result.getAsJsonObject("content")?.get("sha")?.asString ?: ""
        val action = if (sha.isNullOrBlank()) "created" else "updated"

        return GitHubToolResult(id, true, buildString {
            appendLine("File $action successfully: $path")
            appendLine("SHA: $newSha")
            appendLine("Commit: ${result.getAsJsonObject("commit")?.get("html_url")?.asString ?: ""}")
            if (fileUrl.isNotBlank()) appendLine("URL: $fileUrl")
        }.trim())
    }

    private suspend fun deleteFile(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parameter 'repo' required")
        val path = args.get("path")?.asString ?: return GitHubToolResult(id, false, "Parameter 'path' required")
        val message = args.get("message")?.asString ?: return GitHubToolResult(id, false, "Parameter 'message' required")
        val sha = args.get("sha")?.asString ?: return GitHubToolResult(id, false, "Parameter 'sha' required")
        val branch = args.get("branch")?.asString

        val jsonBody = buildString {
            append("{\"message\":${com.google.gson.JsonPrimitive(message)}")
            append(",\"sha\":${com.google.gson.JsonPrimitive(sha)}")
            if (!branch.isNullOrBlank()) append(",\"branch\":${com.google.gson.JsonPrimitive(branch)}")
            append("}")
        }

        val result = delete("$API_URL/repos/$repo/contents/$path", jsonBody, token)
            ?: return GitHubToolResult(id, false, "Error deleting file. Check permissions and SHA.")

        return GitHubToolResult(id, true, buildString {
            appendLine("File deleted: $path")
            appendLine("Commit: ${result.getAsJsonObject("commit")?.get("html_url")?.asString ?: ""}")
        }.trim())
    }

    private suspend fun createBranch(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parameter 'repo' required")
        val branch = args.get("branch")?.asString ?: return GitHubToolResult(id, false, "Parameter 'branch' required")
        val from = args.get("from")?.asString

        // Get the SHA of the source branch
        val sourceBranch = from ?: run {
            val repoInfo = get("$API_URL/repos/$repo", token)
                ?: return GitHubToolResult(id, false, "Could not get repo info")
            repoInfo.get("default_branch")?.asString ?: "main"
        }

        val refInfo = get("$API_URL/repos/$repo/git/ref/heads/$sourceBranch", token)
            ?: return GitHubToolResult(id, false, "Source branch '$sourceBranch' not found")

        val sourceSha = refInfo.getAsJsonObject("object")?.get("sha")?.asString
            ?: return GitHubToolResult(id, false, "Could not get SHA for '$sourceBranch'")

        val jsonBody = "{\"ref\":\"refs/heads/$branch\",\"sha\":${com.google.gson.JsonPrimitive(sourceSha)}}"

        val result = post("$API_URL/repos/$repo/git/refs", jsonBody, token)
            ?: return GitHubToolResult(id, false, "Error creating branch. It may already exist.")

        return GitHubToolResult(id, true, "Branch '$branch' created from '$sourceBranch' (SHA: ${sourceSha.take(7)})")
    }

    private suspend fun createPull(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parameter 'repo' required")
        val title = args.get("title")?.asString ?: return GitHubToolResult(id, false, "Parameter 'title' required")
        val head = args.get("head")?.asString ?: return GitHubToolResult(id, false, "Parameter 'head' required")
        val body = args.get("body")?.asString ?: ""
        val base = args.get("base")?.asString ?: run {
            val repoInfo = get("$API_URL/repos/$repo", token)
            repoInfo?.get("default_branch")?.asString ?: "main"
        }

        val jsonBody = buildString {
            append("{\"title\":${com.google.gson.JsonPrimitive(title)}")
            append(",\"head\":${com.google.gson.JsonPrimitive(head)}")
            append(",\"base\":${com.google.gson.JsonPrimitive(base)}")
            if (body.isNotBlank()) append(",\"body\":${com.google.gson.JsonPrimitive(body)}")
            append("}")
        }

        val result = post("$API_URL/repos/$repo/pulls", jsonBody, token)
            ?: return GitHubToolResult(id, false, "Error creating PR. Check that branches exist and have differences.")

        val prNumber = result.get("number")?.asInt
        val prUrl = result.get("html_url")?.asString
        return GitHubToolResult(id, true, "PR #$prNumber created: $head → $base\nURL: $prUrl")
    }

    private suspend fun commentIssue(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parameter 'repo' required")
        val number = args.get("number")?.asInt ?: return GitHubToolResult(id, false, "Parameter 'number' required")
        val body = args.get("body")?.asString ?: return GitHubToolResult(id, false, "Parameter 'body' required")

        val jsonBody = "{\"body\":${com.google.gson.JsonPrimitive(body)}}"

        val result = post("$API_URL/repos/$repo/issues/$number/comments", jsonBody, token)
            ?: return GitHubToolResult(id, false, "Error posting comment")

        val commentUrl = result.get("html_url")?.asString
        return GitHubToolResult(id, true, "Comment added to #$number: $commentUrl")
    }

    private suspend fun listBranches(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parameter 'repo' required")

        val items = getArray("$API_URL/repos/$repo/branches?per_page=30", token)
            ?: return GitHubToolResult(id, false, "Error listing branches")
        if (items.size() == 0) return GitHubToolResult(id, true, "No branches found in $repo")

        val formatted = buildString {
            appendLine("Branches in $repo (${items.size()}):")
            appendLine()
            items.forEach { item ->
                val b = item.asJsonObject
                val name = b.get("name")?.asString ?: ""
                val sha = b.getAsJsonObject("commit")?.get("sha")?.asString?.take(7) ?: ""
                val protected = b.get("protected")?.asBoolean ?: false
                val protectedTag = if (protected) " [protected]" else ""
                appendLine("  - $name ($sha)$protectedTag")
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    private suspend fun patch(url: String, body: String, token: String): com.google.gson.JsonObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .patch(body.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = okHttpClient.newCall(req).execute()
            val respBody = resp.body?.string() ?: ""
            if (resp.code !in 200..299) { Log.e(TAG, "PATCH $url -> ${resp.code}: $respBody"); return@withContext null }
            JsonParser.parseString(respBody).asJsonObject
        } catch (e: Exception) { Log.e(TAG, "PATCH error", e); null }
    }

    private suspend fun putNoBody(url: String, token: String): Int = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("Content-Length", "0")
                .put("".toRequestBody("application/json".toMediaType()))
                .build()
            okHttpClient.newCall(req).execute().code
        } catch (e: Exception) { Log.e(TAG, "PUT no-body error", e); -1 }
    }

    private suspend fun deleteNoBody(url: String, token: String): Int = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .delete()
                .build()
            okHttpClient.newCall(req).execute().code
        } catch (e: Exception) { Log.e(TAG, "DELETE no-body error", e); -1 }
    }

    // ── Issues (extra) ─────────────────────────────────────────────────────

    private suspend fun getIssue(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val number = args.get("number")?.asInt ?: return GitHubToolResult(id, false, "Parámetro 'number' requerido")

        val json = get("$API_URL/repos/$repo/issues/$number", token)
            ?: return GitHubToolResult(id, false, "Issue #$number no encontrado en $repo")

        val labelsList = json.getAsJsonArray("labels")?.joinToString(", ") { it.asJsonObject.get("name").asString } ?: ""
        val assignees = json.getAsJsonArray("assignees")?.joinToString(", ") { it.asJsonObject.get("login").asString } ?: ""

        val formatted = buildString {
            appendLine("**#${json.get("number")?.asInt} ${json.get("title")?.asString}** [${json.get("state")?.asString}]")
            appendLine()
            appendLine("Por: ${json.get("user")?.asJsonObject?.get("login")?.asString} | Creado: ${json.get("created_at")?.asString}")
            if (assignees.isNotBlank()) appendLine("Asignados: $assignees")
            if (labelsList.isNotBlank()) appendLine("Labels: $labelsList")
            appendLine("Comentarios: ${json.get("comments")?.asInt ?: 0}")
            appendLine("URL: ${json.get("html_url")?.asString}")
            val body = json.get("body")?.asString
            if (!body.isNullOrBlank()) {
                appendLine()
                appendLine("---")
                if (body.length > 5000) append(body.take(5000) + "\n\n... (truncado)")
                else append(body)
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    private suspend fun updateIssue(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val number = args.get("number")?.asInt ?: return GitHubToolResult(id, false, "Parámetro 'number' requerido")

        val jsonBody = buildString {
            append("{")
            val parts = mutableListOf<String>()
            args.get("title")?.asString?.let { parts.add("\"title\":${com.google.gson.JsonPrimitive(it)}") }
            args.get("body")?.asString?.let { parts.add("\"body\":${com.google.gson.JsonPrimitive(it)}") }
            args.get("state")?.asString?.let { parts.add("\"state\":${com.google.gson.JsonPrimitive(it)}") }
            args.get("labels")?.asString?.let { labels ->
                val arr = labels.split(",").joinToString(",") { "\"${it.trim()}\"" }
                parts.add("\"labels\":[$arr]")
            }
            args.get("assignees")?.asString?.let { assignees ->
                val arr = assignees.split(",").joinToString(",") { "\"${it.trim()}\"" }
                parts.add("\"assignees\":[$arr]")
            }
            append(parts.joinToString(","))
            append("}")
        }

        val result = patch("$API_URL/repos/$repo/issues/$number", jsonBody, token)
            ?: return GitHubToolResult(id, false, "Error al actualizar issue #$number")

        return GitHubToolResult(id, true, "Issue #$number actualizado: ${result.get("html_url")?.asString}")
    }

    private suspend fun listIssueComments(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val number = args.get("number")?.asInt ?: return GitHubToolResult(id, false, "Parámetro 'number' requerido")
        val perPage = args.get("per_page")?.asInt?.coerceIn(1, 50) ?: 20

        val items = getArray("$API_URL/repos/$repo/issues/$number/comments?per_page=$perPage", token)
            ?: return GitHubToolResult(id, false, "Error al obtener comentarios")
        if (items.size() == 0) return GitHubToolResult(id, true, "No hay comentarios en #$number")

        val formatted = buildString {
            appendLine("Comentarios en #$number (${items.size()}):")
            appendLine()
            items.forEachIndexed { i, item ->
                val c = item.asJsonObject
                val author = c.get("user")?.asJsonObject?.get("login")?.asString ?: "?"
                val date = c.get("created_at")?.asString ?: ""
                val body = c.get("body")?.asString ?: ""
                appendLine("${i + 1}. **$author** ($date)")
                val truncBody = if (body.length > 500) body.take(500) + "..." else body
                appendLine("   $truncBody")
                appendLine()
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    // ── Pull Requests (extra) ──────────────────────────────────────────────

    private suspend fun getPull(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val number = args.get("number")?.asInt ?: return GitHubToolResult(id, false, "Parámetro 'number' requerido")

        val json = get("$API_URL/repos/$repo/pulls/$number", token)
            ?: return GitHubToolResult(id, false, "PR #$number no encontrado en $repo")

        val formatted = buildString {
            appendLine("**PR #${json.get("number")?.asInt} ${json.get("title")?.asString}** [${json.get("state")?.asString}]")
            appendLine()
            val head = json.get("head")?.asJsonObject?.get("ref")?.asString ?: "?"
            val base = json.get("base")?.asJsonObject?.get("ref")?.asString ?: "?"
            appendLine("$head → $base")
            appendLine("Por: ${json.get("user")?.asJsonObject?.get("login")?.asString} | Creado: ${json.get("created_at")?.asString}")
            appendLine("Mergeable: ${json.get("mergeable")?.asString ?: "unknown"} | Merged: ${json.get("merged")?.asBoolean ?: false}")
            appendLine("Commits: ${json.get("commits")?.asInt ?: 0} | Archivos: ${json.get("changed_files")?.asInt ?: 0} | +${json.get("additions")?.asInt ?: 0} -${json.get("deletions")?.asInt ?: 0}")
            appendLine("URL: ${json.get("html_url")?.asString}")
            val body = json.get("body")?.asString
            if (!body.isNullOrBlank()) {
                appendLine()
                appendLine("---")
                if (body.length > 5000) append(body.take(5000) + "\n\n... (truncado)")
                else append(body)
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    private suspend fun mergePull(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val number = args.get("number")?.asInt ?: return GitHubToolResult(id, false, "Parámetro 'number' requerido")
        val mergeMethod = args.get("merge_method")?.asString ?: "merge"

        val jsonBody = buildString {
            append("{\"merge_method\":${com.google.gson.JsonPrimitive(mergeMethod)}")
            args.get("commit_title")?.asString?.let { append(",\"commit_title\":${com.google.gson.JsonPrimitive(it)}") }
            args.get("commit_message")?.asString?.let { append(",\"commit_message\":${com.google.gson.JsonPrimitive(it)}") }
            append("}")
        }

        val result = put("$API_URL/repos/$repo/pulls/$number/merge", jsonBody, token)
            ?: return GitHubToolResult(id, false, "Error al mergear PR #$number. Verifica que sea mergeable.")

        val merged = result.get("merged")?.asBoolean ?: false
        return if (merged) {
            GitHubToolResult(id, true, "PR #$number mergeado exitosamente (${mergeMethod})\nSHA: ${result.get("sha")?.asString}")
        } else {
            GitHubToolResult(id, false, "No se pudo mergear PR #$number: ${result.get("message")?.asString}")
        }
    }

    private suspend fun updatePull(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val number = args.get("number")?.asInt ?: return GitHubToolResult(id, false, "Parámetro 'number' requerido")

        val jsonBody = buildString {
            append("{")
            val parts = mutableListOf<String>()
            args.get("title")?.asString?.let { parts.add("\"title\":${com.google.gson.JsonPrimitive(it)}") }
            args.get("body")?.asString?.let { parts.add("\"body\":${com.google.gson.JsonPrimitive(it)}") }
            args.get("state")?.asString?.let { parts.add("\"state\":${com.google.gson.JsonPrimitive(it)}") }
            append(parts.joinToString(","))
            append("}")
        }

        val result = patch("$API_URL/repos/$repo/pulls/$number", jsonBody, token)
            ?: return GitHubToolResult(id, false, "Error al actualizar PR #$number")

        return GitHubToolResult(id, true, "PR #$number actualizado: ${result.get("html_url")?.asString}")
    }

    private suspend fun listPrFiles(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val number = args.get("number")?.asInt ?: return GitHubToolResult(id, false, "Parámetro 'number' requerido")

        val items = getArray("$API_URL/repos/$repo/pulls/$number/files?per_page=50", token)
            ?: return GitHubToolResult(id, false, "Error al obtener archivos del PR")
        if (items.size() == 0) return GitHubToolResult(id, true, "No hay archivos cambiados en PR #$number")

        val formatted = buildString {
            appendLine("Archivos cambiados en PR #$number (${items.size()}):")
            appendLine()
            items.forEach { item ->
                val f = item.asJsonObject
                val filename = f.get("filename")?.asString ?: "?"
                val status = f.get("status")?.asString ?: "?"
                val additions = f.get("additions")?.asInt ?: 0
                val deletions = f.get("deletions")?.asInt ?: 0
                appendLine("  [$status] $filename (+$additions -$deletions)")
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    private suspend fun requestReview(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val number = args.get("number")?.asInt ?: return GitHubToolResult(id, false, "Parámetro 'number' requerido")
        val reviewers = args.get("reviewers")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'reviewers' requerido")

        val reviewerArray = reviewers.split(",").joinToString(",") { "\"${it.trim()}\"" }
        val jsonBody = "{\"reviewers\":[$reviewerArray]}"

        val result = post("$API_URL/repos/$repo/pulls/$number/requested_reviewers", jsonBody, token)
            ?: return GitHubToolResult(id, false, "Error al solicitar review en PR #$number")

        return GitHubToolResult(id, true, "Review solicitado en PR #$number a: $reviewers")
    }

    // ── Commits ────────────────────────────────────────────────────────────

    private suspend fun listCommits(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val branch = args.get("branch")?.asString
        val perPage = args.get("per_page")?.asInt?.coerceIn(1, 50) ?: 15

        var url = "$API_URL/repos/$repo/commits?per_page=$perPage"
        if (!branch.isNullOrBlank()) url += "&sha=${enc(branch)}"

        val items = getArray(url, token)
            ?: return GitHubToolResult(id, false, "Error al obtener commits")
        if (items.size() == 0) return GitHubToolResult(id, true, "No hay commits en $repo")

        val formatted = buildString {
            appendLine("Commits recientes en $repo${if (!branch.isNullOrBlank()) " ($branch)" else ""}:")
            appendLine()
            items.forEachIndexed { i, item ->
                val c = item.asJsonObject
                val sha = c.get("sha")?.asString?.take(7) ?: "?"
                val commit = c.getAsJsonObject("commit")
                val msg = commit?.get("message")?.asString?.lines()?.firstOrNull() ?: ""
                val author = commit?.getAsJsonObject("author")?.get("name")?.asString ?: "?"
                val date = commit?.getAsJsonObject("author")?.get("date")?.asString ?: ""
                appendLine("${i + 1}. `$sha` $msg")
                appendLine("   $author | $date")
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    private suspend fun getCommit(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val sha = args.get("sha")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'sha' requerido")

        val json = get("$API_URL/repos/$repo/commits/$sha", token)
            ?: return GitHubToolResult(id, false, "Commit no encontrado: $sha")

        val commit = json.getAsJsonObject("commit")
        val message = commit?.get("message")?.asString ?: ""
        val author = commit?.getAsJsonObject("author")?.get("name")?.asString ?: "?"
        val date = commit?.getAsJsonObject("author")?.get("date")?.asString ?: ""
        val files = json.getAsJsonArray("files")
        val stats = json.getAsJsonObject("stats")

        val formatted = buildString {
            appendLine("Commit `${sha.take(7)}`")
            appendLine()
            appendLine("Autor: $author | Fecha: $date")
            appendLine("Mensaje: $message")
            if (stats != null) {
                appendLine("Stats: +${stats.get("additions")?.asInt ?: 0} -${stats.get("deletions")?.asInt ?: 0} (${stats.get("total")?.asInt ?: 0} cambios)")
            }
            if (files != null && files.size() > 0) {
                appendLine()
                appendLine("Archivos (${files.size()}):")
                files.forEach { f ->
                    val fo = f.asJsonObject
                    val filename = fo.get("filename")?.asString ?: "?"
                    val status = fo.get("status")?.asString ?: "?"
                    appendLine("  [$status] $filename (+${fo.get("additions")?.asInt ?: 0} -${fo.get("deletions")?.asInt ?: 0})")
                }
            }
            appendLine()
            appendLine("URL: ${json.get("html_url")?.asString}")
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    // ── Releases ───────────────────────────────────────────────────────────

    private suspend fun listReleases(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val perPage = args.get("per_page")?.asInt?.coerceIn(1, 30) ?: 10

        val items = getArray("$API_URL/repos/$repo/releases?per_page=$perPage", token)
            ?: return GitHubToolResult(id, false, "Error al obtener releases")
        if (items.size() == 0) return GitHubToolResult(id, true, "No hay releases en $repo")

        val formatted = buildString {
            appendLine("Releases en $repo (${items.size()}):")
            appendLine()
            items.forEachIndexed { i, item ->
                val r = item.asJsonObject
                val name = r.get("name")?.asString ?: r.get("tag_name")?.asString ?: "?"
                val tag = r.get("tag_name")?.asString ?: ""
                val draft = r.get("draft")?.asBoolean ?: false
                val prerelease = r.get("prerelease")?.asBoolean ?: false
                val date = r.get("published_at")?.asString ?: r.get("created_at")?.asString ?: ""
                val flags = buildString {
                    if (draft) append(" [draft]")
                    if (prerelease) append(" [pre-release]")
                }
                appendLine("${i + 1}. **$name** ($tag)$flags")
                appendLine("   Fecha: $date | URL: ${r.get("html_url")?.asString}")
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    private suspend fun createRelease(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val tagName = args.get("tag_name")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'tag_name' requerido")

        val jsonBody = buildString {
            append("{\"tag_name\":${com.google.gson.JsonPrimitive(tagName)}")
            args.get("name")?.asString?.let { append(",\"name\":${com.google.gson.JsonPrimitive(it)}") }
            args.get("body")?.asString?.let { append(",\"body\":${com.google.gson.JsonPrimitive(it)}") }
            args.get("target_commitish")?.asString?.let { append(",\"target_commitish\":${com.google.gson.JsonPrimitive(it)}") }
            if (args.get("draft")?.asString == "true") append(",\"draft\":true")
            if (args.get("prerelease")?.asString == "true") append(",\"prerelease\":true")
            append("}")
        }

        val result = post("$API_URL/repos/$repo/releases", jsonBody, token)
            ?: return GitHubToolResult(id, false, "Error al crear release")

        return GitHubToolResult(id, true, "Release creado: ${result.get("name")?.asString ?: tagName}\nTag: $tagName\nURL: ${result.get("html_url")?.asString}")
    }

    private suspend fun getLatestRelease(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")

        val json = get("$API_URL/repos/$repo/releases/latest", token)
            ?: return GitHubToolResult(id, false, "No se encontró un release en $repo")

        val formatted = buildString {
            appendLine("Último release de $repo:")
            appendLine()
            appendLine("**${json.get("name")?.asString ?: json.get("tag_name")?.asString}** (${json.get("tag_name")?.asString})")
            appendLine("Fecha: ${json.get("published_at")?.asString}")
            appendLine("Autor: ${json.get("author")?.asJsonObject?.get("login")?.asString}")
            appendLine("URL: ${json.get("html_url")?.asString}")
            val body = json.get("body")?.asString
            if (!body.isNullOrBlank()) {
                appendLine()
                appendLine("---")
                if (body.length > 3000) append(body.take(3000) + "\n\n... (truncado)")
                else append(body)
            }
            val assets = json.getAsJsonArray("assets")
            if (assets != null && assets.size() > 0) {
                appendLine()
                appendLine("Assets (${assets.size()}):")
                assets.forEach { a ->
                    val ao = a.asJsonObject
                    appendLine("  - ${ao.get("name")?.asString} (${ao.get("download_count")?.asInt ?: 0} descargas)")
                }
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    // ── Repository (extra) ─────────────────────────────────────────────────

    private suspend fun listRepoContents(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val path = args.get("path")?.asString ?: ""
        val ref = args.get("ref")?.asString

        var url = "$API_URL/repos/$repo/contents/$path"
        if (!ref.isNullOrBlank()) url += "?ref=${enc(ref)}"

        val items = getArray(url, token)
            ?: return GitHubToolResult(id, false, "Error al listar contenido de $path en $repo")

        val formatted = buildString {
            appendLine("Contenido de `${if (path.isBlank()) "/" else path}` en $repo:")
            appendLine()
            items.forEach { item ->
                val f = item.asJsonObject
                val type = f.get("type")?.asString ?: "?"
                val name = f.get("name")?.asString ?: "?"
                val size = f.get("size")?.asInt ?: 0
                val icon = when (type) { "dir" -> "📁"; "file" -> "📄"; else -> "  " }
                if (type == "dir") appendLine("  $icon $name/")
                else appendLine("  $icon $name ($size bytes)")
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    private suspend fun listTags(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val perPage = args.get("per_page")?.asInt?.coerceIn(1, 50) ?: 20

        val items = getArray("$API_URL/repos/$repo/tags?per_page=$perPage", token)
            ?: return GitHubToolResult(id, false, "Error al obtener tags")
        if (items.size() == 0) return GitHubToolResult(id, true, "No hay tags en $repo")

        val formatted = buildString {
            appendLine("Tags en $repo (${items.size()}):")
            appendLine()
            items.forEach { item ->
                val t = item.asJsonObject
                val name = t.get("name")?.asString ?: "?"
                val sha = t.getAsJsonObject("commit")?.get("sha")?.asString?.take(7) ?: ""
                appendLine("  - $name ($sha)")
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    private suspend fun listContributors(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val perPage = args.get("per_page")?.asInt?.coerceIn(1, 50) ?: 20

        val items = getArray("$API_URL/repos/$repo/contributors?per_page=$perPage", token)
            ?: return GitHubToolResult(id, false, "Error al obtener contribuidores")
        if (items.size() == 0) return GitHubToolResult(id, true, "No hay contribuidores en $repo")

        val formatted = buildString {
            appendLine("Contribuidores de $repo (${items.size()}):")
            appendLine()
            items.forEachIndexed { i, item ->
                val c = item.asJsonObject
                val login = c.get("login")?.asString ?: "?"
                val contributions = c.get("contributions")?.asInt ?: 0
                appendLine("${i + 1}. **$login** ($contributions contribuciones)")
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    private suspend fun createRepo(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val name = args.get("name")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'name' requerido")

        val jsonBody = buildString {
            append("{\"name\":${com.google.gson.JsonPrimitive(name)}")
            args.get("description")?.asString?.let { append(",\"description\":${com.google.gson.JsonPrimitive(it)}") }
            if (args.get("private")?.asString == "true") append(",\"private\":true")
            if (args.get("auto_init")?.asString == "true") append(",\"auto_init\":true")
            append("}")
        }

        val result = post("$API_URL/user/repos", jsonBody, token)
            ?: return GitHubToolResult(id, false, "Error al crear repositorio")

        return GitHubToolResult(id, true, buildString {
            appendLine("Repositorio creado: ${result.get("full_name")?.asString}")
            appendLine("URL: ${result.get("html_url")?.asString}")
            appendLine("Privado: ${result.get("private")?.asBoolean ?: false}")
        }.trim())
    }

    private suspend fun forkRepo(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val organization = args.get("organization")?.asString

        val jsonBody = if (!organization.isNullOrBlank()) {
            "{\"organization\":${com.google.gson.JsonPrimitive(organization)}}"
        } else "{}"

        val result = post("$API_URL/repos/$repo/forks", jsonBody, token)
            ?: return GitHubToolResult(id, false, "Error al hacer fork de $repo")

        return GitHubToolResult(id, true, "Fork creado: ${result.get("full_name")?.asString}\nURL: ${result.get("html_url")?.asString}")
    }

    // ── Users ──────────────────────────────────────────────────────────────

    private suspend fun getUser(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val username = args.get("username")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'username' requerido")

        val json = get("$API_URL/users/$username", token)
            ?: return GitHubToolResult(id, false, "Usuario no encontrado: $username")

        val formatted = buildString {
            appendLine("**${json.get("login")?.asString}** ${json.get("name")?.asString ?: ""}")
            val bio = json.get("bio")?.asString
            if (!bio.isNullOrBlank()) appendLine(bio)
            appendLine()
            appendLine("Repos públicos: ${json.get("public_repos")?.asInt ?: 0}")
            appendLine("Seguidores: ${json.get("followers")?.asInt ?: 0} | Siguiendo: ${json.get("following")?.asInt ?: 0}")
            val company = json.get("company")?.asString
            if (!company.isNullOrBlank()) appendLine("Empresa: $company")
            val location = json.get("location")?.asString
            if (!location.isNullOrBlank()) appendLine("Ubicación: $location")
            appendLine("Creado: ${json.get("created_at")?.asString}")
            appendLine("URL: ${json.get("html_url")?.asString}")
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    private suspend fun getAuthenticatedUser(id: String, token: String): GitHubToolResult {
        val json = get("$API_URL/user", token)
            ?: return GitHubToolResult(id, false, "Error al obtener perfil del usuario autenticado")

        val formatted = buildString {
            appendLine("**${json.get("login")?.asString}** ${json.get("name")?.asString ?: ""}")
            val bio = json.get("bio")?.asString
            if (!bio.isNullOrBlank()) appendLine(bio)
            appendLine()
            appendLine("Repos públicos: ${json.get("public_repos")?.asInt ?: 0} | Privados: ${json.get("total_private_repos")?.asInt ?: 0}")
            appendLine("Seguidores: ${json.get("followers")?.asInt ?: 0} | Siguiendo: ${json.get("following")?.asInt ?: 0}")
            appendLine("Plan: ${json.getAsJsonObject("plan")?.get("name")?.asString ?: "free"}")
            appendLine("Email: ${json.get("email")?.asString ?: "N/A"}")
            appendLine("URL: ${json.get("html_url")?.asString}")
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    private suspend fun listUserRepos(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val username = args.get("username")?.asString
        val sort = args.get("sort")?.asString ?: "updated"
        val perPage = args.get("per_page")?.asInt?.coerceIn(1, 50) ?: 15

        val url = if (username.isNullOrBlank()) {
            "$API_URL/user/repos?sort=$sort&per_page=$perPage"
        } else {
            "$API_URL/users/$username/repos?sort=$sort&per_page=$perPage"
        }

        val items = getArray(url, token)
            ?: return GitHubToolResult(id, false, "Error al obtener repositorios")
        if (items.size() == 0) return GitHubToolResult(id, true, "No se encontraron repositorios")

        val formatted = buildString {
            appendLine("Repositorios${if (!username.isNullOrBlank()) " de $username" else ""} (${items.size()}):")
            appendLine()
            items.forEachIndexed { i, item ->
                val r = item.asJsonObject
                val private = r.get("private")?.asBoolean ?: false
                val visibility = if (private) "[privado]" else "[público]"
                appendLine("${i + 1}. **${r.get("full_name")?.asString}** $visibility")
                val desc = r.get("description")?.asString
                if (!desc.isNullOrBlank()) appendLine("   $desc")
                appendLine("   Lang: ${r.get("language")?.asString ?: "N/A"} | Stars: ${r.get("stargazers_count")?.asInt ?: 0} | Updated: ${r.get("updated_at")?.asString}")
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    // ── Gists ──────────────────────────────────────────────────────────────

    private suspend fun listGists(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val perPage = args.get("per_page")?.asInt?.coerceIn(1, 30) ?: 10

        val items = getArray("$API_URL/gists?per_page=$perPage", token)
            ?: return GitHubToolResult(id, false, "Error al obtener gists")
        if (items.size() == 0) return GitHubToolResult(id, true, "No tienes gists")

        val formatted = buildString {
            appendLine("Tus gists (${items.size()}):")
            appendLine()
            items.forEachIndexed { i, item ->
                val g = item.asJsonObject
                val desc = g.get("description")?.asString
                val public = g.get("public")?.asBoolean ?: false
                val files = g.getAsJsonObject("files")
                val fileNames = files?.keySet()?.joinToString(", ") ?: ""
                val visibility = if (public) "[público]" else "[secreto]"
                appendLine("${i + 1}. ${if (!desc.isNullOrBlank()) desc else "(sin descripción)"} $visibility")
                appendLine("   Archivos: $fileNames")
                appendLine("   URL: ${g.get("html_url")?.asString}")
                appendLine()
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    private suspend fun createGist(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val filename = args.get("filename")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'filename' requerido")
        val content = args.get("content")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'content' requerido")
        val description = args.get("description")?.asString ?: ""
        val isPublic = args.get("public")?.asString == "true"

        val jsonBody = buildString {
            append("{\"description\":${com.google.gson.JsonPrimitive(description)}")
            append(",\"public\":$isPublic")
            append(",\"files\":{${com.google.gson.JsonPrimitive(filename)}:{\"content\":${com.google.gson.JsonPrimitive(content)}}}")
            append("}")
        }

        val result = post("$API_URL/gists", jsonBody, token)
            ?: return GitHubToolResult(id, false, "Error al crear gist")

        return GitHubToolResult(id, true, "Gist creado: ${result.get("html_url")?.asString}")
    }

    // ── Actions/Workflows ──────────────────────────────────────────────────

    private suspend fun listWorkflows(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")

        val json = get("$API_URL/repos/$repo/actions/workflows", token)
            ?: return GitHubToolResult(id, false, "Error al obtener workflows")

        val workflows = json.getAsJsonArray("workflows")
        if (workflows == null || workflows.size() == 0) return GitHubToolResult(id, true, "No hay workflows en $repo")

        val formatted = buildString {
            appendLine("Workflows en $repo (${workflows.size()}):")
            appendLine()
            workflows.forEachIndexed { i, item ->
                val w = item.asJsonObject
                val name = w.get("name")?.asString ?: "?"
                val state = w.get("state")?.asString ?: "?"
                val wid = w.get("id")?.asLong ?: 0
                val path = w.get("path")?.asString ?: ""
                appendLine("${i + 1}. **$name** [$state]")
                appendLine("   ID: $wid | Archivo: $path")
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    private suspend fun triggerWorkflow(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val workflowId = args.get("workflow_id")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'workflow_id' requerido")
        val ref = args.get("ref")?.asString ?: run {
            val repoInfo = get("$API_URL/repos/$repo", token)
            repoInfo?.get("default_branch")?.asString ?: "main"
        }
        val inputs = args.get("inputs")?.asString

        val jsonBody = buildString {
            append("{\"ref\":${com.google.gson.JsonPrimitive(ref)}")
            if (!inputs.isNullOrBlank()) append(",\"inputs\":$inputs")
            append("}")
        }

        // workflow_dispatch returns 204 No Content on success
        val code = withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$API_URL/repos/$repo/actions/workflows/$workflowId/dispatches")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/vnd.github+json")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()
                okHttpClient.newCall(req).execute().code
            } catch (e: Exception) {
                Log.e(TAG, "Trigger workflow error", e)
                -1
            }
        }

        return if (code in 200..299) {
            GitHubToolResult(id, true, "Workflow '$workflowId' disparado en branch '$ref'")
        } else {
            GitHubToolResult(id, false, "Error al disparar workflow (HTTP $code). Verifica que el workflow soporte workflow_dispatch.")
        }
    }

    private suspend fun listWorkflowRuns(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val workflowId = args.get("workflow_id")?.asString
        val status = args.get("status")?.asString
        val perPage = args.get("per_page")?.asInt?.coerceIn(1, 30) ?: 10

        val url = buildString {
            if (!workflowId.isNullOrBlank()) {
                append("$API_URL/repos/$repo/actions/workflows/$workflowId/runs?per_page=$perPage")
            } else {
                append("$API_URL/repos/$repo/actions/runs?per_page=$perPage")
            }
            if (!status.isNullOrBlank()) append("&status=$status")
        }

        val json = get(url, token)
            ?: return GitHubToolResult(id, false, "Error al obtener ejecuciones de workflows")

        val runs = json.getAsJsonArray("workflow_runs")
        if (runs == null || runs.size() == 0) return GitHubToolResult(id, true, "No hay ejecuciones de workflows")

        val formatted = buildString {
            appendLine("Ejecuciones de workflows en $repo (${runs.size()}):")
            appendLine()
            runs.forEachIndexed { i, item ->
                val r = item.asJsonObject
                val name = r.get("name")?.asString ?: "?"
                val runStatus = r.get("status")?.asString ?: "?"
                val conclusion = r.get("conclusion")?.asString ?: "pending"
                val branch = r.get("head_branch")?.asString ?: ""
                val runNumber = r.get("run_number")?.asInt ?: 0
                val createdAt = r.get("created_at")?.asString ?: ""
                val icon = when (conclusion) {
                    "success" -> "✅"; "failure" -> "❌"; "cancelled" -> "⏹"; else -> "🔄"
                }
                appendLine("${i + 1}. $icon **$name** #$runNumber [$runStatus/$conclusion]")
                appendLine("   Branch: $branch | $createdAt")
                appendLine("   URL: ${r.get("html_url")?.asString}")
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    // ── Stars ──────────────────────────────────────────────────────────────

    private suspend fun starRepo(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val repo = args.get("repo")?.asString ?: return GitHubToolResult(id, false, "Parámetro 'repo' requerido")
        val unstar = args.get("unstar")?.asString == "true"

        val url = "$API_URL/user/starred/$repo"
        val code = if (unstar) deleteNoBody(url, token) else putNoBody(url, token)

        return if (code in 200..299) {
            val action = if (unstar) "quitado star de" else "dado star a"
            GitHubToolResult(id, true, "Has $action $repo")
        } else {
            GitHubToolResult(id, false, "Error al ${if (unstar) "quitar" else "dar"} star (HTTP $code)")
        }
    }

    private suspend fun listStarred(id: String, args: com.google.gson.JsonObject, token: String): GitHubToolResult {
        val sort = args.get("sort")?.asString ?: "created"
        val perPage = args.get("per_page")?.asInt?.coerceIn(1, 50) ?: 15

        val items = getArray("$API_URL/user/starred?sort=$sort&per_page=$perPage", token)
            ?: return GitHubToolResult(id, false, "Error al obtener repos con star")
        if (items.size() == 0) return GitHubToolResult(id, true, "No tienes repos con star")

        val formatted = buildString {
            appendLine("Repos con star (${items.size()}):")
            appendLine()
            items.forEachIndexed { i, item ->
                val r = item.asJsonObject
                appendLine("${i + 1}. **${r.get("full_name")?.asString}** - ${r.get("stargazers_count")?.asInt ?: 0} stars")
                val desc = r.get("description")?.asString
                if (!desc.isNullOrBlank()) appendLine("   $desc")
                appendLine("   Lang: ${r.get("language")?.asString ?: "N/A"}")
            }
        }
        return GitHubToolResult(id, true, formatted.trim())
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
