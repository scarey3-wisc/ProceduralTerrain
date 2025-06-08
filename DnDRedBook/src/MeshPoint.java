import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class MeshPoint
{
	public double x, y;
	private double currElev;
	private ConcurrentHashMap<MeshPoint, MeshConnection> adjacent;
	
	//Temporary storage
	private int searchID;
	private int containerIndex;
	private int tag;
	
	//for uplift algorithm
	private MeshPoint sink;
	private ArrayList<MeshPoint> sources;
	private double totalDrainageArea;
	private double personalDrainageArea;
	private ArrayList<MeshPoint> directlyAdjacent;
		
	public MeshPoint(double x, double y)
	{
		this.x = x;
		this.y = y;
		ResetElevation();
		adjacent = new ConcurrentHashMap<MeshPoint, MeshConnection>();
		searchID = 0;
		ResetContainerIndex();
		ResetDrainage();
		ResetTag();
	}
	public MeshPoint(DataInputStream dis)
	{
		try
		{
			this.x = dis.readDouble();
			this.y = dis.readDouble();
			double elev = dis.readDouble();
			SetElevation(elev);
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		adjacent = new ConcurrentHashMap<MeshPoint, MeshConnection>();
		searchID = 0;
		ResetContainerIndex();
		ResetDrainage();
		ResetTag();
	}
	public MeshPoint(Iterator<String> desc)
	{
		this.x = Double.parseDouble(desc.next());
		this.y = Double.parseDouble(desc.next());
		double elev = Double.parseDouble(desc.next());
		SetElevation(elev);
		adjacent = new ConcurrentHashMap<MeshPoint, MeshConnection>();
		searchID = 0;
		ResetContainerIndex();
		ResetDrainage();
		ResetTag();
	}
	public static boolean ConsumeDescription(DataInputStream dis)
	{
		try {
			dis.readDouble();
			dis.readDouble();
			dis.readDouble();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	public boolean WriteDescription(DataOutputStream dos)
	{
		try {
			dos.writeDouble(x);
			dos.writeDouble(y);
			dos.writeDouble(currElev);
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	
	public String GetDescription()
	{
		String desc = "";
		desc += Double.toString(x) + " ";
		desc += Double.toString(y) + " ";
		desc += Double.toString(currElev);
		return desc;
	}
	public void ResetDirectlyAdjacent()
	{
		directlyAdjacent.clear();
		directlyAdjacent = null;
	}
	public void InitDirectlyAdjacent()
	{
		directlyAdjacent = new ArrayList<MeshPoint>();
		for(MeshPoint p : GetAdjacent())
		{
			MeshPoint adj = GetClosestDirectAdjacency(p);
			directlyAdjacent.add(adj);
		}
	}
	public ArrayList<MeshPoint> GetDirectlyAdjacent()
	{
		return directlyAdjacent;
	}
	public MeshPoint GetDrainageSink()
	{
		return sink;
	}
	public ArrayList<MeshPoint> GetDrainageSources()
	{
		return sources;
	}
	public double GetDrainageArea()
	{
		return totalDrainageArea;
	}
	public double GetPersonalDrainageArea()
	{
		return personalDrainageArea;
	}
	public void CalculateDrainageArea()
	{
		totalDrainageArea = personalDrainageArea;
		for(MeshPoint mp : sources)
		{
			totalDrainageArea += mp.GetDrainageArea();
		}
	}
	public void AssignDrainage()
	{
		for(MeshPoint adj : directlyAdjacent)
		{
			if(adj.GetElevation() >= GetElevation())
				continue;
			if(sink == null)
			{
				sink = adj;
				continue;
			}
			if(IsWaterPoint())
			{
				if(sink.IsWaterPoint() && !adj.IsWaterPoint())
					continue;
				if(adj.IsWaterPoint() && !sink.IsWaterPoint())
				{
					sink = adj;
					continue;
				}
			}
			if(adj.GetElevation() < sink.GetElevation())
				sink = adj;
		}
		if(sink != null)
			sink.sources.add(this);
	}
	//the forced drainage doesn't need to be adjacent to us
	//this is primarily in the case of "local minima", where
	//we need to teleport water from the minima to the nearest
	//pass to keep water running downhill. 
	public void ForceAssignDrainage(MeshPoint targetSink)
	{
		sink = targetSink;
		if(sink != null)
			sink.sources.add(this);
	}
	public void ResetDrainage()
	{
		sink = null;
		sources = new ArrayList<MeshPoint>();
		totalDrainageArea = 0;
	}
	//Just looking at my direct adjacencies, what drains into me?
	public void CalculatePersonalDrainageArea()
	{
		personalDrainageArea = 0;
		ArrayList<MeshPoint> directlyAdjacent = new ArrayList<MeshPoint>();
		for(MeshPoint p : GetAdjacent())
		{
			MeshPoint dir = GetClosestDirectAdjacency(p);
			if(dir != null)
				directlyAdjacent.add(dir);
		}
		Comparator<MeshPoint> compare = new Comparator<MeshPoint>()
		{

			@Override
			public int compare(MeshPoint o1, MeshPoint o2) {
				double theta1 = Math.atan2(o1.x - x, o1.y - y);
				double theta2 = Math.atan2(o2.x - x, o2.y - y);
				if(theta1 < theta2)
					return -1;
				if(theta1 > theta2)
					return 1;
				return 0;
			}
		};
		directlyAdjacent.sort(compare);	
		for(int i = 0; i < directlyAdjacent.size(); i++)
		{
			MeshPoint one = directlyAdjacent.get(i);
			MeshPoint two = directlyAdjacent.get((i + 1) % directlyAdjacent.size());
			double area = VoronoiAlgorithms.TriangleArea(this, one, two);
			personalDrainageArea += area / 3;
		}
	}
	public boolean ContainerIndexSet()
	{
		return containerIndex >= 0;
	}
	public void ResetContainerIndex()
	{
		containerIndex = -1;
	}
	public int GetContainerIndex()
	{
		return containerIndex;
	}
	public void SetContainerIndex(int index)
	{
		containerIndex = index;
	}
	public boolean TagSet()
	{
		return tag >= 0;
	}
	public void ResetTag()
	{
		tag = -1;
	}
	public int GetTag()
	{
		return tag;
	}
	public void SetTag(int t)
	{
		tag = t;
	}
	public double DistTo(MeshPoint vp)
	{
		double dx = vp.x - x;
		double dy = vp.y - y;
		return Math.sqrt(dx * dx + dy * dy);
	}
	public boolean HasZeroElevDiffs()
	{
		double[] perlinElevDiffs = GetPerlinElevDiffs();
		for(int i = 0; i < perlinElevDiffs.length; i++)
		{
			if(perlinElevDiffs[i] != 0)
				return false;
		}
		return true;
	}
	
	public abstract double GetMaxGrade();
	public abstract double GetTectonicUplift();
	public abstract boolean IsOcean();
	public abstract boolean IsInlandLake();
	public boolean IsWaterPoint()
	{
		return IsOcean() || IsInlandLake();
	}
	public abstract double[] GetPerlinElevDiffs();
	public abstract byte GetDetailLevel();
	
	public void ResetAdjacencies()
	{
		for(Entry<MeshPoint, MeshConnection> e : adjacent.entrySet())
		{
			if(e.getValue().MidInitialized())
				e.getValue().GetMid().ResetAdjacencies();
			e.getKey().adjacent.remove(this);
		}
		adjacent = new ConcurrentHashMap<MeshPoint, MeshConnection>();
	}
	protected void ForceRemoveAdjacency(MeshPoint target)
	{
		adjacent.remove(target);
	}
	protected void ForceOneWayAdjacency(MeshPoint target)
	{
		MeshConnection con = new MeshConnection(this, target);
		adjacent.put(target, con);
	}
	protected void MarkAdjacent(MeshPoint p)
	{
		if(adjacent.containsKey(p) && p.adjacent.containsKey(this))
			return;
		if(p.adjacent.containsKey(this))
			p.adjacent.remove(this);
		if(adjacent.containsKey(p))
			adjacent.remove(p);
		MeshConnection con = new MeshConnection(this, p);
		adjacent.put(p, con);
		p.adjacent.put(this, con);
	}
	public MeshConnection GetConnection(MeshPoint adj)
	{
		return adjacent.get(adj);
	}
	public Set<MeshPoint> GetAdjacent()
	{
		return adjacent.keySet();
	}
	
	//The idea here is that our adjacency list might
	//include points that we aren't "directly adjacent to"
	//because the connection has a midpoint; we're directly
	//adjacent to that midpoint. This descends the tree to
	//those midpoints. 
	public MeshPoint GetClosestDirectAdjacency(MeshPoint adj)
	{
		MeshConnection con = GetConnection(adj);
		if(con.GetMid() == null)
			return adj;
		while(con.GetMid() != null)
		{
			adj = con.GetMid();
			con = adj.GetConnection(this);
		}
		return adj;
	}
	public void ResetElevation()
	{
		currElev = Double.MAX_VALUE;
	}
	public double GetElevation()
	{
		return currElev;
	}
	public void SetElevation(double elev)
	{
		currElev = elev;
	}
	public boolean Reached()
	{
		return searchID == CurrentSearchID;
	}
	public void MarkAsReached()
	{
		searchID = CurrentSearchID;
	}
	public static double ConvertVoronoiDistToMeters(double d)
	{
		return d * RegionalMap.DIMENSION * LocalMap.METER_DIM;
	}
	public static void StartNewSearch()
	{
		CurrentSearchID++;
	}
	private static int CurrentSearchID = 0;
}