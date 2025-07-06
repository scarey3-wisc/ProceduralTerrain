public class Vec3
{
	public double x, y, z;
	public Vec3(double x, double y, double z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	public Vec3(Vec4 extract)
	{
		this.x = extract.x;
		this.y = extract.y;
		this.z = extract.z;
	}
	public Vec3 Clone()
	{
		return new Vec3(this.x, this.y, this.z);
	}
	public boolean IsZero()
	{
		return x == 0 && y == 0 && z == 0;
	}
	public double Len()
	{
		return Math.sqrt(x * x + y * y + z * z);
	}
	public double Dot(Vec3 t)
	{
		return t.x * x + t.y * y + t.z * z;
	}
	public Vec3 Cross(Vec3 t)
	{
		double nX = y * t.z - z * t.y;
		double nY = z * t.x - x * t.z;
		double nZ = x * t.y - y * t.x;
		return new Vec3(nX, nY, nZ);
	}
	public void Normalize()
	{
		double l = Len();
		x /= l;
		y /= l;
		z /= l;
	}
	public void Divide(double d)
	{
		x /= d;
		y /= d;
		z /= d;
	}
	public void Multiply(double m)
	{
		x *= m;
		y *= m;
		z *= m;
	}
	public void Add(Vec3 t)
	{
		x += t.x;
		y += t.y;
		z += t.z;
	}
	public void Invert()
	{
		x *= -1;
		y *= -1;
		z *= -1;
	}
	public static Vec3 Sum(Vec3 a, Vec3 b)
	{
		return new Vec3(a.x + b.x, a.y + b.y, a.z + b.z);
	}
	public static Vec3 Direction(Vec3 a)
	{
		double l = a.Len();
		return new Vec3(a.x / l, a.y / l, a.z / l);
	}
	public static Vec3 Opposite(Vec3 a)
	{
		return new Vec3(a.x * -1, a.y * -1, a.z * -1);
	}
	public static Vec3 Scale(Vec3 a, double m)
	{
		return new Vec3(a.x * m, a.y * m, a.z * m);
	}
}