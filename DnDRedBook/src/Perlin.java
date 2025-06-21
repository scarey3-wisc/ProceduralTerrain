import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Random;
import java.util.Scanner;

public class Perlin
{
	public static class PerlinFunction
	{
		private double scale;
		private double offset;
		private double octaveScale;
		private int octaves;
		private double threshold;
		private boolean thresholdSet;
		private boolean absThreshold;
		private long seed;
		public PerlinFunction(PerlinFunction pf, double t, boolean abs)
		{
			this.seed = pf.seed;
			this.scale = pf.scale;
			this.offset = pf.offset;
			this.octaves = pf.octaves;
			this.octaveScale = pf.octaveScale;
			this.threshold = t;
			this.thresholdSet = true;
			this.absThreshold = abs;
		}
		public PerlinFunction(double s, double o, int oct, double fs)
		{
			Random seedGenerator = new Random();
			seed = seedGenerator.nextLong();
			scale = s;
			offset = o;
			octaves = oct;
			octaveScale = fs;
			threshold = 0;
			thresholdSet = false;
			absThreshold = false;
		}
		public PerlinFunction(double s, double o, int oct, double fs, double t, boolean abs)
		{
			this(s, o, oct, fs);
			threshold = t;
			thresholdSet = true;
			absThreshold = abs;
		}
		public double GetPercentAboveThreshold(double wX, double wY, int octaves)
		{
			double val = Get(wX, wY, octaves);
			val -= threshold;
			val /= (1 - threshold);
			if(val > 1)
				val = 1;
			if(val < 0)
				val = 0;
			return val;
		}
		public double GetPercentBeneathThreshold(double wX, double wY, int octaves)
		{
			double val = Get(wX, wY, octaves);
			val -= threshold;
			val /= (1 + threshold);
			if(val > 1)
				val = 1;
			if(val < 0)
				val = 0;
			return val;
		}
		public double GetPercentAboveThreshold(double wX, double wY)
		{
			double val = Get(wX, wY);
			if(absThreshold)
				val = Math.abs(val);
			val -= threshold;
			val /= (1 - threshold);
			if(val > 1)
				val = 1;
			if(val < 0)
				val = 0;
			return val;
		}
		public double GetPercentBeneathThreshold(double wX, double wY)
		{
			double val = Get(wX, wY);
			if(absThreshold)
			{
				val = Math.abs(val);
				val /= (threshold);
				val = 1 - val;
				if(val > 1)
					val = 1;
				if(val < 0)
					val = 0;
				return val;
			}
			else
			{
				val -= threshold;
				val /= (1 + threshold);
				val *= -1;
				if(val > 1)
					val = 1;
				if(val < 0)
					val = 0;
				return val;
			}

		}
		public boolean UnderThreshold(double wX, double wY)
		{
			if(!thresholdSet)
				return true;
			
			double modThreshold = threshold * GetMaxValue() / NORM_SCALE;
			wX *= scale;
			wY *= scale;
			double result = 0;
			double cM = 1;
			double divider = 1;
			double maxDelta = GetMaxValue();
			for(int i = 0; i < octaves; i++)
			{
				result += Perlin(wX * cM, wY * cM) * divider;
				cM *= 2;
				divider /= octaveScale;
				maxDelta -= divider;
				
				if(i + 1 < octaves)
				{
					if(absThreshold)
					{
						if(Math.abs(result) + maxDelta < modThreshold)
						{
							return true;
						}
					}
					else
					{
						if(result + maxDelta < modThreshold)
						{
							return true;
						}
					}
				}
			}
			if(absThreshold)
				result = Math.abs(result);
			
			return result < modThreshold;
		}
		private double Get(double wX, double wY, int octaves)
		{
			double res = GetNoise(offset + wX * scale, offset + wY * scale, octaves, octaveScale);
			res *= NORM_SCALE;
			if(res >= 1)
			{
				res = 0.99999999;
				//System.out.println("Thought you should know we needed to clamp");
			}
			if(res < -1)
			{
				res = -0.99999999;
				//System.out.println("Thought you should know we needed to clamp");
			}
			return res;
		}
		public double Get(double wX, double wY)
		{
			return Get(wX, wY, octaves);
		}
		public Vec2 GetGradient(double wX, double wY)
		{
			Vec2 res = GetNoiseGradient(offset + wX * scale, offset + wY * scale, octaves, octaveScale);
			res.Multiply(scale);
			res.Multiply(NORM_SCALE);
			return res;
		}
		
		/*private double GetMaxValue(int octave)
		{
			double r = 1 / octaveScale;
			double exp = Math.pow(r, octave);
			double n = 1 - exp;
			double d = 1 - r;
			return n / d;
		}*/
		private double GetMaxValue()
		{
			double r = 1 / octaveScale;
			return 1 / (1 - r);
		}
		private double GetNoise(double x, double y, int octaves, double scaling)
		{
			double result = 0;
			double cM = 1;
			double divider = 1;
			for(int i = 0; i < octaves; i++)
			{
				result += Perlin(x * cM, y * cM) * divider;
				cM *= 2;
				divider /= scaling;
			}
			return result / GetMaxValue();
		}
		private Vec2 GetNoiseGradient(double x, double y, int octaves, double scaling)
		{
			Vec2 result = new Vec2(0, 0);
			double cM = 1;
			double divider = 1;
			for(int i = 0; i < octaves; i++)
			{
				Vec2 octGrad = PerlinGradient(x * cM, y * cM);
				octGrad.Multiply(cM);
				octGrad.Multiply(divider);
				result.Add(octGrad);
				cM *= 2;
				divider /= scaling;
			}
			result.Divide(GetMaxValue());
			return result;
		}
		private Vec2 PerlinGradient(double x, double y)
		{
			int x0 = (int) x;
			int x1 = x0 + 1;
			int y0 = (int) y;
			int y1 = y0 + 1;
			double sX = x - x0;
			double sY = y - y0;
			
			double n0, n1, n2, n3;
			Vec2 gradN0, gradN1, gradN2, gradN3;
			
			n0 = DotGridGradientForPerlin(x0, y0, x, y);
			n1 = DotGridGradientForPerlin(x1, y0, x, y);
			n2 = DotGridGradientForPerlin(x0, y1, x, y);
			n3 = DotGridGradientForPerlin(x1, y1, x, y);
			gradN0 = GradDotGridGradientForPerlin(x0, y0, x, y);
			gradN1 = GradDotGridGradientForPerlin(x1, y0, x, y);
			gradN2 = GradDotGridGradientForPerlin(x0, y1, x, y);
			gradN3 = GradDotGridGradientForPerlin(x1, y1, x, y);
			
			double dx0 = MathToolkit.DerivativeSmoothLerp(n0, n1, sX, gradN0.x, gradN1.x);
			double dx1 = MathToolkit.DerivativeSmoothLerp(n2, n3, sX, gradN2.x, gradN3.x);
			double dx = MathToolkit.SmoothLerp(dx0, dx1, sY);
			
			double dy0 = MathToolkit.DerivativeSmoothLerp(n0, n2, sY, gradN0.y, gradN2.y);
			double dy1 = MathToolkit.DerivativeSmoothLerp(n1, n3, sY, gradN1.y, gradN3.y);
			double dy = MathToolkit.SmoothLerp(dy0, dy1, sX);
			
			dx *= 2;
			dx /= 1.41422;
			dy *= 2;
			dy /= 1.41422;
			
			return new Vec2(dx, dy);
		}
		private double Perlin(double x, double y)
		{
			int x0 = (int) x;
			int x1 = x0 + 1;
			int y0 = (int) y;
			int y1 = y0 + 1;
			double sX = x - x0;
			double sY = y - y0;
			
			double n0, n1, n2, n3, t0, t1;
			
			n0 = DotGridGradientForPerlin(x0, y0, x, y);
			n1 = DotGridGradientForPerlin(x1, y0, x, y);
			t0 = MathToolkit.SmoothLerp(n0, n1, sX);
			n2 = DotGridGradientForPerlin(x0, y1, x, y);
			n3 = DotGridGradientForPerlin(x1, y1, x, y);
			t1 = MathToolkit.SmoothLerp(n2, n3, sX);
			
			double result = MathToolkit.SmoothLerp(t0, t1, sY);
			result *= 2;
			result /= 1.41422;
			return result;
		}
		private double DotGridGradientForPerlin(int ix, int iy, double x, double y)
		{
			double gx = 0, gy = 0;
			long lx = ix, ly = iy;
			//lx = ix < 0 ? Integer.MAX_VALUE + ix : ix;
			//ly = iy < 0 ? Integer.MAX_VALUE + iy : iy;
			Random randOne = new Random(lx << 32);
			Random randTwo = new Random(ly);
			long realSeed = randOne.nextLong() ^ randTwo.nextLong() ^ seed;
			
			Random newRand = new Random(realSeed);
			double theta = 2 * Math.PI * newRand.nextDouble();
			gx = (double) Math.sin(theta);
			gy = (double) Math.cos(theta);
			double dx = x - ix;
			double dy = y - iy;
			return gx * dx + gy * dy;
		}
		
		private Vec2 GradDotGridGradientForPerlin(int ix, int iy, double x, double y)
		{
			double gx = 0, gy = 0;
			long lx = ix, ly = iy;
			//lx = ix < 0 ? Integer.MAX_VALUE + ix : ix;
			//ly = iy < 0 ? Integer.MAX_VALUE + iy : iy;
			Random randOne = new Random(lx << 32);
			Random randTwo = new Random(ly);
			long realSeed = randOne.nextLong() ^ randTwo.nextLong() ^ seed;
			
			Random newRand = new Random(realSeed);
			double theta = 2 * Math.PI * newRand.nextDouble();
			gx = (double) Math.sin(theta);
			gy = (double) Math.cos(theta);
			return new Vec2(gx, gy);
		}
	}
	public static void PrintSeeds()
	{
		System.out.println("Oceans: " + oceans.seed);
		System.out.println("Mountains: " + mountains.seed);
		System.out.println("Random Hills: " + randomHills.seed);
		System.out.println("Random Lakes: " + randomLakes.seed);
		System.out.println("Random Passes: " + randomPasses.seed);
		System.out.println("Min-max selector: " + minMaxSelector.seed);
	}
	public static void HackSaveSeeds()
	{
		oceans.seed = -7476565073334897271L;
		mountains.seed = -5003272621707830689L;
		peaks.seed = mountains.seed;
		foothills.seed = mountains.seed;
		randomHills.seed = -5414314227717884271L;
		randomLakes.seed = -6148924136243112461L;
		randomPasses.seed = 9143697602828912792L;
		minMaxSelector.seed = 6291342320517188989L;
	}
	public static boolean SaveSeeds(BufferedWriter wr)
	{
		try {
			wr.write(Long.toString(oceans.seed));
			wr.newLine();
			
			wr.write(Long.toString(mountains.seed));
			wr.newLine();
			
			wr.write(Long.toString(peaks.seed));
			wr.newLine();
			
			wr.write(Long.toString(foothills.seed));
			wr.newLine();
			
			wr.write(Long.toString(randomHills.seed));
			wr.newLine();
			
			wr.write(Long.toString(randomLakes.seed));
			wr.newLine();
			
			wr.write(Long.toString(randomPasses.seed));
			wr.newLine();
			
			wr.write(Long.toString(minMaxSelector.seed));
			wr.newLine();
			
			wr.write(Long.toString(upliftAdjust.seed));
			wr.newLine();
			
			wr.write(Long.toString(blurX.seed));
			wr.newLine();
			
			wr.write(Long.toString(blurY.seed));
			wr.newLine();
			
			wr.write(Long.toString(terra_incognita.seed));
			wr.newLine();
			
			wr.write(Long.toString(sedimentDepth.seed));
			wr.newLine();
			
			wr.write(Long.toString(sedimentPockets.seed));
			wr.newLine();
			
			wr.write(Long.toString(rockyJitters.seed));
			wr.newLine();
			
			for(PerlinFunction pf : elevDeltas)
			{
				wr.write(Long.toString(pf.seed));
				wr.newLine();
			}
		} catch (IOException e) {
			return false;
		}
		
		return true;
	}
	public static boolean LoadSeeds(Scanner std)
	{
		oceans.seed = std.nextLong();
		std.nextLine();
		
		mountains.seed = std.nextLong();
		std.nextLine();
		
		peaks.seed = std.nextLong();
		std.nextLine();
		
		foothills.seed = std.nextLong();
		std.nextLine();
		
		randomHills.seed = std.nextLong();
		std.nextLine();
		
		randomLakes.seed = std.nextLong();
		std.nextLine();
		
		randomPasses.seed = std.nextLong();
		std.nextLine();
		
		minMaxSelector.seed = std.nextLong();
		std.nextLine();
		
		upliftAdjust.seed = std.nextLong();
		std.nextLine();
		
		blurX.seed = std.nextLong();
		std.nextLine();
		
		blurY.seed = std.nextLong();
		std.nextLine();
		
		terra_incognita.seed = std.nextLong();
		std.nextLine();
		
		sedimentDepth.seed = std.nextLong();
		std.nextLine();
		
		sedimentPockets.seed = std.nextLong();
		std.nextLine();
		
		rockyJitters.seed = std.nextLong();
		std.nextLine();
		
		for(PerlinFunction pf : elevDeltas)
		{
			pf.seed = std.nextLong();
			std.nextLine();
		}
		
		return true;
	}
	private static final double NORM_SCALE = 1.5;
	
	//Determines where land is
	public static PerlinFunction oceans = new PerlinFunction(1.1, 0, 10, 1.8, -0.3, false); //0.18 for continents, 1.1 for islands
	
	//Draws strips of mountains
	public static PerlinFunction mountains = new PerlinFunction(0.3, 0, 8, 1.8, 0.036, true); //previously: 0.052
	
	//Marks the center of mountain strips as high peaks
	public static PerlinFunction peaks = new PerlinFunction(mountains, 0.007, true); //previously: 0.01
	
	//Creates a ring of hills around mountain strips
	public static PerlinFunction foothills = new PerlinFunction(mountains, 0.073, true); //previously: 0.09
	
	//Draws blobs of hills
	public static PerlinFunction randomHills = new PerlinFunction(9.4, 0, 7, 2, 0.18, false);
	
	//Draws random lakes; increasing the threshold decreases the size
	public static PerlinFunction randomLakes = new PerlinFunction(1.1, 0, 7, 2, 0.46, false);
	
	//Cuts through mountain strips, demoting peaks to mountains, mountains to foothills, and foothills to flatland
	public static PerlinFunction randomPasses = new PerlinFunction(2.2, 0, 6, 2, 0.03, true);
	
	//Determines how aggressively we climb
	public static PerlinFunction minMaxSelector = new PerlinFunction(33.8, 0, 4, 1.8, 0, false);
	
	//Adjusts Tectonic Uplift Values
	public static PerlinFunction upliftAdjust = new PerlinFunction(4.5, 0, 10, 2, 0, false);
		
	//Pushes Terrain around a bit to destroy crisp lines
	public static PerlinFunction blurX = new PerlinFunction(100, 0, 4, 2, 0, false);
	public static PerlinFunction blurY = new PerlinFunction(100, 0, 4, 2, 0, false);
	public static double BLUR_DISTANCE = 450;
	
	public static PerlinFunction terra_incognita = new PerlinFunction(0.1, 0, 3, 2);
	
	//During erosion simulation, determines how easily the ground erodes
	public static PerlinFunction sedimentDepth = new PerlinFunction(63, 0, 2, 2, 0, false);
	public static PerlinFunction sedimentPockets = new PerlinFunction(66, 0, 2, 2, 0.23, true);
	
	//some functions to push the terrain a bit more
	public static PerlinFunction rockyJitters = new PerlinFunction(350, 0, 4, 2, 0, false);	
	public static double rockJitterScale = 150;
	public static PerlinFunction mountainHeightDelta = new PerlinFunction(215, 0, 6, 2.2, 0, false);
	public static PerlinFunction plainsHeightDelta = new PerlinFunction(110, 0, 6, 2, 0, false);
	public static PerlinFunction[] elevDeltas = new PerlinFunction[] {plainsHeightDelta, mountainHeightDelta};
	public static double[] elevDeltaScales = new double[] { 40, 90 };
	
}