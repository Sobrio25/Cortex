package com.aiagents.app.data.terminal

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class CommandResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val executionTimeMs: Long,
    val timedOut: Boolean = false
) {
    val isSuccess: Boolean get() = exitCode == 0 && !timedOut
    val combinedOutput: String get() = buildString {
        if (stdout.isNotEmpty()) append(stdout)
        if (stderr.isNotEmpty()) {
            if (isNotEmpty()) append("\n")
            append("STDERR:\n$stderr")
        }
    }
}

class ShellExecutor {
    companion object {
        private const val TAG = "ShellExecutor"
        private const val DEFAULT_TIMEOUT_SECONDS = 30L
        private const val MAX_OUTPUT_LENGTH = 50000
        
        private val BLOCKED_COMMANDS = setOf(
            "rm -rf /", "rm -rf /*", "mkfs", "dd if=", "> /dev/sd",
            "chmod 777 /", "chown root", "su -c", "sudo su",
            "reboot", "shutdown", "halt", "poweroff",
            "init 0", "init 6", "telinit",
            ":(){ :|:& };:", "fork bomb",
            "mv /*", "cp /* /dev/null",
            "wget", "curl -X POST", "curl -X PUT", "curl -X DELETE",
            "nc -l", "netcat", "iptables", "ip6tables",
            "passwd", "useradd", "userdel", "usermod",
            "apt-get", "apt", "dpkg", "yum", "dnf", "pacman", "snap",
            "systemctl", "service", "/etc/init.d",
            "crontab", "at ", "batch",
            "pkill -9", "kill -9 -1", "killall",
            "umount -l", "umount -f", "mount",
            "format", "del /", "rd /s"
        )
        
        private val SAFE_COMMAND_PREFIXES = setOf(
            "ls", "cat", "head", "tail", "grep", "find", "wc", "sort", "uniq",
            "echo", "pwd", "whoami", "date", "cal", "uname", "hostname",
            "df", "du", "free", "top", "ps", "pgrep", "pidof",
            "which", "whereis", "type", "file", "stat", "touch",
            "mkdir", "rmdir", "mv", "cp", "rm", "ln",
            "chmod", "chown", "chgrp",
            "diff", "cmp", "patch",
            "tar", "gzip", "gunzip", "zip", "unzip",
            "git", "svn", "hg",
            "python", "python3", "node", "npm", "npx", "yarn",
            "java", "javac", "gradle", "mvn",
            "go", "cargo", "rustc",
            "ruby", "gem", "bundle",
            "php", "composer",
            "perl", "awk", "sed", "tr", "cut", "paste",
            "xargs", "parallel",
            "curl", "wget",
            "sqlite3", "mysql", "psql",
            "adb", "fastboot",
            "termux", "pkg",
            "env", "export", "set", "unset", "alias",
            "history", "clear"
        )
        
        // Comandos que se pueden simular sin shell real (para dispositivos no rooteados)
        private val SIMULATED_COMMANDS = setOf("cat", "ls", "head", "tail", "grep", "pwd", "echo", "wc")
    }
    
    private var currentWorkingDir: String? = null
    
    fun isCommandBlocked(command: String): Boolean {
        val normalizedCommand = command.trim().lowercase()
        val commandWords = normalizedCommand.split(Regex("\\s+"))
        val baseCommand = commandWords.firstOrNull() ?: ""
        
        BLOCKED_COMMANDS.forEach { blocked ->
            val blockedLower = blocked.lowercase().trim()
            
            if (blockedLower.contains(" ")) {
                if (normalizedCommand.startsWith(blockedLower)) {
                    Log.w(TAG, "Blocked command detected: $command")
                    return true
                }
            } else {
                if (baseCommand == blockedLower) {
                    Log.w(TAG, "Blocked command detected: $command")
                    return true
                }
            }
        }
        
        return false
    }
    
    fun isCommandSafe(command: String): Boolean {
        val trimmedCommand = command.trim()
        if (trimmedCommand.isEmpty()) return false
        
        val commandName = trimmedCommand.split(Regex("\\s+")).firstOrNull() ?: return false
        
        return SAFE_COMMAND_PREFIXES.any { safePrefix ->
            commandName == safePrefix || commandName.startsWith("$safePrefix.")
        }
    }
    
    fun getCommandRiskLevel(command: String): CommandRiskLevel {
        if (isCommandBlocked(command)) return CommandRiskLevel.BLOCKED
        if (isCommandSafe(command)) return CommandRiskLevel.SAFE
        return CommandRiskLevel.MODERATE
    }
    
    fun execute(
        command: String,
        workingDirectory: String? = null,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS
    ): CommandResult {
        val startTime = System.currentTimeMillis()
        
        if (isCommandBlocked(command)) {
            return CommandResult(
                command = command,
                exitCode = -1,
                stdout = "",
                stderr = "Comando bloqueado por seguridad",
                executionTimeMs = 0,
                timedOut = false
            )
        }
        
        // Actualizar directorio de trabajo actual
        currentWorkingDir = workingDirectory ?: currentWorkingDir
        
        // Verificar si es un comando que podemos simular (para dispositivos no rooteados)
        val baseCommand = command.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: ""

        return if (baseCommand in SIMULATED_COMMANDS && !containsShellOperators(command)) {
            // Comando simple sin operadores de shell → simular con Java File API
            executeSimulatedCommand(command, currentWorkingDir, startTime)
        } else {
            // Comando con pipes/operadores o comando no simulable → usar shell real
            executeProcessCommand(command, currentWorkingDir, timeoutSeconds, startTime)
        }
    }
    
    /**
     * Ejecuta comandos comunes usando Java File API (funciona sin root)
     */
    private fun executeSimulatedCommand(
        command: String,
        workingDir: String?,
        startTime: Long
    ): CommandResult {
        Log.d(TAG, "Executing simulated command: $command")
        
        val parts = parseCommand(command)
        val cmd = parts.firstOrNull()?.lowercase() ?: ""
        val args = parts.drop(1)
        
        val baseDir = workingDir?.let { File(it) } ?: File(".")
        
        return try {
            val result = when (cmd) {
                "cat" -> simulateCat(args, baseDir)
                "ls" -> simulateLs(args, baseDir)
                "head" -> simulateHead(args, baseDir)
                "tail" -> simulateTail(args, baseDir)
                "grep" -> simulateGrep(args, baseDir)
                "pwd" -> simulatePwd(baseDir)
                "echo" -> simulateEcho(args)
                "wc" -> simulateWc(args, baseDir)
                else -> Pair(1, "Comando no soportado en modo simulado: $cmd")
            }
            
            CommandResult(
                command = command,
                exitCode = result.first,
                stdout = if (result.first == 0) result.second else "",
                stderr = if (result.first != 0) result.second else "",
                executionTimeMs = System.currentTimeMillis() - startTime,
                timedOut = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing simulated command", e)
            CommandResult(
                command = command,
                exitCode = 1,
                stdout = "",
                stderr = "Error: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime,
                timedOut = false
            )
        }
    }
    
    private fun simulateCat(args: List<String>, baseDir: File): Pair<Int, String> {
        if (args.isEmpty()) {
            return Pair(1, "cat: falta el operando")
        }
        
        val output = StringBuilder()
        for (arg in args) {
            if (arg.startsWith("-")) continue // Ignorar flags
            
            val file = resolveFile(arg, baseDir)
            if (!file.exists()) {
                return Pair(1, "cat: $arg: No existe el archivo o directorio")
            }
            if (!file.isFile) {
                return Pair(1, "cat: $arg: Es un directorio")
            }
            
            try {
                output.append(file.readText())
            } catch (e: Exception) {
                return Pair(1, "cat: $arg: ${e.message}")
            }
        }
        
        return Pair(0, output.toString())
    }
    
    private fun simulateLs(args: List<String>, baseDir: File): Pair<Int, String> {
        val showAll = args.contains("-a") || args.contains("-la") || args.contains("-al")
        val longFormat = args.contains("-l") || args.contains("-la") || args.contains("-al")
        
        val target = args.find { !it.startsWith("-") }
        val dir = if (target != null) resolveFile(target, baseDir) else baseDir
        
        if (!dir.exists()) {
            return Pair(1, "ls: $target: No existe el archivo o directorio")
        }
        
        if (!dir.isDirectory) {
            // Si es un archivo, mostrar solo ese archivo
            return Pair(0, if (longFormat) formatLongListing(dir) else dir.name)
        }
        
        val files = dir.listFiles()?.toList() ?: emptyList()
        val filteredFiles = if (showAll) files else files.filter { !it.name.startsWith(".") }
        val sortedFiles = filteredFiles.sortedBy { it.name }
        
        val output = StringBuilder()
        
        if (longFormat) {
            output.appendLine("total ${sortedFiles.size}")
            sortedFiles.forEach { file ->
                    output.appendLine(formatLongListing(file))
                }
        } else {
            sortedFiles.forEach { file ->
                    output.append(file.name).append("  ")
                }
            if (sortedFiles.isNotEmpty()) output.appendLine()
        }
        
        return Pair(0, output.toString())
    }
    
    private fun formatLongListing(file: File): String {
        val type = if (file.isDirectory) "d" else "-"
        val perms = if (file.isDirectory) "rwxr-xr-x" else "rw-r--r--"
        val size = file.length()
        val date = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date(file.lastModified()))
        val name = file.name
        
        return "$type$perms 1 user group $size $date $name"
    }
    
    private fun simulateHead(args: List<String>, baseDir: File): Pair<Int, String> {
        var lines = 10
        var fileArg: String? = null
        
        var i = 0
        while (i < args.size) {
            when {
                args[i] == "-n" && i + 1 < args.size -> {
                    lines = args[i + 1].toIntOrNull() ?: 10
                    i += 2
                }
                args[i].startsWith("-n") -> {
                    lines = args[i].substring(2).toIntOrNull() ?: 10
                    i++
                }
                !args[i].startsWith("-") && fileArg == null -> {
                    fileArg = args[i]
                    i++
                }
                else -> i++
            }
        }
        
        if (fileArg == null) {
            return Pair(1, "head: falta el operando")
        }
        
        val file = resolveFile(fileArg, baseDir)
        if (!file.exists()) {
            return Pair(1, "head: $fileArg: No existe el archivo")
        }
        
        val content = file.readLines().take(lines).joinToString("\n")
        return Pair(0, content)
    }
    
    private fun simulateTail(args: List<String>, baseDir: File): Pair<Int, String> {
        var lines = 10
        var fileArg: String? = null
        
        var i = 0
        while (i < args.size) {
            when {
                args[i] == "-n" && i + 1 < args.size -> {
                    lines = args[i + 1].toIntOrNull() ?: 10
                    i += 2
                }
                args[i].startsWith("-n") -> {
                    lines = args[i].substring(2).toIntOrNull() ?: 10
                    i++
                }
                !args[i].startsWith("-") && fileArg == null -> {
                    fileArg = args[i]
                    i++
                }
                else -> i++
            }
        }
        
        if (fileArg == null) {
            return Pair(1, "tail: falta el operando")
        }
        
        val file = resolveFile(fileArg, baseDir)
        if (!file.exists()) {
            return Pair(1, "tail: $fileArg: No existe el archivo")
        }
        
        val allLines = file.readLines()
        val content = allLines.takeLast(lines).joinToString("\n")
        return Pair(0, content)
    }
    
    private fun simulateGrep(args: List<String>, baseDir: File): Pair<Int, String> {
        if (args.size < 2) {
            return Pair(1, "grep: uso: grep [patrón] [archivo]")
        }
        
        val pattern = args[0]
        val fileArg = args[1]
        
        val file = resolveFile(fileArg, baseDir)
        if (!file.exists()) {
            return Pair(1, "grep: $fileArg: No existe el archivo")
        }
        
        val matchingLines = file.readLines()
            .filter { it.contains(pattern, ignoreCase = true) }
            .joinToString("\n")
        
        return if (matchingLines.isEmpty()) Pair(1, "") else Pair(0, matchingLines)
    }
    
    private fun simulatePwd(baseDir: File): Pair<Int, String> {
        return Pair(0, baseDir.absolutePath)
    }
    
    private fun simulateEcho(args: List<String>): Pair<Int, String> {
        return Pair(0, args.joinToString(" "))
    }
    
    private fun simulateWc(args: List<String>, baseDir: File): Pair<Int, String> {
        val showLines = args.contains("-l") || !args.contains("-c")
        val showChars = args.contains("-c") || !args.contains("-l")
        
        val fileArg = args.find { !it.startsWith("-") }
            ?: return Pair(1, "wc: falta el operando")
        
        val file = resolveFile(fileArg, baseDir)
        if (!file.exists()) {
            return Pair(1, "wc: $fileArg: No existe el archivo")
        }
        
        val content = file.readText()
        val lines = content.lines().size
        val chars = content.length
        
        val output = when {
            showLines && showChars -> "$lines $chars $fileArg"
            showLines -> "$lines $fileArg"
            showChars -> "$chars $fileArg"
            else -> "$lines $chars $fileArg"
        }
        
        return Pair(0, output)
    }
    
    /**
     * Ejecuta comandos mediante ProcessBuilder (requiere shell disponible)
     */
    private fun executeProcessCommand(
        command: String,
        workingDir: String?,
        timeoutSeconds: Long,
        startTime: Long
    ): CommandResult {
        return try {
            Log.d(TAG, "Executing via ProcessBuilder: $command")
            
            val processBuilder = ProcessBuilder("sh", "-c", command)
            
            workingDir?.let {
                val dir = File(it)
                if (dir.exists() && dir.isDirectory) {
                    processBuilder.directory(dir)
                }
            }
            
            processBuilder.redirectErrorStream(false)
            
            val process = processBuilder.start()
            
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            
            val stdoutReader = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (stdout.length < MAX_OUTPUT_LENGTH) {
                                stdout.append(line).append("\n")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading stdout", e)
                }
            }
            
            val stderrReader = Thread {
                try {
                    BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (stderr.length < MAX_OUTPUT_LENGTH) {
                                stderr.append(line).append("\n")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading stderr", e)
                }
            }
            
            stdoutReader.start()
            stderrReader.start()
            
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            
            if (!finished) {
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
                stdoutReader.interrupt()
                stderrReader.interrupt()
                
                return CommandResult(
                    command = command,
                    exitCode = -1,
                    stdout = truncateOutput(stdout.toString()),
                    stderr = "Timeout: comando excedió ${timeoutSeconds}s",
                    executionTimeMs = System.currentTimeMillis() - startTime,
                    timedOut = true
                )
            }
            
            stdoutReader.join(1000)
            stderrReader.join(1000)
            
            val exitCode = process.exitValue()
            
            CommandResult(
                command = command,
                exitCode = exitCode,
                stdout = truncateOutput(stdout.toString()),
                stderr = truncateOutput(stderr.toString()),
                executionTimeMs = System.currentTimeMillis() - startTime,
                timedOut = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing process command", e)
            CommandResult(
                command = command,
                exitCode = 1,
                stdout = "",
                stderr = "Error al ejecutar comando: ${e.message}\n" +
                        "Nota: Este comando requiere un shell disponible en el dispositivo.",
                executionTimeMs = System.currentTimeMillis() - startTime,
                timedOut = false
            )
        }
    }
    
    private fun parseCommand(command: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var quoteChar: Char? = null
        
        for (char in command) {
            when {
                char == '"' || char == '\'' -> {
                    if (!inQuotes) {
                        inQuotes = true
                        quoteChar = char
                        if (current.isNotEmpty()) {
                            result.add(current.toString())
                            current.clear()
                        }
                    } else if (quoteChar == char) {
                        inQuotes = false
                        quoteChar = null
                        result.add(current.toString())
                        current.clear()
                    } else {
                        current.append(char)
                    }
                }
                char.isWhitespace() && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(char)
            }
        }
        
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        
        return result
    }
    
    private fun containsShellOperators(command: String): Boolean {
        // Detectar operadores de shell que requieren ejecución real
        val operators = listOf("|", "&&", "||", ";", ">>", ">", "<", "<<", "$(", "`")
        var inSingleQuote = false
        var inDoubleQuote = false

        var i = 0
        while (i < command.length) {
            val char = command[i]
            when {
                char == '\'' && !inDoubleQuote -> inSingleQuote = !inSingleQuote
                char == '"' && !inSingleQuote -> inDoubleQuote = !inDoubleQuote
                !inSingleQuote && !inDoubleQuote -> {
                    for (op in operators) {
                        if (command.startsWith(op, i)) {
                            return true
                        }
                    }
                }
            }
            i++
        }
        return false
    }

    private fun resolveFile(path: String, baseDir: File): File {
        return if (path.startsWith("/")) {
            File(path)
        } else {
            File(baseDir, path)
        }
    }
    
    private fun truncateOutput(output: String): String {
        return if (output.length > MAX_OUTPUT_LENGTH) {
            output.take(MAX_OUTPUT_LENGTH) + "\n... (salida truncada)"
        } else {
            output
        }
    }
}

enum class CommandRiskLevel {
    SAFE,
    MODERATE,
    BLOCKED
}
