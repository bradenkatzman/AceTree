package org.rhwlab.snight;

import java.util.StringTokenizer;
import javafx.geometry.Point3D;
import javafx.scene.transform.Rotate;

/**
 * This class represents the transform model used to rotate vectors to canonical orientation
 * 
 * Before the 4-cell identity is assigned, this transform is constructed based on the two vectors
 * supplied in the AuxInfo_v2.csv file that specify the orientation of the AP vector and the LR
 * vector in the given dataset. Two transforms (rotation matrices) are constructed that rotate
 * these given vectors into the known canonical orientation. These transformations are then used
 * in naming code to rotate vectors between dividing cells by these same initial rotations so 
 * that the division direction is with respect to canonical orientation. Thus, we can use spatial
 * heuristics to assign names dynamically.
 * 
 * *** NOTE: this class is only used under the AuxInfo scheme in which datasets do not need to be compressed.
 * This new transform allows complete 3 dimensional rotation of datasets in any initial orientation
 * 
 * @author bradenkatzman
 * @date 10/2016
 *
 */
public class CanonicalTransform {

	// holds the configuration vectors used to construct the two transforms
	private MeasureCSV measureCSV;

	// vectors, angles, axes used for rotation to canonical orientation
	private double[] AP_orientation_vec;
	private double[] LR_orientation_vec;
	private Point3D AP_orientation_vector;
	private Point3D LR_orientation_vector;
	private Point3D DV_orientation_vector;

	// angles of rotations
	private double angleOfRotationAP;
	private double angleOfRotationLR;

	// rotation axes
	private Point3D rotationAxisAP;
	private Point3D rotationAxisLR;

	// rotation matrices
	private Rotate rotMatrixAP;
	private Rotate rotMatrixLR;

	// set to true when rotations are confirmed, false on any failure
	public boolean activeTransform;

	/**
	 * Only the two initial vectors contained in the MeasureCSV class are required to build
	 * the transform
	 * 
	 * @param measureCSV
	 */
	public CanonicalTransform(MeasureCSV measureCSV) {
		System.out.println("Constructing CanonicalTransform");
		// for safety reasons we'll deactive the transform until we've confirmed their correct construction
		this.activeTransform = false;

		if (measureCSV == null) {
			System.out.println("No configurations supplied to CanonicalTransform...returning");
			return;
		}
		this.measureCSV = measureCSV;

		boolean continue_;
		continue_ = prepConfigVecs(); // read the configuration vectors into memory
		if (continue_) {
			continue_ = initOrientation(); // build the two transforms
		}

		if (continue_) {
			continue_ = confirmOrientation(); // confirm that the transforms rotate the config vecs into canonical orientation
		}

		// if we get through everything, turn on the activeTransform flag
		if(continue_) {
			this.activeTransform = true;
		}
	}

	/**
	 * Read the vectors from the MeasureCSV class, parse them, and add values to instance vars
	 * 
	 * @return true on success, false on failure
	 */
	private boolean prepConfigVecs() {
		// read the initial orientations
		String AP_orientation_vec_str = measureCSV.iMeasureHash.get(MeasureCSV.att_v2[MeasureCSV.AP_ORIENTATION]);
		String LR_orientation_vec_str = measureCSV.iMeasureHash.get(MeasureCSV.att_v2[MeasureCSV.LR_ORIENTATION]);

		this.AP_orientation_vec = new double[THREE];
		StringTokenizer st = new StringTokenizer(AP_orientation_vec_str, " ");
		if (st.countTokens() != THREE) {
			System.err.println("AP orientation vector is incorrect size - may be formatted improperly. Refer to documentation.");
			return false;
		}
		AP_orientation_vec[0] = Double.parseDouble(st.nextToken());
		AP_orientation_vec[1] = Double.parseDouble(st.nextToken());
		AP_orientation_vec[2] = Double.parseDouble(st.nextToken());

		this.LR_orientation_vec = new double[THREE];
		StringTokenizer st2 = new StringTokenizer(LR_orientation_vec_str, " ");
		if (st2.countTokens() != THREE) {
			System.err.println("LR orientation vector is incorrect size - may be formatted improperly. Refer to documentation.");
			return false;
		}
		LR_orientation_vec[0] = Double.parseDouble(st2.nextToken());
		LR_orientation_vec[1] = Double.parseDouble(st2.nextToken());
		LR_orientation_vec[2] = Double.parseDouble(st2.nextToken());

		// create vector objects from initial orientations
		this.AP_orientation_vector = new Point3D(AP_orientation_vec[0], AP_orientation_vec[1], AP_orientation_vec[2]);
		this.LR_orientation_vector = new Point3D(LR_orientation_vec[0], LR_orientation_vec[1], LR_orientation_vec[2]);

		return true;
	}

	/**
	 * - Normalizes input vectors
	 * - Finds the DV orientation (cross product of AP and LR)
	 * - Finds the Axis-Angle representation of the rotation from AP initial to AP canonical
	 * - Finds the Axis-Angle representation of the rotation from LR initial to LR canonical
	 * - Builds a rotation matrix (JavaFX Rotate object) for AP
	 * - Builds a rotation matrix (JavaFX Rotate object) for LR
	 * 
	 * 
	 * *** There is a degenerate case of the axis-angle representation that needs to be handled manually:
	 * - When the cross product of the two vectors is <0,0,0>, the resulting rotation matrix will not rotate
	 * 		the vector even if there is an angle between the two vectors
	 * - A <0,0,0> vector will result when the two vectors are in the same plane. Thus, when this happens, use
	 *		the two vectors to figure out which plane they are in and manually set the rotation matrix to move
	 *		around an axis perpendicular to the plane in which the two vectors lie. e.g.:
	 *			- rotate around the z-axis if the vectors are in the xy-plane
	 * 
	 * @author: Braden Katzman (July-August 2016)
	 */
	private boolean initOrientation() {
		System.out.println(" ");
		System.out.println("Initialized orientations with AuxInfo v2.0");
		if (AP_orientation_vec == null || LR_orientation_vec == null || AP_orientation_vec.length != THREE || LR_orientation_vec.length != THREE) {
			System.err.println("Incorrect AP, LR orientations from AuxInfo");
			return false;
		}

		// normalize
		this.AP_orientation_vector = AP_orientation_vector.normalize();
		this.LR_orientation_vector = LR_orientation_vector.normalize();

		// cross product for DV orientation vector --> init with AP_orientation coords and then cross with LR
		this.DV_orientation_vector = new Point3D(AP_orientation_vector.getX(), AP_orientation_vector.getY(), AP_orientation_vector.getZ());
		this.DV_orientation_vector.crossProduct(LR_orientation_vector);

		// axis angle rep. of AP --> init with AP_orientation coords and then cross with AP canonical orientation
		this.rotationAxisAP = new Point3D(AP_orientation_vector.getX(), AP_orientation_vector.getY(), AP_orientation_vector.getZ());
		this.rotationAxisAP = rotationAxisAP.crossProduct(AP_can_or);
		this.rotationAxisAP = roundVecCoords(rotationAxisAP);
		this.rotationAxisAP = rotationAxisAP.normalize();
		this.angleOfRotationAP = angBWVecs(AP_orientation_vector, AP_can_or);

		// check for degenerate case --> make sure angle of nonzero first to ensure the dataset isn't just already in canonical orientation
		if (angleOfRotationAP != 0 && rotationAxisAP.getX() == 0 && rotationAxisAP.getY() == 0 && rotationAxisAP.getZ() == 0) {
			// ensure that the AP orientation vector is in fact a vector in the xy plane
			if (AP_orientation_vector.getX() != 0 && AP_orientation_vector.getY() == 0 && AP_orientation_vector.getZ() == 0) {
				System.out.println("Degenerate case of axis angle rotation, rotation only about z axis in xy plane");

				// make the z axis the axis of rotation
				this.rotationAxisAP = new Point3D(0., 0., 1.);
			}
		}

		// build rotation matrix for AP
		this.rotMatrixAP = new Rotate(radiansToDegrees(this.angleOfRotationAP),
				new Point3D(rotationAxisAP.getX(), rotationAxisAP.getY(), rotationAxisAP.getZ()));

		// axis angle rep. of LR		
		this.rotationAxisLR = new Point3D(LR_orientation_vector.getX(), LR_orientation_vector.getY(), LR_orientation_vector.getZ());
		this.rotationAxisLR = rotationAxisLR.crossProduct(LR_can_or);
		this.rotationAxisLR = roundVecCoords(rotationAxisLR);
		this.rotationAxisLR = rotationAxisLR.normalize();
		this.angleOfRotationLR = angBWVecs(LR_orientation_vector, LR_can_or);

		// check for degenerate case --> make sure angle of nonzero first to ensure the dataset isn't just already in canonical orientation
		if (angleOfRotationLR != 0 && rotationAxisLR.getX() == 0 && rotationAxisLR.getY() == 0 && rotationAxisLR.getZ() == 0) {
			// ensure that the LR orientation vector is in fact a vector in the yz plane
			if (LR_orientation_vector.getX() == 0 && LR_orientation_vector.getY() == 0 && LR_orientation_vector.getZ() != 0) {
				System.out.println("Degenerate case of axis angle rotation, rotation only about x axis in yz plane");

				// make the x axis the axis of rotation
				this.rotationAxisLR = new Point3D(1., 0., 0.);
			}
		}

		// build rotation matrix for LR
		this.rotMatrixLR = new Rotate(radiansToDegrees(this.angleOfRotationLR),
				new Point3D(rotationAxisLR.getX(), rotationAxisLR.getY(), rotationAxisLR.getZ()));

		return true;
	}

	/**
	 * After the rotations are initialized, this method is called to confirm that the rotations
	 * rotate the initial orientation of the dataset into canonical orientation. If this fails,
	 * the flag denoting the version of AuxInfo in use (1.0 or 2.0) is set to false (denoting version
	 * 1.0).
	 * 
	 * @author Braden Katzman
	 * Date: 8/15/16
	 */
	private boolean confirmOrientation() {
		Point3D AP_orientation_pt = rotMatrixAP.deltaTransform(AP_orientation_vector.getX(), AP_orientation_vector.getY(), AP_orientation_vector.getZ());
		AP_orientation_pt = AP_orientation_pt.normalize();
		Point3D AP_orientation_test_vec = new Point3D(AP_orientation_pt.getX(), AP_orientation_pt.getY(), AP_orientation_pt.getZ());
		AP_orientation_test_vec = roundVecCoords(AP_orientation_test_vec);
		if (!AP_can_or.equals(AP_orientation_test_vec)) {
			System.out.println("AP orientation incorrectly rotated to: <" + 
					AP_orientation_test_vec.getX() + ", " + AP_orientation_test_vec.getY() + ", " + AP_orientation_test_vec.getZ() + ">");
			System.out.println("Reverting to AuxInfo v1.0");
			return false;
		}

		Point3D LR_orientation_pt = rotMatrixLR.deltaTransform(LR_orientation_vector.getX(), LR_orientation_vector.getY(), LR_orientation_vector.getZ());
		LR_orientation_pt = LR_orientation_pt.normalize();
		Point3D LR_orientation_test_vec = new Point3D(LR_orientation_pt.getX(), LR_orientation_pt.getY(), LR_orientation_pt.getZ());
		LR_orientation_test_vec = roundVecCoords(LR_orientation_test_vec);
		if (!LR_can_or.equals(LR_orientation_test_vec)) {
			System.out.println("LR orientation incorrectly rotated to: <" + 
					LR_orientation_test_vec.getX() + ", " + LR_orientation_test_vec.getY() + ", " + LR_orientation_test_vec.getZ() + ">");
			System.out.println("Reverting to AuxInfo v1.0");

			return false;
		}

		System.out.println("Confirmed transforms rotate from initial AP, LR to canonical");
		System.out.println(rotMatrixAP.toString());
		System.out.println(rotMatrixLR.toString());
		System.out.println(" ");
		return true;
	}

	/**
	 * Convert radians to degrees
	 * 
	 * @param radians
	 * @return degrees
	 */
	private double radiansToDegrees(double radians) {
		if (Double.isNaN(radians)) return 0.;

		return radians * (180/Math.PI);
	}

	/**
	 * Finds the angle between two vectors using the formula:
	 * acos( dot(vec_1, vec_2) / (length(vec_1) * length(vec_2)) )
	 * 
	 * @param v1
	 * @param v2
	 * @return the angle between the two input vectors
	 */
	private double angBWVecs(Point3D v1, Point3D v2) {
		if (v1 == null || v2 == null) return 0.;

		double ang = Math.acos(v1.dotProduct(v2) / (vecLength(v1) * vecLength(v2)));

		return (Double.isNaN(ang)) ? 0. : ang;
	}

	/**
	 * JavaFX does not have a built in Vector class, so we use Point3D as a substitute
	 * This method treats a Point3D as a vector and finds its length
	 * 
	 * @param v - the vector represented by a Point3D
	 * @return the length of the vector
	 */
	private double vecLength(Point3D v) {
		if (v == null) return 0.;

		return Math.sqrt((v.getX()*v.getX()) + (v.getY()*v.getY()) + (v.getZ()*v.getZ()));
	}

	/**
	 * Round the coordinates of a vector to a whole number if within a certain threshold to that number
	 * ZERO_THRESHOLD defined at bottom of file
	 * 
	 * @param vec - the vector to be rounded
	 * @return the updated vector in the form of a Point3D
	 */
	private Point3D roundVecCoords(Point3D vec) {
		Point3D tmp = new Point3D(Math.round(vec.getX()), Math.round(vec.getY()), Math.round(vec.getZ()));

		vec = new Point3D(
				(Math.abs(vec.getX() - tmp.getX()) <= ZERO_THRESHOLD) ? tmp.getX() : vec.getX(),
				(Math.abs(vec.getY() - tmp.getY()) <= ZERO_THRESHOLD) ? tmp.getY() : vec.getY(),		
				(Math.abs(vec.getZ() - tmp.getZ()) <= ZERO_THRESHOLD) ? tmp.getZ() : vec.getZ());

		return vec;
	}

	/**
	 * Rotate the vector between divided cells by applying the two transforms
	 * 
	 * @param vec - the vector between daughter cells after division
	 */
	public boolean applyProductTransform(double[] vec) {
		if (!this.activeTransform) return false;
		
		// make local copy
		double[] vec_local = new double[3];
		vec_local[0] = vec[0];
		vec_local[1] = vec[1];
		vec_local[2] = vec[2];

		// get Point3D from applying first rotation (AP) to vector
		Point3D daughterCellsPt3d_firstRot = rotMatrixAP.deltaTransform(vec_local[0], vec_local[1], vec_local[2]);

		// update vec_local
		vec_local[0] = daughterCellsPt3d_firstRot.getX();
		vec_local[1] = daughterCellsPt3d_firstRot.getY();
		vec_local[2] = daughterCellsPt3d_firstRot.getZ();
		
		// get Point3D from applying second rotation (LR) to vector
		Point3D daughterCellsPt3d_bothRot = rotMatrixLR.deltaTransform(vec_local[0], vec_local[1], vec_local[2]);
		
		// update vec_local
		vec_local[0] = daughterCellsPt3d_bothRot.getX();
		vec_local[1] = daughterCellsPt3d_bothRot.getY();
		vec_local[2] = daughterCellsPt3d_bothRot.getZ();

		// error handling
		if (Double.isNaN(vec_local[0]) || Double.isNaN(vec_local[1]) || Double.isNaN(vec_local[2])) return false;
		
		// update parameter vector
		vec[0] = vec_local[0];
		vec[1] = vec_local[1];
		vec[1] = vec_local[2];
		
		return true;
	}
	
	/**
	 * Apply a single transform to the given vector specified with either "AP" or "LR"
	 * 
	 * @param vec - the vector to be transformed
	 * @param axis - the axis around which to rotate ("AP" or "LR")
	 * @return
	 */
	public boolean applySingleTransform(double[] vec, String axis) {
		if (!this.activeTransform || (!axis.equals("AP") && !axis.equals("LR")) || axis == null) return false;
		
		// make local copy
		double[] vec_local = new double[3];
		vec_local[0] = vec[0];
		vec_local[1] = vec[1];
		vec_local[2] = vec[2];
		
		Point3D rotatedVec = null;
		if (axis.equals("AP")) {
			rotatedVec = rotMatrixAP.deltaTransform(vec_local[0], vec_local[1], vec_local[2]);
		} else if (axis.equals("LR")) {
			rotatedVec = rotMatrixLR.deltaTransform(vec_local[0], vec_local[1], vec_local[2]);
		}
		
		// update vec_local
		if (rotatedVec == null) return false;
		vec_local[0] = rotatedVec.getX();
		vec_local[1] = rotatedVec.getY();
		
		vec_local[2] = rotatedVec.getZ();
		
		// error handling
		if (Double.isNaN(vec_local[0]) || Double.isNaN(vec_local[1]) || Double.isNaN(vec_local[2])) return false;
		
		// update parameter vector
		vec[0] = vec_local[0];
		vec[1] = vec_local[1];
		vec[1] = vec_local[2];
		
		return true;
	}


	public boolean isActiveTransform() {
		return this.activeTransform;
	}

	// static variables
	private static final double[] AP_canonical_orientation = {-1, 0, 0}; // A points down the negative x axis in canonical orienatation
	private static final double[] LR_canonical_orientation = {0, 0, 1}; // L points out toward the viewer in canonical orienatation
	private static final Point3D AP_can_or = new Point3D(AP_canonical_orientation[0], AP_canonical_orientation[1], AP_canonical_orientation[2]);
	private static final Point3D LR_can_or = new Point3D(LR_canonical_orientation[0], LR_canonical_orientation[1], LR_canonical_orientation[2]);
	private static final int THREE = 3;
	private static final double ZERO_THRESHOLD = .1;
	
	// probably won't need to use these, but if so here they are
//  private static final Point3D DV_can_or = new Point3D(DV_canonical_orientation[0], DV_canonical_orientation[1], DV_canonical_orientation[2]);
//	private static final double[] DV_canonical_orientation = {0, -1, 0};
}