package dev.cloudframe.common.quarry;

/**
 * Deterministic frame bounds + mining region semantics for quarries.
 *
 * <p>The glass frame perimeter is placed/removed on the outer edge of frame bounds.
 * The mining region is the interior, always inset by 1 block on all horizontal sides.
 * This relationship is fixed and deterministic across platforms.</p>
 */
public final class QuarryFrameBounds {

	private QuarryFrameBounds() {
	}

	/**
	 * Helper to compute the inner mining region from frame bounds.
	 *
	 * <p>The mining region is the interior: inset by 1 from the perimeter.</p>
	 *
	 * @return Inner region bounds, or null if frame is too small (< 3x3).
	 */
	public record InnerBounds(int minX, int maxX, int minZ, int maxZ) {
		public boolean valid() {
			return minX <= maxX && minZ <= maxZ;
		}
	}

	/**
	 * Compute inner mining region from frame bounds.
	 *
	 * @param frameMinX Frame outer perimeter min X
	 * @param frameMaxX Frame outer perimeter max X
	 * @param frameMinZ Frame outer perimeter min Z
	 * @param frameMaxZ Frame outer perimeter max Z
	 * @return Inner bounds (inset by 1), or null if frame is too small.
	 */
	public static InnerBounds computeInner(int frameMinX, int frameMaxX, int frameMinZ, int frameMaxZ) {
		int innerMinX = frameMinX + 1;
		int innerMaxX = frameMaxX - 1;
		int innerMinZ = frameMinZ + 1;
		int innerMaxZ = frameMaxZ - 1;

		// Require at least 1x1 interior (frame must be at least 3x3 outer).
		if (innerMinX > innerMaxX || innerMinZ > innerMaxZ) {
			return null;
		}

		return new InnerBounds(innerMinX, innerMaxX, innerMinZ, innerMaxZ);
	}

	/**
	 * Returns true if frame bounds are large enough for a valid mining region.
	 */
	public static boolean isValidSize(int frameMinX, int frameMaxX, int frameMinZ, int frameMaxZ) {
		return computeInner(frameMinX, frameMaxX, frameMinZ, frameMaxZ) != null;
	}
}
