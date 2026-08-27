package zhigalin.predictions.config;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import zhigalin.predictions.service.api.ApiClient;
import zhigalin.predictions.util.AppTimeZones;

@Component
public class StartupNotifier {

    private static final Logger log = LoggerFactory.getLogger("server");
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private final ApiClient apiClient;
    private final Environment environment;
    private final ObjectProvider<GitProperties> gitProperties;
    private final String adminChatId;
    private final String webAppUrl;
    private final int serverPort;

    public StartupNotifier(
            ApiClient apiClient,
            Environment environment,
            ObjectProvider<GitProperties> gitProperties,
            @Value("${chatId:}") String adminChatId,
            @Value("${bot.webAppUrl:}") String webAppUrl,
            @Value("${server.port:8080}") int serverPort
    ) {
        this.apiClient = apiClient;
        this.environment = environment;
        this.gitProperties = gitProperties;
        this.adminChatId = adminChatId == null ? "" : adminChatId.trim();
        this.webAppUrl = webAppUrl == null ? "" : webAppUrl.trim();
        this.serverPort = serverPort;
    }

    @Order(300)
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (adminChatId.isBlank() || "0".equals(adminChatId)) {
            log.info("Startup notify skipped: chatId/ADMIN_CHAT_ID not set");
            return;
        }
        try {
            String text = buildMessage();
            boolean ok = apiClient.sendMessage(adminChatId, text, null);
            if (ok) {
                log.info("Startup notify sent to admin chat");
            } else {
                log.warn("Startup notify failed to send");
            }
        } catch (Exception e) {
            log.warn("Startup notify error: {}", e.getMessage());
        }
    }

    private String buildMessage() {
        ZoneId zone = AppTimeZones.DISPLAY;
        ZonedDateTime now = ZonedDateTime.now(zone);
        String profiles = String.join(",", environment.getActiveProfiles());
        if (profiles.isBlank()) {
            profiles = "(default)";
        }

        String host = hostname();
        String ip = hostAddress();
        String user = Optional.ofNullable(System.getProperty("user.name")).orElse("?");
        String javaVer = Optional.ofNullable(System.getProperty("java.version")).orElse("?");
        String os = Optional.ofNullable(System.getProperty("os.name")).orElse("?")
                + " " + Optional.ofNullable(System.getProperty("os.arch")).orElse("");
        long pid = ProcessHandle.current().pid();
        long uptimeSec = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        String cwd = Optional.ofNullable(System.getProperty("user.dir")).orElse("?");

        GitProperties git = gitProperties.getIfAvailable();
        String commit = git != null ? abbreviate(git.getShortCommitId(), git.getCommitId()) : "unknown";
        String branch = git != null ? nullToDash(git.getBranch()) : "-";
        String commitTime = "-";
        String dirty = "-";
        if (git != null) {
            try {
                if (git.getCommitTime() != null) {
                    commitTime = TS.format(git.getCommitTime().atZone(zone));
                }
            } catch (Exception ignored) {
                // older git.properties may lack commit time
            }
            dirty = Boolean.parseBoolean(git.get("dirty")) ? "yes" : "no";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🚀 Predictions started\n");
        sb.append("time: ").append(TS.format(now)).append('\n');
        sb.append("host: ").append(host);
        if (!ip.isBlank() && !ip.equals(host)) {
            sb.append(" (").append(ip).append(')');
        }
        sb.append('\n');
        sb.append("user: ").append(user).append("  pid: ").append(pid).append('\n');
        sb.append("profile: ").append(profiles).append("  port: ").append(serverPort).append('\n');
        sb.append("commit: ").append(commit);
        if (!"-".equals(branch)) {
            sb.append("  branch: ").append(branch);
        }
        sb.append('\n');
        sb.append("commitTime: ").append(commitTime).append("  dirty: ").append(dirty).append('\n');
        sb.append("java: ").append(javaVer).append('\n');
        sb.append("os: ").append(os.trim()).append('\n');
        sb.append("cwd: ").append(cwd).append('\n');
        sb.append("boot: ").append(uptimeSec).append("s\n");
        if (!webAppUrl.isBlank()) {
            sb.append("webApp: ").append(webAppUrl).append('\n');
        }
        return sb.toString().trim();
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return Optional.ofNullable(System.getenv("HOSTNAME")).orElse("unknown");
        }
    }

    private static String hostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "";
        }
    }

    private static String abbreviate(String shortId, String fullId) {
        if (shortId != null && !shortId.isBlank()) {
            return shortId;
        }
        if (fullId != null && fullId.length() >= 7) {
            return fullId.substring(0, 7);
        }
        return fullId != null && !fullId.isBlank() ? fullId : "unknown";
    }

    private static String nullToDash(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }
}
