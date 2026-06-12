import java.util.*;
import java.util.concurrent.*;

public class PointInPolygon {

    record Point(double x, double y) {
    }

    public static boolean isInsideSequential(List<Point> polygon, Point p) {
        int n = polygon.size();
        boolean inside = false;
        int j = n - 1;
        for (int i = 0; i < n; i++) {
            Point vi = polygon.get(i);
            Point vj = polygon.get(j);

            // kesisim kontrolu
            if ((vi.y() > p.y()) != (vj.y() > p.y())) {
                double xIntersect = (vj.x() - vi.x()) * (p.y() - vi.y())
                        / (vj.y() - vi.y()) + vi.x();
                if (p.x() < xIntersect) {
                    inside = !inside;
                }
            }
            j = i;
        }
        return inside;
    }

    public static Map<Integer, Boolean> testPointsSequential(
            List<Point> polygon, List<Point> queryPoints) {
        Map<Integer, Boolean> results = new HashMap<>();
        for (int i = 0; i < queryPoints.size(); i++) {
            results.put(i, isInsideSequential(polygon, queryPoints.get(i)));
        }
        return results;
    }

    static class PolygonWorker extends Thread {
        private final List<Point> polygon;
        private final List<Point> queryPoints;
        private final int startIdx;
        private final int endIdx; // dahil degil
        private final ConcurrentHashMap<Integer, Boolean> results;

        PolygonWorker(List<Point> polygon, List<Point> queryPoints,
                int startIdx, int endIdx,
                ConcurrentHashMap<Integer, Boolean> results) {
            this.polygon = polygon;
            this.queryPoints = queryPoints;
            this.startIdx = startIdx;
            this.endIdx = endIdx;
            this.results = results;
        }

        @Override
        public void run() {
            for (int i = startIdx; i < endIdx; i++) {
                results.put(i, isInsideSequential(polygon, queryPoints.get(i)));
            }
        }
    }

    public static Map<Integer, Boolean> testPointsParallel(
            List<Point> polygon, List<Point> queryPoints, int numThreads)
            throws InterruptedException {

        ConcurrentHashMap<Integer, Boolean> results = new ConcurrentHashMap<>();
        int total = queryPoints.size();
        int chunk = (total + numThreads - 1) / numThreads;

        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < numThreads; t++) {
            int start = t * chunk;
            int end = Math.min(start + chunk, total);
            if (start >= total)
                break;
            PolygonWorker worker = new PolygonWorker(
                    polygon, queryPoints, start, end, results);
            threads.add(worker);
            worker.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        return results;
    }

    static List<Point> randomPoints(int count, double range, Random rng) {
        List<Point> pts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            pts.add(new Point(
                    (rng.nextDouble() * 2 - 1) * range,
                    (rng.nextDouble() * 2 - 1) * range));
        }
        return pts;
    }

    static void runBenchmark(List<Point> polygon, int[] pointCounts, int[] threadCounts) throws InterruptedException {

        Random rng = new Random(42);
        System.out.printf("%-9s %-10s %-16s %-14s %-11s %-10s%n",
                "Points", "Threads", "Sequential(ms)", "Parallel(ms)", "Speedup", "Seq = Par");
        System.out.println("-".repeat(75));

        for (int pc : pointCounts) {
            List<Point> queryPoints = randomPoints(pc, 1.5, rng);

            // seri islem
            long seqStart = System.nanoTime();
            Map<Integer, Boolean> seqResult = testPointsSequential(polygon, queryPoints);
            long seqTime = System.nanoTime() - seqStart;
            double seqMs = seqTime / 1_000_000.0;

            // paralel compute
            for (int tc : threadCounts) {
                long parStart = System.nanoTime();
                Map<Integer, Boolean> parResult = testPointsParallel(polygon, queryPoints, tc);
                long parTime = System.nanoTime() - parStart;
                double parMs = parTime / 1_000_000.0;

                boolean match = seqResult.equals(parResult);
                double speedup = seqMs / parMs;

                System.out.printf("%-12d %-10d %-14.3f %-14.3f %-12.3f %s%n",
                        pc, tc, seqMs, parMs, speedup, match ? "MATCH" : "MISMATCH!");
            }
        }
    }

    static List<Point> squarePolygon() {
        return List.of(
                new Point(-1, -1),
                new Point(1, -1),
                new Point(1, 1),
                new Point(-1, 1));
    }

    // duzenli convex sekizgen
    static List<Point> octagonPolygon() {
        List<Point> pts = new ArrayList<>();
        int sides = 8;
        for (int i = 0; i < sides; i++) {
            double angle = 2 * Math.PI * i / sides - Math.PI / 2; // start at top
            pts.add(new Point(Math.cos(angle), Math.sin(angle)));
        }
        return pts;
    }

    // duzensiz konkav yildiz
    static List<Point> concaveStarPolygon() {
        // 8-kose
        double[][] v = {
                { 0.0, 1.0 }, { 0.2, 0.3 }, { 1.0, 0.0 }, { 0.2, -0.3 },
                { 0.0, -1.0 }, { -0.2, -0.3 }, { -1.0, 0.0 }, { -0.2, 0.3 }
        };
        List<Point> pts = new ArrayList<>();
        for (double[] vv : v)
            pts.add(new Point(vv[0], vv[1]));
        return pts;
    }

    // L-shape konkav 
    static List<Point> lShapePolygon() {
        return List.of(
                new Point(0, 0),
                new Point(2, 0),
                new Point(2, 1),
                new Point(1, 1),
                new Point(1, 2),
                new Point(0, 2));
    }

    static void demo() throws InterruptedException {
        System.out.println("=== Point-in-Polygon Demo ===\n");

        // ---- Convex polygon: square [-1,1] x [-1,1] ----
        List<Point> square = squarePolygon();
        System.out.println("Polygon: Unit Square (convex)");
        System.out.println("  Vertices: (-1,-1), (1,-1), (1,1), (-1,1)");

        List<Point> testPts = List.of(
                new Point(0.0, 0.0), // inside
                new Point(0.5, 0.5), // inside
                new Point(1.5, 0.0), // outside
                new Point(-1.5, -1.5), // outside
                new Point(1.0, 1.0) // on edge/corner (boundary)
        );

        System.out.println("\nSequential results:");
        for (int i = 0; i < testPts.size(); i++) {
            Point p = testPts.get(i);
            boolean r = isInsideSequential(square, p);
            System.out.printf("  Point(%.1f, %.1f) -> %s%n", p.x(), p.y(), r ? "INSIDE" : "OUTSIDE");
        }

        Map<Integer, Boolean> parResults = testPointsParallel(square, testPts, 2);
        System.out.println("\nParallel results (2 threads):");
        for (int i = 0; i < testPts.size(); i++) {
            Point p = testPts.get(i);
            System.out.printf("  Point(%.1f, %.1f) -> %s%n",
                    p.x(), p.y(), parResults.get(i) ? "INSIDE" : "OUTSIDE");
        }

        // ---- Convex polygon: regular octagon inscribed in unit circle ----
        List<Point> octagon = octagonPolygon();
        System.out.println("\nPolygon: Regular Octagon (convex, 8 vertices)");
        List<Point> octPts = List.of(
                new Point(0.0, 0.0), // center - inside
                new Point(0.0, 0.95), // near top vertex - inside
                new Point(0.85, 0.85), // outside octagon (corner cut)
                new Point(1.5, 0.0), // far outside
                new Point(0.6, 0.6) // inside
        );
        System.out.println("Sequential results:");
        for (Point p : octPts) {
            System.out.printf("  Point(%.2f, %.2f) -> %s%n",
                    p.x(), p.y(), isInsideSequential(octagon, p) ? "INSIDE" : "OUTSIDE");
        }
        Map<Integer, Boolean> octParResults = testPointsParallel(octagon, octPts, 2);
        System.out.println("Parallel results (2 threads):");
        for (int i = 0; i < octPts.size(); i++) {
            Point p = octPts.get(i);
            System.out.printf("  Point(%.2f, %.2f) -> %s%n",
                    p.x(), p.y(), octParResults.get(i) ? "INSIDE" : "OUTSIDE");
        }

        // Concave polygon: star
        List<Point> star = concaveStarPolygon();
        System.out.println("\nPolygon: 8-Point Star (concave)");
        List<Point> starPts = List.of(
                new Point(0.0, 0.0), // center - inside
                new Point(0.0, 0.95), // tip - inside
                new Point(0.5, 0.5), // arm recess - outside
                new Point(0.15, 0.0) // just inside arm
        );
        System.out.println("Sequential results:");
        for (Point p : starPts) {
            System.out.printf("  Point(%.2f, %.2f) -> %s%n",
                    p.x(), p.y(), isInsideSequential(star, p) ? "INSIDE" : "OUTSIDE");
        }

        // L-Shape concave
        List<Point> lShape = lShapePolygon();
        System.out.println("\nPolygon: L-Shape (concave)");
        System.out.println("  Vertices: (0,0)-(2,0)-(2,1)-(1,1)-(1,2)-(0,2)");
        List<Point> lPts = List.of(
                new Point(0.5, 0.5), // inside bottom part
                new Point(0.5, 1.5), // inside top part
                new Point(1.5, 1.5), // outside (notch)
                new Point(1.5, 0.5) // inside bottom-right
        );
        System.out.println("Sequential results:");
        for (Point p : lPts) {
            System.out.printf("  Point(%.1f, %.1f) -> %s%n",
                    p.x(), p.y(), isInsideSequential(lShape, p) ? "INSIDE" : "OUTSIDE");
        }
    }

    public static void main(String[] args) throws InterruptedException {

        demo();

        System.out.println("\n\n=== Benchmark: Convex Square (4 vertices) ===\n");
        runBenchmark(
                squarePolygon(),
                new int[] { 100, 500, 1_000, 5_000, 10_000, 50_000, 100_000, 500_000 },
                new int[] { 2, 4, 8 });

        System.out.println("\n=== Benchmark: Convex Regular Octagon (8 vertices) ===\n");
        runBenchmark(
                octagonPolygon(),
                new int[] { 100, 500, 1_000, 5_000, 10_000, 50_000, 100_000, 500_000 },
                new int[] { 2, 4, 8 });

        System.out.println("\n=== Benchmark: Concave Star (8 vertices) ===\n");
        runBenchmark(
                concaveStarPolygon(),
                new int[] { 100, 500, 1_000, 5_000, 10_000, 50_000, 100_000, 500_000 },
                new int[] { 2, 4, 8 });

        System.out.println("\n=== Benchmark: Concave L-Shape (6 vertices) ===\n");
        runBenchmark(
                lShapePolygon(),
                new int[] { 100, 500, 1_000, 5_000, 10_000, 50_000, 100_000, 500_000 },
                new int[] { 2, 4, 8 });
    }
}
