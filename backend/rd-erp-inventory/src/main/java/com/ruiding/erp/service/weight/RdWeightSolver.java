package com.ruiding.erp.service.weight;

import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.LinearExpr;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.VersionUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Component
public class RdWeightSolver {
    public static final BigDecimal MAX_WEIGHT_KG = new BigDecimal("1000000");
    public record Bag(Long packageId, String packageCode, BigDecimal packageWeightKg,
                      Long locationId, String locationCode, Long stockLotId) { }
    public record Result(String status, List<Bag> bags, BigDecimal totalWeightKg, Double objectiveBound) { }

    public static long grams(BigDecimal kg) {
        if (kg == null || kg.signum() < 0 || kg.compareTo(MAX_WEIGHT_KG) > 0)
            throw new IllegalArgumentException("Weight outside supported range");
        try { return kg.movePointRight(3).longValueExact(); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Weight requires exact 0.001 kg precision", ex); }
    }

    static boolean compatibleMsvcVersion(int major, int minor) { return major > 14 || major == 14 && minor >= 40; }

    static boolean nativeRuntimeSupported() {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return true;
        try {
            // Old JDK-bundled MSVC mutex implementations can crash the JVM, not throw a Java exception.
            // Read the actually loaded DLL (OR-Tools issue #4690); never replace runtime files.
            var module = Kernel32.INSTANCE.GetModuleHandle("msvcp140.dll");
            if (module == null) return false;
            char[] path = new char[32768];
            int length = Psapi.INSTANCE.GetModuleFileNameExW(Kernel32.INSTANCE.GetCurrentProcess(), module, path, path.length);
            if (length <= 0 || length >= path.length) return false;
            var version = VersionUtil.getFileVersionInfo(Native.toString(path));
            return compatibleMsvcVersion(version.getFileVersionMajor(), version.getFileVersionMinor());
        } catch (LinkageError | RuntimeException ex) { return false; }
    }

    public Result solve(List<Bag> bags, Map<Long, Integer> lotCaps, int maxCount,
                        BigDecimal targetKg, BigDecimal toleranceKg, double seconds) {
        var request = new RdWeightSolverWorker.Request(bags, lotCaps, maxCount, targetKg, toleranceKg, seconds);
        String workerJava = System.getenv("RD_WEIGHT_SOLVER_JAVA");
        if (workerJava != null)
            return RdWeightWorkerProcess.solve(request, workerJava, System.getProperty("java.class.path", ""));
        return solveLocally(request);
    }

    static long[] validateInput(List<Bag> bags, Map<Long, Integer> lotCaps, int maxCount,
                                BigDecimal targetKg, BigDecimal toleranceKg, double seconds) {
        long target = grams(targetKg);
        long tolerance = grams(toleranceKg);
        if (bags == null || lotCaps == null || bags.size() > 500 || lotCaps.size() > 500 ||
                maxCount < 1 || maxCount > 500 || target <= tolerance ||
                seconds < 0 || seconds > 2 || !Double.isFinite(seconds)) throw new IllegalArgumentException("Invalid solver budget");
        for (var entry : lotCaps.entrySet())
            if (entry.getKey() == null || entry.getKey() <= 0 || entry.getValue() == null || entry.getValue() < 0)
                throw new IllegalArgumentException("Invalid lot capacity");
        var ids = new HashSet<Long>();
        var codes = new HashSet<String>();
        long[] weights = new long[bags.size()];
        for (int i = 0; i < bags.size(); i++) {
            Bag bag = bags.get(i);
            if (bag == null || bag.packageId() == null || bag.packageId() <= 0 ||
                    bag.packageCode() == null || bag.packageCode().isBlank() || bag.packageCode().length() > 256 ||
                    bag.locationCode() != null && bag.locationCode().length() > 256 ||
                    !ids.add(bag.packageId()) || !codes.add(bag.packageCode()) ||
                    bag.stockLotId() == null || !lotCaps.containsKey(bag.stockLotId()))
                throw new IllegalArgumentException("Invalid candidate identity or capacity");
            weights[i] = grams(bag.packageWeightKg());
            if (weights[i] <= 0) throw new IllegalArgumentException("Unknown bag weight");
        }
        return weights;
    }

    Result solveLocally(RdWeightSolverWorker.Request request) {
        var bags = request.bags();
        var lotCaps = request.lotCaps();
        int maxCount = request.maxCount();
        long target = grams(request.targetKg());
        long tolerance = grams(request.toleranceKg());
        long[] weights = validateInput(bags, lotCaps, maxCount, request.targetKg(), request.toleranceKg(), request.seconds());
        long sum = 0;
        for (long weight : weights) sum = Math.addExact(sum, weight);
        if (!nativeRuntimeSupported()) return new Result("UNAVAILABLE", List.of(), null, null);
        try { Loader.loadNativeLibraries(); }
        catch (LinkageError | RuntimeException ex) { return new Result("UNAVAILABLE", List.of(), null, null); }

        CpModel model = new CpModel();
        BoolVar[] selected = new BoolVar[bags.size()];
        for (int i = 0; i < selected.length; i++) selected[i] = model.newBoolVar("bag_" + bags.get(i).packageId());
        var total = model.newIntVar(0, sum, "total_grams");
        model.addEquality(total, LinearExpr.weightedSum(selected, weights));
        model.addLinearConstraint(total, target - tolerance, target + tolerance);
        model.addLessOrEqual(LinearExpr.sum(selected), maxCount);
        for (var entry : lotCaps.entrySet()) {
            var count = LinearExpr.newBuilder();
            for (int i = 0; i < bags.size(); i++)
                if (entry.getKey().equals(bags.get(i).stockLotId())) count.add(selected[i]);
            model.addLessOrEqual(count, entry.getValue());
        }
        var deviation = model.newIntVar(0, Math.max(sum, target), "absolute_deviation_grams");
        model.addAbsEquality(deviation, LinearExpr.affine(total, 1, -target));
        // Lexicographic objective: one gram of deviation outweighs every possible bag-count tie.
        model.minimize(LinearExpr.newBuilder().addTerm(deviation, bags.size() + 1).addSum(selected));
        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(request.seconds()).setMaxDeterministicTime(1)
                .setMaxMemoryInMb(128).setNumSearchWorkers(1).setRandomSeed(1);
        CpSolverStatus status = solver.solve(model);
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE)
            return new Result(status.name(), List.of(), null, null);
        var chosen = new ArrayList<Bag>();
        for (int i = 0; i < selected.length; i++) if (solver.booleanValue(selected[i])) chosen.add(bags.get(i));
        return new Result(status.name(), List.copyOf(chosen), BigDecimal.valueOf(solver.value(total), 3), solver.bestObjectiveBound());
    }
}
