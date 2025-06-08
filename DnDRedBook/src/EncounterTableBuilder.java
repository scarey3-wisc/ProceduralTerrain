import java.util.ArrayList;

public class EncounterTableBuilder
{
	ArrayList<WeightPair> encounters;
	public static void main(String[] args)
	{
		BuildRegionF();
	}
	public static void BuildRegionF()
	{
		EncounterTableBuilder region = new EncounterTableBuilder();
		region.Add("Chelon Hunting Party: 2d4 Scouts", 8);
		region.Add("Sundrack Herd: 2d6 Adult Sundrack and 2d6 Infant Sundrack", 12);
		region.Add("Wild Dragonweed Patch", 10);
		region.Add("2d6 Giant Centipede", 8);
		region.Add("Berry Patch, Uneaten", 13);
		region.Add("Berry Patch, Eaten", 5);
		region.Add("1d4 Roving Brown Bear", 3);
		region.Add("Lone Sundrack", 9);
		region.Add("Chelon Logging Convoy", 5);
		region.Add("Mushrooms, Poisonous", 14);
		region.Add("Mushrooms, Nutritious", 7);
		region.Add("Dragonweed Farmer", 3);
		region.Add("Air Elemental", 1);
		region.Add("Surveyor from Seawatch", 1);
		region.Add("Goblin Scout", 1);
		region.CalculateFull();
		System.out.println("\n\n\n");
		region.CalculateNames();
		System.out.println("\n\n\n");
		region.CalculateProbabilities();
	}
	public static void BuildRegionE()
	{
		EncounterTableBuilder region = new EncounterTableBuilder();
		region.Add("Bloodcrazed Orc Sign", 13);
		region.Add("Rogue Orc Band: 1d4 Bloodcrazed Orcs", 5);
		region.Add("Goblin Scout Tower", 2);
		region.Add("Goblin Scout Tower Foundation", 9);
		region.Add("Goblin Scout Party: 2 Goblin Snipers", 5);
		region.Add("Hennai Hunting Party: 2d4 Scouts", 8);
		region.Add("Dremmar Hunting Party: 2d4 Scouts", 4);
		region.Add("Sundrack Herd: 2d6 Adult Sundrack and 2d6 Infant Sundrack", 8);
		region.Add("Wild Dragonweed Patch", 10);
		region.Add("2d6 Giant Centipede", 8);
		region.Add("An apple orchard", 13);
		region.Add("Dragonweed Farmer", 5);
		region.Add("Seawatch Vigilante", 1);
		region.Add("A Dead Body", 9);
		region.CalculateNames();
		region.CalculateProbabilities();
	}
	public static void BuildRegionD()
	{
		EncounterTableBuilder region = new EncounterTableBuilder();
		region.Add("Rogue Orc Band: 1d4 Bloodcrazed Orcs", 3);
		region.Add("Goblin Scout Tower", 10);
		region.Add("Goblin Scout Party: 2 Goblin Snipers", 15);
		region.Add("Grimfang Slaver Band", 15);
		region.Add("Grimfang Officer", 4);
		region.Add("1d4 Brown Bears", 2);
		region.Add("2d4 Escaped Slaves", 4);
		region.Add("1d4 Dropped Mana Crystal", 4);
		region.Add("Grimfang Mana Crystal Convoy", 6);
		region.Add("Grimfang Supply Convoy", 8);
		region.Add("Human Campfire, Abandoned", 3);
		region.Add("Human Campfire, Recent", 2);
		region.Add("Seawatch Vigilante", 1);
		region.Add("Murdered Goblin", 3);
		region.Add("Human Agent of Grimfang", 1);
		region.Add("Orc Agent of Grimfang", 1);
		region.Add("Dwarf Agent of Grimfang", 2);
		region.Add("Wild Berries", 4);
		region.Add("Poisonous Mushrooms", 4);
		region.Add("Gorgeous Waterfall", 8);
		region.CalculateFull();
	}
	public static void BuildRegionC()
	{
		EncounterTableBuilder region = new EncounterTableBuilder();
		region.Add("Bloodcrazed Orc Sign", 17);
		region.Add("Rogue Orc Band: 1d4 Bloodcrazed Orcs", 7);
		region.Add("Goblin Scout Tower", 4);
		region.Add("Goblin Scout Party: 2 Goblin Snipers", 6);
		region.Add("Grimfang Slaver Band", 5);
		region.Add("1d4 Brown Bears", 5);
		region.Add("Miner from Ironstead", 6);
		region.Add("Air Elemental", 3);
		region.Add("Human Campfire, Abandoned", 6);
		region.Add("Human Campfire, Recent", 2);
		region.Add("Seawatch Vigilante", 1);
		region.Add("Village Mercy Caravan", 5);
		region.Add("Murdered Goblin", 3);
		region.Add("Infected Orc", 4);
		region.Add("Mushrooms, Nutritious", 4);
		region.Add("Mushrooms, Poisonous", 4);
		region.Add("Merchant from Hennai", 5);
		region.Add("Wild Apple Grove", 13);
		region.CalculateFull();
	}
	public static void BuildRegionB()
	{
		EncounterTableBuilder region = new EncounterTableBuilder();
		region.Add("Bloodcrazed Orc Sign", 12);
		region.Add("Rogue Orc Band: 1d4 Bloodcrazed Orcs", 4);
		region.Add("Goblin Scout Tower", 1);
		region.Add("Goblin Scout Tower Foundation", 4);
		region.Add("Goblin Scout Party: 2 Goblin Snipers", 2);
		region.Add("Miner from Ironstead", 13);
		region.Add("Abandoned Mana Crystal", 5);
		region.Add("Mushrooms, Nutritious", 6);
		region.Add("Mushrooms, Poisonous", 6);
		region.Add("Orc Agent of the Grimfang", 2);
		region.Add("Human Agent of the Grimfang", 2);
		region.Add("Dwarf Agent of the Grimfang", 4);
		region.Add("Logging Crew from Seawatch", 5);
		region.Add("3d4 Bandits", 6);
		region.Add("Bandit Camp with 4d6 Bandits", 1);
		region.Add("Grimfang Slaver Band", 1);
		region.Add("1d4 Brown Bears", 5);
		region.Add("Rock Outcropping", 13);
		region.Add("Dead Body", 8);
		region.CalculateFull();
	}
	public static void BuildRegionA()
	{
		EncounterTableBuilder region = new EncounterTableBuilder();
		region.Add("Bloodcrazed Orc Sign", 3);
		region.Add("Rogue Orc Band: 1d4 Bloodcrazed Orcs", 1);
		region.Add("Goblin Scout Tower", 2);
		region.Add("Goblin Scout Tower Foundation", 8);
		region.Add("Goblin Scout Party: 2 Goblin Snipers", 4);
		region.Add("Seawatch Hunting Party: 2d4 Scouts", 10);
		region.Add("Travelling Miner", 13);
		region.Add("Sundrack Herd: 2d6 Adult Sundrack and 2d6 Infant Sundrack", 8);
		region.Add("Wild Dragonweed Patch", 10);
		region.Add("Berry Patch, Eaten", 8);
		region.Add("Berry Patch, Uneaten", 3);
		region.Add("1d4 Brown Bears", 2);
		region.Add("Centipede Carcass", 9);
		region.Add("2d6 Giant Centipede", 3);
		region.Add("A Pleasant Hillside Stream", 13);
		region.Add("A Dead Body", 3);
		region.CalculateFull();
	}
	public EncounterTableBuilder()
	{
		encounters = new ArrayList<WeightPair>();
	}
	public void Add(String name, double weight)
	{
		encounters.add(new WeightPair(name, weight));
	}
	public void CalculateProbabilities()
	{
		double totalWeight = 0;
		for(WeightPair wp : encounters)
			totalWeight += wp.amount;
		double currentWeight = 0;
		System.out.println("Total Weight: " + totalWeight);
		System.out.println("Number of Encounters: " + encounters.size());
		for(WeightPair wp : encounters)
		{
			double start = currentWeight;
			double finish = currentWeight + wp.amount;
			int startPer = (int) (100 * start / totalWeight);
			int finishPer = (int) (100 * finish / totalWeight) - 1;
			if(finishPer < startPer)
				System.out.println("Highly Unlikely");
			else if(finishPer == startPer)
				System.out.println(finishPer);
			else
				System.out.println(startPer + "-" + finishPer);
			currentWeight = finish;
		}
	}
	public void CalculateNames()
	{
		double totalWeight = 0;
		for(WeightPair wp : encounters)
			totalWeight += wp.amount;
		double currentWeight = 0;
		System.out.println("Total Weight: " + totalWeight);
		System.out.println("Number of Encounters: " + encounters.size());
		for(WeightPair wp : encounters)
		{
			double start = currentWeight;
			double finish = currentWeight + wp.amount;
			int startPer = (int) (100 * start / totalWeight);
			int finishPer = (int) (100 * finish / totalWeight) - 1;
			if(finishPer < startPer)
				System.out.println(wp.encounterName);
			else if(finishPer == startPer)
				System.out.println(wp.encounterName);
			else
				System.out.println(wp.encounterName);
			currentWeight = finish;
		}
	}
	public void CalculateFull()
	{
		double totalWeight = 0;
		for(WeightPair wp : encounters)
			totalWeight += wp.amount;
		double currentWeight = 0;
		System.out.println("Total Weight: " + totalWeight);
		System.out.println("Number of Encounters: " + encounters.size());
		for(WeightPair wp : encounters)
		{
			double start = currentWeight;
			double finish = currentWeight + wp.amount;
			int startPer = (int) (100 * start / totalWeight);
			int finishPer = (int) (100 * finish / totalWeight) - 1;
			if(finishPer < startPer)
				System.out.println("Highly Unlikely: " + wp.encounterName);
			else if(finishPer == startPer)
				System.out.println(finishPer + ": " + wp.encounterName);
			else
				System.out.println(startPer + "-" + finishPer + ": " + wp.encounterName);
			currentWeight = finish;
		}
	}
	private class WeightPair
	{
		public WeightPair(String name, double amount)
		{
			encounterName = name;
			this.amount = amount;
		}
		String encounterName;
		double amount;
	}
}