package com.aiagents.app.data.terminal

import android.util.Base64
import android.util.Log
import com.google.gson.JsonParser
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class CodeExecutionResult(
    val toolCallId: String,
    val toolName: String,
    val success: Boolean,
    val content: String,
    val htmlPreview: String? = null, // non-null when preview_web is called
    val projectPreviewUrl: String? = null, // non-null when preview_project is called
    val previewTitle: String? = null // title for the preview dialog
)

@Singleton
class CodeExecutionHandler @Inject constructor(
    private val localWebServer: LocalWebServer
) {

    companion object {
        private const val TAG = "CodeExecutionHandler"

        const val TOOL_RUN_CODE = "run_code"
        const val TOOL_PREVIEW_WEB = "preview_web"
        const val TOOL_PREVIEW_PROJECT = "preview_project"

        val ALL_TOOL_NAMES = setOf(TOOL_RUN_CODE, TOOL_PREVIEW_WEB, TOOL_PREVIEW_PROJECT)

        private val LANGUAGE_CONFIG = mapOf(
            "python" to LangConfig("python3", "py"),
            "javascript" to LangConfig("node", "js"),
            "bash" to LangConfig("sh", "sh"),
            "sh" to LangConfig("sh", "sh")
        )

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_RUN_CODE,
                "description" to "Execute code (Python, JavaScript, Bash). Python runs via Pyodide (WebAssembly) in a WebView — supports numpy, pandas, matplotlib, scipy, etc. Output is displayed visually. Bash runs natively. Saves to workspace and runs automatically.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "language" to mapOf(
                            "type" to "string",
                            "description" to "Lenguaje de programacion",
                            "enum" to listOf("python", "javascript", "bash")
                        ),
                        "code" to mapOf(
                            "type" to "string",
                            "description" to "Codigo fuente a ejecutar"
                        ),
                        "file_name" to mapOf(
                            "type" to "string",
                            "description" to "Nombre del archivo (opcional, se genera automaticamente si no se proporciona)"
                        )
                    ),
                    "required" to listOf("language", "code"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_PREVIEW_WEB,
                "description" to "Render HTML/CSS/JS in a WebView for visual preview. Use for SINGLE-FILE web pages, UI components, charts, React demos. HTML must be self-contained or use CDN links. For multi-file projects, use preview_project instead.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "html" to mapOf(
                            "type" to "string",
                            "description" to "Codigo HTML completo a renderizar. Puede incluir <style> y <script> inline, o enlaces CDN (React, Chart.js, etc.)"
                        ),
                        "title" to mapOf(
                            "type" to "string",
                            "description" to "Titulo para la ventana de preview (opcional)"
                        )
                    ),
                    "required" to listOf("html"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_PREVIEW_PROJECT,
                "description" to buildString {
                    append("Serve and preview a multi-file web project in the browser. ")
                    append("Starts a local HTTP server pointing at the project directory and opens a WebView. ")
                    append("Use this for projects with multiple HTML/CSS/JS files, local imports, ES modules, images, fonts, etc. ")
                    append("The project must have an index.html entry point (or specify a custom one). ")
                    append("Supports: static sites, React/Vue/Svelte builds (serve the dist/ or build/ folder), ")
                    append("Tailwind with local CSS, any multi-file web project. ")
                    append("For single-file HTML previews, use preview_web instead. ")
                    append("IMPORTANT: If the project needs a build step (npm run build), run it first with run_code(bash) before calling this tool.")
                },
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf(
                            "type" to "string",
                            "description" to "Path to the project directory to serve, relative to the workspace root. Examples: '.', 'my-site', 'dist', 'build', 'frontend/dist'"
                        ),
                        "entry" to mapOf(
                            "type" to "string",
                            "description" to "Entry file to open (default: 'index.html'). Relative to the project path."
                        ),
                        "title" to mapOf(
                            "type" to "string",
                            "description" to "Title for the preview window (optional)"
                        ),
                        "live_reload" to mapOf(
                            "type" to "boolean",
                            "description" to "Enable live reload — auto-refreshes when files change (default: true)"
                        ),
                        "lan_access" to mapOf(
                            "type" to "boolean",
                            "description" to "Enable LAN access — makes the preview accessible from other devices on the same network (default: true). Returns the LAN URL (e.g. http://192.168.1.42:8080)"
                        )
                    ),
                    "required" to listOf("path"))
            ))
        )
    }

    fun executeTool(
        toolCallId: String,
        toolName: String,
        arguments: String,
        workspacePath: String
    ): CodeExecutionResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            when (toolName) {
                TOOL_RUN_CODE -> runCode(toolCallId, args, workspacePath)
                TOOL_PREVIEW_WEB -> previewWeb(toolCallId, args, workspacePath)
                TOOL_PREVIEW_PROJECT -> previewProject(toolCallId, args, workspacePath)
                else -> CodeExecutionResult(toolCallId, toolName, false, "Unknown tool: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            CodeExecutionResult(toolCallId, toolName, false, "Error: ${e.message}")
        }
    }

    /** Stop the local web server (call when preview is dismissed) */
    fun stopServer() {
        if (localWebServer.isActive) {
            localWebServer.stop()
            Log.d(TAG, "Local web server stopped")
        }
    }

    private fun runCode(
        id: String,
        args: com.google.gson.JsonObject,
        workspacePath: String
    ): CodeExecutionResult {
        val language = args.get("language")?.asString
            ?: return CodeExecutionResult(id, TOOL_RUN_CODE, false, "Parameter 'language' required")
        val code = args.get("code")?.asString
            ?: return CodeExecutionResult(id, TOOL_RUN_CODE, false, "Parameter 'code' required")
        val customName = args.get("file_name")?.asString

        // Python & JavaScript run via Pyodide/WebView since native runtimes aren't available on Android
        if (language == "python" || language == "javascript") {
            return runViaPyodide(id, language, code, customName, workspacePath)
        }

        val config = LANGUAGE_CONFIG[language]
            ?: return CodeExecutionResult(id, TOOL_RUN_CODE, false, "Unsupported language: $language. Supported: ${LANGUAGE_CONFIG.keys}")

        val fileName = customName ?: "run_${System.currentTimeMillis()}.${config.extension}"

        val workDir = File(workspacePath)
        workDir.mkdirs()

        val scriptFile = File(workDir, fileName)
        scriptFile.writeText(code)
        if (config.extension == "sh") {
            scriptFile.setExecutable(true)
        }

        Log.d(TAG, "Running $language: ${scriptFile.absolutePath}")

        val shellExecutor = ShellExecutor()
        val result = shellExecutor.execute(
            "${config.command} ${scriptFile.absolutePath}",
            workspacePath
        )

        val output = buildString {
            appendLine("=== $language | $fileName ===")
            if (result.stdout.isNotBlank()) append(result.stdout)
            if (result.stderr.isNotBlank()) {
                if (isNotBlank()) appendLine()
                appendLine("[stderr] ${result.stderr}")
            }
            if (result.timedOut) appendLine("[timeout after ${result.executionTimeMs}ms]")
            appendLine("=== exit: ${result.exitCode} | ${result.executionTimeMs}ms ===")
        }

        return CodeExecutionResult(
            toolCallId = id,
            toolName = TOOL_RUN_CODE,
            success = result.isSuccess,
            content = output
        )
    }

    /**
     * Runs Python or JavaScript code in a WebView via Pyodide (Python→WASM) or direct JS execution.
     * Pyodide supports numpy, pandas, matplotlib, scipy, scikit-learn, etc.
     * Output (stdout/stderr/plots) is displayed visually in the WebView.
     */
    private fun runViaPyodide(
        id: String,
        language: String,
        code: String,
        customName: String?,
        workspacePath: String
    ): CodeExecutionResult {
        val ext = if (language == "python") "py" else "js"
        val fileName = customName ?: "run_${System.currentTimeMillis()}.$ext"

        val workDir = File(workspacePath)
        workDir.mkdirs()

        // Save source file for reference
        val srcFile = File(workDir, fileName)
        srcFile.writeText(code)

        // Base64-encode the code to avoid escaping issues in HTML
        val b64Code = Base64.encodeToString(code.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        val html = if (language == "python") buildPyodideHtml(b64Code) else buildJsHtml(b64Code)

        // Save HTML for reference
        val htmlFile = File(workDir, fileName.substringBeforeLast('.') + "_output.html")
        htmlFile.writeText(html)

        Log.d(TAG, "Running $language via WebView: ${srcFile.absolutePath}")

        return CodeExecutionResult(
            toolCallId = id,
            toolName = TOOL_RUN_CODE,
            success = true,
            content = "Python ejecutado via Pyodide (WebAssembly) en WebView. Archivo fuente: $fileName. " +
                "El usuario puede ver la salida (stdout, stderr, gráficos matplotlib) en el preview.",
            htmlPreview = html,
            previewTitle = if (language == "python") "Python · $fileName" else "JavaScript · $fileName"
        )
    }

    private fun buildPyodideHtml(b64Code: String): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Python</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Cascadia Code','Fira Code','Courier New',monospace;background:#1a1b26;color:#a9b1d6;padding:12px;font-size:13px}
#status{color:#7aa2f7;padding:8px 0;display:flex;align-items:center;gap:8px}
#status .spinner{width:14px;height:14px;border:2px solid #7aa2f7;border-top-color:transparent;border-radius:50%;animation:spin .6s linear infinite}
@keyframes spin{to{transform:rotate(360deg)}}
#output{white-space:pre-wrap;word-wrap:break-word;line-height:1.6}
.stdout{color:#a9b1d6}
.stderr{color:#f7768e}
.result{color:#9ece6a;opacity:0.8}
img,canvas,svg{max-width:100%;height:auto;margin:8px 0;border-radius:4px}
.done{color:#9ece6a;padding:8px 0;border-top:1px solid #24283b;margin-top:8px;font-size:12px}
.error-block{background:#1a1b26;border-left:3px solid #f7768e;padding:8px;margin:4px 0}
</style>
</head>
<body>
<div id="status"><div class="spinner"></div><span>Cargando Python...</span></div>
<div id="output"></div>
<script src="https://cdn.jsdelivr.net/pyodide/v0.27.5/full/pyodide.js"></script>
<script>
const out=document.getElementById('output'),stat=document.getElementById('status');
function add(t,cls){const s=document.createElement('span');s.className=cls;s.textContent=t;out.appendChild(s)}
function setStatus(t){stat.querySelector('span').textContent=t}
function done(t){stat.innerHTML='';const d=document.createElement('div');d.className='done';d.textContent=t;out.appendChild(d)}

async function main(){
  const t0=performance.now();
  try{
    setStatus('Inicializando Pyodide...');
    const py=await loadPyodide();
    py.setStdout({batched:t=>add(t+'\n','stdout')});
    py.setStderr({batched:t=>add(t+'\n','stderr')});

    const code=atob('$b64Code');

    // Auto-detect and install packages
    const imports=[...code.matchAll(/^\s*(?:import|from)\s+(\w+)/gm)].map(m=>m[1]);
    const stdlib=new Set(['os','sys','math','json','re','time','datetime','collections','itertools',
      'functools','random','string','io','csv','pathlib','typing','dataclasses','abc','copy',
      'operator','textwrap','enum','statistics','struct','hashlib','base64','urllib','html',
      'xml','unittest','pprint','decimal','fractions','bisect','heapq','array','queue',
      'threading','multiprocessing','subprocess','socket','http','email','logging',
      'argparse','configparser','traceback','inspect','ast','dis','gc','pickle','shelve',
      'sqlite3','zlib','gzip','zipfile','tarfile','tempfile','shutil','glob','fnmatch',
      'contextlib','warnings','weakref','types','numbers','cmath','codecs']);
    const needed=imports.filter(p=>!stdlib.has(p));
    if(needed.length>0){
      setStatus('Instalando: '+needed.join(', ')+'...');
      try{await py.loadPackagesFromImports(code)}catch(e){add('Warning: '+e.message+'\n','stderr')}
    }

    // matplotlib support: render to inline image
    if(code.includes('matplotlib')||code.includes('plt.')){
      setStatus('Configurando matplotlib...');
      await py.loadPackage('matplotlib');
      py.runPython(`
import matplotlib
matplotlib.use('AGG')
import matplotlib.pyplot as plt
import io,base64,js

_orig_show=plt.show
def _patched_show(*a,**kw):
    for i in plt.get_fignums():
        fig=plt.figure(i)
        buf=io.BytesIO()
        fig.savefig(buf,format='png',bbox_inches='tight',dpi=150,facecolor='#1a1b26',edgecolor='none')
        buf.seek(0)
        d=base64.b64encode(buf.read()).decode()
        el=js.document.createElement('img')
        el.src='data:image/png;base64,'+d
        js.document.getElementById('output').appendChild(el)
        buf.close()
    plt.close('all')
plt.show=_patched_show
`);
    }

    setStatus('Ejecutando...');
    stat.querySelector('.spinner')?.remove();
    const result=await py.runPythonAsync(code);
    if(result!==undefined&&result!==null&&String(result)!==''){
      add(String(result)+'\n','result');
    }
    const ms=Math.round(performance.now()-t0);
    done('Completado en '+ms+'ms');
  }catch(e){
    const msg=e.message||String(e);
    add(msg+'\n','stderr');
    done('Error');
  }
}
main();
</script>
</body>
</html>""".trimIndent()

    private fun buildJsHtml(b64Code: String): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>JavaScript</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Cascadia Code','Fira Code','Courier New',monospace;background:#1a1b26;color:#a9b1d6;padding:12px;font-size:13px}
#output{white-space:pre-wrap;word-wrap:break-word;line-height:1.6}
.stdout{color:#a9b1d6}
.stderr{color:#f7768e}
.result{color:#9ece6a;opacity:0.8}
.done{color:#9ece6a;padding:8px 0;border-top:1px solid #24283b;margin-top:8px;font-size:12px}
</style>
</head>
<body>
<div id="output"></div>
<script>
const out=document.getElementById('output');
function add(t,cls){const s=document.createElement('span');s.className=cls;s.textContent=t;out.appendChild(s)}

// Override console
const _log=console.log,_err=console.error,_warn=console.warn;
console.log=(...a)=>{add(a.map(x=>typeof x==='object'?JSON.stringify(x,null,2):String(x)).join(' ')+'\n','stdout')};
console.error=(...a)=>{add(a.map(String).join(' ')+'\n','stderr')};
console.warn=console.log;

const t0=performance.now();
try{
  const code=atob('$b64Code');
  const result=eval(code);
  if(result!==undefined&&result!==null) add(String(result)+'\n','result');
}catch(e){
  add(e.stack||e.message||String(e)+'\n','stderr');
}
const ms=Math.round(performance.now()-t0);
const d=document.createElement('div');d.className='done';d.textContent='Completado en '+ms+'ms';out.appendChild(d);
</script>
</body>
</html>""".trimIndent()

    private fun previewWeb(
        id: String,
        args: com.google.gson.JsonObject,
        workspacePath: String
    ): CodeExecutionResult {
        val html = args.get("html")?.asString
            ?: return CodeExecutionResult(id, TOOL_PREVIEW_WEB, false, "Parameter 'html' required")
        val title = args.get("title")?.asString ?: "Preview"

        // Save HTML to workspace for reference
        val workDir = File(workspacePath)
        workDir.mkdirs()
        val htmlFile = File(workDir, "preview_${System.currentTimeMillis()}.html")
        htmlFile.writeText(html)

        Log.d(TAG, "Web preview: ${htmlFile.absolutePath} (${html.length} chars)")

        return CodeExecutionResult(
            toolCallId = id,
            toolName = TOOL_PREVIEW_WEB,
            success = true,
            content = "Preview '$title' listo. Archivo: ${htmlFile.name}",
            htmlPreview = html,
            previewTitle = title
        )
    }

    private fun previewProject(
        id: String,
        args: com.google.gson.JsonObject,
        workspacePath: String
    ): CodeExecutionResult {
        val relativePath = args.get("path")?.asString
            ?: return CodeExecutionResult(id, TOOL_PREVIEW_PROJECT, false, "Parameter 'path' required")
        val entry = args.get("entry")?.asString ?: "index.html"
        val title = args.get("title")?.asString ?: "Project Preview"
        val liveReload = args.get("live_reload")?.asBoolean ?: true
        val lanAccess = args.get("lan_access")?.asBoolean ?: true

        // Resolve the project directory
        val projectDir = if (relativePath == "." || relativePath.isEmpty()) {
            File(workspacePath)
        } else {
            File(workspacePath, relativePath)
        }

        if (!projectDir.exists() || !projectDir.isDirectory) {
            return CodeExecutionResult(
                id, TOOL_PREVIEW_PROJECT, false,
                "Directory not found: ${projectDir.absolutePath}. Make sure to create the files first with write_file."
            )
        }

        // Check that entry file exists
        val entryFile = File(projectDir, entry)
        if (!entryFile.exists()) {
            // List available files to help debug
            val available = projectDir.listFiles()
                ?.filter { it.isFile }
                ?.map { it.name }
                ?.sorted()
                ?.take(20)
                ?.joinToString(", ") ?: "none"
            return CodeExecutionResult(
                id, TOOL_PREVIEW_PROJECT, false,
                "Entry file '$entry' not found in ${projectDir.absolutePath}. Available files: $available"
            )
        }

        return try {
            // Start the local server pointing at the project directory
            val serverUrl = localWebServer.start(projectDir.absolutePath, liveReload, lanAccess)
            val fullUrl = if (entry == "index.html") serverUrl else "$serverUrl/$entry"

            // For WebView, always use localhost (even in LAN mode)
            val localUrl = localWebServer.localUrl
            val webViewUrl = if (entry == "index.html") localUrl else "$localUrl/$entry"

            Log.d(TAG, "Project preview: $fullUrl (dir: ${projectDir.absolutePath}, LAN: $lanAccess)")

            // Count project files for the response
            val fileCount = countFiles(projectDir)

            val lanInfo = if (lanAccess) {
                val lanUrl = localWebServer.lanUrl
                if (lanUrl != null) {
                    "\n\nAcceso LAN habilitado — otros dispositivos en la misma red pueden acceder en:\n$lanUrl" +
                        if (entry != "index.html") "/$entry" else ""
                } else {
                    "\n\nAcceso LAN habilitado pero no se pudo detectar la IP local. Verifica que estés conectado a WiFi."
                }
            } else ""

            CodeExecutionResult(
                toolCallId = id,
                toolName = TOOL_PREVIEW_PROJECT,
                success = true,
                content = "Servidor local iniciado en $fullUrl — sirviendo ${fileCount} archivos desde ${projectDir.name}/. Live reload: ${if (liveReload) "ON" else "OFF"}$lanInfo",
                projectPreviewUrl = webViewUrl,
                previewTitle = title
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start local server", e)
            CodeExecutionResult(
                id, TOOL_PREVIEW_PROJECT, false,
                "Error starting local server: ${e.message}"
            )
        }
    }

    private fun countFiles(dir: File): Int {
        var count = 0
        val files = dir.listFiles() ?: return 0
        for (f in files) {
            if (f.name.startsWith(".") || f.name == "node_modules") continue
            if (f.isFile) count++ else count += countFiles(f)
        }
        return count
    }

    data class LangConfig(val command: String, val extension: String)
}
