package com.ruiding.erp.service.weight;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static com.ruiding.erp.service.weight.RdWeightSolverWorkerTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RdWeightWorkerProcessTest {
    @TempDir Path temp;

    @Test void productionLaunchUsesOnlyTrustedJavaAndSingleRegularBootJar() throws Exception {
        Path jar = Files.createFile(temp.resolve("server with spaces.jar"));
        Path java = currentJava();
        var builder = RdWeightWorkerProcess.createProcess(java.toString(), jar.toString(),
                Map.of("SystemRoot", "C:\\Windows", "TEMP", temp.toString(), "TMP", temp.toString(),
                        "JAVA_TOOL_OPTIONS", "-javaagent:secret", "JDK_JAVA_OPTIONS", "secret", "_JAVA_OPTIONS", "secret",
                        "RD_DB_PASSWORD", "secret", "GLM_API_KEY", "secret", "PATH", "untrusted"));
        assertEquals(List.of(java.toRealPath().toString(), "-Xmx192m", "--enable-native-access=ALL-UNNAMED",
                "-jar", jar.toRealPath().toString(), "--weight-solver-worker"), builder.command());
        assertEquals(temp.toString(), builder.environment().get("TEMP"));
        assertEquals("C:\\Windows", builder.environment().get("SystemRoot"));
        assertTrue(builder.environment().get("PATH").startsWith(java.toRealPath().getParent().toString()));
        assertTrue(builder.environment().get("PATH").contains("System32"));
        assertFalse(builder.environment().values().stream().anyMatch(value -> value.contains("secret") || value.contains("untrusted")));
        assertFalse(builder.redirectErrorStream());
        assertEquals(6000, RdWeightWorkerProcess.WATCHDOG_MILLIS);
        for (String cp : List.of("", temp.toString(), jar + File.pathSeparator + jar, temp.resolve("missing.jar").toString()))
            assertThrows(Exception.class, () -> RdWeightWorkerProcess.createProcess(java.toString(), cp, Map.of()));
        assertThrows(Exception.class, () -> RdWeightWorkerProcess.createProcess("java", jar.toString(), Map.of()));
        assertThrows(Exception.class, () -> RdWeightWorkerProcess.createProcess(temp.resolve("missing-java.exe").toString(), jar.toString(), Map.of()));
    }

    @Test void missingRuntimeOrMavenClassDirectoriesFailClosed() {
        assertEquals("UNAVAILABLE", RdWeightWorkerProcess.solve(request(), "missing", System.getProperty("java.class.path")).status());
        assertEquals("UNAVAILABLE", RdWeightWorkerProcess.solve(request(), currentJava().toString(),
                System.getProperty("java.class.path")).status());
    }

    @Test void boundedHelperAcceptsOnlyValidatedJsonAndZeroExit() {
        assertEquals("UNKNOWN", exchange("valid", 6000).status());
        for (String mode : List.of("malformed", "forged", "nonzero", "stdout-flood", "stderr-flood"))
            assertEquals("UNAVAILABLE", exchange(mode, 6000).status(), mode);
    }

    @Test void watchdogAlsoBoundsBlockedInputAndKeepsUnrelatedProcessAlive() throws Exception {
        Process unrelated = helper("hang").start();
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(4), () -> {
                assertEquals("UNAVAILABLE", exchange("hang", 300).status());
                var large = LongStream.rangeClosed(1, 500).mapToObj(id -> new RdWeightSolver.Bag(
                        id, "PK-" + id + "x".repeat(240), kg("1"), 20L, "A", 10L)).toList();
                var blocked = new RdWeightSolverWorker.Request(large, Map.of(10L, 500), 500, kg("500"), kg("0"), 2);
                assertEquals("UNAVAILABLE", RdWeightWorkerProcess.exchange(blocked, helper("no-read"), 300).status());
            });
            assertTrue(unrelated.isAlive());
        } finally {
            unrelated.destroyForcibly();
            assertTrue(unrelated.waitFor(2, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test void interruptionFailsClosedAndPreservesInterruptFlag() {
        Thread.currentThread().interrupt();
        try {
            assertEquals("UNAVAILABLE", exchange("hang", 6000).status());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test void openJdk26ExecutesRealJsonRoundtripWithoutSpring() throws Exception {
        String configured = System.getProperty("rd.weight.worker.test.java");
        assumeTrue(configured != null, "Set rd.weight.worker.test.java to the existing compatible Java");
        Path java = Path.of(configured);
        assertTrue(Files.isRegularFile(java));
        var builder = new ProcessBuilder(java.toString(), "-Xmx192m", "--enable-native-access=ALL-UNNAMED",
                "-cp", System.getProperty("java.class.path"), RdWeightSolverWorker.class.getName());
        RdWeightWorkerProcess.sanitizeEnvironment(builder, java.toRealPath(), System.getenv());
        builder.environment().put("RD_WEIGHT_SOLVER_JAVA", "must-not-recurse");
        var result = RdWeightWorkerProcess.exchange(request(), builder, 6000);
        assertEquals("OPTIMAL", result.status());
        assertEquals(List.of(1L, 2L), result.bags().stream().map(RdWeightSolver.Bag::packageId).toList());
        assertEquals(0, kg("1000").compareTo(result.totalWeightKg()));
        var impossible = new RdWeightSolverWorker.Request(request().bags(), Map.of(10L, 1), 2, kg("1000"), kg("0"), 2);
        assertEquals("INFEASIBLE", RdWeightWorkerProcess.exchange(impossible, builder, 6000).status());
        var noTime = new RdWeightSolverWorker.Request(request().bags(), request().lotCaps(), 2, kg("1000"), kg("0"), 0);
        assertEquals("UNKNOWN", RdWeightWorkerProcess.exchange(noTime, builder, 6000).status());
    }

    @Test void compiledServerEntrypointRunsWorkerBeforeSpring() throws Exception {
        String configured = System.getProperty("rd.weight.worker.test.java");
        String serverClasses = System.getProperty("rd.weight.worker.test.server.classes");
        assumeTrue(configured != null && serverClasses != null, "Supply compatible Java and previously compiled server classes");
        Path java = Path.of(configured);
        assertTrue(Files.isRegularFile(Path.of(serverClasses, "com/ruiding/erp/server/RdErpServerApplication.class")));
        var builder = new ProcessBuilder(java.toString(), "-Xmx192m", "--enable-native-access=ALL-UNNAMED", "-cp",
                serverClasses + File.pathSeparator + System.getProperty("java.class.path"),
                "com.ruiding.erp.server.RdErpServerApplication", "--weight-solver-worker");
        RdWeightWorkerProcess.sanitizeEnvironment(builder, java.toRealPath(), System.getenv());
        builder.environment().put("RD_WEIGHT_SOLVER_JAVA", "must-not-recurse");
        var result = RdWeightWorkerProcess.exchange(request(), builder, 6000);
        assertEquals("OPTIMAL", result.status());
        assertEquals(List.of(1L, 2L), result.bags().stream().map(RdWeightSolver.Bag::packageId).toList());
    }

    private RdWeightSolver.Result exchange(String mode, long timeout) {
        return RdWeightWorkerProcess.exchange(request(), helper(mode), timeout);
    }

    private ProcessBuilder helper(String mode) {
        var builder = new ProcessBuilder(currentJava().toString(), "-cp", System.getProperty("java.class.path"),
                WorkerHelper.class.getName(), mode);
        RdWeightWorkerProcess.sanitizeEnvironment(builder, currentJava(), System.getenv());
        return builder;
    }

    private static Path currentJava() {
        return Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
    }

    public static class WorkerHelper {
        public static void main(String[] args) throws Exception {
            if ("no-read".equals(args[0])) { Thread.sleep(30000); return; }
            System.in.readAllBytes();
            switch (args[0]) {
                case "hang" -> Thread.sleep(30000);
                case "malformed" -> System.out.print("not JSON");
                case "forged" -> System.out.write(RdWeightSolverWorker.encodeResult(
                        new RdWeightSolver.Result("OPTIMAL", List.of(bag(99, "1000")), kg("1000"), 1.0)));
                case "stdout-flood" -> System.out.write(padded(RdWeightSolverWorker.encodeResult(
                        new RdWeightSolver.Result("UNKNOWN", List.of(), null, null)), RdWeightSolverWorker.MAX_OUTPUT_BYTES + 1));
                case "stderr-flood" -> {
                    System.err.write(new byte[RdWeightWorkerProcess.MAX_STDERR_BYTES + 1]);
                    System.out.write(RdWeightSolverWorker.encodeResult(new RdWeightSolver.Result("UNKNOWN", List.of(), null, null)));
                }
                case "nonzero" -> { System.out.write(RdWeightSolverWorker.encodeResult(
                        new RdWeightSolver.Result("UNKNOWN", List.of(), null, null))); System.exit(7); }
                default -> System.out.write(RdWeightSolverWorker.encodeResult(new RdWeightSolver.Result("UNKNOWN", List.of(), null, null)));
            }
        }
    }
}
