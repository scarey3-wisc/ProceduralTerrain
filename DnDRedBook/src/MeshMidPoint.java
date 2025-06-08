import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

public class MeshMidPoint extends MeshPoint
{
	private double[] perlinElevDiffs;
	private byte detailLevel;
	private MeshPoint a;
	private MeshPoint b;
	private double tectonicUplift;
	private double maxGrade;
	private enum WaterType
	{
		NotWater,
		Ocean,
		InlandLake;
		
		public static WaterType fromByte(byte b)
		{
			return values()[b];
		}
		public byte toByte()
		{
			return (byte) this.ordinal();
		}
	}
	private WaterType myWaterType;
	public MeshMidPoint(MeshConnection parent, boolean permanent)
	{
		super(
			(parent.a.x + parent.b.x) / 2, 
			(parent.a.y + parent.b.y) / 2 
		);
		a = parent.a;
		b = parent.b;
		myWaterType = WaterType.NotWater;
		if(!permanent)
		{
			if(a.IsOcean() && b.IsOcean())
				myWaterType = WaterType.Ocean;
			else if(a.IsInlandLake() && b.IsInlandLake())
				myWaterType = WaterType.InlandLake;
			else if(a.IsOcean() && b.IsInlandLake())
				myWaterType = WaterType.InlandLake;
			else if(b.IsInlandLake() && a.IsOcean())
				myWaterType = WaterType.InlandLake;
		}
		InitInterpolation(parent, true);
		
		if(!permanent)
			return;
		InitConnections(parent);
	}
	public MeshMidPoint(MeshConnection parent, DataInputStream dis)
	{
		super(dis);
		a = parent.a;
		b = parent.b;
		InitInterpolation(parent, false);
		try
		{
			myWaterType = WaterType.fromByte(dis.readByte());
			for(int i = 0; i < perlinElevDiffs.length; i++)
			{
				perlinElevDiffs[i] = dis.readDouble();
			}
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		
		InitConnections(parent);
	}
	public MeshMidPoint(MeshConnection parent, Iterator<String> tokenStream)
	{
		super(tokenStream);
		a = parent.a;
		b = parent.b;
		InitInterpolation(parent, false);
		myWaterType = WaterType.fromByte(Byte.parseByte(tokenStream.next()));
		for(int i = 0; i < perlinElevDiffs.length; i++)
		{
			perlinElevDiffs[i] = Double.parseDouble(tokenStream.next());
		}
		InitConnections(parent);
	}
	public static boolean ConsumeDescription(DataInputStream dis)
	{
		try {
			MeshPoint.ConsumeDescription(dis);
			dis.readByte();
			for(int i = 0; i < SamplePoint.NumPerlinElevDiffs(); i++)
			{
				dis.readDouble();
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	private void InitInterpolation(MeshConnection parent, boolean interpolateElevation)
	{
		detailLevel = (byte) (parent.GetLargerDetailLevel() + 1);
		AveragePerlinElevDiffs(parent.a.GetPerlinElevDiffs(), parent.b.GetPerlinElevDiffs());
		if(interpolateElevation)
			SetElevation(a.GetElevation() / 2 + b.GetElevation() / 2);
		tectonicUplift = a.GetTectonicUplift() / 2 + b.GetTectonicUplift() / 2;
		maxGrade = a.GetMaxGrade() / 2 + b.GetMaxGrade() / 2;
	}
	private void InitConnections(MeshConnection parent)
	{
		ForceOneWayAdjacency(a);
		ForceOneWayAdjacency(b);
		MeshConnection connA = GetConnection(a);
		MeshConnection connB = GetConnection(b);
		if(parent.IsRidgeline())
		{
			connA.SetRidgeline();
			connB.SetRidgeline();
		}
		if(parent.IsRiver())
		{
			connA.SetRiver();
			connB.SetRiver();
		}
				
		ArrayList<MeshPoint> quadCorners = parent.GetQuadCorners();

		for(MeshPoint c : quadCorners)
		{
			MeshConnection connOne = MeshConnection.FindConnection(a, c);
			if(connOne != null && connOne.MidInitialized())
				MarkAdjacent(connOne.GetMid());
			
			MeshConnection connTwo = MeshConnection.FindConnection(b, c);
			if(connTwo != null && connTwo.MidInitialized())
				MarkAdjacent(connTwo.GetMid());
		}
	}
	
	
	
	public MeshPoint GetParentA()
	{
		return a;
	}
	public MeshPoint GetParentB()
	{
		return b;
	}
	public String GetDescription()
	{
		String desc = super.GetDescription() + " " + Byte.toString(myWaterType.toByte()) + " ";
		for(int i = 0; i < perlinElevDiffs.length; i++)
		{
			double d = perlinElevDiffs[i];
			desc += Double.toString(d);
			if(i != perlinElevDiffs.length - 1)
				desc += " ";
		}
		return desc;
	}
	
	public boolean WriteDescription(DataOutputStream dos)
	{
		if(!super.WriteDescription(dos))
			return false;
		try {
			dos.writeByte(myWaterType.toByte());
			for(double d : perlinElevDiffs)
				dos.writeDouble(d);
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	public void CopyPerlinElevDiffs(double[] a)
	{
		perlinElevDiffs = a.clone();
	}
	public void AveragePerlinElevDiffs(double[] a, double[] b)
	{
		perlinElevDiffs = new double[Math.max(a.length, b.length)];
		for(int i = 0; i < Math.min(a.length, b.length); i++)
		{
			perlinElevDiffs[i] = (a[i] + b[i]) / 2;
		}
	}
	
	@Override
	public double[] GetPerlinElevDiffs() {
		return perlinElevDiffs;
	}

	@Override
	public byte GetDetailLevel() {
		return detailLevel;
	}
	public void SetInlandLake()
	{
		myWaterType = WaterType.InlandLake;
	}
	public void SetOcean()
	{
		myWaterType = WaterType.Ocean;
	}
	public void SetNotWater()
	{
		myWaterType = WaterType.NotWater;
	}
	@Override
	public boolean IsOcean() {
		return myWaterType == WaterType.Ocean;
	}
	@Override
	public boolean IsInlandLake() {
		return myWaterType == WaterType.InlandLake;
	}
	public void SetTectonicUplift(double tu)
	{
		tectonicUplift = tu;
	}
	@Override
	public double GetTectonicUplift() {
		return tectonicUplift;
	}
	@Override
	public double GetMaxGrade() {
		return maxGrade;
	}

}