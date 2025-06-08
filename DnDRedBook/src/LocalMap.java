/*
 * A Local Map represents a 10km x 10km area as a heightmap
 * in a 1000x1000 pixel image. Each pixel is therefore a 30ft
 * square, more or less. These heightmaps will be generated
 * using coherent noise techniques (and information from the
 * RegionalMap above us), but can be modified by the user
 * 
 * We might also have a "Dryness map" that gives water amounts
 * per pixel, and perhaps a third quality too (heat map?). The
 * basic point, in any case, is to store detailed information
 * about the terrain on a local level - hills, valleys, rivers
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.stream.IntStream;


public class LocalMap implements RenderQueue.RenderRequester
{
	private int x, y; //what tile am I in my RegionalMap?
	private RegionalMap parent;
	
	private final DataImage32Decimal heightmap;
	private final DataImageByte watermap;
	private final DataImageInt rainflowmap;
	private DrainRecord drainRecord;

	private double[] waterHeightsRec;
	private boolean activityFlag;
	
	public static enum WatermapValue
	{
		NotWater,
		Ocean,
		Lake,
		Unknown;
		
		public static WatermapValue fromByte(byte b)
		{
			return values()[b];
		}
		public byte toByte()
		{
			return (byte) this.ordinal();
		}
	}
	public LocalMap(int x, int y, RegionalMap p)
	{
		this.x = x;
		this.y = y;
		parent = p;
		String name = "Datamap_" + Integer.toString(x) + "_" + Integer.toString(y);
		String fullName = "DMap(" + Integer.toString(parent.GetWorldX());
		fullName += ";" + String.format("%02d", x) + " , ";
		fullName += Integer.toString(parent.GetWorldY());
		fullName += ";" + String.format("%02d", y) + ")";
		heightmap = new DataImage32Decimal(p.GetDirectory(),RegionalMap.K_HEIGHTMAP_FOLDER_NAME, name, fullName, new HeightQuery(), RedBook.heightmaps);
		watermap = new DataImageByte(p.GetDirectory(), RegionalMap.K_WATERMAP_FOLDER_NAME, name, fullName, new WaterQuery(), RedBook.watermaps);
		rainflowmap = new DataImageInt(p.GetDirectory(), RegionalMap.K_RAINFLOWMAP_FOLDER_NAME, name, fullName, new RainflowQuery(), RedBook.rainflowmaps);

	}
	public void RunFullRender(int dimension, String name) {
		ImageRender t = new ImageRender(dimension, dimension, BufferedImage.TYPE_INT_RGB);
		RedBook.tile_renderings.Insert(name, t);
		
		if(RequiresHeightmapToRender())
			heightmap.DemandResolution(dimension);
		if(RequiresWatermapToRender())
			watermap.DemandResolution(dimension);
		if(RequiresRainflowMapToRender())
			rainflowmap.DemandResolution(dimension);
		
		if(Switches.PARALLEL_RENDERING)
		{
			final int[] pixels = ((DataBufferInt) t.getRaster().getDataBuffer()).getData();
			
			IntStream.range(0, dimension * dimension).parallel().forEach(index ->{
				int j = index / dimension;
				int i = index % dimension;
				double localX = i;
				double localY = j;
				localX /= dimension;
				localY /= dimension;
				int rgb = CalculatePixel(localX, localY, 1.0 / dimension);
				pixels[index] = rgb;
			});
		}
		else
		{
			for(int i = 0; i < dimension; i++)
				for(int j = 0; j < dimension; j++)
				{
					double localX = i;
					double localY = j;
					localX /= dimension;
					localY /= dimension;
					int rgb = CalculatePixel(localX, localY, 1.0/dimension);
					t.setRGB(i, j, rgb);
				}
		}
		t.FinishRendering();
	}
	public void RecalculateNearbyCachedPixels(double lX, double lY)
	{
		String renderName = GetIdentification();
		ArrayList<ImageRender> previousRenders = RedBook.tile_renderings.GetAllWithPrefix(renderName);
		for(BufferedImage t : previousRenders)
		{
			int dimension = t.getWidth();
			int i = (int) (lX * dimension);
			int j = (int) (lY * dimension);
			double pixelWidth = 1.0 / dimension;
			int rgb = CalculatePixel(lX, lY, pixelWidth);
			t.setRGB(i, j, rgb);
		}
	}
	public void RecalculateAllCachedPixels()
	{
		String renderName = GetIdentification();
		ArrayList<ImageRender> previousRenders = RedBook.tile_renderings.GetAllWithPrefix(renderName);
		for(BufferedImage t : previousRenders)
		{
			int dimension = t.getWidth();
			for(int i = 0; i < dimension; i++)
				for(int j = 0; j < dimension; j++)
				{
					double localX = i;
					double localY = j;
					localX /= dimension;
					localY /= dimension;
					int rgb = CalculatePixel(localX, localY, 1.0/dimension);
					t.setRGB(i, j, rgb);
				}
		}
	}
	private int CalculatePixelTerrainInterpretation(double lX, double lY, double wX, double wY)
	{	
		int lakeColor = (18 << 16) + (146 << 8) + (201);
		int peakColor = (171 << 16) + (156 << 8) + (135);
		int mounColor = (148 << 16) + (126 << 8) + (40);
		int hillColor = (156 << 16) + (158 << 8) + (93);
		int flatColor = (124 << 16) + (166 << 8) + (88);
		
		Vec2 grad = heightmap.GetGradient(lX, lY);
		grad.Divide(METER_DIM);
		//double elev = heightmap.Get(lX, lY);
		
		Vec3 sunDirection = new Vec3(.354, .354, .866);
		Vec3 gradDirection = new Vec3(-grad.x, -grad.y, 1);
		sunDirection.Normalize();
		gradDirection.Normalize();
		double dot = -1 * sunDirection.Dot(gradDirection);
		
		WatermapValue waterPresent = WatermapValue.fromByte(watermap.Get(lX, lY));
		
		if(waterPresent == WatermapValue.Ocean)
			return CalculateOceanPixel(wX, wY);
		
		if(waterPresent == WatermapValue.Lake)
			return AdjustColorBrightnessFromSunAngle(lakeColor, dot);
		
		if(waterPresent == WatermapValue.Unknown)
			return CalculateTerraIncognitaPixel(wX, wY);
		
		int rainflow = rainflowmap.Get(lX, lY);
		
		int base = 255 << 16;
		if(grad.Len() > 0.5)
			base = peakColor;
		else if(grad.Len() > 0.3)
			base = MathToolkit.SmoothColorLerp(mounColor, peakColor, (grad.Len() - 0.3) / (0.5 - 0.3));
		else if(grad.Len() > 0.17)
			base = MathToolkit.SmoothColorLerp(hillColor, mounColor, (grad.Len() - 0.17) / (0.3 - 0.17));
		else if(grad.Len() > 0.03)
			base = MathToolkit.SmoothColorLerp(flatColor, hillColor, (grad.Len() - 0.03) / (0.17 - 0.03));
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
		
		return AdjustColorBrightnessFromSunAngle(base, dot);
	}
	private int AdjustColorBrightnessFromSunAngle(int baseColor, double surfaceSunDot)
	{
		int red = (baseColor >> 16) & 0xFF;
		int green = (baseColor >> 8) & 0xFF;
		int blue = baseColor & 0xFF;
		
		float[] hsb = Color.RGBtoHSB(red, green, blue, null);
		hsb[2] += 0.4 * (surfaceSunDot - 0.1); 
		
		if(hsb[2] > 1)
			hsb[2] = 1f;
		if(hsb[2] < 0)
			hsb[2] = 0f;
		
		return Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
	}
	private int CalculatePixelContour(double lX, double lY, double wX, double wY, double pixelWidth)
	{
		Vec2 grad = heightmap.GetGradient(lX, lY);
		grad.Divide(METER_DIM);
		double elev = heightmap.Get(lX, lY);
		
		WatermapValue waterPresent = WatermapValue.fromByte(watermap.Get(lX, lY));
		if(waterPresent == WatermapValue.Ocean)
			return CalculateOceanPixel(wX, wY);
		if(waterPresent == WatermapValue.Lake)
			return (105 << 16) + (142 << 8) + 201;
		if(waterPresent == WatermapValue.Unknown)
			return CalculateTerraIncognitaPixel(wX, wY);
		
		boolean isContourPoint = false;
		double thickness = 1;
		double spacing = 200;
		double slope = grad.Len();
		double elevP = elev + slope * pixelWidth * thickness * LocalMap.METER_DIM;
		double elevM = elev - slope * pixelWidth * thickness * LocalMap.METER_DIM;
		if((int) (elev / spacing) != (int) (elevP / spacing))
			isContourPoint = true;
		if((int) (elev / spacing) != (int) (elevM / spacing))
			isContourPoint = true;
		
		
		if(isContourPoint)
			return 0;
					
		int[] cutOffs = new int[] {
				0,
				2000,
				3000,
				5000,
				6000,
				8000
			};
		Color[] colors = new Color[] {
				new Color(164, 191, 165),
				new Color(164, 191, 165),
				new Color(184, 180, 173),
				new Color(184, 180, 173),
				new Color(227, 227, 220),
				new Color(255, 255, 255)
			};
		int index = 0;
		while(index + 1 < cutOffs.length && elev > cutOffs[index + 1])
			index++;
		if(index >= cutOffs.length - 1)
			return (255 << 16) + (255 << 8) + (255);
		int lower = cutOffs[index];
		int upper = cutOffs[index + 1];
		Color lowerC = colors[index];
		Color upperC = colors[index + 1];
		int range = upper - lower;
		double pos = elev - lower;
		double per = 1.0 * pos / range;
		int r = (int) (per * upperC.getRed() + (1 - per) * lowerC.getRed());
		int g = (int) (per * upperC.getGreen() + (1 - per) * lowerC.getGreen());
		int b = (int) (per * upperC.getBlue() + (1 - per) * lowerC.getBlue());
		return (r << 16) + (g << 8) + b;
	}
	private int CalculatePixelCurrentElevation(double lX, double lY, double wX, double wY)
	{
		double elev = heightmap.Get(lX, lY);
		
		if(elev == 0)
			return CalculateOceanPixel(wX, wY);
		
		double bandElev = (500 * Math.pow(elev, 0.333)) % 1000;
		float hue = (float) (bandElev / 1000);
		float brt = (float) (0.2 + elev / 10000);
		
		Color terr = new Color(Color.HSBtoRGB(hue, 0.8f, brt));
		return terr.getRGB();

		
		/*int[] cutOffs = new int[] {
				0, 
				300, 
				600, 
				900, 
				1500,
				2000, 
				2500, 
				3000, 
				4000,
				5000, 
				6000, 
				7000, 
				8000,
				9000, 
				2000000
			};
			Color[] colors = new Color[] {
				new Color(232, 230, 176),
				new Color(123, 227, 116),
				new Color(73, 153, 67),
				new Color(25, 79, 21),
				new Color(157, 194, 72),
				new Color(197, 209, 102),
				new Color(209, 200, 117),
				new Color(176, 149, 84),
				new Color(138, 100, 80),
				new Color(191, 145, 171),
				new Color(196, 179, 201),
				new Color(204, 201, 171),
				new Color(222, 210, 191),
				new Color(196, 196, 194),
				new Color(255, 255, 255)	
			};
			
			int index = 0;
			while(index + 1 < cutOffs.length && elev > cutOffs[index + 1])
				index++;
			if(index >= cutOffs.length - 1)
				return (255 << 16) + (255 << 8) + (255);
			int lower = cutOffs[index];
			int upper = cutOffs[index + 1];
			Color lowerC = colors[index];
			Color upperC = colors[index + 1];
			int range = upper - lower;
			double pos = elev - lower;
			double per = 1.0 * pos / range;
			int r = (int) (per * upperC.getRed() + (1 - per) * lowerC.getRed());
			int g = (int) (per * upperC.getGreen() + (1 - per) * lowerC.getGreen());
			int b = (int) (per * upperC.getBlue() + (1 - per) * lowerC.getBlue());
			return (r << 16) + (g << 8) + b;*/
	}
	private int CalculatePixelElevationGradient(double lX, double lY, double wX, double wY)
	{
		
		float riverHue = 217f / 360f;
		float riverSat = 0.8f;
		float hue = 0f;
		float sat = 0f;
		float brt = 0f;


		Vec2 grad = heightmap.GetGradient(lX, lY);
		grad.Divide(METER_DIM);
		double elev = heightmap.Get(lX, lY);
		
		WatermapValue waterPresent = WatermapValue.fromByte(watermap.Get(lX, lY));
		if(waterPresent == WatermapValue.Ocean)
			return CalculateOceanPixel(wX, wY);
		if(waterPresent == WatermapValue.Lake)
		{
			hue = riverHue;
			sat = riverSat;
		}
		if(waterPresent == WatermapValue.Unknown)
			return CalculateTerraIncognitaPixel(wX, wY);
		
		float e = (float) (0.2 + 0.8 * elev / 8000);
		if(e > 1f)
			e = 1f;
		
		brt = e;
		
		if(waterPresent == WatermapValue.NotWater)
		{
			double angle = Math.atan2(grad.x, grad.y);
			float h = (float) ((angle + Math.PI) / (2 * Math.PI));
			hue = h;
			double mag = grad.Len();
			float g = (float) (0.1 + 0.9 * mag);
			if(g > 1f)
				g = 1f;
			sat = g;
		}
		
		
		Color terr = new Color(Color.HSBtoRGB(hue, sat, brt));
		Color river = new Color(Color.HSBtoRGB(riverHue, riverSat, brt));
		
		double flowPer = parent.DrainageBasedRiverPercent(wX, wY);
		
		int red = (int) (flowPer * river.getRed() + (1 - flowPer) * terr.getRed());
		int green = (int) (flowPer * river.getGreen() + (1 - flowPer) * terr.getGreen());
		int blue = (int) (flowPer * river.getBlue() + (1 - flowPer) * terr.getBlue());
		
		return (red << 16) + (green << 8) + blue;
	}
	private int CalculatePixelPerlinDisplay(double wX, double wY)
	{
		SamplePoint vp = parent.GetNearest(wX / RegionalMap.DIMENSION, wY / RegionalMap.DIMENSION);
		if(vp.IsOcean())
			return CalculateOceanPixel(wX, wY);
		
		double perlinPush = Perlin.rockyJitters.Get(wX / RegionalMap.DIMENSION, wY / RegionalMap.DIMENSION);
		perlinPush *= 2;
		if(perlinPush > 0)
		{
			int red = (int) (255 * perlinPush);
			int green = (int) (255 * (1-perlinPush));
			if(red > 255)
				red = 255;
			if(green < 0)
				green = 0;
			return (red << 16) + (green << 8);
		}
		else
		{
			int blue = (int) (-255 * perlinPush);
			int green = (int) (255 + 255 * perlinPush);
			if(blue > 255)
				blue = 255;
			if(green < 0)
				green = 0;
			return (green << 8) + blue;
		}
	}
	private int CalculatePixelTerrainBasic(double wX, double wY)
	{
		SamplePoint vp = parent.GetNearest(wX / RegionalMap.DIMENSION, wY / RegionalMap.DIMENSION);
		if(vp.IsOcean())
			return CalculateOceanPixel(wX, wY);
		return CalculateTerrainColor(vp.type);
	}
	private int CalculateTerrainColor(TerrainType t)
	{
		if(t.IsTerrainOfType(TerrainTemplate.LAKE) && !t.IsTerrainOfType(TerrainTemplate.FLAT))
			return (71) + (94 << 8) + (99);
		if(t.IsTerrainOfType(TerrainTemplate.PEAKS))
			return (171 << 16) + (156 << 8) + (135);
		if(t.IsTerrainOfType(TerrainTemplate.MOUNTAINS))
			return (148 << 16) + (126 << 8) + (40);
		if(t.IsTerrainOfType(TerrainTemplate.HILLS))
			return (156 << 16) + (158 << 8) + (93);
		if(t.IsTerrainOfType(TerrainTemplate.LAKE))
			return (18 << 16) + (146 << 8) + (201);
		if(t.IsTerrainOfType(TerrainTemplate.FLAT))
			return (124 << 16) + (166 << 8) + (88);
		return (255 << 16);
	}
	private int CalculatePixelTerrainVoronoi(double wX, double wY, String mode)
	{
		SamplePoint vp = parent.GetNearest(wX / RegionalMap.DIMENSION, wY / RegionalMap.DIMENSION);
		if(mode.equals("Pure") && vp.IsOcean())
			return CalculateOceanPixel(wX, wY);
		if(mode.equals("Pure"))
			return vp.myColor.getRGB();
		
		SamplePoint[] tri = VoronoiAlgorithms.FindContainingSampleTriangle(wX / RegionalMap.DIMENSION, wY / RegionalMap.DIMENSION, vp);
		if(tri == null && vp.IsOcean())
			return CalculateOceanPixel(wX, wY);
		if(tri == null)
			return vp.myColor.getRGB();
		
		if(tri[0].IsOcean() && tri[1].IsOcean() && tri[2].IsOcean())
			return CalculateOceanPixel(wX, wY);
		double[] lerp = VoronoiAlgorithms.BarycentricCoordinates(wX / RegionalMap.DIMENSION, wY / RegionalMap.DIMENSION, tri);
		Color c0 = tri[0].myColor;
		Color c1 = tri[1].myColor;
		Color c2 = tri[2].myColor;
		if(mode.equals("Tri"))
		{
			lerp[0] = .333;
			lerp[1] = .333;
			lerp[2] = .333;
		}
		
		int red = (int) (lerp[0] * c0.getRed() + lerp[1] * c1.getRed() + lerp[2] * c2.getRed());
		int green = (int) (lerp[0] * c0.getGreen() + lerp[1] * c1.getGreen() + lerp[2] * c2.getGreen());
		int blue = (int) (lerp[0] * c0.getBlue() + lerp[1] * c1.getBlue() + lerp[2] * c2.getBlue());
		return (red << 16) + (green << 8) + blue;
	}
	private int CalculateOceanPixel(double wX, double wY)
	{
		double perOc = 192 * Perlin.oceans.GetPercentAboveThreshold(
				wX / RegionalMap.DIMENSION, wY / RegionalMap.DIMENSION, 5);
		int v = (int) (192 - 2 * perOc);
		if(v < 40)
			v = 40;
		return v;	
	}
	private int CalculatePixel(double lX, double lY, double pixelWidth)
	{
		double wX = lX + GetWorldX();
		double wY = lY + GetWorldY();
		switch(Switches.CURR_PAINT_TYPE)
		{
		case CONTOUR:
			return CalculatePixelContour(lX, lY, wX, wY, pixelWidth);
		case ELEVATION_CURR:
			return CalculatePixelCurrentElevation(lX, lY, wX, wY);
		case TERRAIN_EVAL:
			return CalculatePixelTerrainInterpretation(lX, lY, wX, wY);
		case ELEV_GRADIENT:
			return CalculatePixelElevationGradient(lX, lY, wX, wY);
		case MIN_MAX_SELECTOR_DISPLAY:
			return CalculatePixelPerlinDisplay(wX, wY);
		case TERRAIN:
			return CalculatePixelTerrainBasic(wX, wY);
		case VORONOI_INTERPOLATED:
			return CalculatePixelTerrainVoronoi(wX, wY, "Interp");
		case VORONOI_PURE:
			return CalculatePixelTerrainVoronoi(wX, wY, "Pure");
		case VORONOI_TRIANGLES:
			return CalculatePixelTerrainVoronoi(wX, wY, "Tri");
		default:
			return 255 << 16;
		}
	}
	private boolean RequiresRainflowMapToRender()
	{
		switch(Switches.CURR_PAINT_TYPE)
		{
		case CONTOUR:
			return true;
		case ELEVATION_CURR:
			return true;
		case TERRAIN_EVAL:
			return true;
		case ELEV_GRADIENT:
			return true;
		case MIN_MAX_SELECTOR_DISPLAY:
			return false;
		case TERRAIN:
			return false;
		case VORONOI_INTERPOLATED:
			return false;
		case VORONOI_PURE:
			return false;
		case VORONOI_TRIANGLES:
			return false;
		default:
			return false;
		}
	}
	private boolean RequiresWatermapToRender()
	{
		switch(Switches.CURR_PAINT_TYPE)
		{
		case CONTOUR:
			return true;
		case ELEVATION_CURR:
			return true;
		case TERRAIN_EVAL:
			return true;
		case ELEV_GRADIENT:
			return true;
		case MIN_MAX_SELECTOR_DISPLAY:
			return false;
		case TERRAIN:
			return false;
		case VORONOI_INTERPOLATED:
			return false;
		case VORONOI_PURE:
			return false;
		case VORONOI_TRIANGLES:
			return false;
		default:
			return false;
		}
	}
	private boolean RequiresHeightmapToRender()
	{
		switch(Switches.CURR_PAINT_TYPE)
		{
		case CONTOUR:
			return true;
		case ELEVATION_CURR:
			return true;
		case TERRAIN_EVAL:
			return true;
		case ELEV_GRADIENT:
			return true;
		case MIN_MAX_SELECTOR_DISPLAY:
			return false;
		case TERRAIN:
			return false;
		case VORONOI_INTERPOLATED:
			return false;
		case VORONOI_PURE:
			return false;
		case VORONOI_TRIANGLES:
			return false;
		default:
			return false;
		}
	}
	
	//Returns true if we already had a WaterHeightsRecord
	public boolean RecordWaterHeights(boolean resetIfExists)
	{
		boolean alreadyExists = waterHeightsRec != null;
		if(alreadyExists && !resetIfExists)
			return true;
		int dim = DataImage.trueDim;
		waterHeightsRec = new double[(dim+1) * (dim + 1)];
		for(int i = 0; i <= dim; i++)
		{
			for(int j = 0; j <= dim; j++)
			{
				WatermapValue val = WatermapValue.fromByte(watermap.Get(i, j));
				if(val == WatermapValue.Unknown || val == WatermapValue.NotWater)
					waterHeightsRec[i * (dim + 1) + j] = -1;
				else
					waterHeightsRec[i * (dim + 1) + j] = heightmap.Get(i, j);
			}
		}
		return alreadyExists;
	}
	
	public void FixWaterHeightsFromRecord(boolean resetRecord)
	{
		if(waterHeightsRec == null)
			return;
		int dim = DataImage.trueDim;
		for(int i = 0; i <= dim; i++)
		{
			for(int j = 0; j <= dim; j++)
			{
				WatermapValue val = WatermapValue.fromByte(watermap.Get(i, j));
				if(val == WatermapValue.Unknown || val == WatermapValue.NotWater)
					continue;
				else
					heightmap.Set(i, j, waterHeightsRec[i * (dim + 1) + j]);
			}
		}
		if(resetRecord)
			waterHeightsRec = null;
	}
	
	//Returns true if, based on the WAterHeightsRecord, it seems like we were already editing
	public boolean PrepareForEditing(boolean height, boolean water, boolean rainflow)
	{
		if(height)
		{
			heightmap.RemoveFromManagement();
			heightmap.ForceEditReady(false);
		}
		if(water)
		{
			watermap.RemoveFromManagement();
			watermap.ForceEditReady(false);
		}
		if(rainflow)
		{
			rainflowmap.RemoveFromManagement();
			rainflowmap.ForceEditReady(false);
		}
		boolean alreadyEditing = false;
		if(height || water)
			alreadyEditing = RecordWaterHeights(false);
		return alreadyEditing;
	}
	public void CompleteEditing(boolean height, boolean water, boolean rainflow, boolean killWaterHeightsRec)
	{
		if(height || water)
			FixWaterHeightsFromRecord(killWaterHeightsRec);
		if(height)
		{
			heightmap.SaveAllResolutions(false);
			heightmap.GiveToManagement(RedBook.heightmaps);
		}
		if(water)
		{
			watermap.SaveAllResolutions(false);
			watermap.GiveToManagement(RedBook.watermaps);
		}
		if(rainflow)
		{
			rainflowmap.SaveAllResolutions(false);
			rainflowmap.GiveToManagement(RedBook.rainflowmaps);
		}
	}
	public void LaplacianErosionIteration(int num)
	{
		boolean alreadyEditing = PrepareForEditing(true, false, false);
		for(int n = 0; n < num; n++)
		{
			double erosionFactor = .05;
			heightmap.InitializePhasedDelta();
			for(int i = 0; i <= DataImage.trueDim; i++)
			{
				for(int j = 0; j <= DataImage.trueDim; j++)
				{
					double x = 1.0 * i / DataImage.trueDim;
					double y = 1.0 * j / DataImage.trueDim;
					
					WatermapValue water = WatermapValue.fromByte(watermap.Get(x, y));
					if(water != WatermapValue.NotWater)
						continue;
					
					double lap = GetHeightLaplacian(x, y);
					double mpp = 1.0 * METER_DIM / DataImage.trueDim; //meters per pixel
					
					double delta = erosionFactor * lap * mpp * mpp;
					heightmap.PrepareDelta(i, j, delta);
				}
			}
			heightmap.FlushDelta();
		}
		CompleteEditing(true,false,false, !alreadyEditing);
	}
	public void SendRandomRainErosion(int numDroplets)
	{
		HashMap<LocalMap, Boolean> used = new HashMap<LocalMap, Boolean>();
		for(int i = 0; i < numDroplets; i++)
		{
			int perCurr = 100 * i / numDroplets;
			int perNext = 100 * (i + 1) / numDroplets;
			if(perCurr != perNext)
				System.out.println(perCurr + "% through sending the rain");
			WaterDroplet nova = new WaterDroplet(this, 1, used, true);
			boolean okay = true;
			while(okay)
			{
				okay = nova.OneErosionStep();
			}
		}
		for(Entry<LocalMap, Boolean> usedMap : used.entrySet())
		{
			usedMap.getKey().CompleteEditing(true, false, false, !usedMap.getValue());
		}
	}
	
	public void ManualHeightSet(int px, int py, double value)
	{
		heightmap.Set(px, py, value);
	}
	public void ManualHeightChange(int px, int py, double amount)
	{
		heightmap.ManualPixelChange(px, py, amount);
	}
	public void SetWatermapValue(int px, int py, WatermapValue set, boolean updateRecordWithCurrentHeight)
	{
		watermap.ManualPixelChange(px, py, set.toByte());
		if(updateRecordWithCurrentHeight && waterHeightsRec != null)
			waterHeightsRec[px * (DataImage.trueDim + 1) + py] = heightmap.Get(px, py);
	}
	public WatermapValue GetWaterPresence(int px, int py)
	{
		return WatermapValue.fromByte(watermap.Get(px, py));
	}
	public int GetRainflow(int px, int py)
	{
		return rainflowmap.Get(px, py);
	}
	public void SendEvenRain()
	{
		rainflowmap.ForceEditReady(false);
		
		//Typically, we'd do <= to hit the boundary pixel
		//But presumably we'll do the adjacent Local Map sometime too,
		//So hitting the boundary pixel would double-rain the boundary.
		//Therefore, we don't do the east / south boundary.
		//(But we do the i, j = 0, which is the west / north boundary
		for(int i = 0; i < DataImage.trueDim; i++)
		{
			for(int j = 0; j < DataImage.trueDim; j++)
			{
				rainflowmap.ManualPixelChange(i, j, 1);
			}
		}
		rainflowmap.SaveAllResolutions(true);
	}
	public void ChangeRainflow(int px, int py, int amount)
	{
		rainflowmap.ManualPixelChange(px, py, amount);
	}
	public double GetHeight(int px, int py)
	{
		return heightmap.Get(px, py);
	}
	//local coordinates - going from 0 to 1 in this local map
	public double GetHeight(double lX, double lY)
	{
		return heightmap.Get(lX, lY);
	}
	public Vec2 GetHeightGradient(double lX, double lY)
	{
		Vec2 grad = heightmap.GetGradient(lX, lY);
		grad.Divide(METER_DIM);
		return grad;
	}
	public double GetHeightLaplacian(double lX, double lY)
	{
		double lap = heightmap.GetLaplacian(lX, lY);
		lap /= (METER_DIM * METER_DIM);
		return lap;
	}
	public DrainRecord.Status GetDrainStatus(int px, int py)
	{
		if(drainRecord == null)
			return DrainRecord.Status.OffMap;
		return drainRecord.GetStatus(px, py);
	}
	public DrainRecord.Dir GetDrainDirection(int px, int py)
	{
		if(drainRecord == null)
			return DrainRecord.Dir.None;
		return drainRecord.GetDirection(px, py);
	}
	public void SetActivityFlag()
	{
		activityFlag = true;
	}
	public void ResetActivityFlag()
	{
		activityFlag = false;
	}
	public boolean ActivityFlagSet()
	{
		return activityFlag;
	}
	public void InitializeDrainRecord()
	{
		if(drainRecord != null)
			return;
		drainRecord = new DrainRecord(new DrainRecordQuery());
	}
	public void DestroyDrainRecord()
	{
		drainRecord = null;
	}
	public void Render(int dim, Graphics2D g2)
	{
		int log = MathToolkit.BinaryLog(dim);
				
		int np2Dim = 1 << (log + 1); //the power of 2 above requested dimension
		np2Dim = np2Dim < 16 ? 16 : np2Dim;
		int cp2Dim = 16;
		
		BufferedImage paint = new BufferedImage(cp2Dim, cp2Dim, BufferedImage.TYPE_INT_RGB);
		Graphics2D pg2 = paint.createGraphics();
		pg2.setColor(Color.gray);
		pg2.fillRect(0, 0, cp2Dim, cp2Dim);
		while(cp2Dim <= np2Dim)
		{
			String renderName = GetIdentification() + ImageArchive.PREFIX_MARKER + cp2Dim;
			ImageRender t = RedBook.tile_renderings.Query(renderName);
			if(t == null)
				RenderQueue.QueueRender(this, cp2Dim, renderName, cp2Dim > 16);
			else if(t.IsReadyToRender())
				paint = t;
			cp2Dim = cp2Dim << 1;
		}
		
		g2.drawImage(paint, 0, 0, dim, dim, null);
	}
	public static int CalculateTerraIncognitaPixel(double wX, double wY)
	{
		wX /= TERRA_INCOGNITA_ZOOM;
		wY /= TERRA_INCOGNITA_ZOOM;
		double perlinNoise = Perlin.terra_incognita.Get(wX, wY);
		int greyVal = (int) (128 + 128 * perlinNoise + 0 * Math.abs(perlinNoise));
		int r = greyVal == 120 ? 0 : greyVal;
		int g = greyVal == 120 ? 255 : greyVal;
		int b = greyVal == 120 ? 0 : greyVal;
		int rgb = (r << 16) + (g << 8) + b;
		greyVal = (int) (128 + 64 * (perlinNoise));
		rgb = (greyVal << 16) + (greyVal << 8) + greyVal;
		return rgb;
	}
	public String GetIdentification()
	{
		return "LocalMap!" + GetWorldX() + "!" + GetWorldY();
	}
	public int GetWorldX()
	{
		return parent.GetWorldX() * RegionalMap.DIMENSION + x;
	}
	public int GetWorldY()
	{
		return parent.GetWorldY() * RegionalMap.DIMENSION + y;
	}
	public LocalMap GetNorth()
	{
		if(y - 1 < 0)
		{
			RegionalMap north = parent.GetNorth();
			if(north == null)
				return null;
			return north.GetLocalMapAt(x, RegionalMap.DIMENSION - 1);
		}
		return parent.GetLocalMapAt(x, y-1);
	}
	public LocalMap GetEast()
	{
		if(x + 1 == RegionalMap.DIMENSION)
		{
			RegionalMap east = parent.GetEast();
			if(east == null)
				return null;
			return east.GetLocalMapAt(0, y);
		}
		return parent.GetLocalMapAt(x + 1, y);
	}
	public LocalMap GetWest()
	{
		if(x - 1 < 0)
		{
			RegionalMap west = parent.GetWest();
			if(west == null)
				return null;
			return west.GetLocalMapAt(RegionalMap.DIMENSION - 1, y);
		}
		return parent.GetLocalMapAt(x - 1, y);
	}
	public LocalMap GetSouth()
	{
		if(y + 1 == RegionalMap.DIMENSION)
		{
			RegionalMap south = parent.GetSouth();
			if(south == null)
				return null;
			return south.GetLocalMapAt(x, 0);
		}
		return parent.GetLocalMapAt(x, y+1);
	}

    @Override
    public int hashCode() {
        int result = (x << 16 | y);
        return result;
    }
    public class Pixel
    {
    	public int x;
    	public int y;
    	public Pixel(int x, int y)
    	{
    		this.x = x;
    		this.y = y;
    	}
    	public Vec2 GetHeightGradient()
    	{
    		return heightmap.GetGradient(x, y);
    	}
    	public double GetHeight()
    	{
    		return heightmap.Get(x, y);
    	}
    	public int GetCurrentRainflow()
    	{
    		return rainflowmap.Get(x, y);
    	}
    	public WatermapValue GetWaterType()
    	{
    		byte waterVal = watermap.Get(x, y);
    		return WatermapValue.fromByte(waterVal);
    	}
    	public boolean IsOcean()
    	{
    		return WatermapValue.fromByte(watermap.Get(x, y)) == WatermapValue.Ocean;
    	}
    	public Pixel GetNorth()
    	{
    		return GetPixelInDir(DrainRecord.Dir.N);
    	}
    	public Pixel GetSouth()
    	{
    		return GetPixelInDir(DrainRecord.Dir.S);
    	}
    	public Pixel GetEast()
    	{
    		return GetPixelInDir(DrainRecord.Dir.E);
    	}
    	public Pixel GetWest()
    	{
    		return GetPixelInDir(DrainRecord.Dir.W);
    	}
    	public Pixel GetNorthEast()
    	{
    		Pixel temp = GetNorth();
    		if(temp != null)
    			return temp.GetEast();
    		temp = GetEast();
    		if(temp != null)
    			return temp.GetNorth();
    		return null;
    	}
    	public Pixel GetNorthWest()
    	{
    		Pixel temp = GetNorth();
    		if(temp != null)
    			return temp.GetWest();
    		temp = GetWest();
    		if(temp != null)
    			return temp.GetNorth();
    		return null;
    	}
    	public Pixel GetSouthWest()
    	{
    		Pixel temp = GetSouth();
    		if(temp != null)
    			return temp.GetWest();
    		temp = GetWest();
    		if(temp != null)
    			return temp.GetSouth();
    		return null;
    	}
    	public Pixel GetSouthEast()
    	{
    		Pixel temp = GetSouth();
    		if(temp != null)
    			return temp.GetEast();
    		temp = GetEast();
    		if(temp != null)
    			return temp.GetSouth();
    		return null;
    	}
    	public Pixel GetPixelInDir(DrainRecord.Dir dir)
    	{
    		if(dir == DrainRecord.Dir.W)
    		{
    			int newX = x - 1;
    			int newY = y;
    			LocalMap par = LocalMap.this;
    			while(newX < 0)
    			{
    				newX += DataImage.trueDim;
    				par = par.GetWest();
    				if(par == null)
    					return null;
    			}
    			return par.new Pixel(newX, newY);
    		}
    		else if(dir == DrainRecord.Dir.E)
    		{

    			int newX = x + 1;
    			int newY = y;
    			LocalMap par = LocalMap.this;
    			while(newX > DataImage.trueDim)
    			{
    				newX -= DataImage.trueDim;
    				par = par.GetEast();
    				if(par == null)
    					return null;
    			}
    			return par.new Pixel(newX, newY);
    		}
    		else if(dir == DrainRecord.Dir.N)
    		{
    			int newX = x;
    			int newY = y - 1;
    			LocalMap par = LocalMap.this;
    			while(newY < 0)
    			{
    				newY += DataImage.trueDim;
    				par = par.GetNorth();
    				if(par == null)
    					return null;
    			}
    			return par.new Pixel(newX, newY);
    		}
    		else if(dir == DrainRecord.Dir.S)
    		{
    			int newX = x;
    			int newY = y + 1;
    			LocalMap par = LocalMap.this;
    			while(newY > DataImage.trueDim)
    			{
    				newY -= DataImage.trueDim;
    				par = par.GetSouth();
    				if(par == null)
    					return null;
    			}
    			return par.new Pixel(newX, newY);
    		}
    		else
    		{
    			return this;
    		}
    	}
    	public DrainRecord.Dir GetDirectionInDrainRecord()
    	{
    		if(drainRecord == null)
    			return DrainRecord.Dir.None;
    		return drainRecord.GetDirection(x, y);
    	}
    	public DrainRecord.Status GetStatusInDrainRecord()
    	{
    		if(drainRecord == null)
    			return DrainRecord.Status.OffMap;
    		return drainRecord.GetStatus(x, y);
    	}
    	public void SetDirectionInDrainRecord(DrainRecord.Dir d)
    	{
    		if(drainRecord == null)
    			return;
    		drainRecord.SetDirection(x, y, d);
    	}
    	public void SetStatusInDrainRecord(DrainRecord.Status s)
    	{
    		if(drainRecord == null)
    			return;
    		drainRecord.SetStatus(x, y, s);
    	}
    	public LocalMap GetParent()
    	{
    		return LocalMap.this;
    	}
    }
    public class DrainRecordQuery
    {
    	private DrainRecordQuery()
    	{
    		
    	}
		public DrainRecord GetNorth() 
		{
			LocalMap next = LocalMap.this.GetNorth();
			if(next == null)
				return null;
			return next.drainRecord;
		}
		public DrainRecord GetSouth()
		{
			LocalMap next = LocalMap.this.GetSouth();
			if(next == null)
				return null;
			return next.drainRecord;
		}
		public DrainRecord GetWest()
		{
			LocalMap next = LocalMap.this.GetWest();
			if(next == null)
				return null;
			return next.drainRecord;
		}
		public DrainRecord GetEast()
		{
			LocalMap next = LocalMap.this.GetEast();
			if(next == null)
				return null;
			return next.drainRecord;
		}
    }
    private class RainflowQuery implements DataImageInt.DataProvider
    {

		@Override
		public int GetData(double x, double y) {
			return 0;
		}
    	
    	@Override
		public DataImageInt GetNorth() 
		{
			LocalMap next = LocalMap.this.GetNorth();
			if(next == null)
				return null;
			return next.rainflowmap;
		}

		@Override
		public DataImageInt GetSouth()
		{
			LocalMap next = LocalMap.this.GetSouth();
			if(next == null)
				return null;
			return next.rainflowmap;
		}

		@Override
		public DataImageInt GetWest()
		{
			LocalMap next = LocalMap.this.GetWest();
			if(next == null)
				return null;
			return next.rainflowmap;
		}

		@Override
		public DataImageInt GetEast()
		{
			LocalMap next = LocalMap.this.GetEast();
			if(next == null)
				return null;
			return next.rainflowmap;
		}
    	
    }
    private class WaterQuery implements DataImageByte.DataProvider
    {

		@Override
		public byte GetData(double x, double y) 
		{
			double wX = x + GetWorldX();
			double wY = y + GetWorldY();
			WatermapValue result = parent.IsWaterPoint(wX, wY);
			return result.toByte();
		}
    	
		@Override
		public DataImageByte GetNorth() 
		{
			LocalMap next = LocalMap.this.GetNorth();
			if(next == null)
				return null;
			return next.watermap;
		}

		@Override
		public DataImageByte GetSouth()
		{
			LocalMap next = LocalMap.this.GetSouth();
			if(next == null)
				return null;
			return next.watermap;
		}

		@Override
		public DataImageByte GetWest()
		{
			LocalMap next = LocalMap.this.GetWest();
			if(next == null)
				return null;
			return next.watermap;
		}

		@Override
		public DataImageByte GetEast()
		{
			LocalMap next = LocalMap.this.GetEast();
			if(next == null)
				return null;
			return next.watermap;
		}
    }
    private class HeightQuery implements DataImage32Decimal.DataProvider
    {

		@Override
		public double GetData(double x, double y) 
		{
			double wX = x + GetWorldX();
			double wY = y + GetWorldY();
			double elev = parent.CalculateElevation(wX, wY);
			return elev;
		}

		@Override
		public DataImage32Decimal GetNorth() 
		{
			LocalMap next = LocalMap.this.GetNorth();
			if(next == null)
				return null;
			return next.heightmap;
		}

		@Override
		public DataImage32Decimal GetSouth()
		{
			LocalMap next = LocalMap.this.GetSouth();
			if(next == null)
				return null;
			return next.heightmap;
		}

		@Override
		public DataImage32Decimal GetWest()
		{
			LocalMap next = LocalMap.this.GetWest();
			if(next == null)
				return null;
			return next.heightmap;
		}

		@Override
		public DataImage32Decimal GetEast()
		{
			LocalMap next = LocalMap.this.GetEast();
			if(next == null)
				return null;
			return next.heightmap;
		}
    	
    }
    public class Coordinate
    {
    	public double x, y;
		public Coordinate(double x, double y)
		{
			this.x = x;
			this.y = y;
		}
		public LocalMap GetLocalMap()
		{
			return LocalMap.this;
		}
    }
    
    //These two fields will eventually replace the two below;
    public static final int METER_DIM = 10240;
    
	public static final double TERRA_INCOGNITA_ZOOM = 1;
}