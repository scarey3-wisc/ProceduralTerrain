

public class PixelStatus
{
	private byte[] rec;
	private static final int dim = DataImage.trueDim;
	private LocalMap.PixelStatusQuery query;
	public PixelStatus(LocalMap.PixelStatusQuery query)
	{
		rec = new byte[dim * dim];
		this.query = query;
	}
	private void SetStatus(int px, int py, byte stat)
	{
		if(px < 0)
		{
			PixelStatus west = query.GetWest();
			if(west != null)
			{
				west.SetStatus(px + dim, py, stat);
			}
			return;
		}
		if(px >= dim)
		{
			PixelStatus east = query.GetEast();
			if(east != null)
			{
				east.SetStatus(px - dim, py, stat);
			}
			return;
		}
		if(py < 0)
		{
			PixelStatus north = query.GetNorth();
			if(north != null)
			{
				north.SetStatus(px, py + dim, stat);
			}
			return;
		}
		if(py >= dim)
		{
			PixelStatus south = query.GetSouth();
			if(south != null)
			{
				south.SetStatus(px, py - dim, stat);			}
			return;
		}
		rec[px * dim + py] = stat;
	}
	private byte GetStatus(int px, int py)
	{
		if(px < 0)
		{
			PixelStatus west = query.GetWest();
			if(west == null)
				return 0x00;
			return west.GetStatus(px + dim, py);
		}
		if(px >= dim)
		{
			PixelStatus east = query.GetEast();
			if(east == null)
				return 0x00;
			return east.GetStatus(px - dim, py);
		}
		if(py < 0)
		{
			PixelStatus north = query.GetNorth();
			if(north == null)
				return 0x00;
			return north.GetStatus(px, py + dim);
		}
		if(py >= dim)
		{
			PixelStatus south = query.GetSouth();
			if(south == null)
				return 0x00;
			return south.GetStatus(px, py - dim);
		}
		byte info = rec[px * dim + py];
		return info;
	}
	public boolean IsActive(int px, int py)
	{
		byte current = GetStatus(px, py);
		boolean test = (current & 0x01) != 0;
		return test;
	}
	public void SetActive(int px, int py, boolean active)
	{
		byte current = GetStatus(px, py);
		if(active)
			current = (byte) (current | 0x01);
		else
			current = (byte) (current & ~0x01);
		SetStatus(px, py, current);
	}
	public boolean IsQueued(int px, int py)
	{
		byte current = GetStatus(px, py);
		boolean test = (current & 0x02) != 0;
		return test;
	}
	public void SetQueued(int px, int py, boolean queued)
	{
		byte current = GetStatus(px, py);
		if(queued)
			current = (byte) (current | 0x02);
		else
			current = (byte) (current & ~0x02);
		SetStatus(px, py, current);
	}
}