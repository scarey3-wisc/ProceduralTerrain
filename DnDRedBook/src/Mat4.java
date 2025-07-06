public class Mat4
{
	private Mat4()
	{
		
	}
	private double vals[] = new double[16];
	private double get(int col, int row)
	{
		return vals[4 * row + col];
	}
	private void set(int col, int row, double val)
	{
		vals[4 * row + col] = val;
	}
	public static Mat4 ProjectionMatrix(double fovDegrees, double aspectRatio, double nearPlaneDistance, double farPlaneDistance)
	{
		double f = 1 / Math.tan(Math.toRadians(fovDegrees) / 2);
		Mat4 nova = new Mat4();
		nova.set(0, 0, f / aspectRatio);
		nova.set(1, 1, f);
		nova.set(2, 2, (farPlaneDistance + nearPlaneDistance) / (nearPlaneDistance - farPlaneDistance));
		nova.set(2, 3, (2 * farPlaneDistance * nearPlaneDistance) / (nearPlaneDistance - farPlaneDistance));
		nova.set(3, 2, -1);
		nova.set(3, 3, 0);
		return nova;
	}
	public static Mat4 ViewMatrix(Vec3 cameraPos, Vec3 cameraDir, Vec3 upwards)
	{
		Mat4 nova = new Mat4();
		Vec3 z = cameraDir.Clone();
		z.Normalize();
		Vec3 x = upwards.Cross(z);
		x.Normalize();
		Vec3 y = z.Cross(x);
		nova.set(0, 0, x.x);
		nova.set(1, 0, x.y);
		nova.set(2, 0, x.z);
		nova.set(3, 0, -x.Dot(cameraPos));
		nova.set(0, 1, y.x);
		nova.set(1, 1, y.y);
		nova.set(2, 1, y.z);
		nova.set(3, 1, -y.Dot(cameraPos));
		nova.set(0, 2, z.x);
		nova.set(1, 2, z.y);
		nova.set(2, 2, z.z);
		nova.set(3, 2, -z.Dot(cameraPos));
		nova.set(0, 3, 0);
		nova.set(1, 3, 0);
		nova.set(2, 3, 0);
		nova.set(3, 3, 1);
		return nova;
	}
	public Vec4 postMultiply(Vec4 r)
	{
		double x = get(0, 0) * r.x + get(1, 0) * r.y + get(2, 0) * r.z + get(3, 0) * r.k;
		double y = get(0, 1) * r.x + get(1, 1) * r.y + get(2, 1) * r.z + get(3, 1) * r.k;
		double z = get(0, 2) * r.x + get(1, 2) * r.y + get(2, 2) * r.z + get(3, 2) * r.k;
		double k = get(0, 3) * r.x + get(1, 3) * r.y + get(2, 3) * r.z + get(3, 3) * r.k;
		return new Vec4(x, y, z, k);
	}
	public void postMultiply(Vec4 r, Vec4 dest)
	{
		double x = get(0, 0) * r.x + get(1, 0) * r.y + get(2, 0) * r.z + get(3, 0) * r.k;
		double y = get(0, 1) * r.x + get(1, 1) * r.y + get(2, 1) * r.z + get(3, 1) * r.k;
		double z = get(0, 2) * r.x + get(1, 2) * r.y + get(2, 2) * r.z + get(3, 2) * r.k;
		double k = get(0, 3) * r.x + get(1, 3) * r.y + get(2, 3) * r.z + get(3, 3) * r.k;
		dest.x = x;
		dest.y = y;
		dest.z = z;
		dest.k = k;
	}
}