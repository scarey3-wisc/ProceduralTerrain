public class Switches
{
	public static double LAPLACE_EROSION_ROCK_CONSTANT = 0.001;
	public static double LAPLACE_EROSION_DEPOSITION_CONSTANT = 0.02;
	public static double LAPLACE_EROSION_SEDIMENT_CONSTANT = 0.05;
	
	public static boolean SIMPLE_TERRAIN_TYPE_RENDERING = true;
	public static boolean OUTLINE_MAPS = false;
	public static boolean POISSON_DENSE = false;
	public static boolean POISSON_BIASED = false;
	public static boolean PAINT_VORONOI_CENTERS = false;
	public static boolean INITIAL_EXPAND_RENDERED = false;
	public static boolean INITIAL_EXPAND_EMPTY = true;
	public static boolean USE_BLURRING = true;
	public static boolean CLEAR_IMAGE_CACHES = false;
	public static boolean PARALLEL_RENDERING = true;
	public static boolean PARALLEL_CONTINENT_GEN = true;
	public static boolean PHOTOGRAPH_PARALLEL_TILED_RENDER = false;
	
	public static int MAX_SAMPLE_POINTS_IN_LAKE = 8000;
	public static int MIN_LOCAL_MAP_PIXELS_IN_LAKE = 400;
	
	public static PAINT_TYPE CURR_PAINT_TYPE = PAINT_TYPE.TERRAIN;
	public static FLOW_MODEL CURR_FLOW_MODEL = FLOW_MODEL.D4_Random;
	
	
	public static enum FLOW_MODEL
	{
		D4_Random,
		D8_Random,
		D_Infinity
	}
	public static enum PAINT_TYPE
	{
		ELEVATION_CURR,
		TERRAIN,
		TERRAIN_EVAL,
		CONTOUR,
		MIN_MAX_SELECTOR_DISPLAY,
		ELEV_GRADIENT,
		VORONOI_PURE,
		VORONOI_INTERPOLATED,
		VORONOI_TRIANGLES
	};
	
	public static TERRAIN_GEN_ALGO CURR_TERRAIN_ALGO = TERRAIN_GEN_ALGO.FUSED_TECTONIC_AND_DISTANCE;
	public static enum TERRAIN_GEN_ALGO
	{
		COASTAL_DISTANCE,
		COASTAL_DISTANCE_TYPE_CONSTRAINED,
		TECTONIC_UPLIFT,
		FUSED_TECTONIC_AND_DISTANCE
	}
}