import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

public class MeshConnection
{
	private MeshMidPoint mid;
	private boolean ridgeline;
	private boolean river;
	public MeshPoint a;
	public MeshPoint b;
	public MeshConnection(MeshPoint first, MeshPoint second)
	{
		a = first;
		b = second;
		mid = null;
		river = false;
		ridgeline = false;
	}
	public byte GetLargerDetailLevel()
	{
		if(a.GetDetailLevel() > b.GetDetailLevel())
			return a.GetDetailLevel();
		return b.GetDetailLevel();
	}
	public int GetSmallerDetailLevel()
	{
		if(a.GetDetailLevel() < b.GetDetailLevel())
			return a.GetDetailLevel();
		return b.GetDetailLevel();

	}
	public ArrayList<MeshPoint> GetQuadCorners()
	{
		
		MeshPoint start = a;
		MeshPoint other = b;
		if(b.GetDetailLevel() > a.GetDetailLevel())
		{
			start = b;
			other = a;
		}
		
		ArrayList<MeshPoint> quadCorners = new ArrayList<MeshPoint>(2);
		MeshPoint startParentA = null;
		MeshPoint startParentB = null;
		if(start instanceof MeshMidPoint)
		{
			MeshMidPoint startAsMid = (MeshMidPoint) start;
			startParentA = startAsMid.GetParentA();
			startParentB = startAsMid.GetParentB();
		}
		for(MeshPoint candCorner : start.GetAdjacent())
		{
			if(candCorner == startParentA)
				continue;
			if(candCorner == startParentB)
				continue;
			if(candCorner.GetAdjacent().contains(other))
				quadCorners.add(candCorner);
		}
		if(quadCorners.size() > 2)
		{
			System.out.println("More than 4 corners in a quad is impossible - deploying repair routine!");
			RemoveIllegalAdjacencies(start, other, quadCorners);
		}
		
		return quadCorners;
	}
	
	public double GetLength()
	{
		double dx = a.x - b.x;
		double dy = a.y - b.y;
		return Math.sqrt(dx * dx + dy * dy);
	}
	public boolean IsRidgeline()
	{
		return ridgeline;
	}
	public boolean IsRiver()
	{
		return river;
	}
	public void SetRidgeline()
	{
		ridgeline = true;
	}
	public void SetRiver()
	{
		river = true;
	}
	public void ResetRidgeline()
	{
		ridgeline = false;
	}
	public void ResetRiver()
	{
		river = false;
	}
	public boolean MidInitialized()
	{
		return mid != null;
	}
	public MeshMidPoint GetMid()
	{
		return GetMid(false);
	}
	public MeshMidPoint GetMid(boolean spawnIfNeeded)
	{
		if(mid == null && spawnIfNeeded)
			mid = new MeshMidPoint(this, true);
		return mid;
	}
	public MeshMidPoint GetMid(DataInputStream dis)
	{
		if(mid != null)
			MeshMidPoint.ConsumeDescription(dis);
		else
			mid = new MeshMidPoint(this, dis);
		return mid;
	}
	public MeshMidPoint GetMid(Iterator<String> tokenStream)
	{
		MeshMidPoint nova = new MeshMidPoint(this, tokenStream);
		if(mid == null)
			mid = nova;
		return mid;
	}
	public static MeshConnection FindConnection(MeshPoint a, MeshPoint b)
	{
		MeshConnection check = a.GetConnection(b);
		if(check == null)
			check = b.GetConnection(a);
		return check;
	}
	private static void RemoveIllegalAdjacencies(MeshPoint s, MeshPoint t, ArrayList<MeshPoint> quadCorners)
	{
		Vec2 sG = new Vec2(s.x, s.y);
		Vec2 tG = new Vec2(t.x, t.y);
		HashSet<MeshPoint> disqualified = new HashSet<MeshPoint>();
		for(int i = 0; i < quadCorners.size(); i++)
		{
			for(int j = i + 1; j < quadCorners.size(); j++)
			{
				MeshPoint a = quadCorners.get(i);
				MeshPoint b = quadCorners.get(j);
				Vec2 s_a = new Vec2(a.x - s.x, a.y - s.y);
				Vec2 s_b = new Vec2(b.x - s.x, b.y - s.y);
				Vec2 t_a = new Vec2(a.x - t.x, a.y - t.y);
				Vec2 t_b = new Vec2(b.x - t.x, b.y - t.y);
				
				double[] stOne = Vec2.GetIntersectionAsST(sG, tG, s_a, t_b);
				double[] stTwo = Vec2.GetIntersectionAsST(sG, tG, s_b, t_a);
				if(stOne != null && stOne[0] > 0 && stOne[0] < 1 && stOne[1] > 0 && stOne[1] < 1)
				{
					//we found the problem: s->a and t->b cannot simultaneously be connected
					if(s_a.Len() < t_b.Len())
					{
						//remove the longer t-b edge
						disqualified.add(b);
						MeshConnection con = t.GetConnection(b);
						if(con == null)
							con = b.GetConnection(t);
						if(con != null && con.MidInitialized())
						{
							System.out.println("Trying to remove an invalid edge, but that edge has a midpoint!");
						}
						t.ForceRemoveAdjacency(b);
						b.ForceRemoveAdjacency(t);
					}
					else
					{
						//remove the longer s-a edge
						disqualified.add(a);
						MeshConnection con = s.GetConnection(a);
						if(con == null)
							con = a.GetConnection(s);
						if(con != null && con.MidInitialized())
						{
							System.out.println("Trying to remove an invalid edge, but that edge has a midpoint!");
						}
						s.ForceRemoveAdjacency(a);
						a.ForceRemoveAdjacency(s);
					}
				}
				else if(stTwo != null && stTwo[0] > 0 && stTwo[0] < 1 && stTwo[1] > 0 && stTwo[1] < 1)
				{
					//we found the problem: s->b and t->a cannot simultaneously be connected
					if(s_b.Len() < t_a.Len())
					{
						//remove the longer t-a edge
						disqualified.add(a);
						MeshConnection con = t.GetConnection(a);
						if(con == null)
							con = a.GetConnection(t);
						if(con != null && con.MidInitialized())
						{
							System.out.println("Trying to remove an invalid edge, but that edge has a midpoint!");
						}
						t.ForceRemoveAdjacency(a);
						a.ForceRemoveAdjacency(t);
					}
					else
					{
						//remove the longer s-b edge
						disqualified.add(b);
						MeshConnection con = s.GetConnection(b);
						if(con == null)
							con = b.GetConnection(s);
						if(con != null && con.MidInitialized())
						{
							System.out.println("Trying to remove an invalid edge, but that edge has a midpoint!");
						}
						s.ForceRemoveAdjacency(b);
						b.ForceRemoveAdjacency(s);
					}
				}
			}
		}
		if(quadCorners.size() - disqualified.size() < 2)
		{
			System.out.println("Oh boy, we disqualified too many... aborting");
			return;
		}
		if(quadCorners.size() - disqualified.size() > 2)
		{
			System.out.println("Oops, we didn't disqualify enough");
		}
		for(MeshPoint p : disqualified)
			quadCorners.remove(p);
		
	}
}