package io.canvasmc.canvas.world.entities;

/**
 * Where a player stood when the entities resting in its view last checked it.
 */
public final class TrackerViewer {

    public static final double MOVING_SLACK = 8.0;
    public static final double STANDING_SLACK = 0.5;
    public static final int STANDING_CHECKS = 20;
    private static final double MARGIN = 1.0E-3;
    private boolean anchored;
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private double slack = MOVING_SLACK;
    private double standX;
    private double standY;
    private double standZ;
    private int standingChecks;
    private boolean followsY;
    private int viewDistance;
    private double reach;
    private long unplacedWakes;
    private long unplacedWakesSeen;

    public void wakeLater() {
        ++this.unplacedWakes;
    }

    public boolean move(final double x, final double y, final double z, final boolean followsY, final int viewDistance, final double reach) {
        if (!this.anchored
            || followsY != this.followsY
            || viewDistance != this.viewDistance
            || reach != this.reach
            || this.unplacedWakes != this.unplacedWakesSeen
            || !this.within(x, y, z, this.anchorX, this.anchorY, this.anchorZ, this.slack)) {
            this.anchor(x, y, z, MOVING_SLACK);
            this.followsY = followsY;
            this.viewDistance = viewDistance;
            this.reach = reach;
            this.unplacedWakesSeen = this.unplacedWakes;
            return true;
        }

        if (this.slack == STANDING_SLACK) {
            return false;
        }

        if (!this.within(x, y, z, this.standX, this.standY, this.standZ, STANDING_SLACK)) {
            this.stand(x, y, z);
            return false;
        }

        if (++this.standingChecks < STANDING_CHECKS) {
            return false;
        }

        this.anchor(x, y, z, STANDING_SLACK);
        return true;
    }

    private boolean within(final double x, final double y, final double z, final double fromX, final double fromY, final double fromZ, final double slack) {
        final double dx = x - fromX;
        final double dz = z - fromZ;
        return dx * dx + dz * dz <= slack * slack && (!this.followsY || Math.abs(y - fromY) <= slack);
    }

    private void anchor(final double x, final double y, final double z, final double slack) {
        this.anchored = true;
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
        this.slack = slack;
        this.stand(x, y, z);
    }

    private void stand(final double x, final double y, final double z) {
        this.standX = x;
        this.standY = y;
        this.standZ = z;
        this.standingChecks = 0;
    }

    public boolean settles(final double x, final double y, final double z, final double range, final double rangeY) {
        if (!this.anchored) {
            return false;
        }

        final double slack = this.slack;
        final double dx = this.anchorX - x;
        final double dz = this.anchorZ - z;
        final double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance - slack > range + MARGIN) {
            return true;
        }

        if (distance + slack >= range - MARGIN) {
            return false;
        }

        if (rangeY < 0.0) {
            return true;
        }

        if (!this.followsY) {
            return false;
        }

        final double dy = Math.abs(this.anchorY - y);
        return dy - slack > rangeY + MARGIN || dy + slack < rangeY - MARGIN;
    }
}
