package moduledistributionsystem.model;

/**
 * A student's or module's year level in the CITE / BSIT program: 1st to 4th Year.
 *
 * A student sees the modules whose year level equals their own (see Student.isEnrolledIn). toString()
 * gives the label shown in drop-downs ("1st Year", "2nd Year"...); the database stores the constant
 * name.
 */
public enum YearLevel {
    FIRST_YEAR("1st Year"),
    SECOND_YEAR("2nd Year"),
    THIRD_YEAR("3rd Year"),
    FOURTH_YEAR("4th Year");

    /** Text shown to the user for this year level. */
    private final String label;

    /** Creates a constant with its display label. */
    YearLevel(String label) { this.label = label; }

    /** Returns the display label, so a JComboBox of YearLevel shows "1st Year", "2nd Year"... */
    @Override
    public String toString() { return label; }
}
