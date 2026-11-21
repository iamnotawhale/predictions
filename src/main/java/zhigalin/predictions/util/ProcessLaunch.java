package zhigalin.predictions.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ProcessLaunch {

    private ProcessLaunch() {
    }

    public static void preferForkLaunch() {
        if (System.getProperty("jdk.lang.Process.launchMechanism") == null) {
            System.setProperty("jdk.lang.Process.launchMechanism", "FORK");
        }
    }

    public static Process start(List<String> command) throws IOException {
        preferForkLaunch();
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("empty command");
        }
        return new ProcessBuilder(command).redirectErrorStream(true).start();
    }

    public static final class Result {
        private final int exitCode;
        private final String output;

        public Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }

        public int exitCode() {
            return exitCode;
        }

        public String output() {
            return output;
        }
    }

    public static Result run(List<String> command, long timeoutSec)
            throws IOException, InterruptedException {
        Process p = start(command);
        StringBuilder buf = new StringBuilder();
        Thread drain = new Thread(() -> {
            try {
                buf.append(new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        }, "proc-drain");
        drain.setDaemon(true);
        drain.start();
        try {
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                drain.join(1_000);
                throw new IOException("timed out after " + timeoutSec + "s: " + command.get(0));
            }
            drain.join(5_000);
            return new Result(p.exitValue(), buf.toString());
        } catch (InterruptedException e) {
            p.destroyForcibly();
            drain.join(1_000);
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    public static String runCapture(List<String> command, long timeoutSec)
            throws IOException, InterruptedException {
        Result r = run(command, timeoutSec);
        if (r.exitCode() != 0) {
            throw new IOException(command.get(0) + " exit " + r.exitCode()
                    + (r.output().isBlank() ? "" : ": " + r.output().strip()));
        }
        return r.output();
    }

    public static boolean isSpawnHelperFailure(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String m = c.getMessage();
            if (m != null && m.toLowerCase().contains("spawn helper")) {
                return true;
            }
        }
        return false;
    }
}

