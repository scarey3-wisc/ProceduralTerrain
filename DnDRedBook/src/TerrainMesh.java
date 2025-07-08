import java.util.ArrayList;


public class TerrainMesh extends TriangleMesh
{
	private ArrayList<Integer> rainflows;
	private ArrayList<Vec4> normals;
	private ArrayList<Vec4> transformedNormals;
	public ArrayList<Triangle> tris;
	private Vec3 transformedSun;
	private Vec3 sun;
	private boolean renderReady;
	private LocalMap source;
	private int deltaLX;
	private int deltaLY;
	private Vec4[] corners;
	private Vec4[] transformedCorners;
	
	public TerrainMesh(LocalMap source, int dLX, int dLY)
	{
		renderReady = false;
		sun = new Vec3(.354, .354, .866);
		normals = new ArrayList<Vec4>();
		transformedNormals = new ArrayList<Vec4>();
		rainflows = new ArrayList<Integer>();
		tris = new ArrayList<Triangle>();
		this.source = source;
		deltaLX = dLX;
		deltaLY = dLY;
	}
	public void SetMeshShift(int dLX, int dLY)
	{
		deltaLX = dLX;
		deltaLY = dLY;
	}
	public int GetDeltaLX()
	{
		return deltaLX;
	}
	public int GetDeltaLY()
	{
		return deltaLY;
	}
	public LocalMap GetSource()
	{
		return source;
	}
	public void PopulateMesh()
	{
		double xmin = Integer.MAX_VALUE, ymin = Integer.MAX_VALUE, zmin = Integer.MAX_VALUE;
		double xmax = Integer.MIN_VALUE, ymax = Integer.MIN_VALUE, zmax = Integer.MIN_VALUE;
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
				if(x < xmin)
					xmin = x;
				if(y < ymin)
					ymin = y;
				if(height < zmin)
					zmin = height;
				if(x > xmax)
					xmax = x;
				if(y > ymax)
					ymax = y;
				if(height > zmax)
					zmax = height;
				
				Vec4 loc = new Vec4(x, y, height, 1);
				Vec2 gradient = p.GetHeightGradient();
				gradient.Divide(LocalMap.METER_DIM);
				
				Vec4 grad = new Vec4(-gradient.x, -gradient.y, 1, 0);
				grad.Normalize();
				RegisterPoint(loc, grad);
				rainflows.add(p.GetCurrentRainflow());
			}
		}
		
		corners = new Vec4[]
				{
						new Vec4(xmin, ymin, zmin, 1),
						new Vec4(xmin, ymin, zmax, 1),
						new Vec4(xmin, ymax, zmin, 1),
						new Vec4(xmin, ymax, zmax, 1),
						new Vec4(xmax, ymin, zmin, 1),
						new Vec4(xmax, ymin, zmax, 1),
						new Vec4(xmax, ymax, zmin, 1),
						new Vec4(xmax, ymax, zmax, 1)
				};
		transformedCorners = new Vec4[corners.length];
		for(int i = 0; i < transformedCorners.length; i++)
			transformedCorners[i] = new Vec4(0, 0, 0, 1);
		
		for(int i = 0; i < dim; i++)
		{
			for(int j = 0; j < dim; j++)
			{
				int p00 = (i) * (dim + 1) + (j);
				int p01 = (i) * (dim + 1) + (j + 1);
				int p10 = (i + 1) * (dim + 1) + (j);
				int p11 = (i + 1) * (dim + 1) + (j + 1);
				LocalMap.Pixel loc00 = source.new Pixel(i, j);
				LocalMap.Pixel loc01 = source.new Pixel(i, j + 1);
				LocalMap.Pixel loc10 = source.new Pixel(i + 1, j);
				LocalMap.Pixel loc11 = source.new Pixel(i + 1, j + 1);
				
				Triangle one = new Triangle(p00, p10, p01, loc00, loc10, loc01);
				Triangle two = new Triangle(p11, p01, p10, loc11, loc01, loc10);
				tris.add(one);
				tris.add(two);
			}
		}
		source.CompleteEditing(true, true, true, false);
		renderReady = true;
	}
	public Mat4 GetModelTransform()
	{
		return Mat4.ShiftMatrix(deltaLX * LocalMap.METER_DIM, deltaLY * LocalMap.METER_DIM, 0);
	}
	private void RegisterPoint(Vec4 point, Vec4 normal)
	{
		super.RegisterPoint(point);
		normals.add(normal);
		transformedNormals.add(normal.Clone());
	}
	public boolean VisibleInClipSpace(Mat4 view, Mat4 projection)
	{
		Mat4 model = GetModelTransform();
		boolean xm = true, xp = true, ym = true, yp = true, zm = true, zp = true, km = true;
		for(int i = 0; i < corners.length; i++)
		{
			Vec4 dest = transformedCorners[i];
			Vec4 src = corners[i];
			model.postMultiply(src, dest);
			view.postMultiply(dest, dest);
			projection.postMultiply(dest, dest);
			dest.x /= dest.k;
			dest.y /= dest.k;
			dest.z /= dest.k;
			if(dest.k > 0)
				km = false;
			if(dest.x >= -1)
				xm = false;
			if(dest.x <= 1)
				xp = false;
			if(dest.y >= -1)
				ym = false;
			if(dest.y <= 1)
				yp = false;
			if(dest.z >= 0)
				zm = false;
			if(dest.z <= 1)
				zp = false;

		}
		return !(xm || xp || ym || yp || zm || zp || km);
	}
	public void TransformToClipSpace(Mat4 view, Mat4 projection)
	{
		super.TransformToClipSpace(view, projection);
		Mat4 model = GetModelTransform();
		for(int i = 0; i < GetNumPoints(); i++)
		{
			Vec4 normDest = transformedNormals.get(i);
			Vec4 normSec = normals.get(i);
			model.postMultiply(normSec, normDest);
			view.postMultiply(normDest, normDest);
			normDest.Normalize();
		}
		
		Vec4 sunDirection = new Vec4(sun.x, sun.y, sun.z, 0);
		sunDirection.Normalize();
		view.postMultiply(sunDirection, sunDirection);
		transformedSun= new Vec3(sunDirection.x, sunDirection.y, sunDirection.z);
	}

	@Override
	public boolean ReadyToRender() {
		return renderReady;
	}
	public void FillTriangles(ArrayList<TriangleMesh.Triangle> activeTris, int width, int height)
	{
		boolean filledAny = false;
		for(TriangleMesh.Triangle t : tris)
		{
			if(t.GetP0().k < 0 || t.GetP1().k < 0 || t.GetP2().k < 0)
				continue;
			
			if(t.GetP0().x < -1 && t.GetP1().x < -1 && t.GetP2().x < -1)
				continue;
			if(t.GetP0().x > 1 && t.GetP1().x > 1 && t.GetP2().x > 1)
				continue;
			if(t.GetP0().y < -1 && t.GetP1().y < -1 && t.GetP2().y < -1)
				continue;
			if(t.GetP0().y > 1 && t.GetP1().y > 1 && t.GetP2().y > 1)
				continue;

			if(t.GetP0().z < 0 || t.GetP0().z > 1)
				continue;
			
			if(t.GetP1().z < 0 || t.GetP1().z > 1)
				continue;
			
			if(t.GetP2().z < 0 || t.GetP2().z > 1)
				continue;
			t.CalculateBoundingBox();
			t.CalculateScreenCoordinates(width, height);
			activeTris.add(t);
			filledAny = true;
		}
	}

	public class Triangle extends TriangleMesh.Triangle
	{
		private Vec3 normalBuffer;
		private int color;
		private int lakeColor;
		public Triangle(int p0, int p1, int p2, LocalMap.Pixel pi0, LocalMap.Pixel pi1, LocalMap.Pixel pi2) {
			super(p0, p1, p2);
			normalBuffer = new Vec3(0, 0, 0);
			
			Vec3 normal = ComputeNormal();
			normal.Normalize();
			double dot = sun.Dot(normal);
			dot = Math.max(0, dot);
			
			color = CalculateBaseColor(pi0, pi1, pi2);
			lakeColor = (18 << 16) + (146 << 8) + (201);

			int baseColor = MathToolkit.SmoothColorLerp(0, color, 0.4);
			int baseLake = MathToolkit.SmoothColorLerp(0, lakeColor, 0.4);
			color = MathToolkit.SmoothColorLerp(baseColor, color, dot);
			lakeColor = MathToolkit.SmoothColorLerp(baseLake, lakeColor, dot);
		}
		public int Render(double a, double b, double c)
		{
			int result = color;
			double rainflow = a * rainflows.get(GetI0()) + b * rainflows.get(GetI1()) + c * rainflows.get(GetI2());
			if(rainflow > 500)
			{
				double percent = Math.sqrt(rainflow - 500) / 520;
				if(percent > 1)
					percent = 1;
				if(percent < 0)
					percent = 0;
				result = MathToolkit.SmoothColorLerp(result, lakeColor, percent);
			}
			double zDepth = a * GetD0() + b * GetD1() + c * GetD2();
			double fog = 1 - Math.exp(-zDepth * 0.00005);
			if(fog > 0.8)
				fog = 0.8;
			int grey = (128 << 16) + (128 << 8) + 128;
			result = MathToolkit.SmoothColorLerp(result, grey, fog);
			return result;
		}
		private Vec3 ComputeNormal()
		{
			Vec3 v1 = new Vec3(Vec4.Sum(GetP1(), Vec4.Opposite(GetP0())));
			Vec3 v2 = new Vec3(Vec4.Sum(GetP2(), Vec4.Opposite(GetP0())));
			return v1.Cross(v2);
		}
		private Vec2 ComputeGradient()
		{
			Vec3 normal = ComputeNormal();
			if (normal.z == 0) 
				return new Vec2(0, 0);

		    double dzdx = -normal.x / normal.z;
		    double dzdy = -normal.y / normal.z;
		    return new Vec2(dzdx, dzdy);
		}
		private int CalculateBaseColor(LocalMap.Pixel p0, LocalMap.Pixel p1, LocalMap.Pixel p2)
		{		
			int lakeColor = (18 << 16) + (146 << 8) + (201);
			int peakColor = (171 << 16) + (156 << 8) + (135);
			int mounColor = (148 << 16) + (126 << 8) + (40);
			int hillColor = (156 << 16) + (158 << 8) + (93);
			int flatColor = (124 << 16) + (166 << 8) + (88);
			
			LocalMap.WatermapValue wat0 = p0.GetWaterType();
			LocalMap.WatermapValue wat1 = p1.GetWaterType();
			LocalMap.WatermapValue wat2 = p2.GetWaterType();
			
			if(wat0 == LocalMap.WatermapValue.Lake && wat1 == LocalMap.WatermapValue.Lake && wat2 == LocalMap.WatermapValue.Lake)
				return lakeColor;
			else if(wat0 != LocalMap.WatermapValue.NotWater && wat1 != LocalMap.WatermapValue.NotWater && wat2 != LocalMap.WatermapValue.NotWater)
				return 255;
			
			
			Vec2 gradient = ComputeGradient();
			
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
			return base;
		}
		private void InterpolateNormal(double a, double b, double c, Vec3 dest)
		{
			Vec4 aNorm = transformedNormals.get(GetI0());
			Vec4 bNorm = transformedNormals.get(GetI1());
			Vec4 cNorm = transformedNormals.get(GetI2());
			
			dest.x = aNorm.x * a + bNorm.x * b + cNorm.x * c;
			dest.y = aNorm.y * a + bNorm.y * b + cNorm.y * c;
			dest.z = aNorm.z * a + bNorm.z * b + cNorm.z * c;
			
			dest.Normalize();
		}
	}
}