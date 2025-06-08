public class TerrainType extends TerrainTemplate
{
	public TerrainType(){ super(); }
	public TerrainType(short s ) { super(s); }
	public TerrainType(byte v) { super(v); }
	public TerrainType(int v) { super(v); }
	
	public TerrainType(byte v, byte m) { super(v, m); }
	public TerrainType(int v, int m) { super(v, m); }
	
	public TerrainType(TerrainTemplate t, byte v, byte m) { super(t, v, m); }
	public TerrainType(TerrainTemplate t, int v, int m) { super(t, v, m); }
	public TerrainType(TerrainTemplate t, TerrainTemplate s) { super(t, s); }
	public TerrainType(TerrainTemplate t) { super(t); }
	
	public void ActivateQuality(byte qua)
	{
		value = (byte) (value | qua);
		mask = (byte) (mask | qua);
	}
	public void DeactivateQuality(byte qua)
	{
		value = (byte) (value & ~qua);
		mask = (byte) (mask | qua);
	}
	public void SetQualityUnknown(byte qua)
	{
		value = (byte) (value & ~qua);
		mask = (byte) (mask & ~qua);
	}
	public void ApplyTerrain(TerrainTemplate t)
	{
		byte setToTrue = (byte) (t.mask & t.value);
		byte setToFalse = (byte) (t.mask & ~t.value);
		value = (byte) (value | setToTrue);
		value = (byte) (value & ~setToFalse);
		mask = (byte) (mask | t.mask);
	}
}