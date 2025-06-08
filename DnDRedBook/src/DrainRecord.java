

public class DrainRecord
{
	private byte[] rec;
	private static final int dim = DataImage.trueDim;
	private LocalMap.DrainRecordQuery query;
	public DrainRecord(LocalMap.DrainRecordQuery query)
	{
		rec = new byte[(dim + 1) * (dim + 1)];
		this.query = query;
	}
	public void SetDirection(int px, int py, Dir dir)
	{
		if(px <= 0)
		{
			DrainRecord west = query.GetWest();
			if(west != null)
			{
				if(px == 0)
					west.SetDirectionHelper(px + dim, py, dir);
				else
				{
					west.SetDirection(px + dim, py, dir);
					return;
				}
			}
			else if(px < 0)
				return;

		}
		if(px >= dim)
		{
			DrainRecord east = query.GetEast();
			if(east != null)
			{
				if(px == dim)
					east.SetDirectionHelper(px - dim, py, dir);
				else
				{
					east.SetDirection(px - dim, py, dir);
					return;
				}
			}
			else if(px > dim)
				return;
		}
		if(py <= 0)
		{
			DrainRecord north = query.GetNorth();
			if(north != null)
			{
				if(py == 0)
					north.SetDirectionHelper(px, py + dim, dir);
				else
				{
					north.SetDirection(px, py + dim, dir);
					return;
				}
			}
			else if(py < 0)
				return;
		}
		if(py >= dim)
		{
			DrainRecord south = query.GetSouth();
			if(south != null)
			{
				if(py == dim)
					south.SetDirectionHelper(px, py - dim, dir);
				else
				{
					south.SetDirection(px, py - dim, dir);
				}
			}
			else if(py > dim)
				return;
		}
		SetDirectionHelper(px, py, dir);
	}
	private void SetDirectionHelper(int px, int py, Dir dir)
	{
		byte info = rec[px * (dim + 1) + py];
		Status stat = UnpackDrainStatus(info);
		byte newInfo = PackDrainInfo(dir, stat);
		rec[px * (dim + 1) + py] = newInfo;
	}
	public void SetStatus(int px, int py, Status stat)
	{
		if(px <= 0)
		{
			DrainRecord west = query.GetWest();
			if(west != null)
			{
				if(px == 0)
					west.SetStatusHelper(px + dim, py, stat);
				else
				{
					west.SetStatus(px + dim, py, stat);
					return;
				}
			}
			else if(px < 0)
				return;

		}
		if(px >= dim)
		{
			DrainRecord east = query.GetEast();
			if(east != null)
			{
				if(px == dim)
					east.SetStatusHelper(px - dim, py, stat);
				else
				{
					east.SetStatus(px - dim, py, stat);
					return;
				}
			}
			else if(px > dim)
				return;
		}
		if(py <= 0)
		{
			DrainRecord north = query.GetNorth();
			if(north != null)
			{
				if(py == 0)
					north.SetStatusHelper(px, py + dim, stat);
				else
				{
					north.SetStatus(px, py + dim, stat);
					return;
				}
			}
			else if(py < 0)
				return;
		}
		if(py >= dim)
		{
			DrainRecord south = query.GetSouth();
			if(south != null)
			{
				if(py == dim)
					south.SetStatusHelper(px, py - dim, stat);
				else
				{
					south.SetStatus(px, py - dim, stat);
				}
			}
			else if(py > dim)
				return;
		}
		SetStatusHelper(px, py, stat);
	}
	private void SetStatusHelper(int px, int py, Status stat)
	{
		byte info = rec[px * (dim + 1) + py];
		Dir dir = UnpackDrainDirection(info);
		byte newInfo = PackDrainInfo(dir, stat);
		rec[px * (dim + 1) + py] = newInfo;
	}
	public Dir GetDirection(int px, int py)
	{
		if(px < 0)
		{
			DrainRecord west = query.GetWest();
			if(west == null)
				return Dir.None;
			return west.GetDirection(px + dim, py);
		}
		if(px > dim)
		{
			DrainRecord east = query.GetEast();
			if(east == null)
				return Dir.None;
			return east.GetDirection(px - dim, py);
		}
		if(py < 0)
		{
			DrainRecord north = query.GetNorth();
			if(north == null)
				return Dir.None;
			return north.GetDirection(px, py + dim);
		}
		if(py > dim)
		{
			DrainRecord south = query.GetSouth();
			if(south == null)
				return Dir.None;
			return south.GetDirection(px, py - dim);
		}
		byte info = rec[px * (dim + 1) + py];
		return UnpackDrainDirection(info);
	}
	public Status GetStatus(int px, int py)
	{
		if(px < 0)
		{
			DrainRecord west = query.GetWest();
			if(west == null)
				return Status.OffMap;
			return west.GetStatus(px + dim, py);
		}
		if(px > dim)
		{
			DrainRecord east = query.GetEast();
			if(east == null)
				return Status.OffMap;
			return east.GetStatus(px - dim, py);
		}
		if(py < 0)
		{
			DrainRecord north = query.GetNorth();
			if(north == null)
				return Status.OffMap;
			return north.GetStatus(px, py + dim);
		}
		if(py > dim)
		{
			DrainRecord south = query.GetSouth();
			if(south == null)
				return Status.OffMap;
			return south.GetStatus(px, py - dim);
		}
		byte info = rec[px * (dim + 1) + py];
		return UnpackDrainStatus(info);
	}

	private byte PackDrainInfo(Dir d, Status s)
	{
		return (byte) ((d.toByte() << 4) | (s.toByte() & 0x0f));
	}
	private Dir UnpackDrainDirection(byte b)
	{
		return Dir.fromByte((byte) ((b >> 4) & 0x0f));
	}
	private Status UnpackDrainStatus(byte b)
	{
		return Status.fromByte((byte) (b & 0x0f));
	}
	public static enum Dir
	{
		None,
		N,
		S,
		W,
		E;
		public Dir GetOpposite()
		{
			switch(this)
			{
			case E:
				return W;
			case N:
				return S;
			case None:
				return None;
			case S:
				return N;
			case W:
				return E;
			default:
				return None;

			}
		}
		public int dy()
		{
			switch(this)
			{
			case E:
				return 0;
			case N:
				return -1;
			case None:
				return 0;
			case S:
				return 1;
			case W:
				return 0;
			default:
				return 0;
			}
		}
		public int dx()
		{
			switch(this)
			{
			case E:
				return 1;
			case N:
				return 0;
			case None:
				return 0;
			case S:
				return 0;
			case W:
				return -1;
			default:
				return 0;
			}
		}
		public static Dir fromByte(byte b)
		{
			return values()[b];
		}
		public byte toByte()
		{
			return (byte) this.ordinal();
		}
	}
	public static enum Status
	{
		Unknown,
		OffMap,
		DrainsToOcean,
		DrainsOffMap,
		DrainsToPit;

		public static Status fromByte(byte b)
		{
			return values()[b];
		}
		public byte toByte()
		{
			return (byte) this.ordinal();
		}
	}
}