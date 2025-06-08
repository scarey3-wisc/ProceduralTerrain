//This class doesn't have anything to do with RedBook
//It is a RandomNumberGenerator that I wanted to write for a different D&D campaign
//Putting it in the same project as DnDRedBook seemed appropriate
//Even if they're not technically related

import java.util.Scanner;

public class ForestGenerator
{
	private int forest, height, distance;
	public static void main(String[] args)
	{
		Scanner std = new Scanner(System.in);
		while(true)
		{
			System.out.println("Do you want to:");
			System.out.println("A: Find numbers for a new forest generator");
			System.out.println("B: Use an existing forest generator");
			System.out.println("C: View the full cycle of a forest generator");
			System.out.println("D: Exit the application");
			String answer = std.nextLine();
			if(answer.equalsIgnoreCase("A"))
			{
				System.out.println("What do you want the Forest to be?");
				int forest = GetNumberFromUser(std);
				while(true)
				{
					int height = 2 + (int) (Math.random() * (forest - 2));
					int distance = 1 + (int) (Math.random() * (forest - 1));
					int seed = (int) (Math.random() * forest);
					int cycleSize = CheckCycleLength(forest, height, distance, seed);
					if(cycleSize == -1)
						continue;
					System.out.println("We've got something with a cycle length of " + cycleSize + "\n");
					System.out.print("Is that good enough? Y/N: ");
					String goodString = std.nextLine();
					if(goodString.equalsIgnoreCase("Y"))
					{
						System.out.println("Okay! Here are your numbers!");
						System.out.println("Forest: " + forest);
						System.out.println("Height: " + height);
						System.out.println("Distance: " + distance);
						System.out.println("Seed: " + seed);
						break;
					}
				}

			}
			else if(answer.equalsIgnoreCase("B"))
			{
				
			}
			else if(answer.equalsIgnoreCase("C"))
			{
				//60, 31, 58, 34
				System.out.print("Forest: ");
				int forest = GetNumberFromUser(std);
				System.out.print("Height: ");
				int height = GetNumberFromUser(std);
				System.out.print("Distance: ");
				int distance = GetNumberFromUser(std);
				System.out.print("Seed: ");
				int seed = GetNumberFromUser(std);
				
				System.out.print("Sequence: ");
				int curr = GetNext(forest, height, distance, seed);
				System.out.print(curr + ", ");
				while(curr != seed)
				{
					curr = GetNext(forest, height, distance, curr);
					System.out.print(curr + ", ");
				}
				System.out.println();
			}
			else if(answer.equalsIgnoreCase("D"))
			{
				break;
			}
			else
			{
				System.out.println("I didn't understand; can you try again?");
			}
		}
		std.close();
	}
	public static int CheckCycleLength(int f, int h, int d, int s)
	{
		int curr = GetNext(f, h, d, s);
		int count = 1;
		while(curr != s)
		{
			curr = GetNext(f, h, d, curr);
			count++;
			if(count > f)
				return -1;
		}
		return count;
	}
	public static int GetNumberFromUser(Scanner std)
	{
		int result = 0;
		while(true)
		{
			String nString = std.nextLine();
			try
			{
				result = Integer.parseInt(nString);
				break;
			}
			catch (NumberFormatException e)
			{
				System.out.println("That wasn't a number; try again.");
			}
		}
		return result;
	}
	public static int GetNext(int f, int h, int d, int c)
	{
		c *= h;
		c += d;
		//TODO
		//We want c > f if 0 is not an option, c >= f if 0 is an option
		while(c >= f)
			c -= f;
		return c;
	}
}