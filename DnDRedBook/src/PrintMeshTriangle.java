import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

public class PrintMeshTriangle {
	private float nx, ny, nz;
	private float v1x, v1y, v1z;
	private float v2x, v2y, v2z;
	private float v3x, v3y, v3z;
	
	public PrintMeshTriangle() {
		nx = 0;
		ny = 0;
		nz = 1;
		v1x = 0;
		v1y = 0;
		v1z = 0;
		v2x = 1;
		v2y = 0;
		v2z = 0;
		v3x = 0;
		v3y = 1;
		v3z = 0;
	}
	
	public PrintMeshTriangle(
			double x1, double y1, double z1,
			double x2, double y2, double z2,
			double x3, double y3, double z3,
			double nx, double ny, double nz)
	{
		v1x = (float) x1;
		v2x = (float) x2;
		v3x = (float) x3;
		v1y = (float) y1;
		v2y = (float) y2;
		v3y = (float) y3;
		v1z = (float) z1;
		v2z = (float) z2;
		v3z = (float) z3;
		AutoCalcNormal();
		NormalizeNormalInDirection((float) nx, (float) ny, (float) nz);
		
	}
	public void SetPoint1(float x, float y, float z) {
		v1x = x;
		v1y = y;
		v1z = z;
	}
	public void SetPoint2(float x, float y, float z) {
		v2x = x;
		v2y = y;
		v2z = z;
	}
	public void SetPoint3(float x, float y, float z) {
		v3x = x;
		v3y = y;
		v3z = z;
	}
	public void SetPoint1(double x, double y, double z) {
		v1x = (float) x;
		v1y = (float) y;
		v1z = (float) z;
	}
	public void SetPoint2(double x, double y, double z) {
		v2x = (float) x;
		v2y = (float) y;
		v2z = (float) z;
	}
	public void SetPoint3(double x, double y, double z) {
		v3x = (float) x;
		v3y = (float) y;
		v3z = (float) z;
	}
	public void AutoCalcNormal() {
		float dx1 = v2x - v1x;
		float dx2 = v3x - v1x;
		float dy1 = v2y - v1y;
		float dy2 = v3y - v1y;
		float dz1 = v2z - v1z;
		float dz2 = v3z - v1z;
		
		
		nx = dy1 * dz2 - dy2 * dz1;
		ny = dx2 * dz1 - dx1 * dz2;
		nz = dx1 * dy2 - dx2 * dy1;
	}
	public void NormalizeNormalInDirection(float x, float y, float z) {
		double mag = Math.sqrt(nx * nx + ny * ny + nz * nz);
		float dot = x * nx + y * ny + z * nz;
		if(mag == 0)
			return;
		if(dot < 0)
			mag *= -1;
		nx /= mag;
		ny /= mag;
		nz /= mag;
	}
	public void WriteLongForm(PrintWriter b, double s) {
		b.print("facet normal ");
		b.printf("%e %e %e\n", nx * s, ny * s, nz * s);
		b.print("\touter loop\n");
		b.printf("\t\tvertex %e %e %e\n", v1x * s, v1y * s, v1z * s);
		b.printf("\t\tvertex %e %e %e\n", v2x * s, v2y * s, v2z * s);
		b.printf("\t\tvertex %e %e %e\n", v3x * s, v3y * s, v3z * s);
		b.print("\tendloop\n");
		b.print("endfacet\n");
	}
	
	public int FlipToLittleEndian(float x)
	{
		int rep = Float.floatToIntBits(x);
		return Integer.reverseBytes(rep);
	}
	
	public int FlipToLittleEndian(double x)
	{
		int rep = Float.floatToIntBits((float) x);
		return Integer.reverseBytes(rep);
	}
	
	public void WriteCompressedForm(DataOutputStream d, double s) {
		try {
			d.writeInt(FlipToLittleEndian(nx));
			d.writeInt(FlipToLittleEndian(ny));
			d.writeInt(FlipToLittleEndian(nz));
			d.writeInt(FlipToLittleEndian(v1x * s));
			d.writeInt(FlipToLittleEndian(v1y * s));
			d.writeInt(FlipToLittleEndian(v1z * s));
			d.writeInt(FlipToLittleEndian(v2x * s));
			d.writeInt(FlipToLittleEndian(v2y * s));
			d.writeInt(FlipToLittleEndian(v2z * s));
			d.writeInt(FlipToLittleEndian(v3x * s));
			d.writeInt(FlipToLittleEndian(v3y * s));
			d.writeInt(FlipToLittleEndian(v3z * s));
			d.writeShort(0);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}