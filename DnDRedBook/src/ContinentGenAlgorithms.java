import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ContinentGenAlgorithms
{
	public static void RunDrainageCalculation(ArrayList<SamplePoint> continent)
	{
		TectonicUpliftAlgorithm myAlgo = new TectonicUpliftAlgorithm();
		myAlgo.InitGraphVertices(continent);
		myAlgo.CalcStreamTrees();
		myAlgo.RunSingleDrainageCalc();
		myAlgo.ResetTemporaryStorage();
	}
	public static void BlurUpliftForTectonicAlgorithm(ArrayList<SamplePoint> continent, int numIterations, double centerWeight)
	{
		ArrayList<Double> nextUplift = new ArrayList<Double>(continent.size());
		for(int i = 0; i < continent.size(); i++)
			nextUplift.add(0.);
		
		for(int i = 0; i < numIterations; i++)
		{
			IntStream indexStream = IntStream.range(0,  continent.size());
			if(Switches.PARALLEL_CONTINENT_GEN)
				indexStream = indexStream.parallel();
			indexStream.forEach(index ->{
				SamplePoint sp = continent.get(index);
				double upliftSum = centerWeight * sp.GetTectonicUplift();
				double weightTotal = centerWeight;
				for(SamplePoint adj : sp.GetAdjacentSamples())
				{
					if(adj.IsOcean())
						continue;
					weightTotal+=1;
					upliftSum += adj.GetTectonicUplift();
				}
				double newUplift = upliftSum / weightTotal;
				nextUplift.set(index, newUplift);
			});
			
			indexStream = IntStream.range(0,  continent.size());
			if(Switches.PARALLEL_CONTINENT_GEN)
				indexStream = indexStream.parallel();
			indexStream.forEach(index ->{
				SamplePoint sp = continent.get(index);
				double newUplift = nextUplift.get(index);
				sp.OverrideTectonicUplift(newUplift);
			});
		}
	}
	public static int RunTectonicUpliftAlgorithm(
			ArrayList<SamplePoint> continent, double dt, double k, 
			int maxIterations, double completionPercentage)
	{
		TectonicUpliftAlgorithm myAlgo = new TectonicUpliftAlgorithm();
		myAlgo.InitGraphVertices(continent);
		int changeThreshold = (int) (myAlgo.NumIteriorPoints() * completionPercentage);
		int numIterations = 0;
		long currTime, nextTime, deltaTime;
		for(; numIterations < maxIterations; numIterations++)
		{
			System.out.print("It #: " + numIterations + ", ");
			currTime = System.currentTimeMillis();
			myAlgo.CalcStreamTrees();
			nextTime = System.currentTimeMillis();
			deltaTime = nextTime - currTime;
			currTime = nextTime;
			System.out.print("Tree: " + deltaTime + ", ");
			myAlgo.UpdateStreamTreesWithLakeOverflow();
			nextTime = System.currentTimeMillis();
			deltaTime = nextTime - currTime;
			currTime = nextTime;
			System.out.print("Lake: " + deltaTime + ", ");
			myAlgo.RunFullSolveStep(dt, k);
			nextTime = System.currentTimeMillis();
			deltaTime = nextTime - currTime;
			currTime = nextTime;
			System.out.print("Solver Total: " + deltaTime + ", ");
			int changes = myAlgo.UpdateSinkIndicies();
			nextTime = System.currentTimeMillis();
			deltaTime = nextTime - currTime;
			currTime = nextTime;
			System.out.print("Topo: " + deltaTime + ", ");
			System.out.println("Changes: " + changes + "(" + changeThreshold + ")");
			if(changes < changeThreshold)
				break;
		}
		
		myAlgo.ResetTemporaryStorage();
		return numIterations;
	}
	private static class TectonicUpliftAlgorithm
	{
		private ArrayList<MeshPoint> boundary;
		private ArrayList<MeshPoint> interior;
		private ArrayList<MeshPoint> roots;
		private ArrayList<Integer> sinkIndices;
		private ArrayList<Integer> recordedContainerIndices;
		public TectonicUpliftAlgorithm()
		{
			boundary = new ArrayList<MeshPoint>();
			interior = new ArrayList<MeshPoint>();
			roots = new ArrayList<MeshPoint>();
			sinkIndices = new ArrayList<Integer>();
			recordedContainerIndices = new ArrayList<Integer>();
		}
		public int UpdateSinkIndicies()
		{
			IntStream stream = IntStream.range(0, boundary.size() + interior.size());
			if(Switches.PARALLEL_CONTINENT_GEN)
				stream = stream.parallel();
			long count = stream.filter(index -> 
			{
				ArrayList<MeshPoint> chosen = boundary;
				int chosenIndex = index;
				if(index >= boundary.size())
				{
					chosenIndex -= boundary.size();
					chosen = interior;
				}
				MeshPoint mp = chosen.get(chosenIndex);
				int previousSinkIndex = sinkIndices.get(index);
				int nextSinkIndex = -1;
				if(mp.GetDrainageSink() != null)
					nextSinkIndex = mp.GetDrainageSink().GetContainerIndex();
				if(previousSinkIndex != nextSinkIndex)
				{
					sinkIndices.set(index, nextSinkIndex);
					return true;
				}
				return false;
			}).count();
			return (int) count;
		}
		public void RunSingleDrainageCalc()
		{
			Stream<MeshPoint> rootStream = roots.stream();
			if(Switches.PARALLEL_CONTINENT_GEN)
				rootStream = rootStream.parallel();
			
			List<MeshPoint> realRoots = rootStream.filter(root -> {
				if(root.GetDrainageSink() != null)
					return false;
				return true;
			}).collect(Collectors.toList());
			
			System.out.print("<" + realRoots.size() + ">");
			rootStream = realRoots.stream();
			if(Switches.PARALLEL_CONTINENT_GEN)
				rootStream = rootStream.parallel();
			
			rootStream.forEach(root ->
			{
				ArrayList<MeshPoint> tree = GetNodeTreeByDistanceFromRoot(root);
				CalculateDrainageArea(tree);
			});
		}
		public void RunFullSolveStep(double dt, double k)
		{
			Stream<MeshPoint> rootStream = roots.stream();
			if(Switches.PARALLEL_CONTINENT_GEN)
				rootStream = rootStream.parallel();
			
			ArrayList<MeshPoint> realRoots = rootStream.filter(root -> {
				if(root.GetDrainageSink() != null)
					return false;
				return true;
			}).collect(Collectors.toCollection(ArrayList::new));
			
			System.out.print("(Trees: ");
			System.out.print(realRoots.size());
			long[] bfsTime = new long[realRoots.size()];
			long[] drainageTime = new long[realRoots.size()];
			long[] solveTime = new long[realRoots.size()];
			
			
			IntStream solveStream = IntStream.range(0, realRoots.size());
			if(Switches.PARALLEL_CONTINENT_GEN)
				solveStream = solveStream.parallel();
			
			solveStream.forEach(index ->
			{
				MeshPoint root = realRoots.get(index);
				long start = System.currentTimeMillis();
				ArrayList<MeshPoint> tree = GetNodeTreeByDistanceFromRoot(root);
				long treeDone = System.currentTimeMillis();
				CalculateDrainageArea(tree);
				long drainageDone = System.currentTimeMillis();
				RunImplicitSolver(tree, dt, k);
				long solveDone = System.currentTimeMillis();
				bfsTime[index] = treeDone - start;
				drainageTime[index] = drainageDone - treeDone;
				solveTime[index] = solveDone - drainageDone;
			});
			long totalBfs = 0;
			for(long l : bfsTime)
				totalBfs += l;
			long totalDrain = 0;
			for(long l : drainageTime)
				totalDrain += l;
			long totalSolve = 0;
			for(long l : solveTime)
				totalSolve += l;
			System.out.print(", bfs: " + totalBfs + ", drain: " + totalDrain + ", solve: " + totalSolve + ")");
		}
		
		public void RunImplicitSolver(ArrayList<MeshPoint> orderedNodes, double dt, double k)
		{
			for(int i = 0; i < orderedNodes.size(); i++)
			{
				MeshPoint mp = orderedNodes.get(i);
				if(mp.GetContainerIndex() < boundary.size())
				{
					if(i != 0)
						System.out.println("Unexpected result in ContinentGenAlgorithms, TectonicUpliftAlgorithm, RunImplicitSolver!");
					continue;
				}
				
				if(mp.IsOcean())
					continue;
				MeshPoint sink = mp.GetDrainageSink();
				if(sink == null)
				{
					System.out.println("Very so bad result in ContinentGenAlgorithms, TectonicUpliftAlgorithm, RunImplicitSolver!");
				}
					
				if(mp.IsWaterPoint() && sink.IsWaterPoint())
				{
					mp.SetElevation(sink.GetElevation());
					continue;
				}
				
				double uplift = mp.GetTectonicUplift();
				
				double dist = mp.DistTo(sink);
				double erosionCoeff = k * Math.pow(mp.GetDrainageArea(), 0.5) / dist;
				double denominator = 1 + erosionCoeff * dt;
				double parenth = uplift + erosionCoeff * sink.GetElevation();
				double numerator = mp.GetElevation() + dt * parenth;
				double result = numerator / denominator;
				
				double maxResult = mp.GetMaxGrade() * MeshPoint.ConvertVoronoiDistToMeters(dist) + sink.GetElevation();
				if(result > maxResult)
					result = maxResult;
				mp.SetElevation(result);
			}
		}
		public void CalculateDrainageArea(ArrayList<MeshPoint> orderedNodes)
		{
			for(int i = orderedNodes.size() - 1; i >= 0; i--)
			{
				MeshPoint mp = orderedNodes.get(i);
				mp.CalculateDrainageArea();
			}
		}
		public ArrayList<MeshPoint> GetNodeTreeByDistanceFromRoot(MeshPoint root)
		{
			ArrayList<MeshPoint> found = new ArrayList<MeshPoint>();
			LinkedList<MeshPoint> frontier = new LinkedList<MeshPoint>();
			frontier.addLast(root);
			while(!frontier.isEmpty())
			{
				MeshPoint mp = frontier.removeFirst();
				found.add(mp);
				for(MeshPoint source : mp.GetDrainageSources())
					frontier.addLast(source);
			}
			return found;
		}
		public void UpdateStreamTreesWithLakeOverflow()
		{
			//Apply tags of roots all the way up the stack
			//Is this an opportunity to parallelize each root's stack?
			for(MeshPoint mp : roots)
			{
				int tag = mp.GetContainerIndex();
				UpdateTreeTags(mp, tag);
			}
			
			ArrayList<GraphEdge> accepted = new ArrayList<GraphEdge>();
			TreeMap<Double, LinkedList<GraphEdge>> candEdges = new TreeMap<Double, LinkedList<GraphEdge>>();
			
			//Go through the trees originating from the boundary; find edges out of those trees
			for(MeshPoint root : roots)
			{
				if(root.GetTag() >= boundary.size())
					continue;
				AddCandidateEdges(root, candEdges);
			}
			
			while(!candEdges.isEmpty())
			{
				LinkedList<GraphEdge> candList = candEdges.firstEntry().getValue();
				GraphEdge cand = candList.removeFirst();
				if(candList.isEmpty())
					candEdges.remove(cand.GetPassHeight());
				if(!cand.IsCandidateEdge())
					continue;
				accepted.add(cand);
				MeshPoint lakeRoot = interior.get(cand.source.GetTag() - boundary.size());
				int boundaryRootTag = cand.sink.GetTag();
				UpdateTreeTags(lakeRoot, boundaryRootTag);
				AddCandidateEdges(lakeRoot, candEdges);
			}
			
			for(GraphEdge ge : accepted)
			{
				MeshPoint lake = ge.source;
				while(lake.GetDrainageSink() != null)
					lake = lake.GetDrainageSink();
				lake.ForceAssignDrainage(ge.sink);
			}
		}
		private void AddCandidateEdges(MeshPoint root, TreeMap<Double, LinkedList<GraphEdge>> candEdges)
		{
			LinkedList<MeshPoint> childrenStack = new LinkedList<MeshPoint>();
			childrenStack.addFirst(root);
			while(!childrenStack.isEmpty())
			{
				MeshPoint m = childrenStack.removeFirst();
				for(MeshPoint src : m.GetDrainageSources())
					childrenStack.addFirst(src);
				
				for(MeshPoint adj : m.GetDirectlyAdjacent())
				{
					if(adj.GetTag() < 0)
						continue;
					//this adjacency is from a tree rooted in the boundary;
					//adj is not connected to a local minima, so we
					//don't need to work with it
					if(adj.GetTag() < boundary.size())
						continue;
					
					GraphEdge pass = new GraphEdge(adj, m);
					LinkedList<GraphEdge> passes = candEdges.get(pass.GetPassHeight());
					if(passes == null)
					{
						passes = new LinkedList<GraphEdge>();
						candEdges.put(pass.GetPassHeight(), passes);
					}
					passes.addFirst(pass);
				}
			}
		}
		private void UpdateTreeTags(MeshPoint root, int newTag)
		{
			LinkedList<MeshPoint> childrenStack = new LinkedList<MeshPoint>();
			childrenStack.addFirst(root);
			while(!childrenStack.isEmpty())
			{
				MeshPoint m = childrenStack.removeFirst();
				m.SetTag(newTag);
				for(MeshPoint src : m.GetDrainageSources())
					childrenStack.addFirst(src);
			}
		}
		public void CalcStreamTrees()
		{
			Stream<MeshPoint> interiorStream = interior.stream();
			if(Switches.PARALLEL_CONTINENT_GEN)
				interiorStream = interiorStream.parallel();			
			interiorStream.forEach(mp ->{
				mp.ResetDrainage();
				mp.ResetTag();
			});
			
			Stream<MeshPoint> boundaryStream = boundary.stream();
			if(Switches.PARALLEL_CONTINENT_GEN)
				boundaryStream = boundaryStream.parallel();
			boundaryStream.forEach(mp ->{
				mp.ResetDrainage();
				mp.ResetTag();
			});
			
			for(MeshPoint mp : interior)
				mp.AssignDrainage();
			
			roots.clear();
			
			interiorStream = interior.stream();
			if(Switches.PARALLEL_CONTINENT_GEN)
				interiorStream = interiorStream.parallel();
			List<MeshPoint> interiorRoots = interiorStream.filter(mp -> {
				return mp.GetDrainageSink() == null;
			}).collect(Collectors.toList());
			
			roots.addAll(boundary);
			roots.addAll(interiorRoots);
		}
		public void InitGraphVertices(ArrayList<SamplePoint> continent)
		{
			boundary.clear();
			interior.clear();
			MeshPoint.StartNewSearch();
			ArrayList<ArrayList<MeshPoint>> detailLevels = new ArrayList<ArrayList<MeshPoint>>();
			ArrayList<MeshPoint> detail0 = new ArrayList<MeshPoint>();
			for(SamplePoint s: continent)
			{
				s.MarkAsReached();
				detail0.add(s);
			}
			detailLevels.add(detail0);
			while(true)
			{
				ArrayList<MeshPoint> nextLevel = new ArrayList<MeshPoint>();
				for(MeshPoint m : detailLevels.get(detailLevels.size() - 1))
				{
					for(MeshPoint adj : m.GetAdjacent())
					{
						if(!adj.Reached())
							continue;
						MeshConnection con = m.GetConnection(adj);
						if(con == null)
							continue;
						MeshPoint mid = con.GetMid();
						if(mid == null)
							continue;
						if(mid.Reached())
							continue;
						mid.MarkAsReached();
						nextLevel.add(mid);
					}
				}
				if(nextLevel.size() == 0)
					break;
				detailLevels.add(nextLevel);
			}
			for(ArrayList<MeshPoint> almp : detailLevels)
			{
				for(MeshPoint p : almp)
				{
					p.InitDirectlyAdjacent();
					p.CalculatePersonalDrainageArea();
					interior.add(p);
					for(MeshPoint adj : p.GetDirectlyAdjacent())
					{
						if(adj.Reached())
							continue;
						adj.MarkAsReached();
						adj.InitDirectlyAdjacent();
						adj.CalculatePersonalDrainageArea();
						boundary.add(adj);
					}
				}
			}
			for(int i = 0; i < boundary.size(); i++)
			{
				recordedContainerIndices.add(boundary.get(i).GetContainerIndex());
				boundary.get(i).SetContainerIndex(i);
			}
			for(int i = 0; i < interior.size(); i++)
			{
				recordedContainerIndices.add(interior.get(i).GetContainerIndex());
				interior.get(i).SetContainerIndex(i + boundary.size());
			}
			sinkIndices = new ArrayList<Integer>(boundary.size() + interior.size());
			for(int i = 0; i < boundary.size(); i++)
				sinkIndices.add(-1);
			for(int i = 0; i < interior.size(); i++)
				sinkIndices.add(-1);
		}
		public void ResetTemporaryStorage()
		{
			for(int i = 0; i < boundary.size(); i++)
			{
				MeshPoint m = boundary.get(i);
				m.ResetTag();
				m.ResetDirectlyAdjacent();
				m.SetContainerIndex(recordedContainerIndices.get(i));
			}
			for(int i = 0; i < interior.size(); i++)
			{
				MeshPoint m = interior.get(i);
				m.ResetTag();
				m.ResetDirectlyAdjacent();
				m.SetContainerIndex(recordedContainerIndices.get(i + boundary.size()));
			}
		}
		public int NumIteriorPoints()
		{
			return interior.size();
		}
		private class GraphEdge
		{
			MeshPoint source;
			MeshPoint sink;
			private double h;
			public GraphEdge(MeshPoint source, MeshPoint sink)
			{
				this.source = source;
				this.sink = sink;
				h = Math.max(source.GetElevation(), sink.GetElevation());
			}
			public double GetPassHeight()
			{
				return h;
			}
			public boolean IsCandidateEdge()
			{
				if(source.GetTag() == sink.GetTag())
					return false;
				if(source.GetTag() - boundary.size() < 0)
					return false;
				return true;
			}
		}
	}
}