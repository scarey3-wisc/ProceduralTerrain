import java.awt.Color;
import java.util.ArrayList;

public class TriangleMesh
{
	private ArrayList<Vec4> points;
	private ArrayList<Color> colors;
	private ArrayList<Vec4> normals;
	private ArrayList<Vec4> transformedNormals;
	public ArrayList<Triangle> tris;
	private ArrayList<Vec4> transformedPoints;
	
	public TriangleMesh(LocalMap source)
	{
		points = new ArrayList<Vec4>();
		normals = new ArrayList<Vec4>();
		transformedPoints = new ArrayList<Vec4>();
		transformedNormals = new ArrayList<Vec4>();
		colors = new ArrayList<Color>();
		tris = new ArrayList<Triangle>();
		source.PrepareForEditing(true, true, true);
		int dim = DataImage.trueDim;
		double mpp = 1.0 * LocalMap.METER_DIM / DataImage.trueDim; //meters per pixel
		for(int i = 0; i <= dim; i++)
		{
			for(int j = 0; j <= dim; j++)
			{
				LocalMap.Pixel p = source.new Pixel(i, j);
				double height = p.GetHeight();
				double x = 1.0 * i * mpp;
				double y = 1.0 * j * mpp;
				
				points.add(new Vec4(x, y, height, 1));
				transformedPoints.add(new Vec4(0, 0, 0, 0));
				Vec2 gradient = p.GetHeightGradient();
				gradient.Divide(LocalMap.METER_DIM);
				
				normals.add(new Vec4(-gradient.x, -gradient.y, 1, 0));
				transformedNormals.add(new Vec4(0, 0, 0, 0));
				
				Color finalColor = null;
				
				int lakeColor = (18 << 16) + (146 << 8) + (201);
				int peakColor = (171 << 16) + (156 << 8) + (135);
				int mounColor = (148 << 16) + (126 << 8) + (40);
				int hillColor = (156 << 16) + (158 << 8) + (93);
				int flatColor = (124 << 16) + (166 << 8) + (88);
				
				LocalMap.WatermapValue water = p.GetWaterType();
								
				if(water == LocalMap.WatermapValue.Ocean)
					finalColor = new Color(0, 0, 255);
				else if(water == LocalMap.WatermapValue.Lake)
					finalColor = new Color(lakeColor);
				else
				{
					int rainflow = p.GetCurrentRainflow();
					
					int base = 255 << 16;
					if(gradient.Len() > 0.5)
						base = peakColor;
					else if(gradient.Len() > 0.3)
						base = MathToolkit.SmoothColorLerp(mounColor, peakColor, (gradient.Len() - 0.3) / (0.5 - 0.3));
					else if(gradient.Len() > 0.17)
						base = MathToolkit.SmoothColorLerp(hillColor, mounColor, (gradient.Len() - 0.17) / (0.3 - 0.17));
					else if(gradient.Len() > 0.03)
						base = MathToolkit.SmoothColorLerp(flatColor, hillColor, (gradient.Len() - 0.03) / (0.17 - 0.03));
					else
						base = flatColor;
					
					if(rainflow > 500)
					{
						double percent = Math.sqrt(rainflow - 500) / 120;
						if(percent > 1)
							percent = 1;
						if(percent < 0)
							percent = 0;
						base = MathToolkit.SmoothColorLerp(base, lakeColor, percent);
					}
					finalColor = new Color(base);
				}
				colors.add(finalColor);
			}
		}
		for(int i = 0; i < dim; i++)
		{
			for(int j = 0; j < dim; j++)
			{
				int p00 = (i) * (dim + 1) + (j);
				int p01 = (i) * (dim + 1) + (j + 1);
				int p10 = (i + 1) * (dim + 1) + (j);
				int p11 = (i + 1) * (dim + 1) + (j + 1);
				//LocalMap.Pixel loc00 = source.new Pixel(i, j);
				//LocalMap.Pixel loc01 = source.new Pixel(i, j + 1);
				//LocalMap.Pixel loc10 = source.new Pixel(i + 1, j);
				//LocalMap.Pixel loc11 = source.new Pixel(i + 1, j + 1);
				
				Triangle one = new Triangle(p00, p01, p10);
				Triangle two = new Triangle(p11, p10, p01);
				tris.add(one);
				tris.add(two);
			}
		}
		source.CompleteEditing(true, true, true, false);
	}
	
	public void TransformToClipSpace(Mat4 view, Mat4 projection)
	{
		for(int i = 0; i < points.size(); i++)
		{
			Vec4 dest = transformedPoints.get(i);
			Vec4 src = points.get(i);
			view.postMultiply(src, dest);
			projection.postMultiply(dest, dest);
			dest.x /= dest.k;
			dest.y /= dest.k;
			dest.z /= dest.k;
			
			Vec4 normDest = transformedNormals.get(i);
			Vec4 normSec = normals.get(i);
			view.postMultiply(normSec, normDest);
			normDest.Normalize();
		}
	}
	
	public class Triangle
	{
		//indices
		private int p0;
		private int p1;
		private int p2;
		
		//bounding box
		public double xS, xE, yS, yE;
		
		public Triangle(int p0, int p1, int p2)
		{
			this.p0 = p0;
			this.p1 = p1;
			this.p2 = p2;
		}
		public void InterpolateNormal(double a, double b, double c, Vec3 dest)
		{
			Vec4 aNorm = transformedNormals.get(p0);
			Vec4 bNorm = transformedNormals.get(p1);
			Vec4 cNorm = transformedNormals.get(p2);
			
			dest.x = aNorm.x * a + bNorm.x * b + cNorm.x * c;
			dest.y = aNorm.y * a + bNorm.y * b + cNorm.y * c;
			dest.z = aNorm.z * a + bNorm.z * b + cNorm.z * c;
			
			dest.Normalize();
		}
		public int InterpolateColor(double a, double b, double c)
		{
			int reda = colors.get(p0).getRed();
			int redb = colors.get(p1).getRed();
			int redc = colors.get(p2).getRed();
			
			int greena = colors.get(p0).getGreen();
			int greenb = colors.get(p1).getGreen();
			int greenc = colors.get(p2).getGreen();
			
			int bluea = colors.get(p0).getBlue();
			int blueb = colors.get(p1).getBlue();
			int bluec = colors.get(p2).getBlue();
			
			double red = a * reda + b * redb + c * redc;
			double green = a * greena + b * greenb + c * greenc;
			double blue = a * bluea + b * blueb + c * bluec;
			
			int re = (int) red;
			int gr = (int) green;
			int bl = (int) blue;
			return (re << 16) + (gr << 8) + bl;
		}
		public Vec4 GetP0()
		{
			return transformedPoints.get(p0);
		}
		public Vec4 GetP1()
		{
			return transformedPoints.get(p1);
		}
		public Vec4 GetP2()
		{
			return transformedPoints.get(p2);
		}
		
		public void CalculateBoundingBox()
		{
			xS = Integer.MAX_VALUE;
			xE = Integer.MIN_VALUE;
			yS = Integer.MAX_VALUE;
			yE = Integer.MIN_VALUE;
			
			if(transformedPoints.get(p0).x < xS)
				xS = transformedPoints.get(p0).x;
			if(transformedPoints.get(p1).x < xS)
				xS = transformedPoints.get(p1).x;
			if(transformedPoints.get(p2).x < xS)
				xS = transformedPoints.get(p2).x;
			
			if(transformedPoints.get(p0).y < yS)
				yS = transformedPoints.get(p0).y;
			if(transformedPoints.get(p1).y < yS)
				yS = transformedPoints.get(p1).y;
			if(transformedPoints.get(p2).y < yS)
				yS = transformedPoints.get(p2).y;
			
			if(transformedPoints.get(p0).x > xE)
				xE = transformedPoints.get(p0).x;
			if(transformedPoints.get(p1).x > xE)
				xE = transformedPoints.get(p1).x;
			if(transformedPoints.get(p2).x > xE)
				xE = transformedPoints.get(p2).x;
			
			if(transformedPoints.get(p0).y > yE)
				yE = transformedPoints.get(p0).y;
			if(transformedPoints.get(p1).y > yE)
				yE = transformedPoints.get(p1).y;
			if(transformedPoints.get(p2).y > yE)
				yE = transformedPoints.get(p2).y;
		}
	}
}