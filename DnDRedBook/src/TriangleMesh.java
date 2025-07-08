import java.util.ArrayList;

public abstract class TriangleMesh
{
	private ArrayList<Vec4> points;
	private ArrayList<Vec4> transformedPoints;
	private ArrayList<Double> zDepths;
	private int numPoints;
	
	public TriangleMesh()
	{
		points = new ArrayList<Vec4>();
		transformedPoints = new ArrayList<Vec4>();
		zDepths = new ArrayList<Double>();
	}
	public int GetNumPoints()
	{
		return numPoints;
	}
	public Mat4 GetModelTransform()
	{
		return Mat4.IdentityMatrix();
	}
	public void TransformToClipSpace(Mat4 view, Mat4 projection)
	{
		Mat4 model = GetModelTransform();
		for(int i = 0; i < numPoints; i++)
		{
			Vec4 dest = transformedPoints.get(i);
			Vec4 src = points.get(i);
			model.postMultiply(src, dest);
			view.postMultiply(dest, dest);
			double zDepth = -dest.z;
			zDepths.set(i, zDepth);
			projection.postMultiply(dest, dest);
			dest.x /= dest.k;
			dest.y /= dest.k;
			dest.z /= dest.k;
		}
	}
	public void RegisterPoint(Vec4 loc)
	{
		points.add(loc);
		transformedPoints.add(loc.Clone());
		zDepths.add(0.0);
		numPoints++;
	}
	public abstract boolean ReadyToRender();
	public abstract void FillTriangles(ArrayList<Triangle> activeTris, int width, int height);
	public abstract class Triangle
	{
		//indices
		private int p0;
		private int p1;
		private int p2;

		//bounding box, view coordinates
		private double xS, xE, yS, yE;
		
		//screen coordinates
		private double x0, x1, x2, y0, y1, y2;
		private int xL, xR, yU, yD;

		public Triangle(int p0, int p1, int p2)
		{
			this.p0 = p0;
			this.p1 = p1;
			this.p2 = p2;
		}
		public abstract int Render(double a, double b, double c);
		
		public int GetLeftBounds()
		{
			return xL;
		}
		public int GetRightBounds()
		{
			return xR;
		}
		public int GetUpBounds()
		{
			return yU;
		}
		public int GetDownBounds()
		{
			return yD;
		}
		public double GetX0()
		{
			return x0;
		}
		public double GetX1()
		{
			return x1;
		}
		public double GetX2()
		{
			return x2;
		}
		public double GetY0()
		{
			return y0;
		}
		public double GetY1()
		{
			return y1;
		}
		public double GetY2()
		{
			return y2;
		}
		public double GetD0()
		{
			return zDepths.get(p0);
		}
		public double GetD1()
		{
			return zDepths.get(p1);
		}
		public double GetD2()
		{
			return zDepths.get(p2);
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
		public int GetI0()
		{
			return p0;
		}
		public int GetI1()
		{
			return p1;
		}
		public int GetI2()
		{
			return p2;
		}
		public void CalculateScreenCoordinates(int screenWidth, int screenHeight)
		{
			xL = (int) (xS * screenWidth / 2 + screenWidth / 2);
			xR = (int) (xE * screenWidth / 2 + screenWidth / 2);
			
			//this seems flipped here; it's flipped because we're going from coordinates
			//where positive is upwards (view) to coordinates where positive is lower (screen)
			yD = (int) (screenHeight / 2 - yS * screenHeight / 2);
			yU = (int) (screenHeight / 2 - yE * screenHeight / 2);
			
			x0 = (GetP0().x * screenWidth / 2 + screenWidth / 2);
			x1 = (GetP1().x * screenWidth / 2 + screenWidth / 2);
			x2 = (GetP2().x * screenWidth / 2 + screenWidth / 2);
			
			y0 = (screenHeight / 2 - GetP0().y * screenHeight / 2);
			y1 = (screenHeight / 2 - GetP1().y * screenHeight / 2);
			y2 = (screenHeight / 2 - GetP2().y * screenHeight / 2);
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