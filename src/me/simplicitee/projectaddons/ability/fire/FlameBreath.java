package me.simplicitee.projectaddons.ability.fire;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.ComboAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.ability.util.ComboManager.AbilityInformation;
import com.projectkorra.projectkorra.util.ClickType;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.TempBlock;

import me.simplicitee.projectaddons.ProjectAddons;

public class FlameBreath extends FireAbility implements AddonAbility, ComboAbility, Listener{
	
	private int fireTick;
	private double range;
	private double damage;
	private boolean burnGround, burnEntities, rainbow;
	private long duration;
	private Set<Breath> breaths;
	private Queue<Color> colors;
	
	private enum Color {
		RED("#ff0000"),
		GREEN("#00ff00"),
		BLUE("#0000ff"),
		YELLOW("#ffff00"),
		ORANGE("#ff6600"),
		CYAN("#00ffff"),
		PURPLE("#ff00ff");
		
		private String hex;
		
		private Color(String hex) {
			this.hex = hex;
		}
		
		public String getHex() {
			return hex;
		}
	}

	public FlameBreath(Player player) {
		super(player);
		
		if (bPlayer.isOnCooldown(this)) {
			return;
		}
		
		setFields();
		start();
	}
	
	private void setFields() {
		fireTick = ProjectAddons.instance.getConfig().getInt("Combos.FlameBreath.FireTick");
		range = ProjectAddons.instance.getConfig().getDouble("Combos.FlameBreath.Range");
		damage = ProjectAddons.instance.getConfig().getDouble("Combos.FlameBreath.Damage");
		burnGround = ProjectAddons.instance.getConfig().getBoolean("Combos.FlameBreath.Burn.Ground");
		burnEntities = ProjectAddons.instance.getConfig().getBoolean("Combos.FlameBreath.Burn.Entities");
		rainbow = ProjectAddons.instance.getConfig().getBoolean("Combos.FlameBreath.Rainbow");
		duration = ProjectAddons.instance.getConfig().getLong("Combos.FlameBreath.Duration");
		breaths = new HashSet<>();
		
		int turnsPerColor = 4;
		int amount = Color.values().length * turnsPerColor;
		colors = new LinkedList<>();
		for (int i = 0; i < amount; i++) {
			int index = (int) Math.floor(i/turnsPerColor);
			colors.add(Color.values()[index]);
		}
	}

	@Override
	public long getCooldown() {
		return ProjectAddons.instance.getConfig().getLong("Combos.FlameBreath.Cooldown");
	}

	@Override
	public Location getLocation() {
		return player.getEyeLocation();
	}
	
	@Override
	public List<Location> getLocations() {
		List<Location> locList = new ArrayList<>();
		for (Breath b : breaths) {
			locList.add(b.getLocation());
		}
		return locList;
	}

	@Override
	public String getName() {
		return "FlameBreath";
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	@Override
	public boolean isSneakAbility() {
		return true;
	}

	@Override
	public void progress() {
		if (!player.isOnline() || player.isDead()) {
			remove();
			return;
		}
		
		if (!player.isSneaking()) {
			remove();
			return;
		}
		
		if (!bPlayer.canBendIgnoreBinds(this)) {
			remove();
			return;
		}
		
		if (System.currentTimeMillis() > getStartTime() + duration) {
			remove();
			return;
		}
		
		Color c = colors.poll();
		colors.add(c);
		
		Breath b = new Breath(player, c);
		breaths.add(b);
		
		List<Breath> removal = new ArrayList<>();
		
		for (Breath breath : breaths) {
			if (breath.advanceLocation()) {
				float offset = (float) (0.2 * breath.getLocation().distance(player.getEyeLocation()));
				int amount = (int) Math.ceil(breath.getLocation().distance(player.getEyeLocation()));
				if (rainbow && player.hasPermission("bending.ability.FlameBreath.rainbow")) {
					displayColoredParticles(breath.getLocation(), amount, offset, breath.getColor().getHex());
				} else {
					playFirebendingParticles(breath.getLocation(), amount, offset/2, offset/2, offset/2);
				}
				
				if (Math.random() > 0.6) {
					playFirebendingSound(breath.getLocation());
				}
				
				for (Entity entity : GeneralMethods.getEntitiesAroundPoint(breath.getLocation(), offset * 2.5)) {
					if (entity instanceof LivingEntity && entity.getEntityId() != player.getEntityId()) {
						DamageHandler.damageEntity(entity, damage, this);
						entity.setVelocity(breath.getDirection().clone());
						
						if (burnEntities) {
							entity.setFireTicks(fireTick + 10);
						}
					} else if (entity instanceof Item) {
						entity.setFireTicks(fireTick + 40);
					}
				}
				
				if (burnGround) {
					if (GeneralMethods.isSolid(breath.getLocation().getBlock().getRelative(BlockFace.DOWN))) {
						if (breath.getLocation().getBlock().getType() != Material.FIRE) {
							new TempBlock(breath.getLocation().getBlock(), Material.FIRE).setRevertTime((fireTick/20)*1000 + 1000);
						}
					}
				}
			} else {
				removal.add(breath);
			}
		}
		
		breaths.removeAll(removal);
	}
	
	public void displayColoredParticles(Location loc, int amount, float offset, String hexVal) {
		Random r = new Random();
		for (int i = 0; i < amount; i++) {
			double x = Math.cos(r.nextDouble() * Math.PI * 2) * r.nextDouble() * offset;
			double y = Math.sin(r.nextDouble() * Math.PI * 2) * r.nextDouble() * offset;
			double z = Math.sin(r.nextDouble() * Math.PI * 2) * r.nextDouble() * offset;
			
			loc.add(x, y, z);
			
			GeneralMethods.displayColoredParticle(hexVal, loc);
			
			loc.subtract(x, y, z);
		}
	}

	@Override
	public Object createNewComboInstance(Player player) {
		return new FlameBreath(player);
	}

	@Override
	public ArrayList<AbilityInformation> getCombination() {
		ArrayList<AbilityInformation> combo = new ArrayList<>();
		combo.add(new AbilityInformation("HeatControl", ClickType.SHIFT_DOWN));
		combo.add(new AbilityInformation("HeatControl", ClickType.SHIFT_UP));
		combo.add(new AbilityInformation("HeatControl", ClickType.SHIFT_DOWN));
		combo.add(new AbilityInformation("HeatControl", ClickType.SHIFT_UP));
		combo.add(new AbilityInformation("HeatControl", ClickType.SHIFT_DOWN));
		return combo;
	}

	@Override
	public String getAuthor() {
		return "Simplicitee";
	}

	@Override
	public String getVersion() {
		return ProjectAddons.instance.version();
	}

	@Override
	public void load() {}
	
	@Override
	public String getDescription() {
		return "The greatest firebenders were able to breath fire! These firebenders learned from the majestic dragons that are now extinct, but fortunately they passed on their sacred bending arts to you! By breathing super-hot air, you can cause it to spontaneously combust, burning all entities and the ground within its radius!";
	}
	
	@Override
	public String getInstructions() {
		return "HeatControl (double tap sneak) > HeatControl (hold sneak)";
	}
	
	@Override
	public void remove() {
		super.remove();
		bPlayer.addCooldown(this);
	}

	@Override
	public void stop() {}

	public class Breath {
		
		protected Player player;
		protected Vector dir;
		protected Location start, loc;
		protected Color color;
		
		public Breath(Player player, Color color) {
			this.player = player;
			this.start = player.getEyeLocation().clone();
			this.dir = start.getDirection().clone().normalize().multiply(0.5);
			this.loc = start.clone();
			this.color = color;
		}
		
		public boolean advanceLocation() {
			loc = loc.add(dir);
			
			if (GeneralMethods.isSolid(loc.getBlock())) {
				return false;
			} else if (isWater(loc.getBlock())) {
				return false;
			} else if (GeneralMethods.isRegionProtectedFromBuild(player, loc)) {
				return false;
			} else if (start.distance(loc) > range) {
				return false;
			}
			
			return true;
		}
		
		public Vector getDirection() {
			return dir;
		}
		
		public Location getLocation() {
			return loc;
		}
		
		public Location getStart() {
			return start;
		}
		
		public Color getColor() {
			return color;
		}
	}
	
	@Override
	public boolean isEnabled() {
		return ProjectAddons.instance.getConfig().getBoolean("Combos.FlameBreath.Enabled");
	}
}
