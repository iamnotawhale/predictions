package zhigalin.predictions.util;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.helper.Compressor;
import org.springframework.util.StringUtils;

public class CustomTimeBasedRollingPolicy<E> extends TimeBasedRollingPolicy<E> {
    public static final String NOT_VALID_DATE_TOKEN = "FileNamePattern does not contain a valid DateToken";

    @Override
    public void start() {
        super.start();
        compressOldLogs();
    }

    private void compressOldLogs() {
        LocalDate currentDate = LocalDate.now();
        Path logDir = Path.of(this.fileNamePatternStr).getParent();
        int indexOfStartSpecifier;
        String patternOfFileName;
        if (logDir == null) {
            logDir = Path.of("." + File.separator);
            indexOfStartSpecifier = getIndexAndAssert(this.fileNamePatternStr);
            patternOfFileName = this.fileNamePatternStr.substring(0, indexOfStartSpecifier);
        } else {
            String patternOfFileNameWithDate = String.valueOf(Path.of(this.fileNamePatternStr).getFileName());
            indexOfStartSpecifier = getIndexAndAssert(patternOfFileNameWithDate);
            patternOfFileName = patternOfFileNameWithDate.substring(0, indexOfStartSpecifier);
        }
        String patternOfDate = getPattern(indexOfStartSpecifier);
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(patternOfDate);
        File[] files = new File(logDir.toUri()).listFiles(((dir, name) ->
                (name.contains(patternOfFileName) && !Objects.equals(StringUtils.getFilenameExtension(name), "gz"))));
        if (files != null) {
            for (File file : files) {
                String dateOfFileStr = file.getName().substring(patternOfFileName.length(), file.getName().length() - 4);
                LocalDate dateOfFile = LocalDate.parse(dateOfFileStr, dateFormat);
                if (currentDate.isAfter(dateOfFile)) {
                    Compressor compressor = new Compressor(this.compressionMode);
                    compressor.setContext(this.context);
                    Path path = Path.of(String.valueOf(logDir), file.getName());
                    compressor.asyncCompress(String.valueOf(path), String.valueOf(path), file.getName());
                }
            }
        }
    }

    private String getPattern(int indexOfStartSpecifier) {
        int indexOfStartDatePattern = this.fileNamePatternStr.indexOf('{', indexOfStartSpecifier);
        int indexOfEndDatePattern = this.fileNamePatternStr.indexOf('}', indexOfStartSpecifier);
        String patternOfDate;
        if (indexOfStartDatePattern == -1 && indexOfEndDatePattern == -1) {
            patternOfDate = "yyyy-MM-dd";
        } else {
            assert indexOfStartDatePattern != -1 : NOT_VALID_DATE_TOKEN;
            assert indexOfEndDatePattern != -1 : NOT_VALID_DATE_TOKEN;
            patternOfDate = this.fileNamePatternStr.substring(indexOfStartDatePattern + 1, indexOfEndDatePattern);
        }
        return patternOfDate;
    }

    private int getIndexAndAssert(String str) {
        int index = str.indexOf("%d");
        assert index != -1 : NOT_VALID_DATE_TOKEN;
        return index;
    }

}
