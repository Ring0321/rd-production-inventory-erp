package com.ruiding.erp.service.weight;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class RdWeightWorkerProcess {
    static final long WATCHDOG_MILLIS = 6000;
    static final int MAX_STDERR_BYTES = 16 * 1024;

    private RdWeightWorkerProcess() { }

    static RdWeightSolver.Result solve(RdWeightSolverWorker.Request request, String java, String classPath) {
        try {
            return exchange(request, createProcess(java, classPath, System.getenv()), WATCHDOG_MILLIS);
        } catch (IOException | RuntimeException ex) {
            return RdWeightSolverWorker.unavailable();
        }
    }

    static ProcessBuilder createProcess(String configuredJava, String classPath, Map<String, String> inherited) throws IOException {
        Path java = Path.of(configuredJava);
        if (!java.isAbsolute() || !Files.isRegularFile(java) || !Files.isExecutable(java) ||
                !("java.exe".equalsIgnoreCase(java.getFileName().toString()) || "java".equals(java.getFileName().toString())))
            throw new IOException("Trusted worker Java is unavailable");
        if (classPath == null || classPath.isBlank() || classPath.indexOf(File.pathSeparatorChar) >= 0)
            throw new IOException("Worker requires a single executable jar classpath");
        Path jar = Path.of(classPath);
        if (!jar.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar") || !Files.isRegularFile(jar))
            throw new IOException("Worker requires a regular executable jar");
        java = java.toRealPath();
        jar = jar.toRealPath();
        var builder = new ProcessBuilder(java.toString(), "-Xmx192m", "--enable-native-access=ALL-UNNAMED",
                "-jar", jar.toString(), RdWeightSolverWorker.ARGUMENT);
        sanitizeEnvironment(builder, java, inherited);
        return builder;
    }

    static void sanitizeEnvironment(ProcessBuilder builder, Path java, Map<String, String> inherited) {
        var environment = builder.environment();
        environment.clear();
        // Allowlist only OS/native extraction essentials, never application/JVM option variables.
        for (String key : new String[]{"SystemRoot", "WINDIR", "TEMP", "TMP", "TMPDIR"}) {
            String value = valueIgnoringCase(inherited, key);
            if (value != null && !value.isBlank()) environment.put(key, value);
        }
        String systemRoot = valueIgnoringCase(inherited, "SystemRoot");
        String path = java.getParent().toString();
        if (systemRoot != null && !systemRoot.isBlank()) path += File.pathSeparator + Path.of(systemRoot, "System32");
        environment.put("PATH", path);
    }

    private static String valueIgnoringCase(Map<String, String> environment, String key) {
        return environment.entrySet().stream().filter(entry -> key.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    // Package-private process seam permits class-directory helpers in tests, never HTTP/configuration.
    static RdWeightSolver.Result exchange(RdWeightSolverWorker.Request request, ProcessBuilder builder, long timeoutMillis) {
        if (timeoutMillis <= 0 || timeoutMillis > WATCHDOG_MILLIS) return RdWeightSolverWorker.unavailable();
        Process child = null;
        ExecutorService pipes = null;
        try {
            byte[] input = RdWeightSolverWorker.encodeRequest(request);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            child = builder.start();
            Process owned = child;
            pipes = Executors.newFixedThreadPool(3, task -> {
                Thread thread = new Thread(task, "rd-weight-worker-pipe");
                thread.setDaemon(true);
                return thread;
            });
            Future<?> writer = pipes.submit(() -> {
                try (var stdin = owned.getOutputStream()) { stdin.write(input); }
                return null;
            });
            Future<byte[]> stdout = pipes.submit(() -> RdWeightSolverWorker.readBounded(owned.getInputStream(), RdWeightSolverWorker.MAX_OUTPUT_BYTES));
            Future<?> stderr = pipes.submit(() -> {
                RdWeightSolverWorker.readBounded(owned.getErrorStream(), MAX_STDERR_BYTES);
                return null;
            });
            byte[] output = stdout.get(remaining(deadline), TimeUnit.NANOSECONDS);
            writer.get(remaining(deadline), TimeUnit.NANOSECONDS);
            stderr.get(remaining(deadline), TimeUnit.NANOSECONDS);
            if (!child.waitFor(remaining(deadline), TimeUnit.NANOSECONDS) || child.exitValue() != 0)
                return RdWeightSolverWorker.unavailable();
            return RdWeightSolverWorker.readResult(output, request);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return RdWeightSolverWorker.unavailable();
        } catch (Exception | LinkageError ex) {
            return RdWeightSolverWorker.unavailable();
        } finally {
            if (child != null) {
                // Only this invocation's child is terminated; never enumerate or kill JVMs by name.
                if (child.isAlive()) child.destroyForcibly();
                try { child.waitFor(500, TimeUnit.MILLISECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                closeQuietly(child.getOutputStream());
                closeQuietly(child.getInputStream());
                closeQuietly(child.getErrorStream());
            }
            if (pipes != null) pipes.shutdownNow();
        }
    }

    private static long remaining(long deadline) { return Math.max(0, deadline - System.nanoTime()); }

    private static void closeQuietly(Closeable stream) {
        try { stream.close(); }
        catch (IOException ex) { /* A failed child can already have closed its pipes. */ }
    }
}
