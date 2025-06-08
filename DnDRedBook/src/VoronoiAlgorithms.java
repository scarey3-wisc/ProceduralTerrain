import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.PriorityQueue;

public class VoronoiAlgorithms
{
	public static void EnsureRiversFlowDownhill(ArrayList<SamplePoint> continent)
	{
		for(SamplePoint s : continent)
		{
			if(s.GetRiverOutlet() == null)
				continue;
			MeshConnection mc = s.GetConnection(s.GetRiverOutlet());
			if(mc == null)
				continue;
			if(!mc.MidInitialized())
				continue;
			if(s.GetRiverOutlet().IsOcean() || s.GetRiverOutlet().type.IsTerrainOfType(TerrainTemplate.LAKE))
				continue;
			
			MeshMidPoint mid = mc.GetMid();
			mid.SetElevation((s.GetElevation() + s.GetRiverOutlet().GetElevation()) / 2);
			double elevChange = 0;
			for(int i = 0; i < Perlin.elevDeltas.length; i++)
			{
				double del = Math.abs(Perlin.elevDeltas[i].Get(mid.x, mid.y));
				double ctr = mid.GetPerlinElevDiffs()[i];
				double amp = Perlin.elevDeltaScales[i];
				double elevIncrease = amp * ctr * del;
				elevChange += elevIncrease;
			}
			mid.SetElevation(mid.GetElevation() - elevChange);
		}
		for(SamplePoint s : continent)
		{
			if(s.GetRiverInlets().size() != 0)
			{
				double elevChange = 0;
				for(int i = 0; i < Perlin.elevDeltas.length; i++)
				{
					double del = Math.abs(Perlin.elevDeltas[i].Get(s.x, s.y));
					double ctr = s.GetPerlinElevDiffs()[i];
					double amp = Perlin.elevDeltaScales[i];
					double elevIncrease = amp * ctr * del;
					elevChange += elevIncrease;
				}
				s.SetElevation(s.GetElevation() - elevChange);
			}
		}
	}
	public static void IncreaseFractureLevel(ArrayList<SamplePoint> area)
	{
		ArrayList<ArrayList<? extends MeshPoint>> allDetailLevels = new ArrayList<ArrayList<? extends MeshPoint>>();
		ArrayList<? extends MeshPoint> currentDetailLevel = area;
		ArrayList<MeshPoint> nextDetailLevel = new ArrayList<MeshPoint>();
		while(true)
		{
			for(MeshPoint mp : currentDetailLevel)
			{
				for(MeshPoint adj : mp.GetAdjacent())
				{
					MeshConnection con = mp.GetConnection(adj);
					if(con == null)
						continue;
					if(con.GetMid() != null)
						nextDetailLevel.add(con.GetMid());
				}
			}
			allDetailLevels.add(currentDetailLevel);
			currentDetailLevel = nextDetailLevel;
			nextDetailLevel = new ArrayList<MeshPoint>();
			if(currentDetailLevel.size() == 0)
				break;
		}
		for(ArrayList<? extends MeshPoint> detLevel : allDetailLevels)
		{
			for(MeshPoint mp : detLevel)
			{
				for(MeshPoint adj : mp.GetAdjacent())
				{
					MeshConnection con = mp.GetConnection(adj);
					if(con == null)
						continue;
					if(con.GetMid() != null)
						continue;
					
					MeshMidPoint newMid = con.GetMid(true);
					if(mp.IsWaterPoint() && adj.IsWaterPoint())
					{
						if(mp.IsOcean())
						{
							newMid.SetOcean();
							newMid.SetElevation(mp.GetElevation());
							newMid.CopyPerlinElevDiffs(mp.GetPerlinElevDiffs());
						}
						else if(adj.IsOcean())
						{
							newMid.SetOcean();
							newMid.SetElevation(adj.GetElevation());
							newMid.CopyPerlinElevDiffs(adj.GetPerlinElevDiffs());
						}
						else
							newMid.SetInlandLake();
					}
					else if(mp.IsWaterPoint())
					{
						newMid.SetElevation(mp.GetElevation());
						newMid.CopyPerlinElevDiffs(mp.GetPerlinElevDiffs());
						if(mp.IsOcean())
							newMid.SetOcean();
						else
							newMid.SetInlandLake();
					}
					else if(adj.IsWaterPoint())
					{
						newMid.SetElevation(adj.GetElevation());
						newMid.CopyPerlinElevDiffs(adj.GetPerlinElevDiffs());
						if(adj.IsOcean())
							newMid.SetOcean();
						else
							newMid.SetInlandLake();
					}
				}
			}
		}
	}
	public static void SetRiverFlow(ArrayList<SamplePoint> continent)
	{
		VoronoiAlgorithms.SortByElevation(continent);
		for(SamplePoint c: continent)
		{
			c.ResetRiver();
		}
		for(int i = continent.size() - 1; i >= 0; i--)
		{
			SamplePoint p = continent.get(i);
			if(p.RiverFlowProcessed())
				continue;
			if(i == 0)
			{
				continent.get(i).SendRiverFlow();
				break;
			}
			
			//We're checking for a lake block; a block of points all at the same elev
			//j is the index of the next point that goes lower
			boolean atOcean = false;
			int j = i - 1;
			while(continent.get(j).GetElevation() == p.GetElevation())
			{
				j--;
				if(j < 0)
				{
					atOcean = true;
					j++;
					break;
				}
			}
			if(atOcean)
				continue;
			
			if(j == i - 1)
			{
				continent.get(i).SendRiverFlow();
				continue;
			}
			
			//These are locations on our lake block which have a way down
			ArrayList<SamplePoint> found = new ArrayList<SamplePoint>();
			LinkedList<SamplePoint> horizon = new LinkedList<SamplePoint>();
			for(int k = i; k != j; k--)
			{
				if(continent.get(k).GetWayDownhill(true, false) != null)
					horizon.addLast(continent.get(k));
			}
			while(!horizon.isEmpty())
			{
				SamplePoint curr = horizon.removeFirst();
				found.add(curr);
				for(SamplePoint adj : curr.GrabAdjacentUnassignedRiverFlows(false))
					horizon.addLast(adj);
			}
			for(int k = found.size() - 1; k >= 0; k--)
			{
				found.get(k).SendFlowToOutlet();
			}
		}
	}
	public static void ConvertCoastalLakeToOcean(ArrayList<SamplePoint> continentArea)
	{
		ArrayList<SamplePoint> lakeShore = VoronoiAlgorithms.FindBoundaryPoints(continentArea, TerrainTemplate.LAKE);
		ArrayList<ArrayList<SamplePoint>> lakes = 
				VoronoiAlgorithms.FindTypeClumps(lakeShore, TerrainTemplate.LAKE, Integer.MAX_VALUE);
		for(ArrayList<SamplePoint> lake : lakes)
		{
			boolean isCoastalLake = false;
			for(SamplePoint l : lake)
			{
				for(SamplePoint a : l.GetAdjacentSamples())
				{
					if(a.type.IsTerrainOfType(TerrainTemplate.OCEAN))
						isCoastalLake = true;
				}
			}
			if(isCoastalLake)
				for(SamplePoint l : lake)
					l.MakeOcean();
		}
	}
	public static void ConvertSeasToLakes(ArrayList<SamplePoint> continentCoast, int maxLakeSize)
	{
		ArrayList<ArrayList<SamplePoint>> seas = 
				VoronoiAlgorithms.FindTypeClumps(continentCoast, TerrainTemplate.OCEAN, maxLakeSize);
		for(ArrayList<SamplePoint> gp : seas)
		{
			for(SamplePoint v : gp)
			{
				v.MakeLake();
			}
		}
	}
	public static double[] BarycentricCoordinatesDY(double x, double y, MeshPoint[] vertices)
	{
		MeshPoint a = vertices[0];
		MeshPoint b = vertices[1];
		MeshPoint c = vertices[2];
		double abc = SignedTriangleArea(a.x, a.y, b.x, b.y, c.x, c.y);
		double pbc = SignedTriangleAreaDY(x, y, b.x, b.y, c.x, c.y, 1);
		double apc = SignedTriangleAreaDY(a.x, a.y, x, y, c.x, c.y, 2);
		double abp = SignedTriangleAreaDY(a.x, a.y, b.x, b.y, x, y, 3);
		return new double[] {pbc / abc, apc / abc, abp / abc};
	}
	private static double SignedTriangleAreaDY(double x1, double y1, double x2, double y2, double x3, double y3, int whichCoord)
	{
		if(whichCoord == 1)
		{
			return 0.5 * (x3-x2);
		}
		if(whichCoord == 2)
		{
			return 0.5 * (x1-x3);
		}
		if(whichCoord == 3)
		{
			return 0.5 * (x2-x1);
		}
		return 0;
	}
	public static double[] BarycentricCoordinatesDX(double x, double y, MeshPoint[] vertices)
	{
		MeshPoint a = vertices[0];
		MeshPoint b = vertices[1];
		MeshPoint c = vertices[2];
		double abc = SignedTriangleArea(a.x, a.y, b.x, b.y, c.x, c.y);
		double pbc = SignedTriangleAreaDX(x, y, b.x, b.y, c.x, c.y, 1);
		double apc = SignedTriangleAreaDX(a.x, a.y, x, y, c.x, c.y, 2);
		double abp = SignedTriangleAreaDX(a.x, a.y, b.x, b.y, x, y, 3);
		return new double[] {pbc / abc, apc / abc, abp / abc};
	}
	private static double SignedTriangleAreaDX(double x1, double y1, double x2, double y2, double x3, double y3, int whichCoord)
	{
		if(whichCoord == 1)
		{
			return 0.5 * (y2-y3);
		}
		if(whichCoord == 2)
		{
			return 0.5 * (y3-y1);
		}
		if(whichCoord == 3)
		{
			return 0.5 * (y1-y2);
		}
		return 0;
	}
	public static double[] BarycentricCoordinates(double x, double y, MeshPoint[] vertices)
	{
		MeshPoint a = vertices[0];
		MeshPoint b = vertices[1];
		MeshPoint c = vertices[2];
		double abc = SignedTriangleArea(a.x, a.y, b.x, b.y, c.x, c.y);
		double pbc = SignedTriangleArea(x, y, b.x, b.y, c.x, c.y);
		double apc = SignedTriangleArea(a.x, a.y, x, y, c.x, c.y);
		double abp = SignedTriangleArea(a.x, a.y, b.x, b.y, x, y);
		return new double[] {pbc / abc, apc / abc, abp / abc};
	}
	private static double SignedTriangleArea(double x1, double y1, double x2, double y2, double x3, double y3)
	{
		//Area = (1/2) [x1 (y2 – y3) + x2 (y3 – y1) + x3 (y1 – y2)]
		return 0.5 * (x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2));
	}
	public static double TriangleArea(MeshPoint a, MeshPoint b, MeshPoint c)
	{
		double signedArea = SignedTriangleArea(
				a.x, a.y, 
				b.x, b.y, 
				c.x, c.y);
		return Math.abs(signedArea);
	}
	public static double TriangleArea(MeshPoint[] vertices)
	{
		if(vertices.length != 3)
			return -1;
		double signedArea = SignedTriangleArea(
				vertices[0].x, vertices[0].y, 
				vertices[1].x, vertices[1].y, 
				vertices[2].x, vertices[2].y);
		return Math.abs(signedArea);
	}
	public static MeshPoint[] FindContainingTriangle(double x, double y, SamplePoint seed)
	{
		MeshPoint[] curr = FindContainingSampleTriangle(x, y, seed);
		if(curr == null)
			return null;
		while(curr != null)
		{
			boolean subtriangleFound = false;
			double[] coords = BarycentricCoordinates(x,y, curr);
			for(int i = 0; i < 3; i++)
			{
				if(coords[i] >= 0.5)
				{
					MeshConnection a = MeshConnection.FindConnection(curr[i], curr[(i + 1) % 3]);
					MeshConnection b = MeshConnection.FindConnection(curr[i], curr[(i + 2) % 3]);
					if(a == null || b == null)
						return curr;
					if(!a.MidInitialized() && !b.MidInitialized())
						return curr;
					else if(a.MidInitialized() && b.MidInitialized())
					{
						curr = new MeshPoint[] {curr[i], a.GetMid(), b.GetMid()};
						subtriangleFound = true;
					}
					else if(a.MidInitialized() && !b.MidInitialized())
					{
						MeshPoint bMid = new MeshMidPoint(b, false);
						curr = new MeshPoint[] {curr[i], a.GetMid(), bMid};
						subtriangleFound = true;
					}
					else if(!a.MidInitialized() && b.MidInitialized())
					{
						MeshPoint aMid = new MeshMidPoint(a, false);
						curr = new MeshPoint[] {curr[i], aMid, b.GetMid()};
						subtriangleFound = true;
					}
				}
			}
			if(!subtriangleFound)
			{
				MeshConnection a = MeshConnection.FindConnection(curr[0], curr[1]);
				MeshConnection b = MeshConnection.FindConnection(curr[0], curr[2]);
				MeshConnection c = MeshConnection.FindConnection(curr[1], curr[2]);
				if(a == null || b == null || c == null)
					return curr;
				int numMids = 0;
				if(a.MidInitialized())
					numMids++;
				if(b.MidInitialized())
					numMids++;
				if(c.MidInitialized())
					numMids++;
				if(numMids == 3)
					curr = new MeshPoint[] {a.GetMid(), b.GetMid(), c.GetMid()};
				else if(numMids == 2)
				{
					MeshPoint aMid = a.GetMid();
					if(aMid == null)
						aMid = new MeshMidPoint(a, false);
					MeshPoint bMid = b.GetMid();
					if(bMid == null)
						bMid = new MeshMidPoint(b, false);
					MeshPoint cMid = c.GetMid();
					if(cMid == null)
						cMid = new MeshMidPoint(c, false);
					curr = new MeshPoint[] {aMid, bMid, cMid};
				}
				else
					return curr;				
			}
		}
		return curr;
		
	}
	public static SamplePoint[] FindContainingSampleTriangle(double x, double y, SamplePoint seed)
	{
		SamplePoint[] results = new SamplePoint[3];
		results[0] = seed;
		int attempts = 0;
		while(attempts < 5)
		{
			attempts++;
			SamplePoint curr = results[0];
			double vx = x - curr.x;
			double vy = y - curr.y;
			double vD = Math.sqrt(vx * vx + vy * vy);
			vx /= vD;
			vy /= vD;
			SamplePoint best = null;
			double bestDot = -1;
			for(SamplePoint a : curr.GetAdjacentSamples())
			{
				double avx = a.x - curr.x;
				double avy = a.y - curr.y;
				double avD = Math.sqrt(avx * avx + avy * avy);
				avx /= avD;
				avy /= avD;
				double dot = vx * avx + vy * avy;
				if(best == null || dot > bestDot)
				{
					best = a;
					bestDot = dot;
				}
			}
			SamplePoint bestOtherSide = null;
			double bestOtherDot = -1;
			for(SamplePoint b : curr.GetAdjacentSamples())
			{
				if(b == null)
					continue;
				if(b == best)
					continue;
				double bvx = b.x - curr.x;
				double bvy = b.y - curr.y;
				double bvD = Math.sqrt(bvx * bvx + bvy * bvy);
				bvx /= bvD;
				bvy /= bvD;
				double dot = vx * bvx + vy * bvy;
				
				double avx = best.x - curr.x;
				double avy = best.y - curr.y;
				double avD = Math.sqrt(avx * avx + avy * avy);
				avx /= avD;
				avy /= avD;
				
				//using the cross product to check that the vector to our point lies between the two edge vectors
				double aXv = avy * vx - avx * vy;
				double aXb = avy * bvx - avx * bvy;
				double bXv = bvy * vx - bvx * vy;
				if (aXv * aXb >= 0 && bXv * aXb * -1 >= 0)
				{
					if(bestOtherSide == null || dot > bestOtherDot)
					{
						bestOtherSide = b;
						bestOtherDot = dot;
					}
				}
			}
			if(best == null || bestOtherSide == null)
				return null;
			if(curr.DistTo(best) < curr.DistTo(bestOtherSide))
			{
				results[1] = best;
				results[2] = bestOtherSide;
			}
			else
			{
				results[1] = bestOtherSide;
				results[2] = best;
			}
			//so now we have a candidate triangle: are we inside it?
			double[] bcCoords = BarycentricCoordinates(x, y, results);
			if(bcCoords[0] < 0 || bcCoords[1] < 0 || bcCoords[2] < 0)
			{
				results[0] = results[1];
				results[1] = null;
				results[2] = null;
				//return null;
			}
			else
			{
				return results;
			}
		}
		return null;
	}
	private static void ClearHeights(ArrayList<SamplePoint> coastalPoints)
	{
		LinkedList<SamplePoint> frontier = new LinkedList<SamplePoint>();
		SamplePoint.StartNewSearch();
		for(SamplePoint vp : coastalPoints)
		{
			if(vp.IsOcean() || vp.Reached())
				continue;
			vp.MarkAsReached();
			frontier.addLast(vp);
		}
		while(!frontier.isEmpty())
		{
			SamplePoint curr = frontier.removeFirst();
			curr.ResetElevation();
			for(SamplePoint vp : curr.GetAdjacentSamples())
			{
				if(vp.IsOcean() || vp.Reached())
					continue;
				frontier.addLast(vp);
				vp.MarkAsReached();
			}
		}
	}
	public static void AssignHeights(ArrayList<SamplePoint> coastalPoints)
	{
		ClearHeights(coastalPoints);
		PriorityQueue<VoronoiNode> frontier = new PriorityQueue<VoronoiNode>();
		SamplePoint.StartNewSearch();
		for(SamplePoint vp : coastalPoints)
		{
			if(vp.IsOcean())
				continue;
			double distToOcean = -1;
			for(SamplePoint adj : vp.GetAdjacentSamples())
			{
				if(adj.IsOcean())
				{
					double dist = vp.DistTo(adj);
					if(distToOcean == -1 || dist < distToOcean)
						distToOcean = dist;
				}
			}
			if(distToOcean == -1)
				continue;
			distToOcean = SamplePoint.ConvertVoronoiDistToMeters(distToOcean / 2);
			double grade = QueryGrade(TerrainType.OCEAN, vp);
			
			double delta = distToOcean * grade;
			vp.SetElevation(delta);
			frontier.offer(new VoronoiNode(vp, vp.GetElevation()));
		}
		while(!frontier.isEmpty())
		{
			VoronoiNode curr = frontier.poll();
			if(curr.v.Reached())
				continue;
			curr.v.MarkAsReached();
			for(SamplePoint adj : curr.v.GetAdjacentSamples())
			{
				if(adj.IsOcean() || adj.Reached())
					continue;
				double distTo = SamplePoint.ConvertVoronoiDistToMeters(curr.v.DistTo(adj));
				if(curr.v.type.IsTerrainOfType(TerrainType.LAKE) || curr.v.type.IsTerrainOfType(TerrainTemplate.OCEAN))
					distTo /= 2;
				
				double grade = QueryGrade(curr.v.type, adj);
				
				double candDelta = distTo * grade;
				double candValue = curr.v.GetElevation() + candDelta;
				if(candValue < adj.GetElevation())
				{
					adj.SetElevation(candValue);
					frontier.offer(new VoronoiNode(adj, adj.GetElevation()));
				}
				
			}
		}
	}
	public static void SortByElevation(ArrayList<? extends MeshPoint> continent)
	{
		Comparator<MeshPoint> compare = new Comparator<MeshPoint>()
		{

			@Override
			public int compare(MeshPoint o1, MeshPoint o2) {
				if(o1.GetElevation() < o2.GetElevation())
					return -1;
				if(o1.GetElevation() > o2.GetElevation())
					return 1;
				return 0;
			}
		};
		continent.sort(compare);	
	}
	
	public static ArrayList<SamplePoint> FindAllOfType(ArrayList<SamplePoint> area, TerrainTemplate target)
	{
		ArrayList<SamplePoint> found = new ArrayList<SamplePoint>();
		for(SamplePoint sp : area)
			if(sp.type.IsTerrainOfType(target))
				found.add(sp);
		return found;
	}
	//NOTE: the seeds are assumed to be *adjacent* to Terrain of the target type.
	//The classic use case here is having coastal 
	public static ArrayList<ArrayList<SamplePoint>> FindTypeClumps(ArrayList<SamplePoint> seeds, TerrainTemplate target, int maxNum)
	{
		ArrayList<ArrayList<SamplePoint>> found = new ArrayList<ArrayList<SamplePoint>>();
		SamplePoint.StartNewSearch();
		for(SamplePoint cs : seeds)
		{
			for(SamplePoint ts : cs.GetAdjacentSamples())
			{
				ArrayList<SamplePoint> group = new ArrayList<SamplePoint>();
				if(ts.Reached())
					continue;
				if(!ts.type.IsTerrainOfType(target))
					continue;
				LinkedList<SamplePoint> horizon = new LinkedList<SamplePoint>();
				ts.MarkAsReached();
				horizon.addLast(ts);
				while(!horizon.isEmpty())
				{
					SamplePoint curr = horizon.removeFirst();
					group.add(curr);
					for(SamplePoint adj : curr.GetAdjacentSamples())
					{
						if(adj.Reached())
							continue;
						if(!adj.type.IsTerrainOfType(target))
							continue;
						adj.MarkAsReached();
						horizon.addLast(adj);
					}
				}
				if(group.size() <= maxNum)
					found.add(group);
			}
		}
		return found;
	}
	public static ArrayList<SamplePoint> FindAllWithinBoundary(SamplePoint seed, TerrainTemplate boundaryType)
	{
		ArrayList<SamplePoint> listedSeeds = new ArrayList<SamplePoint>();
		listedSeeds.add(seed);
		return FindAllWithinBoundary(listedSeeds, boundaryType);
	}
	public static ArrayList<SamplePoint> FindAllWithinBoundary(ArrayList<SamplePoint> seeds, TerrainTemplate boundaryType)
	{
		SamplePoint.StartNewSearch();
		ArrayList<SamplePoint> found = new ArrayList<SamplePoint>();
		LinkedList<SamplePoint> horizon = new LinkedList<SamplePoint>();
		for(SamplePoint seed : seeds)
		{
			if(seed.type.IsTerrainOfType(boundaryType))
				continue;
			if(seed.Reached())
				continue;
			horizon.add(seed);
			seed.MarkAsReached();
		}

		while(!horizon.isEmpty())
		{
			SamplePoint curr = horizon.removeFirst();
			for(SamplePoint vp : curr.GetAdjacentSamples())
			{
				if(vp.Reached())
					continue;
				if(!vp.type.IsTerrainOfType(boundaryType))
				{
					vp.MarkAsReached();
					horizon.addLast(vp);
				}
			}
			found.add(curr);
		}
		return found;
	}
	public static ArrayList<SamplePoint> FindBoundaryPoints(SamplePoint seed, TerrainTemplate boundaryTo)
	{
		ArrayList<SamplePoint> listedSeeds = new ArrayList<SamplePoint>();
		listedSeeds.add(seed);
		return FindBoundaryPoints(listedSeeds, boundaryTo);
	}
	public static ArrayList<SamplePoint> FindBoundaryPoints(ArrayList<SamplePoint> seeds, TerrainTemplate boundaryTo)
	{
		SamplePoint.StartNewSearch();
		ArrayList<SamplePoint> found = new ArrayList<SamplePoint>();
		LinkedList<SamplePoint> horizon = new LinkedList<SamplePoint>();
		for(SamplePoint seed : seeds)
		{
			if(!seed.Reached())
				horizon.add(seed);
			seed.MarkAsReached();
		}
		while(!horizon.isEmpty())
		{
			SamplePoint curr = horizon.removeFirst();
			if(curr.type.IsTerrainOfType(boundaryTo))
			{
				for(SamplePoint vp : curr.GetAdjacentSamples())
				{
					if(vp.Reached())
						continue;
					vp.MarkAsReached();
					if(vp.type.IsTerrainOfType(boundaryTo))
						horizon.addLast(vp);
					else
						found.add(vp);
				}
			}
			else
			{
				boolean amBoundary = false;
				for(SamplePoint vp : curr.GetAdjacentSamples())
				{
					if(vp.type.IsTerrainOfType(boundaryTo))
						amBoundary = true;
					if(vp.Reached())
						continue;
					if(!vp.type.IsTerrainOfType(boundaryTo))
					{
						vp.MarkAsReached();
						horizon.addLast(vp);
					}
				}
				if(amBoundary)
					found.add(curr);
			}
		}
		return found;
	}
	private static class VoronoiNode implements Comparable<VoronoiNode>
	{
		private double dist;
		public SamplePoint v;
		public VoronoiNode(SamplePoint vp, double dist)
		{
			v = vp;
			this.dist = dist;
		}
		@Override
		public int compareTo(VoronoiNode o) {
			if(dist > o.dist)
				return 1;
			if(dist < o.dist)
				return -1;
			return 0;
		}
	}
	private static double QueryGrade(TerrainTemplate from, SamplePoint to)
	{
		double minGrade = QueryGrade(from, to.type, true);
		double maxGrade = QueryGrade(from, to.type, false);
		double perlinPush = Perlin.minMaxSelector.Get(to.x, to.y);
		perlinPush += 1;
		perlinPush /= 2;
		if(perlinPush < 0)
			perlinPush = 0;
		if(perlinPush > 1)
			perlinPush = 1;
		double grade = perlinPush * maxGrade + (1 - perlinPush) * minGrade;
		return grade;
	}
	private static double QueryGrade(TerrainTemplate from, TerrainTemplate to, boolean min)
	{
		int fromIndex = GradeQueryIndex(from);
		int toIndex = GradeQueryIndex(to);
		if(min)
			return GRADE_MIN_MATRIX[fromIndex][toIndex];
		else
			return GRADE_MAX_MATRIX[fromIndex][toIndex];
	}
	private static int GradeQueryIndex(TerrainTemplate type)
	{
		if(type.IsTerrainOfType(TerrainTemplate.PEAKS))
			return 4;
		else if(type.IsTerrainOfType(TerrainTemplate.MOUNTAINS))
			return 3;
		else if(type.IsTerrainOfType(TerrainTemplate.HILLS))
			return 2;
		else if(type.IsTerrainOfType(TerrainTemplate.OCEAN))
			return 1;
		else if(type.IsTerrainOfType(TerrainTemplate.LAKE))
			return 1;
		else
			return 0;
	}
	private static double[][] GRADE_MAX_MATRIX = new double[][]
	{
		{0.0040, 0.0000, 0.0250, 0.1800, 0.3000}, //From flatland to...
		{0.0040, 0.0000, 0.1500, 0.5000, 0.5000}, //From ocean to...
		{0.0005, 0.0000, 0.0200, 0.1800, 0.3000}, //From hills to...
		{0.0005, 0.0000, 0.0100, 0.0300, 0.3000}, //From mountains to...
		{0.0005, 0.0000, 0.0100, 0.0100, 0.3000}  //From peaks to...
	};
	private static double[][] GRADE_MIN_MATRIX = new double[][]
	{
		{0.0005, 0.0000, 0.0070, 0.0300, 0.1000}, //From flatland to...
		{0.0005, 0.0000, 0.0800, 0.1800, 0.2000}, //From ocean to...
		{0.0001, 0.0000, 0.0050, 0.0500, 0.1000}, //From hills to...
		{0.0001, 0.0000, 0.0010, 0.0100, 0.1000}, //From mountains to...
		{0.0001, 0.0000, 0.0010, 0.0100, 0.1000}  //From peaks to...
	};
}