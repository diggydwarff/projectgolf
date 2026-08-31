package dev.projectgolf.golf;

public enum ClubType {
    DRIVER("Driver", 3.40, 12.0, 8.0, 0.32),
    WOOD("Wood", 2.90, 18.0, 6.0, 0.38),
    IRON("Iron", 2.35, 31.0, 4.5, 0.50),
    WEDGE("Wedge", 1.72, 52.0, 3.0, 0.70),
    PUTTER("Putter", 0.82, 1.0, 1.4, 0.92);

    private final String displayName;
    private final double maxSpeed;
    private final double loftDegrees;
    private final double missDegrees;
    private final double minimumUsefulPower;

    ClubType(String displayName, double maxSpeed, double loftDegrees, double missDegrees, double minimumUsefulPower) {
        this.displayName = displayName;
        this.maxSpeed = maxSpeed;
        this.loftDegrees = loftDegrees;
        this.missDegrees = missDegrees;
        this.minimumUsefulPower = minimumUsefulPower;
    }

    public String displayName() {
        return displayName;
    }

    public double maxSpeed() {
        return maxSpeed;
    }

    public double loftDegrees() {
        return loftDegrees;
    }

    public double missDegrees() {
        return missDegrees;
    }

    public double minimumUsefulPower() {
        return minimumUsefulPower;
    }
}
