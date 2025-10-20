package common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import prism.PrismLog;

/**
 * Singleton helper for collecting timing information across PRISM components.
 */
public final class TimingReporter
{
	private static final TimingReporter INSTANCE = new TimingReporter();
	private static final boolean WRITE_JSON = true; // flip to true to emit JSON reports
	private static final String JSON_FILE_NAME = "explicit-mdp-ltl-prism-ldbas.json";

	private final Map<String, StepStats> steps = new LinkedHashMap<>();
	private final Map<String, Long> runningSteps = new LinkedHashMap<>();
	private boolean sessionActive = false;

	private TimingReporter()
	{
	}

	public static TimingReporter getInstance()
	{
		return INSTANCE;
	}

	public synchronized void startSession()
	{
		steps.clear();
		runningSteps.clear();
		sessionActive = true;
	}

	public synchronized void startStep(String name)
	{
		if (!sessionActive) {
			startSession();
		}
		runningSteps.put(name, System.nanoTime());
	}

	public synchronized void stopStep(String name)
	{
		Long start = runningSteps.remove(name);
		if (start == null) {
			return;
		}
		long duration = System.nanoTime() - start;
		steps.computeIfAbsent(name, StepStats::new).add(duration);
	}

	public synchronized void addDuration(String name, long durationNanos)
	{
		steps.computeIfAbsent(name, StepStats::new).add(durationNanos);
	}

	public synchronized void publishAndReset(PrismLog log)
	{
		if (!sessionActive) {
			return;
		}
		printReport(log);
		writeJsonIfEnabled(log);
		steps.clear();
		runningSteps.clear();
		sessionActive = false;
	}

	public synchronized void printReport(PrismLog log)
	{
		if (steps.isEmpty()) {
			return;
		}
		if (log != null) {
			log.println("\nTiming report (explicit MDP LTL):");
		}
		for (StepStats stats : steps.values()) {
			String message = String.format("  %-35s %10.3f ms",
					stats.name, stats.getTotalMillis());
			if (log != null) {
				log.println(message);
			} else {
				System.out.println(message);
			}
		}
		// write total time
		long totalNanos = steps.values().stream().mapToLong(s -> s.totalNanos).sum();
		String totalMessage = String.format("  %-35s %10.3f ms",
				"Total", totalNanos / 1_000_000.0);
		if (log != null) {
			log.println(totalMessage);
		} else {
			System.out.println(totalMessage);
		}
	}

	private void writeJsonIfEnabled(PrismLog log)
	{
		if (!WRITE_JSON || steps.isEmpty()) {
			return;
		}
		Path path = Paths.get(JSON_FILE_NAME);
		try {
			String entry = buildJsonEntry();
			String content;
			if (Files.exists(path)) {
				content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
				if (content.isEmpty()) {
					content = "[]";
				}
			} else {
				content = "[]";
			}
			String updated = appendJsonEntry(content, entry);
			Files.write(path, updated.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			if (log != null) {
				log.printWarning("Failed to write timing JSON: " + e.getMessage());
			}
		}
	}

	private String buildJsonEntry()
	{
		StringBuilder sb = new StringBuilder();
		sb.append('{');
		sb.append("\"timestamp\":").append(System.currentTimeMillis()).append(',');
		sb.append("\"steps\":[");
		boolean first = true;
		for (StepStats stats : steps.values()) {
			if (!first) {
				sb.append(',');
			}
			sb.append('{');
			sb.append("\"name\":\"").append(escape(stats.name)).append("\",");
			sb.append("\"time\":").append(String.format("%.6f", stats.getTotalMillis()));
			sb.append('}');
			first = false;
		}
		sb.append(']');
		sb.append('}');
		return sb.toString();
	}

	private String appendJsonEntry(String existing, String entry)
	{
		String trimmed = existing.trim();
		if (!trimmed.startsWith("[")) {
			trimmed = "[]";
		}
		if (trimmed.length() == 2) {
			return "[" + entry + "]";
		}
		return trimmed.substring(0, trimmed.length() - 1) + "," + entry + "]";
	}

	private String escape(String value)
	{
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static final class StepStats
	{
		private final String name;
		private long totalNanos;

		private StepStats(String name)
		{
			this.name = Objects.requireNonNull(name);
		}

		private void add(long duration)
		{
			totalNanos += duration;
		}

		private double getTotalMillis()
		{
			return totalNanos / 1_000_000.0;
		}
	}
}
