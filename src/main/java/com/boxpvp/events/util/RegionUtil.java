package com.boxpvp.events.util;

import org.bukkit.Location;

public class RegionUtil {

    /**
     * Whether loc (a continuous player position) falls inside the cuboid spanned
     * by two selected BLOCKS a and b. Uses each corner's full block volume (not
     * just its raw min-corner coordinate) - otherwise a player standing in the
     * far half of the highest-coordinate block would be wrongly excluded,
     * making an e.g. 5-wide selection effectively only cover 4 blocks.
     */
    public static boolean contains(Location loc, Location a, Location b) {
        if (a == null || b == null || loc.getWorld() == null || !loc.getWorld().equals(a.getWorld())) return false;

        int minX = Math.min(a.getBlockX(), b.getBlockX());
        int maxX = Math.max(a.getBlockX(), b.getBlockX()) + 1;
        int minY = Math.min(a.getBlockY(), b.getBlockY());
        int maxY = Math.max(a.getBlockY(), b.getBlockY()) + 1;
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        int maxZ = Math.max(a.getBlockZ(), b.getBlockZ()) + 1;

        return loc.getX() >= minX && loc.getX() < maxX
                && loc.getY() >= minY && loc.getY() < maxY
                && loc.getZ() >= minZ && loc.getZ() < maxZ;
    }
}
