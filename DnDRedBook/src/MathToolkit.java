
public class MathToolkit
{
	public static boolean ExtractBitFromByte(byte t, int which)
	{
		if(which < 0 || which > 7)
			throw new IllegalArgumentException("Bad bit index");
		return ((t >> which) & 1) != 0;
	}
	public static int CalcRGB(byte r, byte g, byte b)
	{
		int i = ((0xFF & r) << 16) |
	            ((0xFF & g) << 8) | 
	            (0xFF & b);
		return i;
	}
	public static byte ExtractRed(int rgb)
	{
		int red = (rgb >>> 16);
		return (byte) red;
	}
	public static byte ExtractGreen(int rgb)
	{
		int green = (rgb >>> 8);
		return (byte) green;
	}
	public static byte ExtractBlue(int rgb)
	{
		int blue = rgb;
		return (byte) blue;
	}
	public static boolean AlmostEquals(int i, double d)
	{
		double diff = d - i;
		return diff <= 0.5 && diff >= -0.5;
	}
	public static int BinaryLog(int num)
	{
		int log = 0;
		if( (num & 0xffff000) != 0) { num>>>=16; log = 16;}
		if( num >= 256 ) { num >>>= 8; log += 8; }
		if( num >= 16 ) { num >>>= 4; log += 4; }
		if( num >= 4) { num >>>= 2; log += 2; }
		return log + (num >>> 1);
	}
	public static long FastExp(long base, long exponent)
	{
		long answer = 1;
		long powerOfTwo = base;
		for(int i = 0; i < 64; i++)
		{
			if((exponent & 1) == 1)
				answer *= powerOfTwo;
			powerOfTwo *= powerOfTwo;
			exponent = exponent >>> 1;
		}
		return answer;
	}
	public static int SmoothColorLerp(int a, int b, double t)
	{
		if(t <= 0)
			return a;
		if(t >= 1)
			return b;
		int aR = (a >> 16) & 0xFF, aG = (a >> 8) & 0xFF, aB = a & 0xFF;
		int bR = (b >> 16) & 0xFF, bG = (b >> 8) & 0xFF, bB = b & 0xFF;
		
		double red = (1.0 * bR - 1.0 * aR) * (3 - t*2) * t * t + 1.0 * aR;
		double gre = (1.0 * bG - 1.0 * aG) * (3 - t*2) * t * t + 1.0 * aG;
		double blu = (1.0 * bB - 1.0 * aB) * (3 - t*2) * t * t + 1.0 * aB;
		
		int R = (int) red;
		int G = (int) gre;
		int B = (int) blu;
		return (R << 16) | (G << 8) | B;
	}
	public static double SmoothLerp(double a, double b, double t)
	{
		if(t <= 0)
			return a;
		if(t >= 1)
			return b;
		return (b - a) * (3 - t*2) * t * t + a;
	}
	public static float SmoothLerp(float a, float b, double t)
	{
		if(t <= 0)
			return a;
		if(t >= 1)
			return b;
		return (float) ((b - a) * (3 - t*2) * t * t + a);
	}
	public static double DerivativeSmoothLerp(double a, double b, double t, double dadt, double dbdt)
	{
		//f(t) = t^2(3-2t)(b(t)-a(t))+a(t)
		//f(t) = (3t^2-2t^3)(b(t)-a(t))+a(t)
		//f'(t) = (3t^2-2t^3)(dbdt-dadt)+(6t-6t^2)(b(t)-a(t))+dadt
		if(t < 0)
			return 0;
		if(t > 1)
			return 0;
		if(t == 0)
			return dadt;
		if(t == 1)
			return dbdt;
		return (3 * t * t - 2 * t * t * t) * (dbdt - dadt) + (6 * t - 6 * t * t) * (b - a) + dadt;
	}
}