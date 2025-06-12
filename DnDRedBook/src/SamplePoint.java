import java.awt.Color;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

public class SamplePoint extends MeshPoint
{
	public TerrainType type;
	private double maxGrade;
	public Color myColor;
	private RegionalMap parent;
	private double riverFlow;
	private SamplePoint riverOutlet;
	private ArrayList<SamplePoint> riverInlets;
	public boolean paintMyColor;
	
	private double tectonicUpliftOverride;
	
	public SamplePoint(double x, double y, RegionalMap parent)
	{
		super(x, y);
		int r = (int) (Math.random() * 256);
		int g = (int) (Math.random() * 256);
		int b = (int) (Math.random() * 256);
		myColor = new Color(r,g,b);
		type = new TerrainType();
		this.parent = parent;
		Init();
	}
	public SamplePoint(DataInputStream dis, RegionalMap parent)
	{
		super(dis);
		try
		{
			short typeBits = dis.readShort();
			int rgb = dis.readInt();
			type = new TerrainType(typeBits);
			myColor = new Color(rgb);
			this.parent = parent;
			Init();
		} catch(IOException e)
		{
			e.printStackTrace();
		}
	}
	public SamplePoint(Iterator<String> tokenStream, RegionalMap parent)
	{
		super(tokenStream);
		short typeBits = Short.parseShort(tokenStream.next());
		int rgb = Integer.parseInt(tokenStream.next());
		type = new TerrainType(typeBits);
		myColor = new Color(rgb);
		this.parent = parent;
		Init();
	}
	private void Init()
	{
		paintMyColor = false;
		maxGrade = 0;
		ResetRiver();
		ResetTectonicUplift();
	}
	public void SetAdjacenciesFromDescription(String desc, WorldMap wm)
	{
		String[] tokens = desc.split(" ");
		for(int i = 0; i < tokens.length; i++)
		{
			SamplePoint adj = FindFromGlobalIdentifier(tokens[i], wm);
			if(adj == null)
				continue;
			MarkAdjacent(adj);
		}
	}
	public static SamplePoint FindFromGlobalIdentifier(String desc, WorldMap wm)
	{
		String[] dcr = desc.split(",");
		int px = Integer.parseInt(dcr[0]);
		int py = Integer.parseInt(dcr[1]);
		int index = Integer.parseInt(dcr[2]);
		RegionalMap par = wm.GetRegion(px, py);
		if(par == null)
			return null;
		return par.GetSamplePoint(index);
	}
	public boolean LoadAdjacencies(DataInputStream dis, WorldMap wm)
	{
		try {
			int num = dis.readByte();
			for(int i = 0; i < num; i++)
			{
				SamplePoint adj = LoadFromGlobalIdentifier(dis, wm);
				MarkAdjacent(adj);
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	public static SamplePoint LoadFromGlobalIdentifier(DataInputStream dis, WorldMap wm)
	{
		try
		{
			int px = dis.readByte();
			int py = dis.readByte();
			int index = dis.readInt();
			RegionalMap par = wm.GetRegion(px, py);
			if(par == null)
			{
				return null;

			}
			return par.GetSamplePoint(index);
		}
		catch ( IOException e)
		{
			return null;
		}
	}
	public boolean WriteAdjacencies(DataOutputStream dos)
	{
		try
		{
			boolean worked = true;
			dos.writeByte(GetAdjacentSamples().size());
			for(SamplePoint adj : GetAdjacentSamples())
			{
				worked = worked && adj.WriteGlobalIdentifier(dos);
			}
			return worked;
		}
		catch(IOException e)
		{
			return false;
		}
	}
	public String GetAdjacencyDescription()
	{
		String desc = "";
		ArrayList<SamplePoint> adjacent = GetAdjacentSamples();
		for(int i = 0; i < adjacent.size(); i++)
		{
			SamplePoint adj = adjacent.get(i);
			desc += adj.GetGlobalIdentifier();
			if(i != adjacent.size() - 1)
				desc += " ";
		}
		return desc;
	}
	public String GetGlobalIdentifier()
	{
		String desc = Integer.toString(parent.GetOriginX()) + ",";
		desc += Integer.toString(parent.GetOriginY()) + ",";
		desc += Integer.toString(GetContainerIndex());
		return desc;
	}
	public boolean WriteGlobalIdentifier(DataOutputStream dos)
	{
		try
		{
			dos.writeByte(parent.GetOriginX());
			dos.writeByte(parent.GetOriginY());
			dos.writeInt(GetContainerIndex());
			return true;
		}
		catch(IOException e)
		{
			return false;
		}
	}
	public boolean WriteDescription(DataOutputStream dos)
	{
		if(!super.WriteDescription(dos))
			return false;
		try {
			dos.writeShort(type.BitsToShort());
			dos.writeInt(myColor.getRGB());
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	
	public String GetDescription()
	{
		String desc = super.GetDescription() + " ";
		desc += Short.toString(type.BitsToShort()) + " ";
		desc += Integer.toString(myColor.getRGB());
		return desc;
	}
	public boolean RidgeAssigned()
	{
		for(MeshPoint p : GetAdjacent())
		{
			MeshConnection m = GetConnection(p);
			if(m.IsRidgeline())
				return true;
		}
		return false;
	}
	public ArrayList<SamplePoint> GetRiverInlets()
	{
		return riverInlets;
	}
	public SamplePoint GetRiverOutlet()
	{
		return riverOutlet;
	}
	public boolean RiverFlowProcessed()
	{
		return riverOutlet != null;
	}
	public boolean SetRiverOutlet(SamplePoint p)
	{
		MeshConnection m = GetConnection(p);
		if(m == null)
			return false;
		if(riverOutlet != null)
		{
			MeshConnection res = GetConnection(riverOutlet);
			if(res == null)
				return false;
			res.ResetRiver();
		}
		m.SetRiver();
		riverOutlet = p;
		p.riverInlets.add(this);

		
		return true;
	}
	public void SendFlowToOutlet()
	{
		if(riverOutlet != null)
			riverOutlet.riverFlow += riverFlow;
	}
	public ArrayList<SamplePoint> GrabAdjacentUnassignedRiverFlows(boolean sendRiverFlow)
	{
		ArrayList<SamplePoint> grabbed = new ArrayList<SamplePoint>();
		for(SamplePoint a : GetAdjacentSamples())
		{
			if(a.GetElevation() != GetElevation())
				continue;
			if(a.riverOutlet != null)
				continue;
			
			a.SetRiverOutlet(this);
			grabbed.add(a);
			if(sendRiverFlow)
				a.SendFlowToOutlet();
		}
		
		return grabbed;
	}
	public SamplePoint GetWayDownhill(boolean markOutlet, boolean sendRiverFlow)
	{
		double maxDelta = -1;
		SamplePoint lowest = null;
		for(SamplePoint p : GetAdjacentSamples())
		{
			double delta = GetElevation() - p.GetElevation();
			if(delta > 0 && delta > maxDelta)
			{
				lowest = p;
				maxDelta = delta;
			}
		}
		if(markOutlet && lowest != null)
		{
			SetRiverOutlet(lowest);
			if(sendRiverFlow)
				lowest.riverFlow += riverFlow;
		}
		return lowest;
	}
	public void ResetRiver()
	{
		riverFlow = 1;
		if(riverOutlet != null)
		{
			MeshConnection m = GetConnection(riverOutlet);
			if(m != null)
				m.ResetRiver();
		}
		riverOutlet = null;
		riverInlets = new ArrayList<SamplePoint>();
	}
	public double GetRiverFlow()
	{
		return riverFlow;
	}
	public void ForceSetRiverFlow(double flow)
	{
		riverFlow = flow;
	}
	public void SendRiverFlow()
	{
		SamplePoint lowest = GetWayDownhill(true, true);
		if(lowest == null)
		{
			type.ApplyTerrain(TerrainTemplate.LAKE);
		}
	}
	public RegionalMap GetRegionalMap()
	{
		return parent;
	}
	public boolean NearNorthEdge()
	{
		int j = (int) ((y - parent.GetWorldY()) * RegionalMap.VORONOI_DIM);
		return j < 3;
	}
	public boolean NearSouthEdge()
	{
		int j = (int) ((y - parent.GetWorldY()) * RegionalMap.VORONOI_DIM);
		return j >= RegionalMap.VORONOI_DIM - 3;
	}
	public boolean NearWestEdge()
	{
		int i = (int) ((x - parent.GetWorldX()) * RegionalMap.VORONOI_DIM);
		return i < 3;
	}
	public boolean NearEastEdge()
	{
		int i = (int) ((x - parent.GetWorldX()) * RegionalMap.VORONOI_DIM);
		return i >= RegionalMap.VORONOI_DIM - 3;
	}
	public void CalculateAdjacencies()
	{
		//10 seems sufficient to find all adjacencies, but it occasionally messes up
		//the circumcenter check on real adjacencies. Boosting to 15 seems sufficient
		//to make that check effectively perfect
		ArrayList<SamplePoint> adj = parent.GetNearestN(x, y, 15);
		for(SamplePoint vp : adj)
		{
			double dist = Math.sqrt((vp.x - x) * (vp.x - x) + (vp.y - y) * (vp.y - y));
			dist /= 2;

			double mx = (vp.x + x) / 2;
			double my = (vp.y + y) / 2;
			
			ArrayList<SamplePoint> objectors = new ArrayList<SamplePoint>();
			for(int i = 0; i < adj.size(); i++)
			{
				SamplePoint a = adj.get(i);
				if(a == vp)
					continue;
				double compDist = Math.sqrt((a.x - mx) * (a.x - mx) + (a.y - my) * (a.y - my));
				if(compDist < dist)
				{
					objectors.add(a);
				}
			}
			if(objectors.isEmpty())
			{
				MarkAdjacent(vp);
				continue;
			}
			boolean objection = false;
			for(SamplePoint b : objectors)
			{
				//now we need to find the circumcenter of this, vp, and b; I'm taking this calc from Wikipedia
				double D = 2 * (x * (vp.y - b.y) + vp.x * (b.y - y) + b.x * (y - vp.y));
				double ccx = ((x*x + y*y) * (vp.y - b.y) + (vp.x*vp.x + vp.y*vp.y) * (b.y - y) + (b.x*b.x+b.y*b.y) * (y - vp.y)) / D;
				double ccy = ((x*x + y*y) * (b.x - vp.x) + (vp.x*vp.x + vp.y*vp.y) * (x - b.x) + (b.x*b.x+b.y*b.y) * (vp.x - x)) / D;
				
				double ccr = Math.sqrt((x - ccx) * (x - ccx) + (y - ccy) * (y - ccy));
				//double ccr2 = Math.sqrt((vp.x - ccx) * (vp.x - ccx) + (vp.y - ccy) * (vp.y - ccy));
				//double ccr3 = Math.sqrt((b.x - ccx) * (b.x - ccx) + (b.y - ccy) * (b.y - ccy));

				for(int i = 0; i < adj.size(); i++)
				{
					SamplePoint a = adj.get(i);
					if(a == vp)
						continue;
					if(a == b)
						continue;
					double compDist = Math.sqrt((a.x - ccx) * (a.x - ccx) + (a.y - ccy) * (a.y - ccy));
					if(compDist < ccr)
					{
						objection = true;
						break;
					}
				}
			}
			
			if(!objection)
			{
				MarkAdjacent(vp);
			}
		}
	}
	public ArrayList<SamplePoint> GetAdjacentSamples()
	{
		ArrayList<SamplePoint> samples = new ArrayList<SamplePoint>();
		for(MeshPoint adj : GetAdjacent())
		{
			if(adj instanceof SamplePoint)
				samples.add((SamplePoint) adj);
		}
		return samples;
	}
	public void MakeLake()
	{
		if(type.IsTerrainOfType(TerrainTemplate.OCEAN))
			type = new TerrainType(TerrainTemplate.FLAT);
		type.ApplyTerrain(TerrainTemplate.LAKE);
	}
	public void MakeOcean()
	{
		SetElevation(0);
		type = new TerrainType(TerrainTemplate.OCEAN);
	}
	
	@Override
	public double GetMaxGrade()
	{
		return maxGrade;
	}
	private void AssignMaxGrade(double min, double max)
	{
		double perlinPush = Perlin.minMaxSelector.Get(x, y);
		perlinPush += 1;
		perlinPush /= 2;
		if(perlinPush < 0)
			perlinPush = 0;
		if(perlinPush > 1)
			perlinPush = 1;
		double grade = perlinPush * max + (1 - perlinPush) * min;
		maxGrade = grade;
	}
	public void AssignVoronoiTerrainType()
	{
		if(!Perlin.oceans.UnderThreshold(x, y))
		{
			type = new TerrainType(TerrainTemplate.OCEAN);
			SetElevation(0);
			AssignMaxGrade(0, 0);
			return;
		}
		if(Perlin.peaks.UnderThreshold(x, y) && Perlin.oceans.GetPercentBeneathThreshold(x, y) > 0.09)
		{
			if(!Perlin.randomPasses.UnderThreshold(x, y))
			{
				type = new TerrainType(TerrainTemplate.PEAKS);
				AssignMaxGrade(.5, 1);
				return;
			}
			else
			{
				type = new TerrainType(TerrainTemplate.HILLS);
				AssignMaxGrade(.174, .5);
				return;
			}
		}
		if(Perlin.mountains.UnderThreshold(x, y) && 
				(Perlin.oceans.GetPercentBeneathThreshold(x, y) > 0.04 || Perlin.peaks.UnderThreshold(x, y)))
		{
			if(!Perlin.randomPasses.UnderThreshold(x, y))
			{
				type = new TerrainType(TerrainTemplate.MOUNTAINS);	
				AssignMaxGrade(.342, .766);
				return;
			}
			else
			{
				type = new TerrainType(TerrainTemplate.HILLS);
				AssignMaxGrade(.174, .5);
				return;
			}
		}
		if(Perlin.foothills.UnderThreshold(x, y) && !Perlin.randomPasses.UnderThreshold(x, y))
		{
			type = new TerrainType(TerrainTemplate.HILLS);
			AssignMaxGrade(.174, .5);
			return;
		}
		if(!Perlin.randomLakes.UnderThreshold(x, y))
		{
			type = new TerrainType(TerrainTemplate.FLAT);
			type.ApplyTerrain(TerrainTemplate.LAKE);
			AssignMaxGrade(0, 0);
			return;
		}

		if(!Perlin.randomHills.UnderThreshold(x, y)
				&& Perlin.oceans.GetPercentBeneathThreshold(x, y) > 0.05
				&& Perlin.randomLakes.GetPercentBeneathThreshold(x, y) > 0.08)
		{
			type = new TerrainType(TerrainTemplate.HILLS);
			AssignMaxGrade(.174, .5);
			return;
		}
		
		TerrainType def = new TerrainType(TerrainTemplate.FLAT);
		AssignMaxGrade(.017, .139);
		type = def;
		return;
	}
	
	public static int NumPerlinElevDiffs()
	{
		return 2;
	}
	
	public double GetBaseSedimentDepth()
	{
		if(type.IsTerrainOfType(TerrainTemplate.PEAKS))
			return 0;
		else if(type.IsTerrainOfType(TerrainTemplate.MOUNTAINS))
			return 0.3;
		else if(type.IsTerrainOfType(TerrainTemplate.ROUGH))
			return 2;
		else
			return 30;
	}
	
	@Override
	public double[] GetPerlinElevDiffs()
	{
		if(IsWaterPoint())
			return new double[] {0, 0};
		else if(type.IsTerrainOfType(TerrainTemplate.PEAKS))
			return new double[] {0, 1};
		else if(type.IsTerrainOfType(TerrainTemplate.MOUNTAINS))
			return new double[] {0.1, 0.9};
		else if(type.IsTerrainOfType(TerrainTemplate.HILLS))
			return new double[] {0.4, 0.6};
		else
			return new double[] {1, 0};
	}
	@Override
	public byte GetDetailLevel() {
		return 0;
	}
	@Override
	public boolean IsOcean() {
		return type.IsTerrainOfType(TerrainTemplate.OCEAN);
	}
	@Override
	public boolean IsInlandLake() {
		if(IsOcean())
			return false;
		return type.IsTerrainOfType(TerrainTemplate.LAKE);
	}
	@Override
	public double GetTectonicUplift()
	{
		if(tectonicUpliftOverride != -1)
			return tectonicUpliftOverride;
		//Paper uses 5.01 * 10^-4 (ie 0.0001)
		double adjust = Perlin.upliftAdjust.Get(x, y);
		if(type.IsTerrainOfType(TerrainTemplate.PEAKS))
			return (6.01 + 1.5 * adjust) * 0.0001;
		else if(type.IsTerrainOfType(TerrainTemplate.MOUNTAINS))
			return (4.52 + 1.5 * adjust) * 0.0001;
		else if(type.IsTerrainOfType(TerrainTemplate.HILLS))
			return (adjust > 0 ? (1.25 + 2.5 * adjust) : (1.25 + 0.75 * adjust)) * 0.0001;
		else if(IsOcean())
			return 0;
		else
			return (4.15 + 3.5 * adjust) * 0.00001;
	}
	public void OverrideTectonicUplift(double newUplift)
	{
		tectonicUpliftOverride = newUplift;
	}
	public void ResetTectonicUplift()
	{
		tectonicUpliftOverride = -1;
	}
}