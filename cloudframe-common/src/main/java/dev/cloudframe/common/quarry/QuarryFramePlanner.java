package dev.cloudframe.common.quarry;

import dev.cloudframe.common.markers.MarkerFrameCanonicalizer;
import dev.cloudframe.common.markers.MarkerPos;

import java.util.List;

import static dev.cloudframe.common.quarry.QuarryFrameBounds.InnerBounds;

/**
 * Platform-neutral quarry registration policy derived from a marker frame.
 * <p>
 * This computes the "inner" mining rectangle (inset by 1 from the frame) and
 * validates that a controller is placed exactly 1 block outside the frame perimeter
 * (non-diagonal) on the same Y as the frame.
 */
public final class QuarryFramePlanner {
	private QuarryFramePlanner() {}

	public enum Status {
		OK,
		INVALID_FRAME,
		CONTROLLER_WRONG_Y,
		CONTROLLER_NOT_ADJACENT
	}

	public static final class Result {
		public final Status status;
		public final MarkerFrameCanonicalizer.Status frameStatus;

		public final int frameMinX;
		public final int frameMaxX;
		public final int frameMinZ;
		public final int frameMaxZ;
		public final int frameY;

		public final int innerMinX;
		public final int innerMaxX;
		public final int innerMinZ;
		public final int innerMaxZ;

		private Result(
				Status status,
				MarkerFrameCanonicalizer.Status frameStatus,
				int frameMinX,
				int frameMaxX,
				int frameMinZ,
				int frameMaxZ,
				int frameY,
				int innerMinX,
				int innerMaxX,
				int innerMinZ,
				int innerMaxZ
		) {
			this.status = status;
			this.frameStatus = frameStatus;
			this.frameMinX = frameMinX;
			this.frameMaxX = frameMaxX;
			this.frameMinZ = frameMinZ;
			this.frameMaxZ = frameMaxZ;
			this.frameY = frameY;
			this.innerMinX = innerMinX;
			this.innerMaxX = innerMaxX;
			this.innerMinZ = innerMinZ;
			this.innerMaxZ = innerMaxZ;
		}

		public boolean ok() {
			return status == Status.OK;
		}
	}

	/**
	 * Plans a quarry registration from the given marker corners.
	 *
	 * @param corners     The 4 marker corners (any order; will be canonicalized).
	 * @param controllerX Controller block X.
	 * @param controllerY Controller block Y.
	 * @param controllerZ Controller block Z.
	 */
	public static Result planFromMarkerCorners(
			List<MarkerPos> corners,
			int controllerX,
			int controllerY,
			int controllerZ
	) {
		MarkerFrameCanonicalizer.Result frame = MarkerFrameCanonicalizer.canonicalize(corners, true);
		if (!frame.ok()) {
			return new Result(Status.INVALID_FRAME, frame.status(), 0, 0, 0, 0, 0, 0, 0, 0, 0);
		}

		List<MarkerPos> canonical = frame.corners();
		MarkerPos first = canonical.get(0);
		int frameMinX = first.x();
		int frameMinZ = first.z();
		int frameY = first.y();
		int frameMaxX = canonical.get(2).x();
		int frameMaxZ = canonical.get(2).z();

		if (controllerY != frameY) {
			return new Result(Status.CONTROLLER_WRONG_Y, null, frameMinX, frameMaxX, frameMinZ, frameMaxZ, frameY, 0, 0, 0, 0);
		}

		// Controller must be exactly 1 block outside the perimeter on one side.
		boolean adjacentX = (controllerX == frameMinX - 1 || controllerX == frameMaxX + 1)
				&& controllerZ >= frameMinZ && controllerZ <= frameMaxZ;
		boolean adjacentZ = (controllerZ == frameMinZ - 1 || controllerZ == frameMaxZ + 1)
				&& controllerX >= frameMinX && controllerX <= frameMaxX;

		if (!(adjacentX ^ adjacentZ)) {
			return new Result(Status.CONTROLLER_NOT_ADJACENT, null, frameMinX, frameMaxX, frameMinZ, frameMaxZ, frameY, 0, 0, 0, 0);
		}

		int innerMinX = frameMinX + 1;
		int innerMaxX = frameMaxX - 1;
		int innerMinZ = frameMinZ + 1;
		int innerMaxZ = frameMaxZ - 1;

		// Use Common frame bounds helper to compute + validate inner region.
		InnerBounds inner = QuarryFrameBounds.computeInner(frameMinX, frameMaxX, frameMinZ, frameMaxZ);
		if (inner == null) {
			return new Result(Status.INVALID_FRAME, null, frameMinX, frameMaxX, frameMinZ, frameMaxZ, frameY, 0, 0, 0, 0);
		}

		return new Result(Status.OK, null, frameMinX, frameMaxX, frameMinZ, frameMaxZ, frameY, inner.minX(), inner.maxX(), inner.minZ(), inner.maxZ());
	}
}
