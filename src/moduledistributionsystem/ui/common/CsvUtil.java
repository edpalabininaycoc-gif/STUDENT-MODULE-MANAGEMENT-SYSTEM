package moduledistributionsystem.ui.common;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes CSV files (the "export to spreadsheet" feature) for the System Activity log and the Reports page.
 *
 * <p>Two safety details: every value is wrapped in quotes (so commas and line breaks inside a value are safe),
 * and a value that starts with = + - or @ gets an apostrophe in front, because Excel would otherwise run it as a
 * formula (CSV / formula injection).
 */
public final class CsvUtil {

    /** Utility class: only static methods, so it is never instantiated. */
    private CsvUtil() { }

    /** One CSV cell: quoted, inner quotes doubled, formula starters neutralized. */
    public static String cell(String value) {
        String s = value == null ? "" : value;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** One CSV line made of the given values. */
    public static String line(String... values) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) b.append(',');
            b.append(cell(values[i]));
        }
        return b.toString();
    }

    /**
     * Saves a header row and the data rows to a file. The file starts with a UTF-8 byte order mark so that Excel
     * shows accents and symbols correctly.
     *
     * @throws IOException when the file can not be written
     */
    public static void write(File file, String[] header, List<String[]> rows) throws IOException {
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            w.print('\uFEFF');
            w.println(line(header));
            for (String[] row : rows) w.println(line(row));
        }
    }
}
