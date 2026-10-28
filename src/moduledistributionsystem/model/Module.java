package moduledistributionsystem.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One learning module (subject) that students pick up from the distribution office, e.g. "ITE 031 -
 * Data Structure & Algorithms".
 *
 * This is a plain data class (a "model"): it only stores values and applies small rules (stock can not
 * go below 0, the slot limit is at least 1). The screens and the database only use the getters /
 * setters below.
 *
 * How to customize: to store one more thing about a module (for example a price), add a private field
 * here with a getter / setter, add the column in schema.sql + Database.java (saveModule / loadModules),
 * and show it in InventoryPanel.
 */
public class Module {
    /** Stable identity and database primary key. The code can be edited, the id never changes. */
    private UUID id = UUID.randomUUID();
    /** Subject code WITHOUT a space, e.g. "ITE031". Use getDisplayCode() to show it as "ITE 031". */
    private String code;
    /** Subject name shown in the screens. */
    private String title;
    /** Units / credits of the subject. */
    private int credits;
    /** Where students collect it (e.g. "Basement"). */
    private String location;
    /** Collection deadline. Students can not book a pickup after this date. */
    private LocalDate collectBy;
    /** READY (open for pickup), PENDING (coming soon) or RELEASED (closed). See ModuleStatus. */
    private ModuleStatus status;
    /** "Semester 1" or "Semester 2". Students only see modules of the current term (set by the admin). */
    private String semester;
    /** Owning department, e.g. "CITE". */
    private String department;
    /**
     * Copies on hand right now. Staff type the opening count. It drops by one every time a pickup is
     * marked Picked Up, and can never go below 0.
     */
    private int stocks;
    /**
     * Which year level this module belongs to (1st to 4th Year). A student automatically sees the
     * modules whose year level equals their own (see Student.isEnrolledIn). Null means "not set": such
     * a module shows for everyone.
     */
    private YearLevel yearLevel;
    /**
     * Default number of students allowed in ONE date + time slot for a module. Staff can change it per
     * module in the Add / Edit Module dialog. Change this number to change the default for new modules.
     */
    public static final int DEFAULT_SLOT_LIMIT = 10;
    /**
     * Most students that may book THIS module for the same date + time slot (so the pickup desk is
     * never crowded). Always 1 or more.
     */
    private int slotLimit = DEFAULT_SLOT_LIMIT;

    /**
     * Short constructor kept for old callers: available = true means READY, false means PENDING.
     * Semester defaults to "Semester 1" and department to "CITE".
     */
    public Module(String code, String title, int credits, String location, LocalDate collectBy, boolean available) {
        this(code, title, credits, location, collectBy, available ? ModuleStatus.READY : ModuleStatus.PENDING, "Semester 1", "CITE");
    }

    /**
     * Constructor with an explicit status. Semester defaults to "Semester 1", department to "CITE",
     * stock to 0.
     */
    public Module(String code, String title, int credits, String location, LocalDate collectBy, ModuleStatus status) {
        this(code, title, credits, location, collectBy, status, "Semester 1", "CITE");
    }

    /** Constructor with status, semester and department. Stock starts at 0. */
    public Module(String code, String title, int credits, String location, LocalDate collectBy,
                  ModuleStatus status, String semester, String department) {
        this(code, title, credits, location, collectBy, status, semester, department, 0);
    }

    /**
     * Full constructor used by the screens and the demo data. A negative stock is stored as 0. A new
     * random id is created.
     */
    public Module(String code, String title, int credits, String location, LocalDate collectBy,
                  ModuleStatus status, String semester, String department, int stocks) {
        this.stocks = Math.max(0, stocks);
        this.code = code;
        this.title = title;
        this.credits = credits;
        this.location = location;
        this.collectBy = collectBy;
        this.status = status;
        this.semester = semester;
        this.department = department;
    }

    /** The module's stable identity (database primary key). */
    public UUID getId() { return id; }

    /**
     * Rebuilds a module exactly as it was saved in the database, keeping its original id. Used only by
     * Database.loadModules.
     */
    public static Module restore(UUID id, String code, String title, int credits, String location, LocalDate collectBy,
                                 ModuleStatus status, String semester, String department, int stocks) {
        Module m = new Module(code, title, credits, location, collectBy, status, semester, department, stocks);
        m.id = id;
        return m;
    }

    /** Code without a space, e.g. "ITE031". */
    public String getCode() { return code; }
    /**
     * Changes the code. Screens should use DemoDataStore.saveModuleEdit, which also rejects duplicate
     * codes.
     */
    public void setCode(String code) { this.code = code; }
    /** Subject name. */
    public String getTitle() { return title; }
    /** Changes the subject name. */
    public void setTitle(String title) { this.title = title; }
    /** Units / credits. */
    public int getCredits() { return credits; }
    /** Changes the units / credits. */
    public void setCredits(int credits) { this.credits = credits; }
    /** Pickup location. */
    public String getLocation() { return location; }
    /** Changes the pickup location. */
    public void setLocation(String location) { this.location = location; }
    /** Collection deadline. */
    public LocalDate getCollectBy() { return collectBy; }
    /** Changes the collection deadline. */
    public void setCollectBy(LocalDate collectBy) { this.collectBy = collectBy; }
    /** Current status (READY, PENDING or RELEASED). */
    public ModuleStatus getStatus() { return status; }
    /**
     * Changes the status. Screens should use DemoDataStore.saveModuleEdit so students get the right
     * notifications.
     */
    public void setStatus(ModuleStatus status) { this.status = status; }
    /** "Semester 1" or "Semester 2". */
    public String getSemester() { return semester; }
    /** Changes the semester. */
    public void setSemester(String semester) { this.semester = semester; }
    /** Owning department. */
    public String getDepartment() { return department; }
    /** Changes the department. */
    public void setDepartment(String department) { this.department = department; }
    /** Copies on hand. */
    public int getStocks() { return stocks; }
    /** Sets the copies on hand. A negative number is stored as 0. */
    public void setStocks(int stocks) { this.stocks = Math.max(0, stocks); }
    /** Year level this module belongs to (may be null). */
    public YearLevel getYearLevel() { return yearLevel; }
    /** Sets the year level this module belongs to. */
    public void setYearLevel(YearLevel yearLevel) { this.yearLevel = yearLevel; }
    /** Maximum students per date + time slot for this module. */
    public int getSlotLimit() { return slotLimit; }
    /** Sets the per-slot student limit. Values below 1 are stored as 1. */
    public void setSlotLimit(int slotLimit) { this.slotLimit = Math.max(1, slotLimit); }

    /**
     * The registrar's way of writing the code, with a space ("ITE 031"). The stored code stays
     * "ITE031". Use this in every screen.
     */
    public String getDisplayCode() {
        return code == null ? "" : code.replaceFirst("^([A-Za-z]+)\\s*([0-9].*)$", "$1 $2");
    }

    /** True while the module is READY (open for pickup), even if the stock is 0. Compare isBookable(). */
    public boolean isAvailable() { return status == ModuleStatus.READY; }
    /** Convenience setter: true makes the module READY, false makes it PENDING. */
    public void setAvailable(boolean available) { this.status = available ? ModuleStatus.READY : ModuleStatus.PENDING; }

    /**
     * True when students can book a NEW pickup right now: the module is READY and at least one copy is
     * in stock.
     */
    public boolean isBookable() { return status == ModuleStatus.READY && stocks > 0; }

    /** Text shown in combo boxes and logs: "CODE - title". */
    @Override
    public String toString() { return code + " — " + title; }
}
