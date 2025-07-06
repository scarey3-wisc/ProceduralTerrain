public class Vec4
{
	public double x, y, z, k;
	public Vec4(double x, double y, double z, double k)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		this.k = k;
	}
	public Vec4(Vec3 original)
	{
		this.x = original.x;
		this.y = original.y;
		this.z = original.z;
		this.k = 1;
	}
	public Vec4 Clone()
	{
		return new Vec4(this.x, this.y, this.z, this.k);
	}
	public boolean IsZero()
	{
		return x == 0 && y == 0 && z == 0 && k == 0;
	}
	public double Len()
	{
		return Math.sqrt(x * x + y * y + z * z + k * k);
	}
	public double Dot(Vec4 t)
	{
		return t.x * x + t.y * y + t.z * z + t.k * k;
	}
	public void Normalize()
	{
		double l = Len();
		x /= l;
		y /= l;
		z /= l;
		k /= l;
	}
	public void Divide(double d)
	{
		x /= d;
		y /= d;
		z /= d;
		k /= d;
	}
	public void Multiply(double m)
	{
		x *= m;
		y *= m;
		z *= m;
		k *= m;
	}
	public void Add(Vec4 t)
	{
		x += t.x;
		y += t.y;
		z += t.z;
		k += t.k;
	}
	public void Invert()
	{
		x *= -1;
		y *= -1;
		z *= -1;
		k *= -1;
	}
	public static Vec4 Sum(Vec4 a, Vec4 b)
	{
		return new Vec4(a.x + b.x, a.y + b.y, a.z + b.z, a.k + b.k);
	}
	public static Vec4 Direction(Vec4 a)
	{
		double l = a.Len();
		return new Vec4(a.x / l, a.y / l, a.z / l, a.k / l);
	}
	public static Vec4 Opposite(Vec4 a)
	{
		return new Vec4(a.x * -1, a.y * -1, a.z * -1, a.k * -1);
	}
	public static Vec4 Scale(Vec4 a, double m)
	{
		return new Vec4(a.x * m, a.y * m, a.z * m, a.k * m);
	}
}