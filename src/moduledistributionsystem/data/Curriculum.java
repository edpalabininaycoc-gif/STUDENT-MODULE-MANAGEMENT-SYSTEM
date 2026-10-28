package moduledistributionsystem.data;

import java.util.ArrayList;
import java.util.List;
import moduledistributionsystem.model.YearLevel;

/**
 * The official subject list (curriculum) of the BSIT program, 1st to 4th Year, Semester 1 and 2.
 *
 * It is the single source of truth for which subjects exist: the demo data and the "sync with the
 * database" step on every start both read this list, so a missing subject is added to an existing
 * database automatically.
 *
 * How to customize: add, rename or fix a subject by editing its add(...) line in the static block below
 * (year, semester, code WITHOUT a space, title, units). Subject codes must be unique. Units are
 * corrected back to this list on the next start, so change them here and not only in Module Inventory.
 */
public final class Curriculum {
    /** Utility class: only static members, so it is never instantiated. */
    private Curriculum() { }

    /** Department that owns every subject of this curriculum. */
    public static final String DEPARTMENT = "CITE";

    /** One subject row of the curriculum: code, title, semester, units and year level. */
    public static final class Entry {
        /** Subject code (no space), subject name and "Semester 1" / "Semester 2". */
        public final String code, title, semester;
        /** Units / credits. */
        public final int units;
        /** Year level the subject belongs to. */
        public final YearLevel year;

        Entry(String code, String title, int units, String semester, YearLevel year) {
            this.code = code;
            this.title = title;
            this.units = units;
            this.semester = semester;
            this.year = year;
        }
    }

    private static final List<Entry> ALL = new ArrayList<>();

    /** Adds one subject to the curriculum list. */
    private static void add(YearLevel y, String sem, String code, String title, int units) {
        ALL.add(new Entry(code, title, units, sem, y));
    }

    static {
        // ---- 2nd Year, 1st Semester (listed first: the demo's default student is a 2nd Year) ----
        YearLevel y2 = YearLevel.SECOND_YEAR;
        add(y2, "Semester 1", "ITE031", "Data Structure & Algorithms", 3);
        add(y2, "Semester 1", "ITE300", "Object Oriented Programming", 3);
        add(y2, "Semester 1", "ITE292", "Networking 1", 3);
        add(y2, "Semester 1", "GEN003", "Science, Technology and Society", 3);
        add(y2, "Semester 1", "HIS007", "Life and Works of Rizal", 3);
        add(y2, "Semester 1", "ITE083", "IT Project Management", 3);
        add(y2, "Semester 1", "ITE298", "Information Management (Including Fundamentals of Database Systems)", 3);
        add(y2, "Semester 1", "PED032", "Physical Activities Toward Health and Fitness (PATHFIT 3): Individual and Dual Sports", 2);
        add(y2, "Semester 1", "SSP005", "Student Success Program 1", 1);

        // ---- 1st Year, 1st Semester ----
        YearLevel y1 = YearLevel.FIRST_YEAR;
        add(y1, "Semester 1", "GEN001", "Purposive Communication", 3);
        add(y1, "Semester 1", "GEN002", "Understanding the Self", 3);
        add(y1, "Semester 1", "GEN006", "Ethics", 3);
        add(y1, "Semester 1", "ITE260", "Computer Programming 1", 3);
        add(y1, "Semester 1", "ITE366", "Introduction to Computing (Including IT Fundamentals)", 3);
        add(y1, "Semester 1", "MAT152", "Mathematics in the Modern World", 3);
        add(y1, "Semester 1", "NST021", "National Service Training Program 1", 3);
        add(y1, "Semester 1", "PED030", "Physical Activities Toward Health and Fitness (PATHFIT 1): Movement Competency Training", 2);

        // ---- 1st Year, 2nd Semester ----
        add(y1, "Semester 2", "ART002", "Art Appreciation", 3);
        add(y1, "Semester 2", "GEN005", "The Contemporary World", 3);
        add(y1, "Semester 2", "GEN008", "Living in the IT Era", 3);
        add(y1, "Semester 2", "ITE048", "Discrete Structures", 3);
        add(y1, "Semester 2", "ITE186", "Computer Programming 2", 3);
        add(y1, "Semester 2", "ITE399", "Human Computer Interaction", 3);
        add(y1, "Semester 2", "NST022", "National Service Training Program 2", 3);
        add(y1, "Semester 2", "PED031", "Physical Activities Toward Health and Fitness (PATHFIT 2): Exercise-Based Fitness Activities", 2);

        // ---- 2nd Year, 2nd Semester ----
        // GEN003 (Science, Technology and Society) is also on this sheet, but a subject code can only
        // exist once, and it is already listed above under 2nd Year, 1st Semester -- it was left there.
        add(y2, "Semester 2", "GEN004", "Readings in Philippine History", 3);
        add(y2, "Semester 2", "GEN009", "The Entrepreneurial Mind", 3);
        add(y2, "Semester 2", "GEN013", "People and the Earth's Ecosystems", 3);
        add(y2, "Semester 2", "ITE308", "Web System and Technologies", 3);
        add(y2, "Semester 2", "ITE380", "Human Computer Interaction 2", 3);
        add(y2, "Semester 2", "ITE393", "Advanced Development and Emerging Technologies (Including Event-Driven Programming)", 3);
        add(y2, "Semester 2", "ITE400", "Systems Integration and Architecture", 3);
        add(y2, "Semester 2", "PED028", "Physical Activities Towards Health and Fitness II", 2);
        add(y2, "Semester 2", "SSP006", "Student Success Program 2", 1);

        // ---- 3rd Year, 1st Semester ----
        YearLevel y3 = YearLevel.THIRD_YEAR;
        add(y3, "Semester 1", "ITE307", "Quantitative Methods (Including Modeling and Simulation)", 3);
        add(y3, "Semester 1", "ITE353", "Data Scalability & Analytics", 3);
        add(y3, "Semester 1", "ITE359", "Networking 2", 3);
        add(y3, "Semester 1", "ITE369", "Information Assurance and Security 1", 3);
        add(y3, "Semester 1", "ITE391", "Freehand and Digital Drawing", 3);
        add(y3, "Semester 1", "ITE397", "Advanced Database Systems (Including Advanced Systems Integration and Architecture)", 3);
        add(y3, "Semester 1", "SSP007", "Student Success Program 3", 1);

        // ---- 3rd Year, 2nd Semester ----
        // The sheet lists "SSP 006" again for Student Success Program 4, but SSP006 is already Student
        // Success Program 2 (2nd Year, 2nd Semester) and codes must be unique, so SSP 4 is stored as SSP008
        // (SSP005 = 1, SSP006 = 2, SSP007 = 3). Change the code here if the registrar uses a different one.
        add(y3, "Semester 2", "ITE235", "Game Development", 3);
        add(y3, "Semester 2", "ITE293", "Systems Administration and Maintenance", 3);
        add(y3, "Semester 2", "ITE370", "Information Assurance and Security 2", 3);
        add(y3, "Semester 2", "ITE386", "Cloud Programming", 3);
        add(y3, "Semester 2", "ITE401", "Platform Technologies", 3);
        add(y3, "Semester 2", "SSP008", "Student Success Program 4", 1);
        add(y3, "Semester 2", "ITE309", "Capstone Project and Research 1", 3);

        // ---- 4th Year, 1st Semester ----
        YearLevel y4 = YearLevel.FOURTH_YEAR;
        add(y4, "Semester 1", "BAM286", "Applied Analytics in Business for IT", 3);
        add(y4, "Semester 1", "ITE381", "IT Business Solutions", 3);
        add(y4, "Semester 1", "ITE310", "Capstone Project and Research 2", 3);
        add(y4, "Semester 1", "ITE367", "Managing IT Resources (Including Social and Professional Issues)", 3);

        // ---- 4th Year, 2nd Semester ----
        add(y4, "Semester 2", "ITE311", "Information Technology Practicum (486 Hours)", 6);
    }

    /** All subjects, in display order. */
    public static List<Entry> all() { return ALL; }
}
