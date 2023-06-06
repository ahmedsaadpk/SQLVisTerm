package io.github.sqlvisterm;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.sql.Types.*;

public class SQLEngine {
    private String HOST, USER, PASS;

    private final String alignLeft = "%-[N]s";

    private final int _BOOL_ = 0;
    private final int _INT_ = 1;
    private final int _REAL_ = 2;
    private final int _CHAR_ = 3;
    private final int _OTHER_ = -1;

    private final int _MIN_ = 0;
    private final int _MAX_ = 1;
    private final int _MEAN_ = 2;
    private final int _STDEV_ = 3;
    private final int _VARIANCE_ = 4;
    private final int _P_VARIANCE_ = 5;

    private Connection conn;
    private Statement stmt;
    private Terminal terminal;

    private Set<String> setEx;

    private String lastSQL;
    private QueryResult qr;
    private Map<String, Float> scale = new HashMap<>();
    private Set<String> hidden = new HashSet<>();
    private boolean scaleLocked, hiddenLocked, statsOn;

    private PrintStream log;

    public SQLEngine(Terminal terminal) {
        this.terminal = terminal;
        Properties ini = new Properties();
        try (FileReader reader = new FileReader("app.ini");) {
            ini.load(reader);
            HOST = ini.getProperty("host");
            USER = ini.getProperty("user");
            PASS = ini.getProperty("password");
            setEx = Arrays.stream(ini.getProperty("exclude", "").split(",")).collect(Collectors.toSet());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            log = new PrintStream(new BufferedOutputStream(new FileOutputStream("query.log", true)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            conn = DriverManager.getConnection(HOST, USER, PASS);
            stmt = conn.createStatement();
            System.out.println("Connected to " + HOST);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void scale(String[] argv) {
        try {
            if (argv.length == 1) {                     // change scale of all columns
                float s = Float.parseFloat(argv[0]);
                scale.keySet().stream().filter(col -> !exclude(col)).forEach(col -> scale.put(col, s));
            } else if (argv.length == 2) {              // change scale of given column
                String col = argv[0].toLowerCase();
                if (!exclude(col)) scale.put(col, Float.parseFloat(argv[1]));
            }
        } catch (NumberFormatException e) {
            println(e.getMessage());
        }
        processInput(lastSQL);
    }

    public void lock(String[] argv) {
        if (argv.length == 1) {
            switch (argv[0].toLowerCase()) {
                case "scale":
                    scaleLocked = true;
                    printf("Scale is locked. It will remain same during query modifications until column names are changed (or use \"unlock scale\" command)%n%n");
                    break;
                case "hidden":
                    hiddenLocked = true;
                    printf("Hidden columns are locked. They will remain hidden during query modifications until column names are changed (or use \"unlock hidden\" command)%n%n");
                    break;
            }
        }
    }

    public void unlock(String[] argv) {
        if (argv.length == 1) {
            switch (argv[0].toLowerCase()) {
                case "scale":
                    scaleLocked = false;
                    printf("Scale is unlocked%n%n");
                    break;
                case "hidden":
                    hiddenLocked = false;
                    printf("Hidden columns are unlocked%n%n");
                    break;
            }
        }
    }

    public void hide(String[] argv) {
        for (int i = 0; i < argv.length; i++) {
            hidden.add(argv[i].toLowerCase());
        }
        processInput(lastSQL);
    }

    public void unhide(String[] argv) {
        if ("all".equalsIgnoreCase(argv[0])) {
            hiddenLocked = false;
            hidden.clear();
            processInput(lastSQL);
        } else {
            for (int i = 0; i < argv.length; i++) {
                hidden.remove(argv[i].toLowerCase());
            }
            if (hidden.size() == 0) hiddenLocked = false;
            processInput(lastSQL);
        }
    }

    public void stats(String[] argv) {
        if (argv.length == 0) {
            processInput(lastSQL, true);
        } else if (argv.length == 1) {
            if ("on".equalsIgnoreCase(argv[0])) {
                statsOn = true;
                processInput(lastSQL, true);
            } else if ("off".equalsIgnoreCase(argv[0])) {
                statsOn = false;
                processInput(lastSQL, false);
            }
        }
    }

    public void processInput(final String line) {
        processInput(line, statsOn);
    }

    public void processInput(final String line, final boolean showStats) {
        if (line == null || "".equals(line)) return;
        boolean newQuery = !line.equals(lastSQL);

        ResultSet rs = null;
        try {
            if (line.startsWith("//")) {
                log.println(line);
                for (int i = 0; i < line.length(); i++) {
                    log.print("-");
                }
                log.println();
                log.println();
            } else {
                if (newQuery) {
                    log.println(line);
                    rs = stmt.executeQuery(line);
                    qr = new QueryResult(rs);
                    lastSQL = line;
                }
                qr.render(showStats);
            }
        } catch (SQLException e) {
            println(e.getMessage());
        } finally {
            if (rs != null) try {
                rs.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void shutdown() {
        try {
            if (stmt != null) stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        try {
            if (conn != null) conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (log != null) {
            log.flush();
            log.close();
        }
    }

    private int getNextBarColor(int color) {
        return (color == AttributedStyle.CYAN) ? AttributedStyle.RED : color + 1;
    }

    private boolean exclude(String c) {
        return setEx.contains(c.toLowerCase());
    }

    private int getColumnType(int t) {
        if (isBool(t)) return _BOOL_;
        else if (isInt(t)) return _INT_;
        else if (isReal(t)) return _REAL_;
        else if (isChar(t)) return _CHAR_;
        else return _OTHER_;
    }

    private boolean isBool(int t) {
        switch (t) {
            case BIT:
            case BOOLEAN:
            case BINARY:
                return true;
            default:
                return false;
        }
    }

    private boolean isInt(int t) {
        switch (t) {
            case SMALLINT:
            case TINYINT:
            case INTEGER:
            case BIGINT:
                return true;
            default:
                return false;
        }
    }

    private boolean isReal(int t) {
        switch (t) {
            case FLOAT:
            case DOUBLE:
            case DECIMAL:
            case NUMERIC:
            case REAL:
                return true;
            default:
                return false;
        }
    }

    private boolean isChar(int t) {
        switch (t) {
            case CHAR:
            case NCHAR:
            case VARCHAR:
            case NVARCHAR:
            case LONGVARCHAR:
            case LONGNVARCHAR:
                return true;
            default:
                return false;
        }
    }

    private void print(String s) {
        if (terminal != null) {
            terminal.writer().print(s);
            log.print(s);
        } else {
            System.out.print(s);
        }
    }

    private void println() {
        if (terminal != null) {
            terminal.writer().println();
            log.println();
        } else {
            System.out.println();
        }
    }

    private void println(String s) {
        if (terminal != null) {
            terminal.writer().println(s);
            log.println(s);
        } else {
            System.out.println(s);
        }
    }

    private void printf(String f, Object... args) {
        if (terminal != null) {
            terminal.writer().printf(f, args);
            log.printf(f, args);
        } else {
            System.out.printf(f, args);
        }
    }

    public static void main(String[] args) {
        int[] numbers = new int[10];
        for (int i = 0; i < numbers.length; i++) {
            numbers[i] = i;
        }
        Arrays.stream(numbers).filter(n -> n < 5).forEach(n -> System.out.println(n));
    }

    private class QueryResult {
        String[] cols;
        int[] type;
        int[] dataWidth;
        int[] colWidth;
        int[] minBarLen;
        long[] iMin;
        long[] iMax;
        double[] rMin;
        double[] rMax;
        double[] interval;
        List<List<String>> rows = new ArrayList<>();
        List<List<Object>> data = new ArrayList<>();
        List<List<String>> stats = new ArrayList<>();
        SummaryStatistics[] summaryStats;

        QueryResult(ResultSet rs) throws SQLException {
            ResultSetMetaData md = rs.getMetaData();
            cols = new String[md.getColumnCount()];
            type = new int[cols.length];
            dataWidth = new int[cols.length];
            colWidth = new int[cols.length];
            minBarLen = new int[cols.length];
            iMin = new long[cols.length];
            iMax = new long[cols.length];
            rMin = new double[cols.length];
            rMax = new double[cols.length];
            interval = new double[cols.length];
            summaryStats = new SummaryStatistics[cols.length];
            if (!scaleLocked) scale.clear();
            if (!hiddenLocked) hidden.clear();

            Set<String> columns = new HashSet<>();

            // initialize
            for (int c = 0; c < cols.length; c++) {
                iMin[c] = Long.MAX_VALUE;
                iMax[c] = Long.MIN_VALUE;
                rMin[c] = Double.MAX_VALUE;
                rMax[c] = Double.MIN_VALUE;
                cols[c] = md.getColumnName(c + 1).toLowerCase();
                type[c] = getColumnType(md.getColumnType(c + 1));
                if (type[c] == _BOOL_ || type[c] == _CHAR_ || type[c] == _OTHER_) setEx.add(cols[c]);
                if (!exclude(cols[c])) summaryStats[c] = new SummaryStatistics();
                if (!(scaleLocked && scale.containsKey(cols[c]))) scale.put(cols[c], 1f);
                columns.add(cols[c]);
//                printf("%s (%d -> %d)%n", cols[c], md.getColumnType(c + 1), type[c]);
            }

            // remove non-existent columns from scale
            List<String> old = new ArrayList<>(scale.keySet());
            old.forEach(c -> {
                if (!columns.contains(c)) scale.remove(c);
            });
            if (scale.size() == 0) scaleLocked = false;

            // remove non-existent columns from hidden
            old = new ArrayList<>(hidden);
            old.forEach(c -> {
                if (!columns.contains(c)) hidden.remove(c);
            });
            if (hidden.size() == 0) hiddenLocked = false;

            // Read the data and store in local structures
            String sval = "";
            int len;
            while (rs.next()) {
                List<String> r = new ArrayList<>();
                List<Object> d = new ArrayList<>();
                rows.add(r);
                data.add(d);
                for (int c = 0; c < cols.length; c++) {
                    int c1 = c + 1;
                    switch (type[c]) {
                        case _BOOL_:
                            boolean bval = rs.getBoolean(c1);
                            sval = String.valueOf(bval);
                            len = Math.max(sval.length(), cols[c].length());
                            if (len > dataWidth[c]) dataWidth[c] = len;
                            r.add(sval);
                            d.add(bval);
                            break;
                        case _INT_:
                            long lval = rs.getLong(c1);
                            long abslval = Math.abs(lval);
                            sval = String.format(exclude(cols[c]) ? "%d" : "%,d", lval);
                            len = Math.max(sval.length(), cols[c].length());
                            if (len > dataWidth[c]) dataWidth[c] = len;
                            if (abslval < iMin[c]) iMin[c] = abslval;
                            if (abslval > iMax[c]) iMax[c] = abslval;
                            r.add(sval);
                            d.add(lval);
                            if (!exclude(cols[c])) summaryStats[c].addValue(lval);
                            break;
                        case _REAL_:
                            double dval = rs.getDouble(c1);
                            double absdval = Math.abs(dval);
                            sval = format(dval);
                            len = Math.max(sval.length(), cols[c].length());
                            if (len > dataWidth[c]) dataWidth[c] = len;
                            if (absdval < rMin[c]) rMin[c] = absdval;
                            if (absdval > rMax[c]) rMax[c] = absdval;
                            r.add(sval);
                            d.add(dval);
                            if (!exclude(cols[c])) summaryStats[c].addValue(dval);
                            break;
                        case _CHAR_:
                        case _OTHER_:
                        default:
                            sval = nvl(rs.getString(c1), "");
                            len = Math.max(sval.length(), cols[c].length());
                            if (len > dataWidth[c]) dataWidth[c] = len;
                            r.add(sval);
                            d.add(sval);
                            break;
                    }
                }
            }
        }

        private String nvl(String val, String def) {
            if (val == null) return def;
            return val;
        }

        private void calcStats() {
            String sval = null;
            int len;
            stats.clear(); // needs optimization
            for (int i = _MIN_; i <= _P_VARIANCE_; i++) {
                List<String> r = new ArrayList<>();
                stats.add(r);
                for (int c = 0; c < cols.length; c++) {
                    if (!exclude(cols[c])) {
                        switch (i) {
                            case _MIN_:
                                if (type[c] == _INT_)
                                    sval = format((long) summaryStats[c].getMin());
                                else if (type[c] == _REAL_)
                                    sval = format(summaryStats[c].getMin());
                                break;
                            case _MAX_:
                                if (type[c] == _INT_)
                                    sval = format((long) summaryStats[c].getMax());
                                else if (type[c] == _REAL_)
                                    sval = format(summaryStats[c].getMax());
                                break;
                            case _MEAN_:
                                sval = format(summaryStats[c].getMean());
                                break;
                            case _STDEV_:
                                sval = format(summaryStats[c].getStandardDeviation());
                                break;
                            case _VARIANCE_:
                                sval = format(summaryStats[c].getVariance());
                                break;
                            case _P_VARIANCE_:
                                sval = format(summaryStats[c].getPopulationVariance());
                                break;
                        }
                        len = Math.max(Math.max(dataWidth[c], sval.length()), cols[c].length());
                        if (len > dataWidth[c]) dataWidth[c] = len;
                        r.add(sval);
                    } else {
                        r.add(null);
                    }
                }
            }
        }

        private String format(long val) {
            return String.format("%,d", val);
        }

        private String format(double val) {
            String sval;
            if (val == 0)
                sval = "0";
            else if (val < 0.00001 || val > 999_999_999_999d)
                sval = String.format("%.4g", val);
            else
                sval = String.format("%,12.15f", val);

            return sval;
        }

        private void render(final boolean showStats) {
            if (rows.size() == 0) {
                printf("%n(0 rows)%n%n");
                return;
            }

            if (showStats || statsOn) calcStats();  // needs optimization

            int totalWidth = cols.length + 1 - hidden.size();   // table width

            // find column widths
            for (int c = 0; c < cols.length; c++) {
                int maxBarLen = 0;
                if (!exclude(cols[c])) {
                    float _scale_ = scale.getOrDefault(cols[c], 1f);
                    if (type[c] == _INT_) {
                        interval[c] = (1.0 * iMax[c] - iMin[c]) / rows.size();
                        minBarLen[c] = (int) Math.round(iMin[c] / interval[c] * _scale_);
                        maxBarLen = (int) Math.round(iMax[c] / interval[c] * _scale_);
                    } else if (type[c] == _REAL_) {
                        interval[c] = (rMax[c] - rMin[c]) / rows.size();
                        minBarLen[c] = (int) Math.round(rMin[c] / interval[c] * _scale_);
                        maxBarLen = (int) Math.round(rMax[c] / interval[c] * _scale_);
                    }
                    if (minBarLen[c] > _scale_) maxBarLen = maxBarLen - (minBarLen[c] - (int) _scale_);
//                    printf("%s | Interval:%f | Wd:%d | MinBL:%d | MaxBL:%d%n", cols[c], interval[c], colWidth[c], minBarLen[c], maxBarLen);
                }
                colWidth[c] = Math.max(dataWidth[c], maxBarLen);
                if (!hidden.contains(cols[c])) totalWidth += colWidth[c];
            }

            // make horizontal rule
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < totalWidth; i++) {
                sb.append("-");
            }
            String hr = sb.toString();

            // print header
            println();
            println(hr);
            print("|");
            for (int c = 0; c < cols.length; c++) {
                if (hidden.contains(cols[c])) continue;
                printf(alignLeft.replace("[N]", String.valueOf(colWidth[c])), cols[c]);
                print("|");
            }
            println();
            println(hr);

            // print data
            for (int r = 0; r < rows.size(); r++) {
                List<String> row = rows.get(r);
                List<Object> dt = data.get(r);
                print("|");
                int color = 0;
                for (int c = 0; c < row.size(); c++) {
                    if (hidden.contains(cols[c])) continue;
                    if ((type[c] == _INT_ || type[c] == _REAL_) && !exclude(cols[c])) {
                        int barLen = 0;
                        float _scale_ = scale.getOrDefault(cols[c], 1f);

                        if (type[c] == _INT_)
                            barLen = (int) Math.round(Math.abs((Long) dt.get(c)) / interval[c] * _scale_);
                        if (type[c] == _REAL_)
                            barLen = (int) Math.round(Math.abs((Double) dt.get(c)) / interval[c] * _scale_);

                        if (minBarLen[c] > _scale_) barLen = barLen - (minBarLen[c] - (int) _scale_);

                        int valLen = row.get(c).length();
                        int remLen = colWidth[c] - Math.max(barLen, valLen);

                        char[] chars = row.get(c).toCharArray();

                        AttributedStringBuilder asb = new AttributedStringBuilder();
                        color = getNextBarColor(color);
                        if (barLen > 0) asb.style(AttributedStyle.DEFAULT.background(color));

                        for (int i = 0; i < chars.length; i++) {
                            asb.append(chars[i]);
                            barLen--;
                            if (barLen == 0) asb.style(AttributedStyle.DEFAULT);
                        }

                        if (barLen > 0) {
                            for (int i = 0; i < barLen; i++) {
                                asb.append(" ");
                            }
                            asb.style(AttributedStyle.DEFAULT);
                        }

                        for (int i = 0; i < remLen; i++) {
                            asb.append(" ");
                        }

                        print(asb.toAnsi());
                    } else {
                        printf(alignLeft.replace("[N]", String.valueOf(colWidth[c])), row.get(c));
                    }
                    print("|");
                }
                println();
            }
            println(hr);

            // print scale
            print("|");
            for (int c = 0; c < cols.length; c++) {
                if (!hidden.contains(cols[c])) {
                    printf(alignLeft.replace("[N]", String.valueOf(colWidth[c])), exclude(cols[c]) ? " " : scale.get(cols[c]));
                    print("|");
                }
            }
            println(" Scale");
            println(hr);

            if (showStats || statsOn) {
                for (int i = _MIN_; i <= _P_VARIANCE_; i++) {
                    print("|");
                    List<String> r = stats.get(i);
                    for (int c = 0; c < cols.length; c++) {
                        if (!hidden.contains(cols[c])) {
                            printf(alignLeft.replace("[N]", String.valueOf(colWidth[c])), exclude(cols[c]) ? " " : r.get(c));
                            print("|");
                        }
                    }
                    switch (i) {
                        case _MIN_:
                            println(" Min.");
                            break;
                        case _MAX_:
                            println(" Max.");
                            break;
                        case _MEAN_:
                            println(" Mean");
                            break;
                        case _STDEV_:
                            println(" StDev.");
                            break;
                        case _VARIANCE_:
                            println(" Var.");
                            break;
                        case _P_VARIANCE_:
                            println(" P.Var.");
                            break;
                    }
                }
                println(hr);
            }

            // print program state
            AttributedStringBuilder asb = new AttributedStringBuilder();
//            if (showStats) asb.append("       ");
            asb.style(AttributedStyle.BOLD.background(AttributedStyle.BRIGHT).foreground(AttributedStyle.YELLOW));
            asb.append(String.format(rows.size() == 1 ? "(%d row)%n" : "(%d rows)", rows.size()));
            if (scaleLocked) {
                asb.append(" | ");
                asb.append("Scale Locked");
            }

            asb.append(" | ");
            asb.append(statsOn ? "Stats On" : "Stats Off");

            if (hiddenLocked) {
                asb.append(" | ");
                asb.append("Hidden Locked");
            }
            if (hidden.size() > 0) {
                asb.append(" | ");
                if (hidden.size() > 0) {
                    asb.append("Hidden Column(s): [");
                    for (String s : hidden)
                        asb.append(s).append(", ");
                    asb.setLength(asb.length() - 2);
                    asb.append("]");
                }
            }
            asb.style(AttributedStyle.DEFAULT);
            asb.append("%n%n");
            printf(asb.toAnsi());
            log.flush();
        }
    }
}