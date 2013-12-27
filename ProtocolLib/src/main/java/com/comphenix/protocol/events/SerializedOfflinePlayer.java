/*
 *  ProtocolLib - Bukkit server library that allows access to the Minecraft protocol.
 *  Copyright (C) 2012 Kristian S. Stangeland
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the 
 *  GNU General Public License as published by the Free Software Foundation; either version 2 of 
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with this program; 
 *  if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 *  02111-1307 USA
 */

package com.comphenix.protocol.events;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.comphenix.protocol.utility.EnhancerFactory;

/**
 * Represents a player object that can be serialized by Java.
 * 
 * @author Kristian
 */
class SerializedOfflinePlayer implements OfflinePlayer, Serializable {

	/**
	 * Generated by Eclipse.
	 */
	private static final long serialVersionUID = -2728976288470282810L;

	private transient Location bedSpawnLocation;
	
	// Relevant data about an offline player
	private String name;
	private long firstPlayed;
	private long lastPlayed;
	private boolean operator;
	private boolean banned;
	private boolean playedBefore;
	private boolean online;
	private boolean whitelisted;
	
	// Proxy helper
	private static Map<String, Method> lookup = new ConcurrentHashMap<String, Method>();

	/**
	 * Constructor used by serialization.
	 */
	public SerializedOfflinePlayer() {
		// Do nothing
	}
	
	/**
	 * Initialize this serializable offline player from another player.
	 * @param offline - another player.
	 */
	public SerializedOfflinePlayer(OfflinePlayer offline) {
		this.name = offline.getName();
		this.firstPlayed = offline.getFirstPlayed();
		this.lastPlayed = offline.getLastPlayed();
		this.operator = offline.isOp();
		this.banned = offline.isBanned();
		this.playedBefore = offline.hasPlayedBefore();
		this.online = offline.isOnline();
		this.whitelisted = offline.isWhitelisted();
	}
	
	@Override
	public boolean isOp() {
		return operator;
	}

	@Override
	public void setOp(boolean operator) {
		this.operator = operator;
	}

	@Override
	public Map<String, Object> serialize() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Location getBedSpawnLocation() {
		return bedSpawnLocation;
	}

	@Override
	public long getFirstPlayed() {
		return firstPlayed;
	}

	@Override
	public long getLastPlayed() {
		return lastPlayed;
	}

	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public boolean hasPlayedBefore() {
		return playedBefore;
	}

	@Override
	public boolean isBanned() {
		return banned;
	}

	@Override
	public void setBanned(boolean banned) {
		this.banned = banned;
	}
	
	@Override
	public boolean isOnline() {
		return online;
	}

	@Override
	public boolean isWhitelisted() {
		return whitelisted;
	}

	@Override
	public void setWhitelisted(boolean whitelisted) {
		this.whitelisted = whitelisted;
	}

	private void writeObject(ObjectOutputStream output) throws IOException {
		output.defaultWriteObject();
		
		// Serialize the bed spawn location
		output.writeUTF(bedSpawnLocation.getWorld().getName());
		output.writeDouble(bedSpawnLocation.getX());
		output.writeDouble(bedSpawnLocation.getY());
		output.writeDouble(bedSpawnLocation.getZ());
	}
	
	private void readObject(ObjectInputStream input) throws ClassNotFoundException, IOException {
		input.defaultReadObject();

		// Well, this is a problem
		bedSpawnLocation = new Location(
				getWorld(input.readUTF()),
				input.readDouble(),
				input.readDouble(),
				input.readDouble()
		);
	}
	
	private World getWorld(String name) {
		try {
			// Try to get the world at least
			return Bukkit.getServer().getWorld(name);
		} catch (Exception e) {
			// Screw it
			return null;
		}
	}
	
	@Override
	public Player getPlayer() {
		try {
			// Try to get the real player underneath
			return Bukkit.getServer().getPlayerExact(name);
		} catch (Exception e) {
			return getProxyPlayer();
		}
	}
	
	/**
	 * Retrieve a player object that implements OfflinePlayer by refering to this object. 
	 * <p>
	 * All other methods cause an exception.
	 * @return Proxy object.
	 */
	public Player getProxyPlayer() {
		
		// Remember to initialize the method filter
		if (lookup.size() == 0) {
			// Add all public methods
			for (Method method : OfflinePlayer.class.getMethods()) {
				lookup.put(method.getName(), method);
			}
		}
		
    	// MORE CGLIB magic!
    	Enhancer ex = EnhancerFactory.getInstance().createEnhancer();
    	ex.setSuperclass(Player.class);
    	ex.setCallback(new MethodInterceptor() {
			@Override
			public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
				
				// There's no overloaded methods, so we don't care
				Method offlineMethod = lookup.get(method.getName());
				
				// Ignore all other methods
				if (offlineMethod == null) {
					throw new UnsupportedOperationException(
							"The method " + method.getName() + " is not supported for offline players.");
				}

				// Invoke our on method
				return offlineMethod.invoke(SerializedOfflinePlayer.this, args);
			}
    	});
    	
    	return (Player) ex.create();
	}
}
