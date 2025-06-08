import java.awt.Color;

public class TerrainTemplate
{
	public TerrainTemplate(){ value = 0; mask = 0; }
	public TerrainTemplate(short s)
	{
		mask = (byte)(s & 0xff);
		value = (byte)((s >>> 8) & 0xff);
	}
	public TerrainTemplate(byte v) { this.value = v; this.mask = (byte) 0xff; }
	public TerrainTemplate(int v) { this((byte) v); }
	
	public TerrainTemplate(byte v, byte m) 
	{ 
		this.value = v; this.mask = (byte) (m | v);
	}
	public TerrainTemplate(int v, int m) { this((byte) v, (byte) m); }
	
	public TerrainTemplate(TerrainTemplate t, byte v, byte m)
	{
		m = (byte) (m | v);
		byte independantMask = (byte) (m ^ t.mask);
		byte overlappingMask = (byte) (m & t.mask);
		byte valueAgreementPoints = (byte) ~(v ^ t.value);
		this.mask = (byte) (independantMask | (overlappingMask & valueAgreementPoints));
		this.value = (byte) ((v | t.value) & this.mask);
	}
	public TerrainTemplate(TerrainTemplate t, int v, int m) { this(t, (byte) v, (byte) m); }
	public TerrainTemplate(TerrainTemplate t, TerrainTemplate s) { this(t, s.value, s.mask); }
	public TerrainTemplate(TerrainTemplate t) { this.value = t.value; this.mask = t.mask; }
	
	public boolean equals(TerrainTemplate t)
	{
		return t.value == value && t.mask == mask;
	}
	public short BitsToShort()
	{
		short s_1_2 = (short) ((value << 8) | (mask & 0xFF));
		return s_1_2;
	}
	public String BitsToString()
	{
		boolean[] valueBits = new boolean[8];
		boolean[] maskBits = new boolean[8];
		byte v = value;
		byte m = mask;
		for(int i = 0; i < 8; i++)
		{
			valueBits[i] = (v & 1) != 0;
			maskBits[i] = (m & 1) != 0;
			v = (byte) (v >>> 1);
			m = (byte) (m >>> 1);
		}
		String result = "";
		for(int i = 7; i >=0; i--)
		{
			if(valueBits[i])
				result += "1";
			else
				result += "0";
			if(i == 4)
				result += " ";
		}
		result += " [";
		for(int i = 7; i >=0; i--)
		{
			if(maskBits[i])
				result += "1";
			else
				result += "0";
			if(i == 4)
				result += " ";
		}
		result += "]";
		return result;
	}
	public boolean QualitySet(byte comp) { return (mask & comp) == comp; }
	public byte QueryQuality(byte comp) { return (byte) (value & comp);}
	public boolean HasQuality(byte comp) { return (value & comp) > 0; }
	public boolean MatchesQualities(byte comp, byte mask) 
	{
		//If I'm interpreting past Stephen correctly, the idea here is:
		//1. every bit I've marked as 'set' (ie the mask), the test must also have 'set'
		//2. for every bit I've marked as 'set', the test and I must have the same value
		if((mask & this.mask) != mask)
			return false;
		return (comp & mask) == (value & mask);
	}
	public boolean IsTerrainOfType(TerrainTemplate t) { return MatchesQualities(t.value, t.mask); }
	public boolean IsTerraIncognita() { return mask == 0; }
	
	public Color GetRepresentativeColor()
	{
		if(IsTerraIncognita())
			return new Color(192, 192, 192);
		if(IsTerrainOfType(OCEAN))
			return Color.blue;
		return Color.red;
	}

	//indicates which qualities we possess
	protected byte value;
	//indicates which qualities have been set
	protected byte mask;

	public static final byte river = (byte) 0x01; 		//0000 0001
	public static final byte lake = (byte) 0x02; 		//0000 0010
	public static final byte cold = (byte) 0x04; 		//0000 0100
	public static final byte extreme = (byte) 0x08;		//0000 1000
	public static final byte elevated = (byte) 0x10;	//0001 0000
	public static final byte rough = (byte) 0x20;		//0010 0000
	public static final byte dry = (byte) 0x40;			//0100 0000
	public static final byte barren = (byte) 0x80;	//1000 0000

	public static final TerrainTemplate MOUNTAINS = new TerrainTemplate(elevated | rough, 0);
	public static final TerrainTemplate PEAKS = new TerrainTemplate(MOUNTAINS, barren, 0);
	public static final TerrainTemplate FLAT = new TerrainTemplate(0, rough);
	public static final TerrainTemplate ROUGH = new TerrainTemplate(rough, 0);
	public static final TerrainTemplate HOT = new TerrainTemplate(extreme, cold);
	public static final TerrainTemplate FRIGID = new TerrainTemplate(cold | extreme, 0);
	public static final TerrainTemplate WARM = new TerrainTemplate(0, cold | extreme);
	public static final TerrainTemplate COOL = new TerrainTemplate(cold, extreme);
	public static final TerrainTemplate DESERT = new TerrainTemplate(dry | barren | extreme, 0);
	public static final TerrainTemplate JUNGLE = new TerrainTemplate(HOT, 0, dry | barren | elevated);
	public static final TerrainTemplate PLAINS = new TerrainTemplate(dry, barren | cold);
	public static final TerrainTemplate GRASSLAND = new TerrainTemplate(WARM, 0, dry | barren);
	public static final TerrainTemplate FOREST = new TerrainTemplate(cold, dry | barren);
	public static final TerrainTemplate HILLS = new TerrainTemplate(rough, elevated);
	public static final TerrainTemplate TUNDRA = new TerrainTemplate(FRIGID, 0, barren);
	public static final TerrainTemplate SNOW = new TerrainTemplate(FRIGID, barren, 0);
	public static final TerrainTemplate LAKE = new TerrainTemplate(lake, 0);
	public static final TerrainTemplate RIVER = new TerrainTemplate(river, 0);
	public static final TerrainTemplate OCEAN = new TerrainTemplate(barren, elevated | rough | dry | extreme);
}