public class Vec2
{
	public double x, y;
	public Vec2(double x, double y)
	{
		this.x = x;
		this.y = y;
	}
	public Vec2 Clone()
	{
		return new Vec2(this.x, this.y);
	}
	public boolean IsZero()
	{
		return x == 0 && y == 0;
	}
	public double Len()
	{
		return Math.sqrt(x * x + y * y);
	}
	public double Dot(Vec2 t)
	{
		return t.x * x + t.y * y;
	}
	public double Cross(Vec2 t)
	{
		return x * t.y - y * t.x;
	}
	public void Normalize()
	{
		double l = Len();
		x /= l;
		y /= l;
	}
	public void Divide(double d)
	{
		x /= d;
		y /= d;
	}
	public void Multiply(double m)
	{
		x *= m;
		y *= m;
	}
	public void Add(Vec2 t)
	{
		x += t.x;
		y += t.y;
	}
	public void Invert()
	{
		x *= -1;
		y *= -1;
	}
	public static Vec2 UnitVector(double x, double y)
	{
		Vec2 newVec = new Vec2(x, y);
		newVec.Normalize();
		return newVec;
	}
	public static Vec2 Sum(Vec2 a, Vec2 b)
	{
		return new Vec2(a.x + b.x, a.y + b.y);
	}
	public static Vec2 Direction(Vec2 a)
	{
		double l = a.Len();
		return new Vec2(a.x / l, a.y / l);
	}
	public static Vec2 Opposite(Vec2 a)
	{
		return new Vec2(a.x * -1, a.y * -1);
	}
	public static Vec2 Perpendicular(Vec2 a)
	{
		return new Vec2(a.y, a.x * -1);
	}
	public static Vec2 Scale(Vec2 a, double m)
	{
		return new Vec2(a.x * m, a.y * m);
	}
	public static double[] GetIntersectionAsST(Vec2 sA, Vec2 sB, Vec2 dirA, Vec2 dirB)
	{
		double lenA = dirA.Len();
		double lenB = dirB.Len();
		dirA = Direction(dirA);
		dirB = Direction(dirB);
		//the determinant of the matrix that solves this problem
		double det = dirB.x * dirA.y - dirA.x * dirB.y;
		if(det == 0)
			return null;
		
		//As in Ax=b
		Vec2 bVec = Sum(sB, Opposite(sA));
		Vec2 aInvTopVec = new Vec2(-dirB.y, dirB.x);
		Vec2 aInvBotVec = new Vec2(-dirA.y, dirA.x);
		
		double s = aInvTopVec.Dot(bVec) / det;
		double t = aInvBotVec.Dot(bVec) / det;
		
		return new double[] {s / lenA, t / lenB};
	}
}